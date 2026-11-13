package de.ls.trucknav.ui.map

import de.ls.trucknav.data.SettingsRepository
import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.RouteResult
import de.ls.trucknav.model.SpeedCamera
import de.ls.trucknav.ui.UiState
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Kartenebenen fuer Route, Fahrzeugposition und Ziel.
 * Wird einmal nach dem Laden des Kartenstils installiert und danach nur
 * noch mit neuen Geometrien befuellt.
 */
object MapLayers {

    const val STYLE_URL = SettingsRepository.DEFAULT_MAP_STYLE

    private const val ROUTE_SOURCE = "ls-route"
    private const val ROUTE_CASING = "ls-route-casing"
    private const val ROUTE_LINE = "ls-route-line"

    private const val USER_SOURCE = "ls-user"
    private const val USER_LAYER = "ls-user-layer"

    private const val DEST_SOURCE = "ls-destination"
    private const val DEST_LAYER = "ls-destination-layer"

    private const val CAMERA_SOURCE = "ls-cameras"
    private const val CAMERA_LAYER = "ls-cameras-layer"

    fun install(style: org.maplibre.android.maps.Style) {
        style.addSource(GeoJsonSource(ROUTE_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(USER_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(DEST_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(CAMERA_SOURCE, emptyCollection()))

        style.addLayer(
            LineLayer(ROUTE_CASING, ROUTE_SOURCE).withProperties(
                PropertyFactory.lineColor("#0A0A0B"),
                PropertyFactory.lineWidth(14f),
                PropertyFactory.lineOpacity(0.85f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            )
        )
        style.addLayer(
            LineLayer(ROUTE_LINE, ROUTE_SOURCE).withProperties(
                PropertyFactory.lineColor("#E02B36"),
                PropertyFactory.lineWidth(7f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            )
        )
        style.addLayer(
            CircleLayer(DEST_LAYER, DEST_SOURCE).withProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor("#FFC246"),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor("#0B141B"),
            )
        )
        // Blitzer liegen unter dem Fahrzeugpunkt, damit die eigene Position
        // immer sichtbar bleibt. Rot/weiß invers zum Fahrzeugpunkt.
        style.addLayer(
            CircleLayer(CAMERA_LAYER, CAMERA_SOURCE).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor("#E02B36"),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
            )
        )
        style.addLayer(
            CircleLayer(USER_LAYER, USER_SOURCE).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(4f),
                PropertyFactory.circleStrokeColor("#E02B36"),
            )
        )
    }

    fun render(style: org.maplibre.android.maps.Style, state: UiState) {
        setRoute(style, state.route)
        // Auf der Route den projizierten Punkt zeigen (liegt sauber auf der
        // Linie), abseits der Route die echte Position, damit der Fahrer
        // sieht, wie er sich von der Route entfernt.
        val nav = state.nav
        val userPoint = if (nav != null && !nav.offRoute) {
            nav.snapped
        } else {
            state.location ?: nav?.snapped
        }
        setPoint(style, USER_SOURCE, userPoint)
        setPoint(style, DEST_SOURCE, state.destination?.point)
        setCameras(style, if (state.cameraWarnings) state.cameras else emptyList())
    }

    private fun setRoute(style: org.maplibre.android.maps.Style, route: RouteResult?) {
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE) ?: return
        if (route == null || route.points.size < 2) {
            source.setGeoJson(emptyCollection())
            return
        }
        val coordinates = route.points.map { Point.fromLngLat(it.lon, it.lat) }
        val line = LineString.fromLngLats(coordinates)
        source.setGeoJson(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(line))))
    }

    private fun setPoint(style: org.maplibre.android.maps.Style, sourceId: String, point: GeoPoint?) {
        val source = style.getSourceAs<GeoJsonSource>(sourceId) ?: return
        if (point == null) {
            source.setGeoJson(emptyCollection())
            return
        }
        val feature = Feature.fromGeometry(Point.fromLngLat(point.lon, point.lat))
        source.setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
    }

    private fun setCameras(style: org.maplibre.android.maps.Style, cameras: List<SpeedCamera>) {
        val source = style.getSourceAs<GeoJsonSource>(CAMERA_SOURCE) ?: return
        if (cameras.isEmpty()) {
            source.setGeoJson(emptyCollection())
            return
        }
        val features = cameras.map { camera ->
            Feature.fromGeometry(Point.fromLngLat(camera.point.lon, camera.point.lat))
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun emptyCollection(): FeatureCollection = FeatureCollection.fromFeatures(emptyList<Feature>())
}
