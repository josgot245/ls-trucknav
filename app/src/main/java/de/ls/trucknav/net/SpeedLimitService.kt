package de.ls.trucknav.net

import de.ls.trucknav.model.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
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

/** Tempolimit und Strassenname des aktuellen Abschnitts. */
data class RoadInfo(
    val speedLimitKmh: Int?,
    val roadName: String?,
)

/**
 * Ermittelt das zulaessige Tempo des aktuellen Strassenabschnitts ueber
 * Valhallas `trace_attributes` (Map-Matching der zuletzt gefahrenen Punkte).
 *
 * Die Werte stammen aus OpenStreetMap und koennen unvollstaendig oder
 * veraltet sein. Die Beschilderung vor Ort hat immer Vorrang.
 */
class SpeedLimitService(private val client: OkHttpClient = Http.client) {

    /**
     * @return Informationen zum zuletzt gematchten Abschnitt oder `null`,
     *         wenn die Abfrage fehlgeschlagen ist (Netz, Server, Parsing).
     */
    suspend fun lookup(baseUrl: String, shape: List<GeoPoint>): RoadInfo? =
        withContext(Dispatchers.IO) {
            if (shape.size < 2) return@withContext null

            val payload = buildJsonObject {
                put("shape", buildJsonArray {
                    shape.forEach { point ->
                        addJsonObject {
                            put("lat", point.lat)
                            put("lon", point.lon)
                        }
                    }
                })
                // Auto-Costing ist fuer das Map-Matching robuster als LKW-
                // Restriktionen; die Tempolimits sind strassenbezogen.
                put("costing", "auto")
                put("shape_match", "map_snap")
                putJsonObject("filters") {
                    put("action", "include")
                    put("attributes", buildJsonArray {
                        add("edge.speed_limit")
                        add("edge.names")
                        add("edge.road_class")
                        // Erst dadurch liefert der Server matched_points
                        // (inkl. edge_index des aktuellen Abschnitts).
                        add("matched.edge_index")
                    })
                }
            }

            val request = Request.Builder()
                .url(traceAttributesUrl(baseUrl))
                .header("User-Agent", Http.USER_AGENT)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    parseRoadInfo(response.body?.string().orEmpty())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Tempolimit ist eine Zusatzinfo: Fehler duerfen die
                // Navigation nicht beeinflussen.
                null
            }
        }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Leitet aus einer Valhalla-Route-URL den trace_attributes-Endpunkt ab. */
        internal fun traceAttributesUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            return when {
                trimmed.endsWith("/route") ->
                    trimmed.dropLast("/route".length) + "/trace_attributes"

                trimmed.endsWith("/trace_attributes") -> trimmed
                else -> trimmed
            }
        }

        internal fun parseRoadInfo(body: String): RoadInfo? {
            val root = runCatching {
                Json.parseToJsonElement(body).jsonObject
            }.getOrNull() ?: return null

            val edges = root["edges"]?.jsonArray ?: return RoadInfo(null, null)
            val matched = root["matched_points"]?.jsonArray
            val edgeIndex = matched?.lastOrNull()?.jsonObject?.intOrNull("edge_index")

            // Ohne matched_points gilt der letzte Abschnitt als aktueller
            // (die edges sind entlang der Fahrtrichtung sortiert).
            val edge = when {
                edgeIndex != null -> edges.getOrNull(edgeIndex)?.jsonObject
                else -> edges.lastOrNull()?.jsonObject
            } ?: return RoadInfo(null, null)

            val limit = edge.intOrNull("speed_limit")?.takeIf { it in 5..140 }
            val name = edge["names"]?.jsonArray
                ?.firstOrNull()
                ?.toString()
                ?.trim('"')
                ?.takeIf { it.isNotBlank() }

            return RoadInfo(speedLimitKmh = limit, roadName = name)
        }
    }
}
