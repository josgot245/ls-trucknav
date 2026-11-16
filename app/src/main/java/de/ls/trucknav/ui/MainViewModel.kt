package de.ls.trucknav.ui

import android.app.Application
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.ls.trucknav.BuildConfig
import de.ls.trucknav.data.MapStyleMode
import de.ls.trucknav.data.MapStyles
import de.ls.trucknav.data.SettingsRepository
import de.ls.trucknav.data.TruckProfile
import de.ls.trucknav.location.LocationFilter
import de.ls.trucknav.location.LocationSmoother
import de.ls.trucknav.location.LocationTracker
import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.HeightRestriction
import de.ls.trucknav.model.Place
import de.ls.trucknav.model.RouteResult
import de.ls.trucknav.model.SpeedCamera
import de.ls.trucknav.model.TrafficKind
import de.ls.trucknav.model.TrafficWarning
import de.ls.trucknav.nav.NavigationEngine
import de.ls.trucknav.net.AutobahnService
import de.ls.trucknav.net.PhotonApi
import de.ls.trucknav.net.RoutingException
import de.ls.trucknav.net.SpeedCameraService
import de.ls.trucknav.net.SpeedLimitService
import de.ls.trucknav.net.TruckInfoService
import de.ls.trucknav.net.UpdateService
import de.ls.trucknav.net.ValhallaApi
import de.ls.trucknav.tts.SpeechPlayer
import de.ls.trucknav.util.ApkInstaller
import de.ls.trucknav.util.DayNight
import de.ls.trucknav.util.Version
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
    private val autobahnApi = AutobahnService()
    private val truckInfoApi = TruckInfoService()
    private val updateApi = UpdateService()
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

    // Verkehrsmeldungen (Autobahn GmbH): zuletzt abgefragte Autobahn.
    private var trafficJob: Job? = null
    private var lastTrafficRoad: String? = null
    private var lastTrafficFetchAt = 0L
    private val announcedTraffic = HashSet<String>()

    // LKW-Punkte und Durchfahrtshoehen (OpenStreetMap).
    private var poiJob: Job? = null
    private var lastPoiCenter: GeoPoint? = null
    private var lastPoiFetchAt = 0L

    private var heightJob: Job? = null
    private var lastHeightCenter: GeoPoint? = null
    private var lastHeightFetchAt = 0L
    private val announcedHeights = HashSet<Long>()

    init {
        _state.update { it.copy(currentVersion = BuildConfig.VERSION_NAME) }
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
                    trafficEnabled = settings.trafficWarnings,
                    truckPoisEnabled = settings.truckPois,
                    heightWarningsEnabled = settings.heightWarnings,
                    mapStyleMode = settings.mapStyleMode,
                    favorites = settings.favorites,
                    history = settings.history,
                )
            }
            updateMapStyle()
        }
        // Tag/Nacht-Wechsel auch im Stand (ohne GPS-Fix) nachziehen.
        viewModelScope.launch {
            while (true) {
                delay(MAP_STYLE_REFRESH_MS)
                updateMapStyle()
            }
        }
        // Beim Start einmal still nach Updates suchen.
        checkForUpdates(manual = false)
    }

    /** Waehlt den Kartenstil anhand von Modus und aktueller Tageszeit. */
    private fun updateMapStyle() {
        val snapshot = _state.value
        val night = DayNight.isNight(snapshot.location)
        val url = MapStyles.url(snapshot.mapStyleMode, night)
        if (url != snapshot.mapStyleUrl) {
            _state.update { it.copy(mapStyleUrl = url) }
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

        // Verkehrsmeldungen (Autobahn), LKW-Punkte und Durchfahrtshoehen.
        val routePoints = _state.value.route?.points
        val routeActive = _state.value.navActive && _state.value.nav?.offRoute == false
        maybeFetchTraffic(smoothed.point, _state.value.speedLimitRoad)
        updateTrafficWarning(smoothed.point, smoothed.bearing, routePoints, routeActive)
        maybeFetchTruckInfo(smoothed.point)
        updateHeightWarning(smoothed.point, smoothed.bearing, routePoints, routeActive)
        updateMapStyle()

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

    // --------------------------------------------- Verkehrsmeldungen (Autobahn)

    /** Laedt Verkehrsmeldungen, wenn das Fahrzeug auf einer Autobahn unterwegs ist. */
    private fun maybeFetchTraffic(point: GeoPoint, roadName: String?) {
        if (!_state.value.trafficEnabled) return
        val road = AutobahnService.normalizeRoad(roadName)
        if (road == null) {
            // Nicht auf der Autobahn: alte Meldungen verwerfen.
            lastTrafficRoad = null
            if (_state.value.trafficWarnings.isNotEmpty() || _state.value.trafficWarning != null) {
                _state.update {
                    it.copy(
                        trafficWarnings = emptyList(),
                        trafficWarning = null,
                        trafficWarningDistance = 0.0,
                    )
                }
            }
            return
        }
        if (trafficJob?.isActive == true) return

        val now = SystemClock.elapsedRealtime()
        val roadChanged = road != lastTrafficRoad
        if (!roadChanged && now - lastTrafficFetchAt < TRAFFIC_MIN_INTERVAL_MS) return

        lastTrafficFetchAt = now
        lastTrafficRoad = road
        trafficJob = viewModelScope.launch {
            val warnings = autobahnApi.warnings(road)
            if (warnings == null) {
                // Fehlversuch: naechster Fix darf es erneut versuchen.
                lastTrafficRoad = null
                return@launch
            }
            _state.update { it.copy(trafficWarnings = warnings) }
        }
    }

    /** Bestimmt die naechste relevante Verkehrsmeldung und warnt einmalig. */
    private fun updateTrafficWarning(
        point: GeoPoint,
        bearing: Float,
        routePoints: List<GeoPoint>?,
        routeActive: Boolean,
    ) {
        val snapshot = _state.value
        if (!snapshot.trafficEnabled || snapshot.trafficWarnings.isEmpty()) {
            if (snapshot.trafficWarning != null) {
                _state.update { it.copy(trafficWarning = null, trafficWarningDistance = 0.0) }
            }
            return
        }

        var nearest: TrafficWarning? = null
        var nearestDistance = Double.MAX_VALUE

        for (warning in snapshot.trafficWarnings) {
            val distance = distanceMeters(point, warning.point)
            if (distance > TRAFFIC_WARN_DISTANCE_M) continue
            if (routeActive && routePoints != null &&
                distanceToPolylineMeters(warning.point, routePoints) > TRAFFIC_ROUTE_TOLERANCE_M
            ) {
                continue
            }
            if (distance > 100.0 && bearingValid(bearing)) {
                val toWarning = bearingDegrees(point, warning.point)
                val delta = abs(((toWarning - bearing + 540.0) % 360.0) - 180.0)
                if (delta > TRAFFIC_AHEAD_ANGLE_DEG) continue
            }
            if (distance < nearestDistance) {
                nearest = warning
                nearestDistance = distance
            }
        }

        _state.update {
            it.copy(
                trafficWarning = nearest,
                trafficWarningDistance = if (nearest != null) nearestDistance else 0.0,
            )
        }

        if (nearest != null && nearestDistance <= TRAFFIC_ANNOUNCE_DISTANCE_M &&
            announcedTraffic.add(nearest.id)
        ) {
            val text = when {
                nearest.blocked -> "Achtung, Sperrung in ${roundedDistance(nearestDistance)} Metern."
                nearest.jam -> "Achtung, Stau in ${roundedDistance(nearestDistance)} Metern."
                nearest.kind == TrafficKind.ROADWORKS ->
                    "Achtung, Baustelle in ${roundedDistance(nearestDistance)} Metern."
                else -> "Achtung, Verkehrsmeldung in ${roundedDistance(nearestDistance)} Metern."
            }
            speech.speak(text)
        }
    }

    // ------------------------------------- LKW-Punkte & Durchfahrtshoehen (OSM)

    private fun maybeFetchTruckInfo(point: GeoPoint) {
        maybeFetchPois(point)
        maybeFetchHeights(point)
    }

    private fun maybeFetchPois(point: GeoPoint) {
        if (!_state.value.truckPoisEnabled) {
            if (_state.value.truckPois.isNotEmpty()) {
                _state.update { it.copy(truckPois = emptyList()) }
            }
            return
        }
        if (poiJob?.isActive == true) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastPoiFetchAt < POI_MIN_INTERVAL_MS) return
        val center = lastPoiCenter
        if (center != null && distanceMeters(center, point) < POI_REFRESH_DISTANCE_M) return

        lastPoiFetchAt = now
        lastPoiCenter = point
        poiJob = viewModelScope.launch {
            val pois = truckInfoApi.pois(point)
            if (pois == null) {
                lastPoiCenter = null
                return@launch
            }
            _state.update { it.copy(truckPois = pois) }
        }
    }

    private fun maybeFetchHeights(point: GeoPoint) {
        if (!_state.value.heightWarningsEnabled) return
        if (heightJob?.isActive == true) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastHeightFetchAt < HEIGHT_MIN_INTERVAL_MS) return
        val center = lastHeightCenter
        if (center != null && distanceMeters(center, point) < HEIGHT_REFRESH_DISTANCE_M) return

        lastHeightFetchAt = now
        lastHeightCenter = point
        heightJob = viewModelScope.launch {
            val restrictions = truckInfoApi.heightRestrictions(point)
            if (restrictions == null) {
                lastHeightCenter = null
                return@launch
            }
            _state.update { it.copy(heightRestrictions = restrictions) }
        }
    }

    /** Warnt vor Bruecken/Unterfuehrungen, die niedriger als das Fahrzeug sind. */
    private fun updateHeightWarning(
        point: GeoPoint,
        bearing: Float,
        routePoints: List<GeoPoint>?,
        routeActive: Boolean,
    ) {
        val snapshot = _state.value
        if (!snapshot.heightWarningsEnabled) {
            if (snapshot.heightWarning != null) {
                _state.update { it.copy(heightWarning = null, heightWarningDistance = 0.0) }
            }
            return
        }

        val vehicleHeight = snapshot.profile.height
        var nearest: HeightRestriction? = null
        var nearestDistance = Double.MAX_VALUE

        for (restriction in snapshot.heightRestrictions) {
            // Passt das Fahrzeug durch, ist die Beschraenkung irrelevant.
            if (restriction.maxHeightM >= vehicleHeight) continue
            val distance = distanceMeters(point, restriction.point)
            if (distance > HEIGHT_WARN_DISTANCE_M) continue
            if (routeActive && routePoints != null &&
                distanceToPolylineMeters(restriction.point, routePoints) > HEIGHT_ROUTE_TOLERANCE_M
            ) {
                continue
            }
            if (distance > 60.0 && bearingValid(bearing)) {
                val toRestriction = bearingDegrees(point, restriction.point)
                val delta = abs(((toRestriction - bearing + 540.0) % 360.0) - 180.0)
                if (delta > HEIGHT_AHEAD_ANGLE_DEG) continue
            }
            if (distance < nearestDistance) {
                nearest = restriction
                nearestDistance = distance
            }
        }

        _state.update {
            it.copy(
                heightWarning = nearest,
                heightWarningDistance = if (nearest != null) nearestDistance else 0.0,
            )
        }

        if (nearest != null && nearestDistance <= HEIGHT_ANNOUNCE_DISTANCE_M &&
            announcedHeights.add(nearest.id)
        ) {
            val height = String.format(java.util.Locale.GERMANY, "%.2f", nearest.maxHeightM)
            speech.speak(
                "Achtung, Durchfahrtshöhe $height Meter in ${roundedDistance(nearestDistance)} Metern.",
            )
        }
    }

    /** Setzt Verkehrs-, POI- und Hoehenwarnungen zurueck (neue Route). */
    private fun resetTruckInfo() {
        announcedTraffic.clear()
        announcedHeights.clear()
        _state.update {
            it.copy(
                trafficWarning = null,
                trafficWarningDistance = 0.0,
                heightWarning = null,
                heightWarningDistance = 0.0,
            )
        }
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
        resetTruckInfo()
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

    // ------------------------------------------------------- Favoriten/Verlauf

    /** Fuegt ein Ziel zu den Favoriten hinzu bzw. entfernt es wieder. */
    fun toggleFavorite(place: Place) {
        val current = _state.value.favorites
        val updated = if (current.any { it == place }) {
            current.filterNot { it == place }
        } else {
            listOf(place) + current
        }
        _state.update { it.copy(favorites = updated) }
        viewModelScope.launch { settingsRepo.saveFavorites(updated) }
    }

    /** Loescht den Verlauf der zuletzt gestarteten Ziele. */
    fun clearHistory() {
        if (_state.value.history.isEmpty()) return
        _state.update { it.copy(history = emptyList()) }
        viewModelScope.launch { settingsRepo.saveHistory(emptyList()) }
    }

    /** Merkt sich ein gestartetes Ziel im Verlauf (neueste zuerst). */
    private fun recordHistory(place: Place) {
        val updated = (listOf(place) + _state.value.history.filterNot { it == place })
            .take(HISTORY_MAX)
        _state.update { it.copy(history = updated) }
        viewModelScope.launch { settingsRepo.saveHistory(updated) }
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
        resetTruckInfo()
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
        resetTruckInfo()
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
        resetTruckInfo()
        val initial = engine.initialUpdate()
        _state.update {
            it.copy(navActive = true, arrived = false, nav = initial, showSearch = false)
        }
        (route.destination ?: _state.value.destination)?.let { recordHistory(it) }
    }

    fun stopNavigation() {
        engine.clear()
        speech.stop()
        recalcCount = 0
        lastRecalcAt = 0L
        rerouteCapNotified = false
        resetRoadInfo()
        resetCameraWarnings()
        resetTruckInfo()
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

    fun saveAppSettings(
        routingUrl: String,
        carFallback: Boolean,
        cameraWarnings: Boolean,
        trafficWarnings: Boolean,
        truckPois: Boolean,
        heightWarnings: Boolean,
        mapStyleMode: MapStyleMode,
    ) {
        val cleanUrl = routingUrl.trim().ifBlank { SettingsRepository.DEFAULT_ROUTING_URL }
        val wasCamera = _state.value.cameraWarnings
        val wasTraffic = _state.value.trafficEnabled
        val wasPois = _state.value.truckPoisEnabled
        val wasHeights = _state.value.heightWarningsEnabled
        _state.update {
            it.copy(
                routingUrl = cleanUrl,
                carFallback = carFallback,
                cameraWarnings = cameraWarnings,
                trafficEnabled = trafficWarnings,
                truckPoisEnabled = truckPois,
                heightWarningsEnabled = heightWarnings,
                mapStyleMode = mapStyleMode,
            )
        }
        updateMapStyle()
        viewModelScope.launch {
            settingsRepo.saveRoutingUrl(cleanUrl)
            settingsRepo.saveCarFallback(carFallback)
            settingsRepo.saveCameraWarnings(cameraWarnings)
            settingsRepo.saveTrafficWarnings(trafficWarnings)
            settingsRepo.saveTruckPois(truckPois)
            settingsRepo.saveHeightWarnings(heightWarnings)
            settingsRepo.saveMapStyleMode(mapStyleMode)
        }
        when {
            !cameraWarnings -> _state.update {
                it.copy(cameras = emptyList(), cameraWarning = null, cameraWarningDistance = 0.0)
            }

            !wasCamera -> {
                // Gerade aktiviert: Bestand neu laden.
                lastCameraCenter = null
                lastCameraFetchAt = 0L
                _state.update { it.copy(cameraWarning = null, cameraWarningDistance = 0.0) }
            }
        }
        when {
            !trafficWarnings -> {
                lastTrafficRoad = null
                _state.update {
                    it.copy(
                        trafficWarnings = emptyList(),
                        trafficWarning = null,
                        trafficWarningDistance = 0.0,
                    )
                }
            }

            !wasTraffic -> {
                lastTrafficRoad = null
                lastTrafficFetchAt = 0L
            }
        }
        when {
            !truckPois -> _state.update { it.copy(truckPois = emptyList()) }
            !wasPois -> {
                lastPoiCenter = null
                lastPoiFetchAt = 0L
            }
        }
        when {
            !heightWarnings -> _state.update {
                it.copy(
                    heightRestrictions = emptyList(),
                    heightWarning = null,
                    heightWarningDistance = 0.0,
                )
            }

            !wasHeights -> {
                lastHeightCenter = null
                lastHeightFetchAt = 0L
            }
        }
    }

    // ------------------------------------------------------------------ Update

    /**
     * Prueft GitHub auf eine neuere Version. [manual] steuert, ob auch bei
     * "kein Update"/Fehler eine Rueckmeldung erfolgt.
     */
    fun checkForUpdates(manual: Boolean = false) {
        if (_state.value.updateChecking) return
        _state.update { it.copy(updateChecking = true, updateError = null, updateMessage = null) }
        viewModelScope.launch {
            val info = updateApi.latest()
            if (info == null) {
                _state.update {
                    it.copy(
                        updateChecking = false,
                        updateError = if (manual) {
                            "Update-Prüfung fehlgeschlagen. Internetverbindung prüfen."
                        } else {
                            null
                        },
                    )
                }
                return@launch
            }
            val current = _state.value.currentVersion
            if (Version.isNewer(info.version, current)) {
                _state.update {
                    it.copy(
                        updateChecking = false,
                        updateInfo = info,
                        updateError = null,
                        updateMessage = null,
                        // Waehrend der Navigation nicht ungefragt einblenden.
                        showUpdateDialog = manual || !it.navActive,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        updateChecking = false,
                        updateInfo = null,
                        updateMessage = if (manual) "Die App ist aktuell (Version $current)." else null,
                    )
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _state.update { it.copy(showUpdateDialog = false) }
    }

    /** Laedt die neue APK herunter und startet die Installation. */
    fun installUpdate() {
        if (_state.value.updateDownloading) return
        val info = _state.value.updateInfo ?: return
        val app = getApplication<Application>()

        // Fehlt die Berechtigung "Unbekannte Apps", zuerst dorthin fuehren.
        if (!ApkInstaller.canInstall(app)) {
            ApkInstaller.requestInstallPermission(app)
            _state.update {
                it.copy(
                    updateError = null,
                    updateMessage = "\"Unbekannte Apps installieren\" für LS TruckNav erlauben " +
                        "und danach erneut auf Aktualisieren tippen.",
                )
            }
            return
        }

        val url = info.downloadUrl
        if (url == null) {
            // Kein APK-Asset vorhanden: Release-Seite im Browser oeffnen.
            openUpdatePage(info.pageUrl)
            return
        }
        _state.update {
            it.copy(
                updateDownloading = true,
                updateProgress = 0f,
                updateError = null,
                updateMessage = null,
            )
        }
        viewModelScope.launch {
            val file = updateApi.download(url, app) { progress ->
                _state.update { it.copy(updateProgress = progress) }
            }
            if (file == null) {
                _state.update {
                    it.copy(
                        updateDownloading = false,
                        updateProgress = null,
                        updateError = "Download fehlgeschlagen. Bitte erneut versuchen.",
                    )
                }
                return@launch
            }
            _state.update { it.copy(updateDownloading = false, updateProgress = null) }
            ApkInstaller.install(app, file)
        }
    }

    private fun openUpdatePage(url: String) {
        if (url.isBlank()) return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
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
        /** Tag/Nacht-Kartenstil regelmaessig neu pruefen (auch im Stand). */
        const val MAP_STYLE_REFRESH_MS = 5 * 60_000L

        /** Mindestabstand zwischen zwei automatischen Neuberechnungen. */
        const val REROUTE_COOLDOWN_MS = 8_000L

        /** Obergrenze pro Routenabschnitt; wird auf der Route zurueckgesetzt. */
        const val MAX_REROUTES = 10

        /** Anzahl der im Verlauf gemerkten Ziele. */
        const val HISTORY_MAX = 8

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

        /** Mindestpause zwischen zwei Verkehrsmeldungs-Abfragen. */
        const val TRAFFIC_MIN_INTERVAL_MS = 120_000L

        /** Warnabstand vor einer Verkehrsmeldung. */
        const val TRAFFIC_WARN_DISTANCE_M = 5_000.0

        /** Ab diesem Abstand wird die Meldung einmal angesagt. */
        const val TRAFFIC_ANNOUNCE_DISTANCE_M = 1_000.0

        /** Maximaler Abstand der Meldung zur Route. */
        const val TRAFFIC_ROUTE_TOLERANCE_M = 60.0

        /** Oeffnungswinkel des Warnkegels in Fahrtrichtung. */
        const val TRAFFIC_AHEAD_ANGLE_DEG = 75.0

        /** Mindestpause zwischen zwei LKW-Punkt-Abfragen. */
        const val POI_MIN_INTERVAL_MS = 180_000L

        /** LKW-Punkte erst nach dieser Strecke neu laden. */
        const val POI_REFRESH_DISTANCE_M = 4_000.0

        /** Mindestpause zwischen zwei Hoehen-Abfragen. */
        const val HEIGHT_MIN_INTERVAL_MS = 180_000L

        /** Hoehen erst nach dieser Strecke neu laden. */
        const val HEIGHT_REFRESH_DISTANCE_M = 2_000.0

        /** Warnabstand vor einer Durchfahrtshoehe. */
        const val HEIGHT_WARN_DISTANCE_M = 800.0

        /** Ab diesem Abstand wird die Hoehe einmal angesagt. */
        const val HEIGHT_ANNOUNCE_DISTANCE_M = 500.0

        /** Maximaler Abstand der Hoehe zur Route. */
        const val HEIGHT_ROUTE_TOLERANCE_M = 40.0

        /** Oeffnungswinkel des Warnkegels in Fahrtrichtung. */
        const val HEIGHT_AHEAD_ANGLE_DEG = 70.0
    }
}
