package de.ls.trucknav.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.ls.trucknav.data.MapStyles
import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.RouteResult
import de.ls.trucknav.ui.UiState
import de.ls.trucknav.util.distanceMeters
import kotlinx.coroutines.isActive
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.abs
import kotlin.math.exp

/**
 * MapLibre-Karte mit Route, Fahrzeugposition und Ziel.
 * Die Karte wird per AndroidView eingebettet, damit der native MapView seine
 * volle Leistung behaelt.
 *
 * Im Navigationsmodus folgt die Kamera der Position ueber einen Frame-Loop
 * mit weicher Interpolation statt bei jedem GPS-Fix zu springen.
 */
@Composable
fun TruckMapView(
    state: UiState,
    modifier: Modifier = Modifier,
    recenterSignal: Int = 0,
    controls: MapControls = remember { MapControls() },
    topPadding: Dp = 150.dp,
    bottomPadding: Dp = 250.dp,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }
    val mapHolder = remember { mutableStateOf<MapLibreMap?>(null) }
    val followEnabled = remember { mutableStateOf(true) }
    // Zusaetzlicher Zoom waehrend der Navigation (Kamera-Folgemodus).
    val zoomBias = remember { mutableStateOf(0.0) }
    var styleReady by remember { mutableStateOf(false) }
    // Zaehlt jeden geladenen Stil hoch, damit die Ebenen danach neu befuellt werden.
    var styleGeneration by remember { mutableStateOf(0) }
    var appliedStyleUrl by remember { mutableStateOf<String?>(null) }
    var lastFittedRoute by remember { mutableStateOf<RouteResult?>(null) }
    var centeredOnce by remember { mutableStateOf(false) }

    val latestState = rememberUpdatedState(state)

    // Laedt einen Kartenstil und installiert die eigenen Ebenen darauf.
    fun applyMapStyle(map: MapLibreMap) {
        val url = latestState.value.mapStyleUrl
        appliedStyleUrl = url
        map.setStyle(Style.Builder().fromUri(url)) { style ->
            MapLayers.addUserArrow(style, context)
            MapLayers.install(style, context)
            // Im dunklen Design Strassen hervorheben, damit sie nachts sichtbar sind.
            if (url == MapStyles.DARK) MapLayers.highlightDarkRoads(style)
            styleReady = true
            styleGeneration++
        }
    }

    AndroidView(
        factory = {
            mapView.also { view ->
                view.getMapAsync { map ->
                    mapHolder.value = map
                    map.uiSettings.apply {
                        isAttributionEnabled = true
                        isLogoEnabled = false
                        isCompassEnabled = false
                        isRotateGesturesEnabled = true
                    }
                    // Verschiebt der Fahrer die Karte selbst, pausiert das
                    // automatische Folgen bis "zentrieren"/Kompass gedrueckt wird.
                    map.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            followEnabled.value = false
                        }
                    }
                    // Waehrend manueller Bedienung (Folgen pausiert) zeigt das
                    // Kompass-Symbol, ob die Karte nach Norden ausgerichtet ist.
                    map.addOnCameraIdleListener {
                        if (!followEnabled.value) {
                            controls.northUp = abs(map.cameraPosition.bearing) < 1.0f
                        }
                    }
                    controls.zoomInAction = { zoomBy(map, zoomBias, followEnabled, +1.0) }
                    controls.zoomOutAction = { zoomBy(map, zoomBias, followEnabled, -1.0) }
                    controls.compassAction = {
                        // Zwischen Fahrtrichtung und Norden wechseln und das
                        // Folgen wieder aufnehmen (wie in ueblichen Navi-Apps).
                        controls.northUp = !controls.northUp
                        followEnabled.value = true
                    }
                    applyMapStyle(map)
                }
            }
        },
        modifier = modifier,
    )

    // Tag/Nacht: Stil wechseln, sobald sich die wirksame URL aendert.
    LaunchedEffect(state.mapStyleUrl, mapHolder.value, styleReady) {
        if (!styleReady) return@LaunchedEffect
        val map = mapHolder.value ?: return@LaunchedEffect
        if (appliedStyleUrl == state.mapStyleUrl) return@LaunchedEffect
        applyMapStyle(map)
    }

    DisposableEffect(lifecycleOwner, mapView) {
        var started = false
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (!started) {
                        mapView.onStart()
                        started = true
                    }
                }

                Lifecycle.Event.ON_RESUME -> {
                    if (!started) {
                        mapView.onStart()
                        started = true
                    }
                    mapView.onResume()
                }

                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> {
                    mapView.onStop()
                    started = false
                }

                Lifecycle.Event.ON_DESTROY -> {
                    if (!destroyed) {
                        mapView.onDestroy()
                        destroyed = true
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val current = lifecycleOwner.lifecycle.currentState
        if (current.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onStart()
            mapView.onResume()
            started = true
        } else if (current.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
            started = true
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapView.onStop() }
            if (!destroyed) runCatching { mapView.onDestroy() }
        }
    }

    LaunchedEffect(
        styleReady,
        styleGeneration,
        mapHolder.value,
        state.route,
        state.location,
        state.bearing,
        state.nav?.snapped,
        state.nav?.offRoute,
        state.destination,
        state.cameras,
        state.cameraWarnings,
        state.trafficWarnings,
        state.truckPois,
        state.truckPoisEnabled,
        state.heightRestrictions,
        state.heightWarningsEnabled,
    ) {
        if (!styleReady) return@LaunchedEffect
        val map = mapHolder.value ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        MapLayers.render(style, state)
    }

    // Kamera-Folgemodus: laeuft mit und ohne Zielfuehrung und interpoliert
    // Position/Kurs weich, statt bei jedem Fix zu springen. Nach manuellem
    // Verschieben pausiert das Folgen, bis "zentrieren"/Kompass gedrueckt wird.
    // styleGeneration in den Keys: nach einem Stilwechsel (Tag/Nacht) sind die
    // Quellen leer, der Loop startet neu und setzt den Pfeil sofort wieder.
    LaunchedEffect(styleReady, styleGeneration, mapHolder.value, state.navActive, topPadding, bottomPadding) {
        if (!styleReady) return@LaunchedEffect
        val map = mapHolder.value ?: return@LaunchedEffect

        val topPaddingPx = with(density) { topPadding.roundToPx() }
        val bottomPaddingPx = with(density) { bottomPadding.roundToPx() }
        map.setPadding(0, topPaddingPx, 0, bottomPaddingPx)

        var camLat = Double.NaN
        var camLon = Double.NaN
        var camBearing = 0.0
        var camZoom = 17.0
        var camTilt = 45.0
        var lastFrameNanos = 0L
        var lastAppliedNanos = 0L
        var appliedLat = Double.NaN
        var appliedLon = Double.NaN
        var appliedBearing = 0.0
        var appliedZoom = 0.0
        // Zuletzt geschriebene Pfeilposition/-richtung (einziger Schreiber der
        // Symbolquelle, damit nichts doppelt gesetzt wird und der Pfeil ruhig
        // bleibt).
        var userLat = Double.NaN
        var userLon = Double.NaN
        var userBearing = Float.NaN

        fun writeUserMarker(lat: Double, lon: Double, bearing: Float) {
            val samePlace = !userLat.isNaN() &&
                distanceMeters(GeoPoint(userLat, userLon), GeoPoint(lat, lon)) < 0.05
            val sameBearing = !userBearing.isNaN() && abs(userBearing - bearing) < 0.2f
            if (samePlace && sameBearing) return
            userLat = lat
            userLon = lon
            userBearing = bearing
            map.style?.let { style -> MapLayers.setUserPosition(style, lat, lon, bearing) }
        }

        while (isActive) {
            withFrameNanos { frameNanos ->
                val st = latestState.value
                val nav = st.nav
                val navigating = st.navActive
                // Ausserhalb der Route zeigt die Kamera die echte Position,
                // damit sichtbar bleibt, dass sich das Fahrzeug bewegt.
                val targetOrNull = when {
                    navigating && nav != null && !nav.offRoute -> nav.snapped
                    st.location != null -> st.location
                    nav != null -> nav.snapped
                    else -> null
                }
                val target = targetOrNull ?: return@withFrameNanos

                if (!followEnabled.value) {
                    // Nach manuellem Verschieben nicht zurueckspringen; erst
                    // "zentrieren"/Kompass nimmt das Folgen wieder auf. Den
                    // Pfeil aber weiter an der echten Position mitfuehren.
                    camLat = Double.NaN
                    appliedLat = Double.NaN
                    lastFrameNanos = 0L
                    writeUserMarker(target.lat, target.lon, st.bearing)
                    return@withFrameNanos
                }

                val offRoute = navigating && nav?.offRoute == true
                val targetZoom = (
                    if (navigating) (if (offRoute) 16.2 else 17.0) else FREE_ZOOM
                    ) + zoomBias.value
                val targetTilt = if (navigating) (if (offRoute) 30.0 else 45.0) else 0.0
                // Kompass: entweder in Fahrtrichtung oder nach Norden.
                val targetBearing = if (controls.northUp) 0.0 else normalizeBearing(st.bearing.toDouble())

                if (camLat.isNaN() || lastFrameNanos == 0L) {
                    camLat = target.lat
                    camLon = target.lon
                    camBearing = targetBearing
                    camZoom = targetZoom
                    camTilt = targetTilt
                } else {
                    val dt = ((frameNanos - lastFrameNanos) / 1e9).coerceIn(0.004, 0.25)
                    val alphaPos = 1.0 - exp(-dt / POSITION_TAU_S)
                    camLat += (target.lat - camLat) * alphaPos
                    camLon += (target.lon - camLon) * alphaPos

                    val alphaBearing = 1.0 - exp(-dt / BEARING_TAU_S)
                    camBearing = normalizeBearing(
                        camBearing +
                            shortestAngleDelta(camBearing, targetBearing) * alphaBearing,
                    )
                    camZoom += (targetZoom - camZoom) * alphaPos
                    camTilt += (targetTilt - camTilt) * alphaPos
                }
                lastFrameNanos = frameNanos

                if (lastAppliedNanos == 0L || frameNanos - lastAppliedNanos >= CAMERA_FRAME_NS) {
                    lastAppliedNanos = frameNanos
                    val moved = if (appliedLat.isNaN()) {
                        Double.MAX_VALUE
                    } else {
                        distanceMeters(
                            GeoPoint(appliedLat, appliedLon),
                            GeoPoint(camLat, camLon),
                        )
                    }
                    val turned = abs(shortestAngleDelta(appliedBearing, camBearing))
                    if (moved > 0.15 || turned > 0.25 || abs(camZoom - appliedZoom) > 0.002) {
                        appliedLat = camLat
                        appliedLon = camLon
                        appliedBearing = camBearing
                        appliedZoom = camZoom
                        MapCamera.followPosition(map, camLat, camLon, camBearing, camZoom, camTilt)
                    }
                    // Pfeil an derselben interpolierten Stelle zeichnen wie
                    // die Kamera. Dadurch bleibt er ruhig im Bildmittelpunkt
                    // statt einmal pro GPS-Fix nach vorn zu springen.
                    writeUserMarker(camLat, camLon, st.bearing)
                }
            }
        }
    }

    // Ohne Zielfuehrung folgt die Kamera der Position. Liegt eine Route vor
    // (Navigation noch nicht gestartet), wird die Gesamtstrecke angezeigt;
    // das Folgen pausiert dann bis "zentrieren"/Kompass.
    LaunchedEffect(
        styleReady,
        mapHolder.value,
        state.navActive,
        state.route,
        state.location,
        topPadding,
        bottomPadding,
    ) {
        if (!styleReady || state.navActive) return@LaunchedEffect
        val map = mapHolder.value ?: return@LaunchedEffect

        val topPaddingPx = with(density) { topPadding.roundToPx() }
        val bottomPaddingPx = with(density) { bottomPadding.roundToPx() }
        val sidePadding = with(density) { 24.dp.roundToPx() }

        val route = state.route
        if (route != null && route !== lastFittedRoute) {
            lastFittedRoute = route
            centeredOnce = true
            followEnabled.value = false
            map.setPadding(sidePadding, topPaddingPx, sidePadding, bottomPaddingPx)
            MapCamera.fitRoute(map, route)
        } else if (state.location != null && !centeredOnce) {
            centeredOnce = true
            // Nur wenn das Folgen pausiert ist, muss die Kamera aktiv gesetzt
            // werden; sonst uebernimmt der Frame-Loop.
            if (!followEnabled.value) {
                MapCamera.center(map, state.location)
            }
        }
    }

    LaunchedEffect(recenterSignal) {
        if (recenterSignal <= 0) return@LaunchedEffect
        // Folgen wieder aufnehmen; der Frame-Loop richtet die Kamera aus.
        followEnabled.value = true
    }

    // Wird das Ziel verworfen (z. B. nach dem Anzeigen einer Route), folgt die
    // Karte im freien Fahrmodus wieder der eigenen Position. Zuvor hatte das
    // Anzeigen der Route das Folgen pausiert.
    LaunchedEffect(state.destination) {
        if (state.destination == null && !state.navActive) {
            followEnabled.value = true
        }
    }

    // Beim Start der Navigation mit Fahrtrichtung und neutralem Zoom beginnen.
    LaunchedEffect(state.navActive) {
        if (state.navActive) {
            controls.northUp = false
            zoomBias.value = 0.0
            followEnabled.value = true
        }
    }
}

/**
 * Zoomt einen Schritt. Laeuft das Folgen, wird der Zoom als Versatz gemerkt,
 * damit die weiche Kamera ihn beruecksichtigt; sonst wird direkt gezoomt.
 */
private fun zoomBy(
    map: MapLibreMap,
    zoomBias: androidx.compose.runtime.MutableState<Double>,
    followEnabled: androidx.compose.runtime.MutableState<Boolean>,
    delta: Double,
) {
    if (followEnabled.value) {
        zoomBias.value = (zoomBias.value + delta).coerceIn(-4.0, 4.0)
    } else {
        map.animateCamera(CameraUpdateFactory.zoomBy(delta), 300)
    }
}

/** Zoomstufe im freien Fahrmodus (ohne Zielfuehrung). */
private const val FREE_ZOOM = 16.0

/** Zeitkonstante der Positionsglaettung (Sekunden). */
private const val POSITION_TAU_S = 0.35

/** Zeitkonstante der Kursglaettung (Sekunden). */
private const val BEARING_TAU_S = 0.40

/** Kamera max. ~30-mal pro Sekunde neu setzen. */
private const val CAMERA_FRAME_NS = 33_000_000L

private fun normalizeBearing(value: Double): Double = (value % 360.0 + 360.0) % 360.0

private fun shortestAngleDelta(from: Double, to: Double): Double =
    (to - from + 540.0) % 360.0 - 180.0
