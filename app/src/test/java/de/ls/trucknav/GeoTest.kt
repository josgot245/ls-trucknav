package de.ls.trucknav

import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.nav.RouteGeometry
import de.ls.trucknav.util.decodePolyline6
import de.ls.trucknav.util.distanceMeters
import de.ls.trucknav.util.distanceToPolylineMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    /**
     * Bekanntes Polyline-Beispiel (Genauigkeit 5). Bei Genauigkeit 6 werden
     * dieselben Ganzzahlen durch 1e6 geteilt.
     */
    @Test
    fun decodesPolyline6() {
        val points = decodePolyline6("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertEquals(3.85, points[0].lat, 1e-9)
        assertEquals(-12.02, points[0].lon, 1e-9)
        assertEquals(4.07, points[1].lat, 1e-9)
        assertEquals(-12.095, points[1].lon, 1e-9)
        assertEquals(4.3252, points[2].lat, 1e-9)
        assertEquals(-12.6453, points[2].lon, 1e-9)
    }

    @Test
    fun haversineOneDegreeLatitude() {
        val distance = distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))
        assertTrue("Entfernung war $distance", distance in 111_100.0..111_300.0)
    }

    @Test
    fun snapsToMiddleOfSegment() {
        val geometry = RouteGeometry(
            listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001))
        )
        val snap = geometry.snap(GeoPoint(0.0001, 0.0005))

        assertTrue("distanceAlong=${snap.distanceAlong}", snap.distanceAlong in 40.0..70.0)
        assertTrue("distanceFromRoute=${snap.distanceFromRoute}", snap.distanceFromRoute < 15.0)
        assertEquals(111.19, geometry.total, 1.0)
    }

    @Test
    fun measuresDistanceToPolyline() {
        val polyline = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.002))

        val onLine = distanceToPolylineMeters(GeoPoint(0.0, 0.001), polyline)
        assertTrue("onLine=$onLine", onLine < 1.0)

        val beside = distanceToPolylineMeters(GeoPoint(0.0001, 0.001), polyline)
        assertTrue("beside=$beside", beside in 8.0..14.0)

        val far = distanceToPolylineMeters(GeoPoint(0.01, 0.001), polyline)
        assertTrue("far=$far", far > 1000.0)
    }
}
