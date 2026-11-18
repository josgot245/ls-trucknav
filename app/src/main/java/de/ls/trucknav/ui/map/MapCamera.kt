package de.ls.trucknav.ui.map

import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.RouteResult
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

/** Kamerabewegungen (Uebersicht, Folgemodus, Zentrieren). */
object MapCamera {

    fun fitRoute(map: MapLibreMap, route: RouteResult) {
        if (route.points.size < 2) return
        val builder = LatLngBounds.Builder()
        route.points.forEach { builder.include(LatLng(it.lat, it.lon)) }
        val bounds = runCatching { builder.build() }.getOrNull() ?: return
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140), 900)
    }

    /**
     * Setzt die Kamera ohne Animation direkt auf den Zielzustand. Wird vom
     * Frame-Loop in [TruckMapView] etwa 30-mal pro Sekunde mit bereits
     * geglaetteten Werten aufgerufen.
     */
    fun followPosition(
        map: MapLibreMap,
        lat: Double,
        lon: Double,
        bearing: Double,
        zoom: Double,
        tilt: Double = 45.0,
        padding: DoubleArray? = null,
    ) {
        val builder = CameraPosition.Builder()
            .target(LatLng(lat, lon))
            .zoom(zoom)
            .tilt(tilt)
            .bearing(bearing)
        // Das Padding muss bei jeder Kamerafahrt mitgegeben werden. Ein nur
        // per setPadding() gesetzter Wert wird von moveCamera() nicht
        // zuverlaessig uebernommen; dann zentriert die Kamera auf den
        // geometrischen Mittelpunkt und der Pfeil verschwindet im Querformat
        // hinter der linken Karte.
        if (padding != null) builder.padding(padding)
        map.moveCamera(CameraUpdateFactory.newCameraPosition(builder.build()))
    }

    fun center(
        map: MapLibreMap,
        point: GeoPoint,
        zoom: Double = 15.5,
        padding: DoubleArray? = null,
    ) {
        val builder = CameraPosition.Builder()
            .target(LatLng(point.lat, point.lon))
            .zoom(zoom)
            .tilt(0.0)
            .bearing(0.0)
        if (padding != null) builder.padding(padding)
        map.animateCamera(CameraUpdateFactory.newCameraPosition(builder.build()), 700)
    }
}
