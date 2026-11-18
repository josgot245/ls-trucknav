package de.ls.trucknav

import de.ls.trucknav.model.RoadClass
import de.ls.trucknav.net.dominantRoadClass
import de.ls.trucknav.net.parseManeuverLanes
import de.ls.trucknav.net.parseManeuverSign
import de.ls.trucknav.net.roadClassOf
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
    fun parsesBranchRoadNumbersWithClasses() {
        // So liefert Valhalla die Strassennummern der Richtung.
        val maneuver = obj(
            """
            {"sign":{
              "exit_branch_elements":[{"text":"A 94"},{"text":"A 9"}],
              "exit_toward_elements":[{"text":"Salzburg"}]
            }}
            """.trimIndent()
        )
        val sign = parseManeuverSign(maneuver, RoadClass.BUNDESSTRASSE)!!
        assertEquals(listOf("A 94", "A 9"), sign.branch.map { it.text })
        assertTrue(sign.branch.all { it.roadClass == RoadClass.AUTOBAHN })
        // Grundfarbe = Strasse, auf der man sich befindet, nicht die Zielstrasse.
        assertEquals(RoadClass.BUNDESSTRASSE, sign.currentRoad)
    }

    @Test
    fun baseColorFollowsCurrentRoadNotBranch() {
        // Auf der Autobahn, Ausfahrt auf eine Bundesstrasse: Schild blau,
        // eingebettetes gelbes Bundesstrassen-Logo.
        val exit = obj(
            """
            {"sign":{
              "exit_number_elements":[{"text":"22"}],
              "exit_branch_elements":[{"text":"B 6"}],
              "exit_toward_elements":[{"text":"Neuss"}]
            }}
            """.trimIndent()
        )
        val exitSign = parseManeuverSign(exit, RoadClass.AUTOBAHN)!!
        assertEquals(RoadClass.AUTOBAHN, exitSign.currentRoad)
        assertEquals(RoadClass.BUNDESSTRASSE, exitSign.branch.single().roadClass)

        // Auf der Bundesstrasse, Auffahrt auf eine Autobahn: Schild gelb,
        // eingebettetes blaues Autobahn-Logo.
        val entry = obj(
            """
            {"sign":{
              "exit_branch_elements":[{"text":"A 27"}],
              "exit_toward_elements":[{"text":"Bremen"}]
            }}
            """.trimIndent()
        )
        val entrySign = parseManeuverSign(entry, RoadClass.BUNDESSTRASSE)!!
        assertEquals(RoadClass.BUNDESSTRASSE, entrySign.currentRoad)
        assertEquals(RoadClass.AUTOBAHN, entrySign.branch.single().roadClass)
    }

    @Test
    fun exitNumberAloneKeepsCurrentRoad() {
        // Nummerierte Knotenpunkte gibt es auch an Bundes-/Land-/Kreisstrassen:
        // das Schild bleibt dann gelb/schwarz.
        val maneuver = obj("""{"sign":{"exit_number_elements":[{"text":"22"}]}}""")
        assertEquals(
            RoadClass.LANDSTRASSE,
            parseManeuverSign(maneuver, RoadClass.LANDSTRASSE)!!.currentRoad,
        )
    }

    @Test
    fun classifiesRoadNumbers() {
        assertEquals(RoadClass.AUTOBAHN, roadClassOf("A 3"))
        assertEquals(RoadClass.AUTOBAHN, roadClassOf("A94"))
        assertEquals(RoadClass.BUNDESSTRASSE, roadClassOf("B 27"))
        assertEquals(RoadClass.LANDSTRASSE, roadClassOf("L 124"))
        assertEquals(RoadClass.KREISSTRASSE, roadClassOf("K 6903"))
        assertEquals(RoadClass.EUROPASTRASSE, roadClassOf("E 42"))
        // Strassennamen ohne Nummer sind keine Klasse.
        assertEquals(RoadClass.OTHER, roadClassOf("Berliner Straße"))
        assertEquals(RoadClass.OTHER, roadClassOf(null))
    }

    @Test
    fun dominantClassPrefersAutobahn() {
        // Valhalla liefert bei Autobahnen oft "A 3" und "E 42" zusammen.
        assertEquals(RoadClass.AUTOBAHN, dominantRoadClass(listOf("A 3", "E 42")))
        assertEquals(RoadClass.BUNDESSTRASSE, dominantRoadClass(listOf("B 27", "E 42")))
        assertEquals(RoadClass.LANDSTRASSE, dominantRoadClass(listOf("L 124", "Östliche Zubringerstraße")))
        assertEquals(RoadClass.OTHER, dominantRoadClass(listOf("Hauptstraße", "Am Markt")))
    }
}
