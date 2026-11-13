package de.ls.trucknav.ui

import android.app.Application
import android.location.Location
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.ls.trucknav.data.SettingsRepository
import de.ls.trucknav.data.TruckProfile
import de.ls.trucknav.location.LocationFilter
import de.ls.trucknav.location.LocationSmoother
import de.ls.trucknav.location.LocationTracker
import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.Place
import de.ls.trucknav.model.RouteResult
import de.ls.trucknav.model.SpeedCamera
import de.ls.trucknav.nav.NavigationEngine
import de.ls.trucknav.net.PhotonApi
import de.ls.trucknav.net.RoutingException
import de.ls.trucknav.net.SpeedCameraService
import de.ls.trucknav.net.SpeedLimitService
import de.ls.trucknav.net.ValhallaApi
import de.ls.trucknav.tts.SpeechPlayer
import de.ls.trucknav.util.bearingDegrees
import de.ls.trucknav.util.distanceMeters
import de.ls.trucknav.util.distanceToPolylineMeters
import de.ls.trucknav.util.roundedDistance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.abs

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val photonApi = PhotonApi()
    private val valhallaApi = ValhallaApi()
    private val speedLimitApi = SpeedLimitService()
    private val cameraApi = SpeedCameraService()
    private val tracker = LocationTracker(application)
    private val locationFilter = LocationFilter()
    private val smoother = LocationSmoother()
    private val speech = SpeechPlayer(application)
    private val engine = NavigationEngine()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var routeRequestId = 0
    private var pendingRoute = false
    private var lastRecalcAt = 0L
    private var recalcCount = 0
    private var rerouteCapNotified = false

    // Tempolimit: gleitendes Stueck der zuletzt gefahrenen Strecke.
    private var speedLimitJob: Job? = null
    private val recentShape = ArrayList<GeoPoint>()
    private var lastSpeedLookupAt = 0L
    private var lastSpeedLookupPoint: GeoPoint? = null

    // Blitzer: zuletzt abgefragter Mittelpunkt und bereits angesagte IDs.
    private var cameraJob: Job? = null
    private var lastCameraCenter: GeoPoint? = null
    private var lastCameraFetchAt = 0L
    private val announcedCameras = HashSet<Long>()

    init {
        viewModelScope.launch {
            val settings = settingsRepo.load()
            speech.setEnabled(settings.voiceEnabled)
            _state.update {
                it.copy(
                    profile = settings.profile,
                    routingUrl = settings.routingUrl,
                    carFallback = settings.carFallback,
                    voiceEnabled = settings.voiceEnabled,
                    cameraWarnings = settings.cameraWarnings,
                )
            }
        }
    }

    // ---------------------------------------------------------------- Standort

    fun hasLocationPermission(): Boolean = tracker.hasPermission()

    fun onResume() {
        if (tracker.hasPermission()) {
            _state.update { it.copy(hasPermission = true) }
            startLocationTracking()
        }
    }

    fun onPause() {
        tracker.stop()
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted) {
            startLocationTracking()
        } else {
            _state.update {
                it.copy(notice = "Ohne Standortberechtigung kann keine Navigation gestartet werden.")
            }
        }
    }

    private fun startLocationTracking() {
        val started = tracker.start { location -> onLocation(location) }
        if (!started) {
            _state.update {
                it.copy(notice = "GPS ist nicht verfügbar. Bitte Standortdienste aktivieren.")
            }
        }
    }

    private fun onLocation(raw: Location) {
        val fix = locationFilter.accept(raw) ?: return

        val timeNanos = if (fix.elapsedRealtimeNanos > 0L) {
            fix.elapsedRealtimeNanos
        } else {
            SystemClock.elapsedRealtimeNanos()
        }
        val rawSpeed = if (fix.hasSpeed()) fix.speed.toDouble() else 0.0
        val rawBearing = if (fix.hasBearing() && rawSpeed > 1.5) fix.bearing else null
        val smoothed = smoother.smooth(
            rawPoint = GeoPoint(fix.latitude, fix.longitude),
            rawBearing = rawBearing,
            rawSpeedMps = rawSpeed,
            timeNanos = timeNanos,
        )

        val current = _state.value
        val navUpdate = if (current.navActive && current.route != null) {
            engine.update(smoothed.point, smoothed.speedMps)
        } else {
            null
        }

        _state.update {
            it.copy(
                location = smoothed.point,
                bearing = smoothed.bearing,
                accuracy = fix.accuracy,
                speedKmh = smoothed.speedMps * 3.6,
                hasPermission = true,
                nav = navUpdate ?: it.nav,
                arrived = navUpdate?.arrived ?: it.arrived,
            )
        }

        navUpdate?.let { update ->
            update.announcement?.let { speech.speak(it) }
            if (update.offRoute) {
                maybeRecalculate()
            } else {
                onBackOnRoute()
            }
        }

        // Tempolimit und Blitzerwarnung anhand der geglaetteten Position.
        maybeUpdateSpeedLimit(smoothed.point)
        maybeFetchCameras(smoothed.point)
        updateCameraWarning(
            point = smoothed.point,
            bearing = smoothed.bearing,
            routePoints = _state.value.route?.points,
            routeActive = _state.value.navActive && _state.value.nav?.offRoute == false,
        )

        val updated = _state.value
        if (pendingRoute && updated.destination != null && updated.route == null && !updated.routing) {
            pendingRoute = false
            planRoute()
        }
    }

    // ------------------------------------------------------ Tempolimit/Blitzer

    /** Aktualisiert das Tempolimit, sobald genug neue Strecke vorliegt. */
    private fun maybeUpdateSpeedLimit(point: GeoPoint) {
        val last = recentShape.lastOrNull()
        if (last == null || distanceMeters(last, point) > SHAPE_RESET_DISTANCE_M) {
            recentShape.clear()
        }
        if (recentShape.isEmpty() || distanceMeters(recentShape.last(), point) >= SHAPE_SPACING_M) {
            recentShape.add(point)
            if (recentShape.size > SHAPE_MAX_POINTS) recentShape.removeAt(0)
        }

        if (speedLimitJob?.isActive == true) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastSpeedLookupAt < SPEED_LOOKUP_MIN_INTERVAL_MS) return

        val anchor = lastSpeedLookupPoint
        if (anchor != null && distanceMeters(anchor, point) < SPEED_LOOKUP_DISTANCE_M) return
        if (recentShape.size < 2) return

        val extent = recentShape.zipWithNext { a, b -> distanceMeters(a, b) }.sum()
        if (extent < SHAPE_MIN_EXTENT_M) return

        lastSpeedLookupAt = now
        lastSpeedLookupPoint = point
        val baseUrl = _state.value.routingUrl
        val shape = recentShape.toList()
        speedLimitJob = viewModelScope.launch {
            val info = speedLimitApi.lookup(baseUrl, shape) ?: return@launch
            _state.update {
                it.copy(
                    speedLimitKmh = info.speedLimitKmh,
                    speedLimitRoad = info.roadName,
                )
            }
        }
    }

    /** Laedt Blitzer im Umkreis, wenn sich das Fahrzeug weit genug bewegt hat. */
    private fun maybeFetchCameras(point: GeoPoint) {
        if (!_state.value.cameraWarnings) return
        if (cameraJob?.isActive == true) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastCameraFetchAt < CAMERA_MIN_INTERVAL_MS) return
        val center = lastCameraCenter
        if (center != null && distanceMeters(center, point) < CAMERA_REFRESH_DISTANCE_M) return

        lastCameraFetchAt = now
        lastCameraCenter = point
        cameraJob = viewModelScope.launch {
            val cameras = cameraApi.nearby(point)
            if (cameras == null) {
                // Fehlversuch: nach der Mindestpause erneut versuchen.
                lastCameraCenter = null
                return@launch
            }
            _state.update { it.copy(cameras = cameras) }
        }
    }

    /** Bestimmt den naechsten Blitzer vor dem Fahrzeug und warnt einmalig. */
    private fun updateCameraWarning(
        point: GeoPoint,
        bearing: Float,
        routePoints: List<GeoPoint>?,
        routeActive: Boolean,
    ) {
        val snapshot = _state.value
        if (!snapshot.cameraWarnings || snapshot.cameras.isEmpty()) {
            if (snapshot.cameraWarning != null) {
                _state.update { it.copy(cameraWarning = null, cameraWarningDistance = 0.0) }
            }
            return
        }

        var nearest: SpeedCamera? = null
        var nearestDistance = Double.MAX_VALUE

        for (camera in snapshot.cameras) {
            val distance = distanceMeters(point, camera.point)
            if (distance > CAMERA_WARN_DISTANCE_M) continue

            // Nur Blitzer auf der befahrenen Route warnen, sonst droht
            // Fehlalarm von parallel verlaufenden Strassen.
            if (routeActive && routePoints != null &&
                distanceToPolylineMeters(camera.point, routePoints) > CAMERA_ROUTE_TOLERANCE_M
            ) {
                continue
            }

            // Nur warnen, wenn der Blitzer in Fahrtrichtung liegt.
            if (distance > 40.0 && bearingValid(bearing)) {
                val toCamera = bearingDegrees(point, camera.point)
                val delta = abs(((toCamera - bearing + 540.0) % 360.0) - 180.0)
                if (delta > CAMERA_AHEAD_ANGLE_DEG) continue
            }

            if (distance < nearestDistance) {
                nearest = camera
                nearestDistance = distance
            }
        }

        _state.update {
            it.copy(
                cameraWarning = nearest,
                cameraWarningDistance = if (nearest != null) nearestDistance else 0.0,
            )
        }

        if (nearest != null && nearestDistance <= CAMERA_ANNOUNCE_DISTANCE_M &&
            announcedCameras.add(nearest.id)
        ) {
            val limit = nearest.maxSpeedKmh?.let { ", $it Kilometer pro Stunde" }.orEmpty()
            speech.speak("Achtung, Blitzer in ${roundedDistance(nearestDistance)} Metern$limit.")
        }
    }

    private fun bearingValid(bearing: Float): Boolean =
        bearing.isFinite() && bearing >= 0f

    /** Setzt Tempolimit-Kontext zurueck (neue Route / Navigation beendet). */
    private fun resetRoadInfo() {
        speedLimitJob?.cancel()
        recentShape.clear()
        lastSpeedLookupAt = 0L
        lastSpeedLookupPoint = null
        _state.update { it.copy(speedLimitKmh = null, speedLimitRoad = null) }
    }

    /** Setzt Blitzerwarnungen zurueck (neue Route / Navigation beendet). */
    private fun resetCameraWarnings() {
        announcedCameras.clear()
        _state.update { it.copy(cameraWarning = null, cameraWarningDistance = 0.0) }
    }

    // ------------------------------------------------------------------ Suche

    fun openSearch() {
        _state.update { it.copy(showSearch = true) }
    }

    fun closeSearch() {
        _state.update { it.copy(showSearch = false) }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < 3) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _state.update { it.copy(searching = true) }
            try {
                val results = photonApi.search(query.trim(), _state.value.location)
                _state.update { it.copy(results = results, searching = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(searching = false, routeError = "Suche fehlgeschlagen: ${e.message}")
                }
            }
        }
    }

    fun selectDestination(place: Place) {
        routeJob?.cancel()
        routeRequestId++
        engine.clear()
        speech.stop()
        recalcCount = 0
        rerouteCapNotified = false
        pendingRoute = false
        resetRoadInfo()
        resetCameraWarnings()
        _state.update {
            it.copy(
                destination = place,
                query = place.name,
                results = emptyList(),
                searching = false,
                showSearch = false,
                route = null,
                routeError = null,
                navActive = false,
                nav = null,
                arrived = false,
                notice = null,
            )
        }
        if (_state.value.location == null) {
            pendingRoute = true
            _state.update { it.copy(notice = "GPS-Signal wird gesucht …") }
        } else {
            planRoute()
        }
    }

    // ------------------------------------------------------------------ Route

    /**
     * Startet eine Routenberechnung. Eine eventuell laufende Anfrage wird
     * abgebrochen; [routeRequestId] stellt sicher, dass nur die jeweils
     * letzte Anfrage das Ergebnis uebernehmen darf und dass `routing` auch
     * nach einem Abbruch zuverlaessig zurueckgesetzt wird.
     */
    fun planRoute() {
        val snapshot = _state.value
        val destination = snapshot.destination ?: return
        val start = snapshot.location
        if (start == null) {
            pendingRoute = true
            _state.update { it.copy(notice = "GPS-Signal wird gesucht …") }
            return
        }

        routeJob?.cancel()
        val token = ++routeRequestId
        routeJob = viewModelScope.launch {
            _state.update { it.copy(routing = true, routeError = null) }
            try {
                val route = valhallaApi.route(
                    from = start,
                    to = destination.point,
                    profile = snapshot.profile,
                    truck = true,
                    baseUrl = snapshot.routingUrl,
                )
                if (token != routeRequestId) return@launch
                applyRoute(route.copy(destination = destination))
            } catch (e: CancellationException) {
                throw e
            } catch (truckError: Exception) {
                if (token != routeRequestId) return@launch
                if (snapshot.carFallback) {
                    fallbackToCar(token, start, destination, snapshot, truckError)
                } else {
                    reportRouteFailure(token, truckError)
                }
            } finally {
                if (token == routeRequestId) {
                    _state.update { it.copy(routing = false) }
                }
            }
        }
    }

    private suspend fun fallbackToCar(
        token: Int,
        start: GeoPoint,
        destination: Place,
        snapshot: UiState,
        truckError: Exception,
    ) {
        try {
            val carRoute = valhallaApi.route(
                from = start,
                to = destination.point,
                profile = snapshot.profile,
                truck = false,
                baseUrl = snapshot.routingUrl,
            )
            if (token != routeRequestId) return
            applyRoute(carRoute.copy(destination = destination))
            _state.update {
                it.copy(
                    notice = "Keine LKW-Route gefunden – PKW-Route aktiv. Beschilderung beachten!",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (carError: Exception) {
            // Die LKW-Fehlermeldung ist fuer den Fahrer die relevantere.
            reportRouteFailure(token, truckError)
        }
    }

    /**
     * Fehler sichtbar machen: waehrend der Navigation gibt es keine
     * Fehlerkarte, deshalb zusaetzlich eine Snackbar-Meldung.
     */
    private fun reportRouteFailure(token: Int, error: Exception) {
        if (token != routeRequestId) return
        val message = friendlyError(error)
        _state.update {
            it.copy(
                routeError = message,
                notice = if (it.navActive) {
                    "Neuberechnung fehlgeschlagen: $message"
                } else {
                    it.notice
                },
            )
        }
    }

    private fun applyRoute(route: RouteResult) {
        engine.setRoute(route)
        recalcCount = 0
        rerouteCapNotified = false
        resetRoadInfo()
        resetCameraWarnings()
        _state.update {
            it.copy(
                route = route,
                routing = false,
                routeError = null,
                nav = if (it.navActive) engine.initialUpdate() else it.nav,
            )
        }
    }

    fun retryRoute() {
        _state.update { it.copy(routeError = null) }
        planRoute()
    }

    fun clearDestination() {
        routeJob?.cancel()
        routeRequestId++
        engine.clear()
        speech.stop()
        pendingRoute = false
        recalcCount = 0
        lastRecalcAt = 0L
        rerouteCapNotified = false
        resetRoadInfo()
        resetCameraWarnings()
        _state.update {
            it.copy(
                destination = null,
                route = null,
                query = "",
                results = emptyList(),
                routeError = null,
                navActive = false,
                nav = null,
                arrived = false,
                routing = false,
            )
        }
    }

    private fun maybeRecalculate() {
        val snapshot = _state.value
        if (!snapshot.navActive || snapshot.routing) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastRecalcAt < REROUTE_COOLDOWN_MS) return
        if (recalcCount >= MAX_REROUTES) {
            if (!rerouteCapNotified) {
                rerouteCapNotified = true
                _state.update {
                    it.copy(
                        notice = "Route verlassen, Neuberechnung mehrfach fehlgeschlagen. " +
                            "Bitte Navigation neu starten.",
                    )
                }
            }
            return
        }

        lastRecalcAt = now
        recalcCount++
        planRoute()
    }

    /** Zurueck auf der Route: Zaehler freigeben, damit spaeter wieder
     *  automatisch neu berechnet werden kann. */
    private fun onBackOnRoute() {
        if (recalcCount != 0 || rerouteCapNotified) {
            recalcCount = 0
            rerouteCapNotified = false
        }
    }

    // ------------------------------------------------------------- Navigation

    fun startNavigation() {
        val route = _state.value.route ?: return
        engine.setRoute(route)
        recalcCount = 0
        lastRecalcAt = 0L
        rerouteCapNotified = false
        resetRoadInfo()
        resetCameraWarnings()
        val initial = engine.initialUpdate()
        _state.update {
            it.copy(navActive = true, arrived = false, nav = initial, showSearch = false)
        }
    }

    fun stopNavigation() {
        engine.clear()
        speech.stop()
        recalcCount = 0
        lastRecalcAt = 0L
        rerouteCapNotified = false
        resetRoadInfo()
        resetCameraWarnings()
        _state.update {
            it.copy(navActive = false, nav = null, arrived = false)
        }
    }

    fun toggleVoice() {
        val enabled = !_state.value.voiceEnabled
        speech.setEnabled(enabled)
        if (!enabled) speech.stop()
        _state.update { it.copy(voiceEnabled = enabled) }
        viewModelScope.launch { settingsRepo.saveVoiceEnabled(enabled) }
    }

    // ------------------------------------------------------------- Einstellung

    fun openSettings() {
        _state.update { it.copy(showSettings = true) }
    }

    fun closeSettings() {
        _state.update { it.copy(showSettings = false) }
    }

    fun saveProfile(profile: TruckProfile) {
        val safe = profile.sanitized()
        _state.update { it.copy(profile = safe) }
        viewModelScope.launch { settingsRepo.saveProfile(safe) }
        if (_state.value.route != null) {
            planRoute()
        }
    }

    fun saveAppSettings(routingUrl: String, carFallback: Boolean, cameraWarnings: Boolean) {
        val cleanUrl = routingUrl.trim().ifBlank { SettingsRepository.DEFAULT_ROUTING_URL }
        val wasEnabled = _state.value.cameraWarnings
        _state.update {
            it.copy(
                routingUrl = cleanUrl,
                carFallback = carFallback,
                cameraWarnings = cameraWarnings,
            )
        }
        viewModelScope.launch {
            settingsRepo.saveRoutingUrl(cleanUrl)
            settingsRepo.saveCarFallback(carFallback)
            settingsRepo.saveCameraWarnings(cameraWarnings)
        }
        when {
            !cameraWarnings -> _state.update {
                it.copy(cameras = emptyList(), cameraWarning = null, cameraWarningDistance = 0.0)
            }

            !wasEnabled -> {
                // Gerade aktiviert: Bestand neu laden.
                lastCameraCenter = null
                lastCameraFetchAt = 0L
                _state.update { it.copy(cameraWarning = null, cameraWarningDistance = 0.0) }
            }
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun clearError() {
        _state.update { it.copy(routeError = null) }
    }

    private fun friendlyError(error: Exception): String = when (error) {
        is RoutingException -> error.message ?: "Route konnte nicht berechnet werden."
        is IOException -> "Keine Verbindung zum Routing-Server. Internet prüfen."
        else -> error.message?.takeIf { it.isNotBlank() }
            ?: "Route konnte nicht berechnet werden."
    }

    override fun onCleared() {
        tracker.stop()
        speech.shutdown()
        super.onCleared()
    }

    private companion object {
        /** Mindestabstand zwischen zwei automatischen Neuberechnungen. */
        const val REROUTE_COOLDOWN_MS = 8_000L

        /** Obergrenze pro Routenabschnitt; wird auf der Route zurueckgesetzt. */
        const val MAX_REROUTES = 10

        /** Punkte im gleitenden Streckenstueck fuer das Tempolimit. */
        const val SHAPE_MAX_POINTS = 12

        /** Mindestabstand zweier Punkte im Streckenstueck. */
        const val SHAPE_SPACING_M = 10.0

        /** Mindestlaenge des Streckenstuecks fuer ein Map-Matching. */
        const val SHAPE_MIN_EXTENT_M = 35.0

        /** Groesserer Sprung -> altes Streckenstueck verwerfen. */
        const val SHAPE_RESET_DISTANCE_M = 250.0

        /** Tempolimit fruehestens nach dieser Zeit erneut abfragen. */
        const val SPEED_LOOKUP_MIN_INTERVAL_MS = 12_000L

        /** Tempolimit erst nach dieser Strecke erneut abfragen. */
        const val SPEED_LOOKUP_DISTANCE_M = 120.0

        /** Blitzer im Umkreis dieses Radius laden. */
        const val CAMERA_REFRESH_DISTANCE_M = 4_000.0

        /** Mindestpause zwischen zwei Blitzer-Abfragen. */
        const val CAMERA_MIN_INTERVAL_MS = 60_000L

        /** Warnabstand vor einem Blitzer. */
        const val CAMERA_WARN_DISTANCE_M = 700.0

        /** Ab diesem Abstand wird der Blitzer einmal angesagt. */
        const val CAMERA_ANNOUNCE_DISTANCE_M = 450.0

        /** Maximaler Abstand des Blitzers zur Route. */
        const val CAMERA_ROUTE_TOLERANCE_M = 40.0

        /** Oeffnungswinkel des Warnkegels in Fahrtrichtung. */
        const val CAMERA_AHEAD_ANGLE_DEG = 75.0
    }
}
