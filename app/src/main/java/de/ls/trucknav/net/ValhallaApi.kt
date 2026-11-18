package de.ls.trucknav.net

import de.ls.trucknav.data.TruckProfile
import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.Maneuver
import de.ls.trucknav.model.RoadClass
import de.ls.trucknav.model.RouteResult
import de.ls.trucknav.util.cumulativeDistances
import de.ls.trucknav.util.decodePolyline6
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class RoutingException(message: String) : IOException(message)

/**
 * Routenberechnung ueber eine Valhalla-Instanz.
 * Standard ist der oeffentliche FOSSGIS-Server (ohne API-Key, ohne SLA).
 * Die URL ist in den Einstellungen austauschbar.
 */
class ValhallaApi(private val client: OkHttpClient = Http.client) {

    suspend fun route(
        from: GeoPoint,
        to: GeoPoint,
        profile: TruckProfile,
        truck: Boolean,
        baseUrl: String,
    ): List<RouteResult> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("locations", buildJsonArray {
                addJsonObject {
                    put("lat", from.lat)
                    put("lon", from.lon)
                    put("type", "break")
                }
                addJsonObject {
                    put("lat", to.lat)
                    put("lon", to.lon)
                    put("type", "break")
                }
            })
            put("costing", if (truck) "truck" else "auto")
            if (truck) {
                putJsonObject("costing_options") {
                    putJsonObject("truck") {
                        put("height", profile.height)
                        put("width", profile.width)
                        put("length", profile.length)
                        put("weight", profile.weight)
                        put("axle_load", profile.axleLoad)
                        put("axle_count", profile.axles)
                        put("hazmat", profile.hazmat)
                        put("use_tolls", if (profile.avoidTolls) 0 else 1)
                        put("use_highways", if (profile.avoidHighways) 0 else 1)
                    }
                }
            }
            putJsonObject("directions_options") {
                put("language", "de-DE")
                put("units", "kilometers")
            }
            // Spurführung und Beschilderung an Verzweigungen anfordern.
            put("turn_lanes", true)
            // Bis zu zwei Ausweichrouten mitliefern lassen (zusammen drei).
            put("alternates", ALTERNATES)
        }

        val request = Request.Builder()
            .url(baseUrl)
            .header("User-Agent", Http.USER_AGENT)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RoutingException(friendlyError(response.code, text))
            }
            parseRoutes(text, truck)
        }
    }

    /**
     * Liest die Hauptroute und, falls vorhanden, die Ausweichrouten aus der
     * Antwort. Reihenfolge: zuerst die Hauptroute (Valhalla-Vorschlag), danach
     * die Alternativen. Fehlerhafte Alternativen werden uebersprungen.
     */
    internal fun parseRoutes(body: String, truck: Boolean): List<RouteResult> {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw RoutingException("Antwort des Routing-Servers konnte nicht gelesen werden.")
        val primaryTrip = root["trip"]?.jsonObject
            ?: throw RoutingException("Der Routing-Server hat keine Route geliefert.")

        val routes = ArrayList<RouteResult>()
        routes.add(parseTrip(primaryTrip, truck))
        root["alternates"]?.jsonArray?.forEach { element ->
            val trip = runCatching { element.jsonObject["trip"]?.jsonObject }.getOrNull()
                ?: return@forEach
            runCatching { parseTrip(trip, truck) }.getOrNull()?.let { routes.add(it) }
        }
        return routes
    }

    private fun parseTrip(trip: JsonObject, truck: Boolean): RouteResult {
        val summary = trip["summary"]?.jsonObject
        val legs = trip["legs"]?.jsonArray
            ?: throw RoutingException("Die Route enthaelt keine Abschnitte.")

        val points = ArrayList<GeoPoint>()
        val maneuvers = ArrayList<Maneuver>()
        var legOffset = 0.0

        for (legElement in legs) {
            val leg = legElement.jsonObject
            val shape = leg.stringOrNull("shape") ?: continue
            val legPoints = decodePolyline6(shape)
            if (legPoints.size < 2) continue
            val cumulative = cumulativeDistances(legPoints)

            // Strasse, auf der man sich beim aktuellen Manoever befindet
            // (Grundfarbe der Schilder). Zu Beginn eines Abschnitts unbekannt.
            var currentRoad = RoadClass.OTHER

            leg["maneuvers"]?.jsonArray?.forEach { maneuverElement ->
                val m = maneuverElement.jsonObject
                val beginIndex = m.intOrNull("begin_shape_index") ?: 0
                val startDistance = legOffset + (cumulative.getOrNull(beginIndex) ?: 0.0)
                val instruction = m.stringOrNull("instruction") ?: "Weiterfahren"
                val streetNames = m["street_names"]?.jsonArray
                    ?.mapNotNull { it.toString().trim('"') }
                    ?: emptyList()
                maneuvers.add(
                    Maneuver(
                        index = maneuvers.size,
                        type = m.intOrNull("type") ?: 0,
                        instruction = instruction,
                        verbal = m.stringOrNull("verbal_pre_transition_instruction")
                            ?: m.stringOrNull("verbal_succinct_transition_instruction")
                            ?: instruction,
                        streetNames = streetNames,
                        startDistance = startDistance,
                        length = (m.doubleOrNull("length") ?: 0.0) * 1000.0,
                        time = m.doubleOrNull("time") ?: 0.0,
                        lanes = parseManeuverLanes(m),
                        // Grundfarbe des Schilds ist die Strasse, auf der man
                        // sich befindet (die Zielstrasse des vorigen Manoevers).
                        sign = parseManeuverSign(m, currentRoad),
                        roundaboutExit = m.intOrNull("roundabout_exit_count"),
                    )
                )
                // Nach dem Manoever faehrt man auf der Zielstrasse weiter. Sie
                // ist damit die Ausgangsstrasse des naechsten Schilds. Leere
                // oder unbekannte Angaben lassen die zuletzt bekannte Klasse
                // stehen (z. B. Rampen ohne Strassennamen).
                dominantRoadClass(streetNames)
                    .takeIf { it != RoadClass.OTHER }
                    ?.let { currentRoad = it }
            }

            // Nahtstellen zwischen Abschnitten nicht doppelt fuehren.
            if (points.isNotEmpty() && legPoints.isNotEmpty()) {
                points.addAll(legPoints.drop(1))
            } else {
                points.addAll(legPoints)
            }
            legOffset += cumulative.lastOrNull() ?: 0.0
        }

        if (points.size < 2) {
            throw RoutingException("Die Route konnte nicht ausgewertet werden.")
        }

        return RouteResult(
            points = points,
            maneuvers = maneuvers.sortedBy { it.startDistance },
            distance = (summary?.doubleOrNull("length") ?: 0.0) * 1000.0,
            time = summary?.doubleOrNull("time") ?: 0.0,
            destination = null,
            truckUsed = truck,
        )
    }

    private fun friendlyError(code: Int, body: String): String {
        val serverMessage = runCatching {
            Json.parseToJsonElement(body).jsonObject.stringOrNull("error")
        }.getOrNull()
        return when (code) {
            429 -> "Routing-Server ausgelastet. Bitte in einigen Sekunden erneut versuchen."
            400 -> serverMessage?.let { "Keine Route gefunden: $it" }
                ?: "Keine Route fuer diese Strecke gefunden."
            413 -> "Strecke zu lang fuer den Routing-Server."
            else -> serverMessage?.let { "Routing-Fehler (HTTP $code): $it" }
                ?: "Routing-Fehler (HTTP $code)."
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Anzahl der Ausweichrouten (Valhalla erlaubt 0-3). */
        private const val ALTERNATES = 2
    }
}
