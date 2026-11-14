package de.ls.trucknav

import de.ls.trucknav.model.TrafficKind
import de.ls.trucknav.model.TruckPoiKind
import de.ls.trucknav.net.AutobahnService
import de.ls.trucknav.net.TruckInfoService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficInfoTest {

    @Test
    fun normalizesAutobahnFromRoadName() {
        assertEquals("A7", AutobahnService.normalizeRoad("A7"))
        assertEquals("A7", AutobahnService.normalizeRoad("A 7"))
        assertEquals("A99", AutobahnService.normalizeRoad("a99"))
        assertNull(AutobahnService.normalizeRoad("B4"))
        assertNull(AutobahnService.normalizeRoad(null))
        assertNull(AutobahnService.normalizeRoad("Hauptstraße"))
    }

    @Test
    fun parsesWarningsAndSkipsFuture() {
        val body = """
            {
              "warning": [
                {
                  "identifier": "w1", "future": false, "isBlocked": "false",
                  "title": "A1 | Bliesheim - Weilerswist", "subtitle": " Köln -> Euskirchen",
                  "abnormalTrafficType": "QUEUING_TRAFFIC", "averageSpeed": "15",
                  "delayTimeValue": "3",
                  "coordinate": { "lat": 50.76, "long": 6.82 }
                },
                {
                  "identifier": "w2", "future": true, "isBlocked": "false",
                  "title": "Zukunft", "coordinate": { "lat": 50.0, "long": 6.0 }
                }
              ]
            }
        """.trimIndent()

        val warnings = AutobahnService.parse(body, TrafficKind.TRAFFIC)
        assertEquals(1, warnings?.size)
        val warning = warnings!!.first()
        assertEquals("w1", warning.id)
        assertTrue(warning.jam)
        assertEquals(15, warning.averageSpeedKmh)
        assertEquals(3, warning.delayMinutes)
        assertFalse(warning.blocked)
        assertEquals(50.76, warning.point.lat, 1e-6)
        assertEquals(6.82, warning.point.lon, 1e-6)
    }

    @Test
    fun parsesClosuresAndMarksBlocked() {
        val body = """
            {
              "closure": [
                {
                  "identifier": "c1", "future": false, "isBlocked": "true",
                  "title": "A1 | Sperrung",
                  "coordinate": { "lat": 53.5, "long": 10.08 }
                }
              ]
            }
        """.trimIndent()

        val closures = AutobahnService.parse(body, TrafficKind.CLOSURE)
        assertEquals(1, closures?.size)
        assertTrue(closures!!.first().blocked)
        assertFalse(closures.first().jam)
    }

    @Test
    fun invalidTrafficBodyReturnsNull() {
        assertNull(AutobahnService.parse("not json", TrafficKind.TRAFFIC))
    }

    @Test
    fun parsesHeightVariants() {
        assertEquals(3.5, TruckInfoService.parseHeight("3.5")!!, 1e-6)
        assertEquals(3.5, TruckInfoService.parseHeight("3,5 m")!!, 1e-6)
        assertEquals(4.0, TruckInfoService.parseHeight("4.0m")!!, 1e-6)
        assertNull(TruckInfoService.parseHeight("default"))
        assertNull(TruckInfoService.parseHeight("below_default"))
        assertNull(TruckInfoService.parseHeight("12'6\""))
        assertNull(TruckInfoService.parseHeight(null))
    }

    @Test
    fun classifiesPoiKinds() {
        fun tags(json: String) = Json.parseToJsonElement(json).jsonObject
        assertEquals(TruckPoiKind.PARKING, TruckInfoService.poiKind(tags("""{"amenity":"parking","hgv":"yes"}""")))
        assertEquals(TruckPoiKind.REST, TruckInfoService.poiKind(tags("""{"highway":"rest_area"}""")))
        assertEquals(TruckPoiKind.REST, TruckInfoService.poiKind(tags("""{"highway":"services"}""")))
        assertEquals(TruckPoiKind.FUEL, TruckInfoService.poiKind(tags("""{"amenity":"fuel","hgv":"designated"}""")))
        assertNull(TruckInfoService.poiKind(tags("""{"amenity":"cafe"}""")))
    }
}
