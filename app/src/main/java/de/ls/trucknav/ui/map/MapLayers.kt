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
import de.ls.trucknav.util.distanceToPolylineMeters
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
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

    /** Ausweichrouten (hellrot), damit sie sich von der aktiven Route abheben. */
    private const val ROUTE_ALT_SOURCE = "ls-route-alt"
    private const val ROUTE_ALT_CASING = "ls-route-alt-casing"
    private const val ROUTE_ALT_LINE = "ls-route-alt-line"

    /** Farbe der aktiven Route (dunkelrot) und der Ausweichrouten (hellrot). */
    private const val ROUTE_ACTIVE_COLOR = "#E02B36"
    private const val ROUTE_ALTERNATE_COLOR = "#F3A0A5"

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

    /** Stationsname (fuer das Label unter Tankpool24-Markern). */
    private const val NAME_PROPERTY = "name"

    /** Eindeutige Kennung eines POI (Art + Nummer) fuer das Antippen. */
    private const val UID_PROPERTY = "uid"

    private const val DEST_SOURCE = "ls-destination"
    private const val DEST_LAYER = "ls-destination-layer"

    private const val CAMERA_SOURCE = "ls-cameras"
    private const val CAMERA_LAYER = "ls-cameras-layer"

    private const val TRAFFIC_SOURCE = "ls-traffic"
    private const val TRAFFIC_LAYER = "ls-traffic-layer"

    private const val POI_SOURCE = "ls-pois"
    private const val POI_LAYER = "ls-pois-layer"

    /** Beschriftungsebene unter den Tankpool24-Markern. */
    private const val POI_LABEL_LAYER = "ls-pois-label-layer"

    /** Ab dieser Zoomstufe werden die Stationsnamen eingeblendet. */
    private const val POI_LABEL_MIN_ZOOM = 12f

    private const val HEIGHT_SOURCE = "ls-heights"
    private const val HEIGHT_LAYER = "ls-heights-layer"

    /** Eigene, farbige Strassennummern-Badges (ueber der Route). */
    private const val SHIELD_LAYER = "ls-road-shield"
    private const val SHIELD_SOURCE = "openmaptiles"
    private const val SHIELD_SOURCE_LAYER = "transportation_name"

    /**
     * Maximaler Abstand einer Durchfahrtshoehe zur Route, damit sie auf der
     * Karte erscheint. Muss zu `MainViewModel.HEIGHT_ROUTE_TOLERANCE_M`
     * passen, damit Marker und Warnung dieselben Bruecken zeigen.
     */
    private const val HEIGHT_ROUTE_TOLERANCE_M = 40.0

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
        // Hintergruende der farbigen Strassenschilder fuer die Karten-Badges.
        MapIconFactory.addRoadShields(style, context)
        // Nach einem Stilwechsel sind die Quellen neu und leer: Erneut befuellen.
        lastRouteRef = null
        lastAltOptions = null
        lastAltSelected = -1
        lastDestination = null
        lastCameras = null
        lastTraffic = null
        lastPois = null
        lastHeights = null
        lastHeightsIn = null
        lastHeightsRoute = null

        style.addSource(GeoJsonSource(ROUTE_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(ROUTE_ALT_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(USER_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(DEST_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(CAMERA_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(TRAFFIC_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(POI_SOURCE, emptyCollection()))
        style.addSource(GeoJsonSource(HEIGHT_SOURCE, emptyCollection()))

        // Ausweichrouten zuerst (liegen unter der aktiven Route), hellrot.
        style.addLayer(
            LineLayer(ROUTE_ALT_CASING, ROUTE_ALT_SOURCE).withProperties(
                PropertyFactory.lineColor("#0A0A0B"),
                PropertyFactory.lineWidth(12f),
                PropertyFactory.lineOpacity(0.55f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            )
        )
        style.addLayer(
            LineLayer(ROUTE_ALT_LINE, ROUTE_ALT_SOURCE).withProperties(
                PropertyFactory.lineColor(ROUTE_ALTERNATE_COLOR),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            )
        )
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
                PropertyFactory.lineColor(ROUTE_ACTIVE_COLOR),
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
                        Expression.literal("tankpool24"), Expression.literal(MapIconFactory.TANKPOOL24),
                        Expression.literal(MapIconFactory.PARKING),
                    )
                ),
                PropertyFactory.iconSize(0.85f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        )
        // Stationsnamen der Tankpool24-Marker unter dem Symbol. Die
        // Beschriftung erscheint erst ab einer sinnvollen Zoomstufe, damit die
        // Karte beim Ueberblick nicht zugestellt wird.
        val poiLabels = SymbolLayer(POI_LABEL_LAYER, POI_SOURCE).withProperties(
            PropertyFactory.textField(Expression.get(NAME_PROPERTY)),
            PropertyFactory.textSize(11f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textHaloColor("#000000"),
            PropertyFactory.textHaloWidth(1.4f),
            PropertyFactory.textAnchor("top"),
            PropertyFactory.textOffset(arrayOf(0f, 1.2f)),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.textIgnorePlacement(false),
            PropertyFactory.textOptional(true),
            PropertyFactory.textMaxWidth(8f),
        )
        poiLabels.setFilter(
            Expression.eq(Expression.get(KIND_PROPERTY), Expression.literal("tankpool24"))
        )
        poiLabels.minZoom = POI_LABEL_MIN_ZOOM
        style.addLayer(poiLabels)
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
        // Farbige Strassennummern-Badges: ueber der Route, aber unter dem
        // eigenen Positionspfeil.
        installRoadShields(style)
        // Beim Aktualisieren der Symbolquellen (z. B. jede GPS-Position) wuerde
        // MapLibre die Symbole sonst aus- und wieder einblenden ("Blinken").
        // Die Platzierungs-Uebergaenge werden daher fuer alle Symbol-Ebenen
        // abgeschaltet, damit Marker sofort an der neuen Stelle erscheinen.
        disableSymbolFades(style)
    }

    /**
     * Ersetzt die (weissen) Strassenschilder des Kartenstils durch eigene,
     * nach Strassenklasse eingefaerbte Badges. Die eigenen Badges liegen ueber
     * der Routenlinie, damit die Strassennummer waehrend der Fuehrung sichtbar
     * bleibt.
     */
    private fun installRoadShields(style: Style) {
        // Vorhandene Schilder des Stils ausblenden (ID enthaelt "shield").
        style.layers.forEach { layer ->
            if (layer.id.contains("shield", ignoreCase = true)) {
                (layer as? SymbolLayer)?.setProperties(PropertyFactory.visibility("none"))
            }
        }
        if (style.getSource(SHIELD_SOURCE) == null) return

        // Erster Buchstabe der Nummer entscheidet ueber Farbe/Logo.
        val firstLetter = Expression.slice(
            Expression.get("ref"),
            Expression.literal(0),
            Expression.literal(1),
        )
        val shieldImage = Expression.match(
            firstLetter,
            Expression.literal("A"), Expression.literal(MapIconFactory.ROAD_SHIELD_A),
            Expression.literal("B"), Expression.literal(MapIconFactory.ROAD_SHIELD_B),
            Expression.literal("L"), Expression.literal(MapIconFactory.ROAD_SHIELD_B),
            Expression.literal("K"), Expression.literal(MapIconFactory.ROAD_SHIELD_B),
            Expression.literal("E"), Expression.literal(MapIconFactory.ROAD_SHIELD_E),
            Expression.literal(MapIconFactory.ROAD_SHIELD_DEFAULT),
        )
        val shieldTextColor = Expression.match(
            firstLetter,
            Expression.literal("A"), Expression.literal("#FFFFFF"),
            Expression.literal("E"), Expression.literal("#FFFFFF"),
            Expression.literal("#111111"),
        )
        val layer = SymbolLayer(SHIELD_LAYER, SHIELD_SOURCE)
        layer.setSourceLayer(SHIELD_SOURCE_LAYER)
        layer.setFilter(Expression.has("ref"))
        layer.minZoom = 8f
        layer.setProperties(
            PropertyFactory.iconImage(shieldImage),
            PropertyFactory.textField(Expression.get("ref")),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(11f),
            PropertyFactory.textColor(shieldTextColor),
            PropertyFactory.iconTextFit("both"),
            PropertyFactory.iconTextFitPadding(arrayOf(4f, 3f, 4f, 3f)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textOptional(true),
        )
        style.addLayerBelow(layer, USER_LAYER)
    }

    private val SYMBOL_LAYERS = listOf(
        DEST_LAYER,
        CAMERA_LAYER,
        TRAFFIC_LAYER,
        POI_LAYER,
        POI_LABEL_LAYER,
        HEIGHT_LAYER,
        SHIELD_LAYER,
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
        // Nicht gewaehlte Ausweichrouten hellrot darstellen; die aktive Route
        // liegt als dunkelrote Linie darueber.
        setRouteAlternatives(style, state.routeOptions, state.selectedRouteIndex)
        // Die eigene Position (Pfeil) wird ausschliesslich vom Frame-Loop in
        // [TruckMapView] gesetzt. Wuerde hier zusaetzlich geschrieben, springt
        // der Pfeil bei jedem GPS-Fix kurz auf den rohen Snap-Punkt und im
        // naechsten Frame wieder zurueck (sichtbares "Blinken").
        setPoint(style, DEST_SOURCE, state.destination?.point)
        setCameras(style, if (state.cameraWarnings) state.cameras else emptyList())
        setTraffic(style, state.trafficWarnings)
        setPois(style, if (state.truckPoisEnabled) state.truckPois else emptyList())
        // Sobald eine Route berechnet ist, werden nur Hoehen auf der Route
        // angezeigt; sonst wuerden Bruecken abseits der Strecke als Warnung
        // erscheinen.
        setHeights(
            style,
            if (state.heightWarningsEnabled) state.heightRestrictions else emptyList(),
            state.route?.points,
        )
    }

    private var lastRouteRef: RouteResult? = null
    private var lastAltOptions: List<RouteResult>? = null
    private var lastAltSelected: Int = -1
    private var lastDestination: GeoPoint? = null
    private var lastCameras: List<SpeedCamera>? = null
    private var lastTraffic: List<TrafficWarning>? = null
    private var lastPois: List<TruckPoi>? = null
    private var lastHeights: List<HeightRestriction>? = null

    /** Eingaben der letzten Hoehenberechnung (Referenzvergleich, s. [setHeights]). */
    private var lastHeightsIn: List<HeightRestriction>? = null
    private var lastHeightsRoute: List<GeoPoint>? = null

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
     * Zeichnet alle nicht gewaehlten Routenvorschlaege als hellrote Linien.
     * Die aktive Route liegt als dunkelrote Linie darueber. Wird bei jedem
     * GPS-Fix aufgerufen, deshalb billiger Referenzvergleich statt Inhalt.
     */
    private fun setRouteAlternatives(
        style: org.maplibre.android.maps.Style,
        options: List<RouteResult>,
        selectedIndex: Int,
    ) {
        if (options === lastAltOptions && selectedIndex == lastAltSelected) return
        lastAltOptions = options
        lastAltSelected = selectedIndex

        val source = style.getSourceAs<GeoJsonSource>(ROUTE_ALT_SOURCE) ?: return
        val features = options.mapIndexedNotNull { index, route ->
            if (index == selectedIndex || route.points.size < 2) return@mapIndexedNotNull null
            val coordinates = route.points.map { Point.fromLngLat(it.lon, it.lat) }
            Feature.fromGeometry(LineString.fromLngLats(coordinates))
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
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
                addStringProperty(KIND_PROPERTY, kindId(poi.kind))
                addStringProperty(UID_PROPERTY, uidOf(poi))
                poi.name?.let { addStringProperty(NAME_PROPERTY, it) }
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /** Sucht den angetippten LKW-Punkt an einer Bildschirmposition. */
    fun poiAt(map: MapLibreMap, latLng: LatLng, pois: List<TruckPoi>): TruckPoi? {
        if (pois.isEmpty()) return null
        val screen = runCatching { map.projection.toScreenLocation(latLng) }.getOrNull()
            ?: return null
        val features = runCatching { map.queryRenderedFeatures(screen, POI_LAYER) }
            .getOrNull() ?: return null
        for (feature in features) {
            val uid = feature.getStringProperty(UID_PROPERTY) ?: continue
            pois.firstOrNull { uidOf(it) == uid }?.let { return it }
        }
        return null
    }

    /** Attributwert der Art im GeoJSON-Feature. */
    private fun kindId(kind: TruckPoiKind): String = when (kind) {
        TruckPoiKind.PARKING -> "parking"
        TruckPoiKind.REST -> "rest"
        TruckPoiKind.FUEL -> "fuel"
        TruckPoiKind.TANKPOOL24 -> "tankpool24"
    }

    /** Eindeutige Kennung aus Art und Nummer. */
    private fun uidOf(poi: TruckPoi): String = "${kindId(poi.kind)}:${poi.id}"

    /**
     * Zeigt Durchfahrtshoehen als Marker. Ist eine Route vorhanden, werden nur
     * Beschraenkungen im nahen Umfeld der Route dargestellt. Da die Filterung
     * teuer ist, wird sie nur bei geaenderten Eingaben (Referenzvergleich)
     * erneut berechnet.
     */
    private fun setHeights(
        style: org.maplibre.android.maps.Style,
        restrictions: List<HeightRestriction>,
        routePoints: List<GeoPoint>?,
    ) {
        if (restrictions === lastHeightsIn && routePoints === lastHeightsRoute) return
        lastHeightsIn = restrictions
        lastHeightsRoute = routePoints

        val visible = if (routePoints == null || routePoints.size < 2) {
            restrictions
        } else {
            restrictions.filter {
                distanceToPolylineMeters(it.point, routePoints) <= HEIGHT_ROUTE_TOLERANCE_M
            }
        }
        if (visible == lastHeights) return
        lastHeights = visible
        val source = style.getSourceAs<GeoJsonSource>(HEIGHT_SOURCE) ?: return
        if (visible.isEmpty()) {
            source.setGeoJson(emptyCollection())
            return
        }
        val features = visible.map { restriction ->
            Feature.fromGeometry(Point.fromLngLat(restriction.point.lon, restriction.point.lat))
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun emptyCollection(): FeatureCollection = FeatureCollection.fromFeatures(emptyList<Feature>())
}
