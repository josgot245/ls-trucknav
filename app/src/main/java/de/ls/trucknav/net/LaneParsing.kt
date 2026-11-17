package de.ls.trucknav.net

import de.ls.trucknav.model.JunctionSign
import de.ls.trucknav.model.LaneGuide
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
 */
internal fun parseManeuverSign(maneuver: JsonObject): JunctionSign? {
    val sign = maneuver["sign"] as? JsonObject ?: return null
    val exitNumber = textElements(sign, "exit_number_elements").firstOrNull()
    val branch = textElements(sign, "exit_branch_elements")
    val toward = textElements(sign, "exit_toward_elements")
    val name = textElements(sign, "exit_name_elements").joinToString("/").ifBlank { null }
    if (exitNumber == null && branch.isEmpty() && toward.isEmpty() && name == null) return null
    return JunctionSign(
        exitNumber = exitNumber,
        branch = branch,
        toward = toward,
        name = name,
        highway = isHighwaySign(maneuver, branch),
    )
}

/**
 * Erkennt, ob eine Beschilderung zu einer Autobahn gehoert (blaues Schild).
 * Grundlage sind Valhallas `highway`-Flag am Manoever sowie Autobahn-Kennungen
 * ("A 3") in den Strassennummern der Richtung oder den Strassennamen.
 * Alles andere (Bundes-, Land- und Kreisstrassen) ist gelb/schwarz.
 *
 * Eine reine Ausfahrtsnummer macht das Schild bewusst *nicht* blau: Auch
 * Bundes-, Land- und Kreisstrassen koennen nummerierte Knotenpunkte haben.
 */
private fun isHighwaySign(
    maneuver: JsonObject,
    branch: List<String>,
): Boolean {
    if (maneuver.booleanOrNull("highway") == true) return true
    if (branch.any { isAutobahnNumber(it) }) return true
    val streets = maneuver["street_names"] as? JsonArray ?: return false
    return streets.any { element ->
        isAutobahnNumber((element as? JsonPrimitive)?.contentOrNull)
    }
}

/** true, wenn die Kennung eine Autobahn ist, z. B. "A 3" oder "A94". */
private fun isAutobahnNumber(text: String?): Boolean {
    val value = text?.trim()?.uppercase() ?: return false
    if (value.startsWith("A ")) return true
    return value.length > 1 && value[0] == 'A' && value[1].isDigit()
}

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
