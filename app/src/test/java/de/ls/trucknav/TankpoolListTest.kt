package de.ls.trucknav

import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.TruckPoi
import de.ls.trucknav.model.TruckPoiKind
import de.ls.trucknav.ui.tankpoolAlongRoute
import de.ls.trucknav.ui.tankpoolNearby
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TankpoolListTest {

    private fun poi(id: Long, lat: Double, lon: Double) = TruckPoi(
        id = id,
        point = GeoPoint(lat, lon),
        name = "Station $id",
        kind = TruckPoiKind.TANKPOOL24,
    )

    @Test
    fun `Umkreissuche sortiert nach Entfernung und filtert den Radius`() {
        val stations = listOf(
            poi(0, 50.0, 8.0),   // 0 m
            poi(1, 50.0, 8.2),   // ca. 14,3 km
            poi(2, 50.0, 8.05),  // ca. 3,6 km
            poi(3, 50.0, 9.0),   // ca. 71,6 km
        )

        val entries = tankpoolNearby(stations, GeoPoint(50.0, 8.0), radiusMeters = 20_000.0)

        assertEquals(listOf(0L, 2L, 1L), entries.map { it.poi.id })
        assertTrue(entries.all { it.routeDistanceMeters == null })
    }

    @Test
    fun `Umkreissuche begrenzt die Anzahl der Eintraege`() {
        val stations = listOf(
            poi(0, 50.0, 8.0),
            poi(1, 50.0, 8.2),
            poi(2, 50.0, 8.05),
        )

        val entries = tankpoolNearby(stations, GeoPoint(50.0, 8.0), 20_000.0, limit = 2)

        assertEquals(listOf(0L, 2L), entries.map { it.poi.id })
    }

    @Test
    fun `Routensuche sortiert in Fahrtrichtung`() {
        val route = listOf(GeoPoint(50.0, 8.0), GeoPoint(50.0, 9.0))
        val stations = listOf(
            poi(1, 50.0, 8.8), // voraus
            poi(2, 50.0, 8.2), // bereits passiert
        )

        val entries = tankpoolAlongRoute(
            stations = stations,
            route = route,
            position = GeoPoint(50.0, 8.5),
            radiusMeters = 15_000.0,
        )

        assertEquals(listOf(1L, 2L), entries.map { it.poi.id })
        assertTrue(entries.all { it.routeDistanceMeters != null })
    }

    @Test
    fun `Routensuche filtert nach Abstand zur Route`() {
        val route = listOf(GeoPoint(50.0, 8.0), GeoPoint(50.0, 9.0))
        val stations = listOf(
            poi(1, 50.0, 8.5),  // auf der Route
            poi(2, 50.1, 8.5),  // ca. 11,1 km neben der Route
            poi(3, 50.2, 8.5),  // ca. 22,2 km neben der Route
        )

        val entries = tankpoolAlongRoute(
            stations = stations,
            route = route,
            position = GeoPoint(50.0, 8.0),
            radiusMeters = 15_000.0,
        )

        val ids = entries.map { it.poi.id }
        assertTrue(1L in ids)
        assertTrue(2L in ids)
        assertFalse(3L in ids)
    }

    @Test
    fun `Routensuche ohne Route bleibt leer`() {
        val entries = tankpoolAlongRoute(
            stations = listOf(poi(1, 50.0, 8.5)),
            route = emptyList(),
            position = GeoPoint(50.0, 8.0),
            radiusMeters = 15_000.0,
        )

        assertTrue(entries.isEmpty())
    }
}
