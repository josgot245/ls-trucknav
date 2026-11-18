package de.ls.trucknav.net

import de.ls.trucknav.model.JunctionSign
import de.ls.trucknav.model.LaneGuide
import de.ls.trucknav.model.RoadClass
import de.ls.trucknav.model.RoadRef
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Liest die Fahrtspuren (Bitmasken) eines Manoevers aus der Valhalla-Antwort.
 * Nur verfuegbar, wenn die Anfrage `turn_lanes: true` gesetzt hat.
 */
internal fun parseManeuverLanes(maneuver: JsonObject): List<LaneGuide> {
    val array = maneuver["lanes"] as? JsonArray ?: return emptyList()
    val lanes = ArrayList<LaneGuide>(array.size)
    for (element in array) {
        val lane = element as? JsonObject ?: continue
        val directions = lane.intOrNull("directions") ?: continue
        if (directions == 0) continue
        lanes.add(LaneGuide(directions = directions, active = isActiveLane(lane)))
    }
    return lanes
}

/**
 * Liest die generierte Beschilderung (Ausfahrtsnummer, Strassennummern und
 * Ziele) eines Manoevers. Liefert null, wenn keine Beschilderung vorhanden ist.
 *
 * [currentRoad] ist die Klasse der Strasse, auf der man sich beim Manoever
 * befindet; sie bestimmt die Grundfarbe des Schilds.
 */
internal fun parseManeuverSign(
    maneuver: JsonObject,
    currentRoad: RoadClass = RoadClass.OTHER,
): JunctionSign? {
    val sign = maneuver["sign"] as? JsonObject ?: return null
    val exitNumber = textElements(sign, "exit_number_elements").firstOrNull()
    val branch = textElements(sign, "exit_branch_elements")
        .map { RoadRef(text = it, roadClass = roadClassOf(it)) }
    val toward = textElements(sign, "exit_toward_elements")
    val name = textElements(sign, "exit_name_elements").joinToString("/").ifBlank { null }
    if (exitNumber == null && branch.isEmpty() && toward.isEmpty() && name == null) return null
    return JunctionSign(
        exitNumber = exitNumber,
        branch = branch,
        toward = toward,
        name = name,
        currentRoad = currentRoad,
    )
}

/** Erkennt Strassennummern wie "A 3", "B 27", "L 124", "K 6903" oder "E 42". */
private val ROAD_REF_REGEX = Regex("""\b([ABLKE])\s*0*(\d{1,4})\b""")

/** Bestimmt die Strassenklasse einer Kennung; unbekanntes ergibt [RoadClass.OTHER]. */
internal fun roadClassOf(text: String?): RoadClass {
    val value = text?.trim()?.uppercase() ?: return RoadClass.OTHER
    val letter = ROAD_REF_REGEX.find(value)?.groupValues?.getOrNull(1) ?: return RoadClass.OTHER
    return when (letter) {
        "A" -> RoadClass.AUTOBAHN
        "B" -> RoadClass.BUNDESSTRASSE
        "L" -> RoadClass.LANDSTRASSE
        "K" -> RoadClass.KREISSTRASSE
        "E" -> RoadClass.EUROPASTRASSE
        else -> RoadClass.OTHER
    }
}

/** Rangfolge der Klassen, wenn eine Strasse mehrere Kennungen traegt (z. B. A 3/E 42). */
private fun roadClassRank(roadClass: RoadClass): Int = when (roadClass) {
    RoadClass.AUTOBAHN -> 5
    RoadClass.BUNDESSTRASSE -> 4
    RoadClass.LANDSTRASSE -> 3
    RoadClass.KREISSTRASSE -> 2
    RoadClass.EUROPASTRASSE -> 1
    RoadClass.OTHER -> 0
}

/**
 * Waehlt aus mehreren Strassennamen die wichtigste Klasse. Damit wird bei
 * "A 3/E 42" die Autobahn erkannt (Europastrassen begleiten meist eine
 * nationale Strasse).
 */
internal fun dominantRoadClass(names: List<String>): RoadClass =
    names.map { roadClassOf(it) }
        .maxByOrNull { roadClassRank(it) }
        ?: RoadClass.OTHER

/** Valhalla liefert "active" je nach Server als true oder als Zahl ungleich 0. */
private fun isActiveLane(lane: JsonObject): Boolean {
    val value = lane["active"] as? JsonPrimitive ?: return false
    value.booleanOrNull?.let { return it }
    return value.intOrNull?.let { it != 0 } ?: false
}

private fun textElements(obj: JsonObject, key: String): List<String> {
    val array = obj[key] as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val entry = element as? JsonObject ?: return@mapNotNull null
        (entry["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}
