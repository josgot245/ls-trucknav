package de.ls.trucknav.net

import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.SpeedCamera
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

/**
 * Laedt stationaere Geschwindigkeitskontrollen aus OpenStreetMap (Overpass).
 *
 * Bewusst keine kommerzielle Blitzer-Datenbank: OSM ist frei verfuegbar,
 * aber laengst nicht vollstaendig. Fehlende oder veraltete Eintraege sind
 * moeglich; die Anzeige ist nur eine Unterstuetzung.
 */
class SpeedCameraService(private val client: OkHttpClient = Http.slowClient) {

    /**
     * @return Blitzer im Umkreis oder `null`, wenn die Abfrage fehlgeschlagen
     *         ist (Netz, Server, Parsing).
     */
    suspend fun nearby(
        center: GeoPoint,
        radiusMeters: Int = DEFAULT_RADIUS_M,
    ): List<SpeedCamera>? = withContext(Dispatchers.IO) {
        val query = buildString {
            append("[out:json][timeout:25];")
            append("(")
            append("node[\"highway\"=\"speed_camera\"]")
            append("(around:$radiusMeters,${center.lat},${center.lon});")
            append("node[\"enforcement\"=\"maxspeed\"]")
            append("(around:$radiusMeters,${center.lat},${center.lon});")
            append(");")
            append("out body;")
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("User-Agent", Http.USER_AGENT)
            .post(FormBody.Builder().add("data", query).build())
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                parseCameras(response.body?.string().orEmpty())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val ENDPOINT = "https://overpass-api.de/api/interpreter"
        const val DEFAULT_RADIUS_M = 5_000

        internal fun parseCameras(body: String): List<SpeedCamera>? {
            val root = runCatching {
                Json.parseToJsonElement(body).jsonObject
            }.getOrNull() ?: return null

            val elements = root["elements"]?.jsonArray ?: return emptyList()
            return elements.mapNotNull { element ->
                val obj = element.jsonObject
                val lat = obj.doubleOrNull("lat") ?: return@mapNotNull null
                val lon = obj.doubleOrNull("lon") ?: return@mapNotNull null
                val id = obj.stringOrNull("id")?.toLongOrNull() ?: return@mapNotNull null
                val tags = obj["tags"]?.jsonObject
                SpeedCamera(
                    id = id,
                    point = GeoPoint(lat, lon),
                    maxSpeedKmh = parseMaxSpeed(tags?.stringOrNull("maxspeed")),
                    name = tags?.stringOrNull("name"),
                )
            }.distinctBy { it.id }
        }

        /** Wandelt OSM-maxspeed-Werte wie "50", "30 km/h" oder "20 mph" um. */
        internal fun parseMaxSpeed(raw: String?): Int? {
            if (raw == null) return null
            val number = Regex("""(\d{1,3})""")
                .find(raw)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull() ?: return null
            val kmh = if (raw.contains("mph", ignoreCase = true)) {
                (number * 1.609344).roundToInt()
            } else {
                number
            }
            return kmh.takeIf { it in 5..140 }
        }
    }
}
