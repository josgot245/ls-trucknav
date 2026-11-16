package de.ls.trucknav

import de.ls.trucknav.net.parseManeuverLanes
import de.ls.trucknav.net.parseManeuverSign
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneParsingTest {

    private fun obj(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun parsesLaneDirectionsAndNumericActiveFlag() {
        // So liefert der oeffentliche Valhalla-Server "active" (Zahl = Richtung).
        val maneuver = obj(
            """{"lanes":[{"directions":8},{"directions":2},{"directions":64,"active":64}]}"""
        )
        val lanes = parseManeuverLanes(maneuver)
        assertEquals(3, lanes.size)
        assertEquals(8, lanes[0].directions)
        assertFalse(lanes[0].active)
        assertEquals(2, lanes[1].directions)
        assertFalse(lanes[1].active)
        assertEquals(64, lanes[2].directions)
        assertTrue(lanes[2].active)
    }

    @Test
    fun acceptsBooleanActiveFlag() {
        val maneuver = obj("""{"lanes":[{"directions":2,"active":true},{"directions":2}]}""")
        val lanes = parseManeuverLanes(maneuver)
        assertTrue(lanes[0].active)
        assertFalse(lanes[1].active)
    }

    @Test
    fun noLanesYieldsEmptyList() {
        assertTrue(parseManeuverLanes(obj("""{"instruction":"Geradeaus"}""")).isEmpty())
    }

    @Test
    fun parsesExitSign() {
        val maneuver = obj(
            """
            {"sign":{
              "exit_number_elements":[{"text":"22"}],
              "exit_toward_elements":[{"text":"Neuss-Hafen"},{"text":"Düsseldorf"}],
              "exit_name_elements":[{"text":"Neuss-Hafen"}]
            }}
            """.trimIndent()
        )
        val sign = parseManeuverSign(maneuver)!!
        assertEquals("22", sign.exitNumber)
        assertEquals(listOf("Neuss-Hafen", "Düsseldorf"), sign.toward)
        assertEquals("Neuss-Hafen", sign.name)
    }

    @Test
    fun parsesSignWithoutExitNumber() {
        val maneuver = obj(
            """{"sign":{"exit_toward_elements":[{"text":"Zoobrücke","consecutive_count":1},{"text":"Nippes"}]}}"""
        )
        val sign = parseManeuverSign(maneuver)!!
        assertNull(sign.exitNumber)
        assertEquals(listOf("Zoobrücke", "Nippes"), sign.toward)
    }

    @Test
    fun emptyOrMissingSignIsNull() {
        assertNull(parseManeuverSign(obj("""{"sign":{}}""")))
        assertNull(parseManeuverSign(obj("""{"instruction":"Links abbiegen"}""")))
    }

    @Test
    fun parsesBranchRoadNumbers() {
        // So liefert Valhalla die Strassennummern der Richtung.
        val maneuver = obj(
            """
            {"sign":{
              "exit_branch_elements":[{"text":"A 94"},{"text":"A 9"}],
              "exit_toward_elements":[{"text":"Salzburg"}]
            }}
            """.trimIndent()
        )
        val sign = parseManeuverSign(maneuver)!!
        assertEquals(listOf("A 94", "A 9"), sign.branch)
        assertTrue(sign.highway)
    }

    @Test
    fun exitNumberImpliesHighway() {
        val maneuver = obj("""{"sign":{"exit_number_elements":[{"text":"22"}]}}""")
        assertTrue(parseManeuverSign(maneuver)!!.highway)
    }

    @Test
    fun highwayFlagOnManeuverImpliesHighway() {
        val maneuver = obj(
            """{"highway":true,"street_names":["A 94"],"sign":{"exit_toward_elements":[{"text":"Passau"}]}}"""
        )
        assertTrue(parseManeuverSign(maneuver)!!.highway)
    }

    @Test
    fun autobahnStreetNameImpliesHighway() {
        val maneuver = obj(
            """{"street_names":["A 94"],"sign":{"exit_toward_elements":[{"text":"Passau"}]}}"""
        )
        assertTrue(parseManeuverSign(maneuver)!!.highway)
    }

    @Test
    fun bundesstrasseIsNotHighway() {
        val maneuver = obj(
            """{"street_names":["B 28"],"sign":{"exit_toward_elements":[{"text":"Kusterdingen"}]}}"""
        )
        val sign = parseManeuverSign(maneuver)!!
        assertFalse(sign.highway)
        assertTrue(sign.branch.isEmpty())
    }

    @Test
    fun kreisstrasseIsNotHighway() {
        val maneuver = obj(
            """{"street_names":["K 6903"],"sign":{"exit_toward_elements":[{"text":"Wankheim"}]}}"""
        )
        assertFalse(parseManeuverSign(maneuver)!!.highway)
    }
}
