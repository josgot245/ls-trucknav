package de.ls.trucknav.net

import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.TrafficKind
import de.ls.trucknav.model.TrafficWarning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Verkehrsmeldungen der Autobahn GmbH fuer eine Bundesautobahn.
 *
 * Quelle: https://verkehr.autobahn.de (offen, kein Schluessel noetig).
 * Geliefert werden Verkehrsmeldungen (inkl. Stau), Sperrungen und Baustellen.
 * Nur Bundesautobahnen – Bundes- und Landstrassen sind nicht enthalten.
 */
class AutobahnService(private val client: OkHttpClient = Http.client) {

    /**
     * @param road Autobahn-Kennung wie "A7" (aus dem Strassennamen).
     * @return Meldungen oder `null`, wenn die Abfrage fehlgeschlagen ist.
     */
    suspend fun warnings(road: String): List<TrafficWarning>? = withContext(Dispatchers.IO) {
        val id = normalizeRoad(road) ?: return@withContext null
        coroutineScope {
            val traffic = async { fetch(id, "warning", TrafficKind.TRAFFIC) }
            val closure = async { fetch(id, "closure", TrafficKind.CLOSURE) }
            val roadworks = async { fetch(id, "roadworks", TrafficKind.ROADWORKS) }
            val parts = listOf(traffic.await(), closure.await(), roadworks.await())
            if (parts.all { it == null }) null else parts.filterNotNull().flatten()
        }
    }

    private fun fetch(road: String, service: String, kind: TrafficKind): List<TrafficWarning>? {
        val request = Request.Builder()
            .url("$BASE/$road/services/$service")
            .header("User-Agent", Http.USER_AGENT)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                parse(response.body?.string().orEmpty(), kind)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val BASE = "https://verkehr.autobahn.de/o/autobahn"

        private val ROAD_IN_NAME = Regex("""A\s?(\d{1,3})""", RegexOption.IGNORE_CASE)

        /** Wandelt einen Strassennamen in eine Autobahn-Kennung ("A7") um. */
        internal fun normalizeRoad(raw: String?): String? {
            val value = raw?.trim()?.replace(" ", "") ?: return null
            val match = ROAD_IN_NAME.find(value) ?: return null
            return "A" + match.groupValues[1]
        }

        internal fun parse(body: String, kind: TrafficKind): List<TrafficWarning>? {
            val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return null
            val key = when (kind) {
                TrafficKind.TRAFFIC -> "warning"
                TrafficKind.CLOSURE -> "closure"
                TrafficKind.ROADWORKS -> "roadworks"
            }
            val array = root[key]?.jsonArray ?: return emptyList()

            return array.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj.stringOrNull("identifier") ?: return@mapNotNull null
                // Zukunftsdatiertes (noch nicht begonnen) ausblenden.
                if (obj.booleanOrNull("future") == true) return@mapNotNull null
                val coord = obj["coordinate"]?.jsonObject ?: return@mapNotNull null
                val lat = coord.doubleOrNull("lat") ?: return@mapNotNull null
                val lon = coord.doubleOrNull("long") ?: return@mapNotNull null

                val abnormal = obj.stringOrNull("abnormalTrafficType")
                val jam = abnormal != null && (
                    abnormal.contains("QUEUING") ||
                        abnormal.contains("STATIONARY") ||
                        abnormal.contains("SLOW")
                    )

                TrafficWarning(
                    id = id,
                    point = GeoPoint(lat, lon),
                    title = obj.stringOrNull("title") ?: "",
                    subtitle = obj.stringOrNull("subtitle")?.trim()?.takeIf { it.isNotBlank() },
                    kind = kind,
                    blocked = obj.stringOrNull("isBlocked") == "true",
                    averageSpeedKmh = obj.intOrNull("averageSpeed"),
                    delayMinutes = obj.intOrNull("delayTimeValue"),
                    jam = jam,
                )
            }
        }
    }
}
