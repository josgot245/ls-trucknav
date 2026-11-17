package de.ls.trucknav

import de.ls.trucknav.net.SpeedCameraService
import de.ls.trucknav.net.SpeedLimitService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedInfoTest {

    @Test
    fun derivesTraceAttributesUrlFromRouteUrl() {
        assertEquals(
            "https://valhalla1.openstreetmap.de/trace_attributes",
            SpeedLimitService.traceAttributesUrl("https://valhalla1.openstreetmap.de/route"),
        )
        assertEquals(
            "https://example.org/trace_attributes",
            SpeedLimitService.traceAttributesUrl("https://example.org/route/"),
        )
        assertEquals(
            "https://example.org/trace_attributes",
            SpeedLimitService.traceAttributesUrl("https://example.org/trace_attributes"),
        )
    }

    @Test
    fun readsLimitOfLastMatchedEdge() {
        val body = """
            {
              "edges": [
                { "speed_limit": 30, "names": ["Erste Straße"] },
                { "speed_limit": 50, "names": ["Kirchenallee"] }
              ],
              "matched_points": [
                { "edge_index": 0 },
                { "edge_index": 1 }
              ]
            }
        """.trimIndent()

        val info = SpeedLimitService.parseRoadInfo(body)
        assertEquals(50, info?.speedLimitKmh)
        assertEquals("Kirchenallee", info?.roadName)
    }

    @Test
    fun ignoresImplausibleLimit() {
        val body = """
            {
              "edges": [ { "speed_limit": 0, "names": [] } ],
              "matched_points": [ { "edge_index": 0 } ]
            }
        """.trimIndent()

        assertNull(SpeedLimitService.parseRoadInfo(body)?.speedLimitKmh)
    }

    @Test
    fun fallsBackToLastEdgeWithoutMatchedPoints() {
        val body = """
            {
              "edges": [
                { "speed_limit": 50, "names": ["Erste Straße"] },
                { "speed_limit": 30, "names": ["Letzte Straße"] }
              ]
            }
        """.trimIndent()

        val info = SpeedLimitService.parseRoadInfo(body)
        assertEquals(30, info?.speedLimitKmh)
        assertEquals("Letzte Straße", info?.roadName)
    }

    @Test
    fun invalidBodyReturnsNull() {
        assertNull(SpeedLimitService.parseRoadInfo("not json"))
    }

    @Test
    fun truckLimitUsesMotorwayAndRuralCaps() {
        // Autobahn ohne allgemeines Limit -> 80 km/h fuer LKW.
        assertEquals(80, SpeedLimitService.truckLimit(null, "motorway"))
        // Autobahn mit 120 fuer PKW -> 80 fuer LKW.
        assertEquals(80, SpeedLimitService.truckLimit(120, "motorway"))
        // Landstrasse mit 100 fuer PKW -> 60 fuer LKW.
        assertEquals(60, SpeedLimitService.truckLimit(100, "primary"))
        // Innerorts 50 -> bleibt 50 (niedriger als die 60).
        assertEquals(50, SpeedLimitService.truckLimit(50, "residential"))
        // Tempo-30-Zone bleibt 30.
        assertEquals(30, SpeedLimitService.truckLimit(30, "residential"))
        // Unbekannte Klasse ohne Limit -> konservativ 60.
        assertEquals(60, SpeedLimitService.truckLimit(null, null))
    }

    @Test
    fun readsTruckLimitForMatchedEdge() {
        val body = """
            {
              "edges": [
                { "speed_limit": 100, "road_class": "motorway", "names": ["A 7"] }
              ],
              "matched_points": [ { "edge_index": 0 } ]
            }
        """.trimIndent()

        val info = SpeedLimitService.parseRoadInfo(body)
        assertEquals(100, info?.speedLimitKmh)
        assertEquals(80, info?.truckSpeedLimitKmh)
    }

    @Test
    fun parsesCamerasAndMaxspeed() {
        val body = """
            {
              "elements": [
                { "type": "node", "id": 1, "lat": 53.55, "lon": 10.0,
                  "tags": { "highway": "speed_camera", "maxspeed": "50" } },
                { "type": "node", "id": 2, "lat": 53.56, "lon": 10.01,
                  "tags": { "enforcement": "maxspeed", "maxspeed": "30 mph" } },
                { "type": "node", "id": 1, "lat": 53.55, "lon": 10.0, "tags": {} }
              ]
            }
        """.trimIndent()

        val cameras = SpeedCameraService.parseCameras(body)
        assertEquals(2, cameras?.size)
        assertEquals(50, cameras?.get(0)?.maxSpeedKmh)
        assertEquals(48, cameras?.get(1)?.maxSpeedKmh)
    }

    @Test
    fun parsesMaxSpeedVariants() {
        assertEquals(50, SpeedCameraService.parseMaxSpeed("50"))
        assertEquals(30, SpeedCameraService.parseMaxSpeed("30 km/h"))
        assertEquals(48, SpeedCameraService.parseMaxSpeed("30 mph"))
        assertNull(SpeedCameraService.parseMaxSpeed("none"))
        assertNull(SpeedCameraService.parseMaxSpeed(null))
    }
}
