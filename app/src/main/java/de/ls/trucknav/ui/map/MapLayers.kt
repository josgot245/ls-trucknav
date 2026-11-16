package de.ls.trucknav.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import de.ls.trucknav.R
import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.HeightRestriction
import de.ls.trucknav.model.RouteResult
import de.ls.trucknav.model.SpeedCamera
import de.ls.trucknav.model.TrafficKind
import de.ls.trucknav.model.TrafficWarning
import de.ls.trucknav.model.TruckPoi
import de.ls.trucknav.model.TruckPoiKind
import de.ls.trucknav.ui.UiState
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.TransitionOptions
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

    private const val ROUTE_SOURCE = "ls-route"
    private const val ROUTE_CASING = "ls-route-casing"
    private const val ROUTE_LINE = "ls-route-line"

    private const val USER_SOURCE = "ls-user"
    private const val USER_LAYER = "ls-user-layer"

    /** Bild-ID des Richtungspfeils (eigene Position). */
    const val USER_ARROW_ID = "ls-user-arrow"

    /** Rastergroesse des Pfeils in dp (scharf auch bei Skalierung). */
    private const val ARROW_DP = 40

    /** Attributname der Fahrtrichtung im GeoJSON-Feature. */
    private const val BEARING_PROPERTY = "bearing"

    /** Attributname der Art (Farbe) bei Meldungen, POIs und Hoehen. */
    private const val KIND_PROPERTY = "kind"

    private const val DEST_SOURCE = "ls-destination"
    private const val DEST_LAYER = "ls-destination-layer"

    private const val CAMERA_SOURCE = "ls-cameras"
    private const val CAMERA_LAYER = "ls-cameras-layer"

    private const val TRAFFIC_SOURCE = "ls-traffic"
    private const val TRAFFIC_LAYER = "ls-traffic-layer"

    private const val POI_SOURCE = "ls-pois"
    private const val POI_LAYER = "ls-pois-layer"

    private const val HEIGHT_SOURCE = "ls-heights"
    private const val HEIGHT_LAYER = "ls-heights-layer"

    /**
     * Registriert den Richtungspfeil als Bild im Stil. Muss vor [install]
     * aufgerufen werden, da die Symbol-Ebene das Bild referenziert.
     */
    fun addUserArrow(style: Style, context: Context) {
        if (style.getImage(USER_ARROW_ID) != null) return
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_nav_arrow) ?: return
        val density = context.resources.displayMetrics.density
        val px = (ARROW_DP * density).toInt().coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        bitmap.density = context.resources.displayMetrics.densityDpi
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(canvas)
        style.addImage(USER_ARROW_ID, bitmap)
    }

    /**
     * Hebt im dunklen Kartenstil die Strassen hervor, damit sie bei Nacht
     * deutlich sichtbar bleiben. Es werden nur die Linienfarben gesetzt.
     */
    fun highlightDarkRoads(style: Style) {
        setLineColor(style, "highway_motorway_inner", "#A6ACB4")
        setLineColor(style, "highway_motorway_subtle", "#6E747C")
        setLineColor(style, "highway_motorway_casing", "#2A2A2E")
        setLineColor(style, "highway_major_inner", "#868C94")
        setLineColor(style, "highway_major_subtle", "#6E747C")
        setLineColor(style, "highway_major_casing", "#2A2A2E")
        setLineColor(style, "highway_minor", "#5C626A")
        setLineColor(style, "highway_path", "#3E444B")
    }

    private fun setLineColor(style: Style, layerId: String, color: String) {
        val layer = style.getLayerAs<LineLayer>(layerId) ?: return
        layer.setProperties(PropertyFactory.lineColor(color))
    }

    fun install(style: org.maplibre.android.maps.Style, context: Context) {
        // Symbole (Ziel, Blitzer, Verkehr, POIs, Hoehen) als Bitmaps anlegen.
        MapIconFactory.addAll(style, context)
        // Nach einem Stilwechsel sind die Quellen neu und leer: Erneut befuellen.
        lastRouteRef = null
        lastDestination = null
        lastCameras = null
        lastTraffic = null
        lastPois = null
        lastHeights = null

        style.addSource(GeoJsonSource(ROUTE_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(USER_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(DEST_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(CAMERA_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(TRAFFIC_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(POI_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(HEIGHT_SOURCE, emptyCollection()))

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
        // Ziel als Flaggen-Symbol.
        style.addLayer(
            SymbolLayer(DEST_LAYER, DEST_SOURCE).withProperties(
                PropertyFactory.iconImage(MapIconFactory.DESTINATION),
                PropertyFactory.iconSize(0.95f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        )
        // Blitzer als Kamera-Symbol.
        style.addLayer(
            SymbolLayer(CAMERA_LAYER, CAMERA_SOURCE).withProperties(
                PropertyFactory.iconImage(MapIconFactory.CAMERA),
                PropertyFactory.iconSize(0.85f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        )
        // Verkehrsmeldungen der Autobahn GmbH (Symbol je Art).
        style.addLayer(
            SymbolLayer(TRAFFIC_LAYER, TRAFFIC_SOURCE).withProperties(
                PropertyFactory.iconImage(
                    Expression.match(
                        Expression.get(KIND_PROPERTY),
                        Expression.literal("traffic"), Expression.literal(MapIconFactory.TRAFFIC),
                        Expression.literal("closure"), Expression.literal(MapIconFactory.CLOSURE),
                        Expression.literal("roadworks"), Expression.literal(MapIconFactory.ROADWORKS),
                        Expression.literal(MapIconFactory.TRAFFIC),
                    )
                ),
                PropertyFactory.iconSize(0.9f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        )
        // LKW-Punkte aus OpenStreetMap (Symbol je Art).
        style.addLayer(
            SymbolLayer(POI_LAYER, POI_SOURCE).withProperties(
                PropertyFactory.iconImage(
                    Expression.match(
                        Expression.get(KIND_PROPERTY),
                        Expression.literal("parking"), Expression.literal(MapIconFactory.PARKING),
                        Expression.literal("rest"), Expression.literal(MapIconFactory.REST),
                        Expression.literal("fuel"), Expression.literal(MapIconFactory.FUEL),
                        Expression.literal(MapIconFactory.PARKING),
                    )
                ),
                PropertyFactory.iconSize(0.85f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        )
        // Durchfahrtshoehen-Beschraenkungen (Symbol mit "m").
        style.addLayer(
            SymbolLayer(HEIGHT_LAYER, HEIGHT_SOURCE).withProperties(
                PropertyFactory.iconImage(MapIconFactory.HEIGHT),
                PropertyFactory.iconSize(0.9f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        )
        // Eigene Position als Richtungspfeil: weiß gefuellt, roter Rand.
        // icon-rotate dreht im Uhrzeigersinn auf die Fahrtrichtung; mit
        // rotation-alignment "map" bleibt der Pfeil korrekt, egal ob die
        // Karte im Navigationsmodus mitdreht oder nach Norden zeigt.
        style.addLayer(
            SymbolLayer(USER_LAYER, USER_SOURCE).withProperties(
                PropertyFactory.iconImage(USER_ARROW_ID),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconRotationAlignment("map"),
                PropertyFactory.iconRotate(Expression.get(BEARING_PROPERTY)),
            )
        )
        // Beim Aktualisieren der Symbolquellen (z. B. jede GPS-Position) wuerde
        // MapLibre die Symbole sonst aus- und wieder einblenden ("Blinken").
        // Die Platzierungs-Uebergaenge werden daher fuer alle Symbol-Ebenen
        // abgeschaltet, damit Marker sofort an der neuen Stelle erscheinen.
        disableSymbolFades(style)
    }

    private val SYMBOL_LAYERS = listOf(
        DEST_LAYER,
        CAMERA_LAYER,
        TRAFFIC_LAYER,
        POI_LAYER,
        HEIGHT_LAYER,
        USER_LAYER,
    )

    private fun disableSymbolFades(style: Style) {
        val noFade = TransitionOptions(0L, 0L, false)
        SYMBOL_LAYERS.forEach { id ->
            style.getLayerAs<SymbolLayer>(id)?.setIconOpacityTransition(noFade)
        }
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
        setUserPoint(style, userPoint, state.bearing)
        setPoint(style, DEST_SOURCE, state.destination?.point)
        setCameras(style, if (state.cameraWarnings) state.cameras else emptyList())
        setTraffic(style, state.trafficWarnings)
        setPois(style, if (state.truckPoisEnabled) state.truckPois else emptyList())
        setHeights(style, if (state.heightWarningsEnabled) state.heightRestrictions else emptyList())
    }

    private var lastRouteRef: RouteResult? = null
    private var lastDestination: GeoPoint? = null
    private var lastCameras: List<SpeedCamera>? = null
    private var lastTraffic: List<TrafficWarning>? = null
    private var lastPois: List<TruckPoi>? = null
    private var lastHeights: List<HeightRestriction>? = null

    private fun setRoute(style: org.maplibre.android.maps.Style, route: RouteResult?) {
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE) ?: return
        if (route === lastRouteRef) {
            return
        }
        lastRouteRef = route
        if (route == null || route.points.size < 2) {
            source.setGeoJson(emptyCollection())
            return
        }
        val coordinates = route.points.map { Point.fromLngLat(it.lon, it.lat) }
        val line = LineString.fromLngLats(coordinates)
        source.setGeoJson(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(line))))
    }

    /**
     * Setzt die eigene Position an eine bereits interpolierte Stelle. Wird vom
     * Frame-Loop mit derselben Position aufgerufen wie die Kamera, damit der
     * Pfeil waehrend der Fahrt ruhig auf dem Bildschirmmittepunkt bleibt statt
     * einmal pro GPS-Fix zu springen.
     */
    fun setUserPosition(style: Style, lat: Double, lon: Double, bearing: Float) {
        setUserPoint(style, GeoPoint(lat, lon), bearing)
    }

    private fun setUserPoint(style: org.maplibre.android.maps.Style, point: GeoPoint?, bearing: Float) {
        val source = style.getSourceAs<GeoJsonSource>(USER_SOURCE) ?: return
        if (point == null) {
            source.setGeoJson(emptyCollection())
            return
        }
        val feature = Feature.fromGeometry(Point.fromLngLat(point.lon, point.lat))
        feature.addNumberProperty(BEARING_PROPERTY, bearing.toDouble())
        source.setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
    }

    private fun setPoint(style: org.maplibre.android.maps.Style, sourceId: String, point: GeoPoint?) {
        if (sourceId == DEST_SOURCE && point == lastDestination) return
        if (sourceId == DEST_SOURCE) lastDestination = point
        val source = style.getSourceAs<GeoJsonSource>(sourceId) ?: return
        if (point == null) {
            source.setGeoJson(emptyCollection())
            return
        }
        val feature = Feature.fromGeometry(Point.fromLngLat(point.lon, point.lat))
        source.setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
    }

    private fun setCameras(style: org.maplibre.android.maps.Style, cameras: List<SpeedCamera>) {
        if (cameras == lastCameras) return
        lastCameras = cameras
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

    private fun setTraffic(style: org.maplibre.android.maps.Style, warnings: List<TrafficWarning>) {
        if (warnings == lastTraffic) return
        lastTraffic = warnings
        val source = style.getSourceAs<GeoJsonSource>(TRAFFIC_SOURCE) ?: return
        if (warnings.isEmpty()) {
            source.setGeoJson(emptyCollection())
            return
        }
        val features = warnings.map { warning ->
            Feature.fromGeometry(Point.fromLngLat(warning.point.lon, warning.point.lat)).apply {
                addStringProperty(
                    KIND_PROPERTY,
                    when (warning.kind) {
                        TrafficKind.TRAFFIC -> "traffic"
                        TrafficKind.CLOSURE -> "closure"
                        TrafficKind.ROADWORKS -> "roadworks"
                    },
                )
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun setPois(style: org.maplibre.android.maps.Style, pois: List<TruckPoi>) {
        if (pois == lastPois) return
        lastPois = pois
        val source = style.getSourceAs<GeoJsonSource>(POI_SOURCE) ?: return
        if (pois.isEmpty()) {
            source.setGeoJson(emptyCollection())
            return
        }
        val features = pois.map { poi ->
            Feature.fromGeometry(Point.fromLngLat(poi.point.lon, poi.point.lat)).apply {
                addStringProperty(
                    KIND_PROPERTY,
                    when (poi.kind) {
                        TruckPoiKind.PARKING -> "parking"
                        TruckPoiKind.REST -> "rest"
                        TruckPoiKind.FUEL -> "fuel"
                    },
                )
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun setHeights(style: org.maplibre.android.maps.Style, restrictions: List<HeightRestriction>) {
        if (restrictions == lastHeights) return
        lastHeights = restrictions
        val source = style.getSourceAs<GeoJsonSource>(HEIGHT_SOURCE) ?: return
        if (restrictions.isEmpty()) {
            source.setGeoJson(emptyCollection())
            return
        }
        val features = restrictions.map { restriction ->
            Feature.fromGeometry(Point.fromLngLat(restriction.point.lon, restriction.point.lat))
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun emptyCollection(): FeatureCollection = FeatureCollection.fromFeatures(emptyList<Feature>())
}
