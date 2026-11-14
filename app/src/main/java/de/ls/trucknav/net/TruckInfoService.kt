package de.ls.trucknav.net

import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.HeightRestriction
import de.ls.trucknav.model.TruckPoi
import de.ls.trucknav.model.TruckPoiKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * LKW-relevante OpenStreetMap-Daten ueber Overpass:
 * Parkplaetze, Raststaetten, LKW-Tankstellen und Durchfahrtshoehen.
 *
 * Die Daten sind frei verfuegbar, aber unvollstaendig und ohne Gewaehr.
 */
class TruckInfoService(private val client: OkHttpClient = Http.slowClient) {

    suspend fun pois(
        center: GeoPoint,
        radiusMeters: Int = DEFAULT_POI_RADIUS_M,
    ): List<TruckPoi>? = query(poiQuery(center, radiusMeters)) { elements ->
        elements.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj.stringOrNull("id")?.toLongOrNull() ?: return@mapNotNull null
            val point = pointOf(obj) ?: return@mapNotNull null
            val tags = obj["tags"]?.jsonObject
            val kind = poiKind(tags) ?: return@mapNotNull null
            TruckPoi(id = id, point = point, name = tags?.stringOrNull("name"), kind = kind)
        }.distinctBy { it.id }
    }

    suspend fun heightRestrictions(
        center: GeoPoint,
        radiusMeters: Int = DEFAULT_HEIGHT_RADIUS_M,
    ): List<HeightRestriction>? = query(heightQuery(center, radiusMeters)) { elements ->
        elements.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj.stringOrNull("id")?.toLongOrNull() ?: return@mapNotNull null
            val point = pointOf(obj) ?: return@mapNotNull null
            val tags = obj["tags"]?.jsonObject
            val height = parseHeight(tags?.stringOrNull("maxheight"))
                ?: parseHeight(tags?.stringOrNull("maxheight:physical"))
                ?: return@mapNotNull null
            HeightRestriction(
                id = id,
                point = point,
                maxHeightM = height,
                name = tags?.stringOrNull("name"),
            )
        }.distinctBy { it.id }
    }

    private suspend fun <T> query(
        overpassQuery: String,
        parse: (List<kotlinx.serialization.json.JsonElement>) -> List<T>,
    ): List<T>? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(SpeedCameraService.ENDPOINT)
            .header("User-Agent", Http.USER_AGENT)
            .post(FormBody.Builder().add("data", overpassQuery).build())
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val root = runCatching {
                    Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                }.getOrNull() ?: return@withContext null
                val elements = root["elements"]?.jsonArray ?: return@withContext emptyList()
                parse(elements)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val DEFAULT_POI_RADIUS_M = 8_000
        const val DEFAULT_HEIGHT_RADIUS_M = 3_000

        internal fun poiQuery(center: GeoPoint, radius: Int): String = buildString {
            append("[out:json][timeout:25];(")
            append("node[\"amenity\"=\"parking\"][\"hgv\"~\"^(yes|designated)$\"](around:$radius,${center.lat},${center.lon});")
            append("way[\"amenity\"=\"parking\"][\"hgv\"~\"^(yes|designated)$\"](around:$radius,${center.lat},${center.lon});")
            append("node[\"amenity\"=\"parking\"][\"parking\"=\"truck\"](around:$radius,${center.lat},${center.lon});")
            append("node[\"highway\"=\"rest_area\"](around:$radius,${center.lat},${center.lon});")
            append("way[\"highway\"=\"rest_area\"](around:$radius,${center.lat},${center.lon});")
            append("node[\"highway\"=\"services\"](around:$radius,${center.lat},${center.lon});")
            append("way[\"highway\"=\"services\"](around:$radius,${center.lat},${center.lon});")
            append("node[\"amenity\"=\"fuel\"][\"hgv\"~\"^(yes|designated)$\"](around:$radius,${center.lat},${center.lon});")
            append("way[\"amenity\"=\"fuel\"][\"hgv\"~\"^(yes|designated)$\"](around:$radius,${center.lat},${center.lon});")
            append(");out center tags;")
        }

        internal fun heightQuery(center: GeoPoint, radius: Int): String = buildString {
            append("[out:json][timeout:25];(")
            append("way[\"maxheight\"](around:$radius,${center.lat},${center.lon});")
            append("way[\"maxheight:physical\"](around:$radius,${center.lat},${center.lon});")
            append("node[\"maxheight\"](around:$radius,${center.lat},${center.lon});")
            append(");out center tags;")
        }

        internal fun poiKind(tags: JsonObject?): TruckPoiKind? {
            val amenity = tags?.stringOrNull("amenity")
            val highway = tags?.stringOrNull("highway")
            val parking = tags?.stringOrNull("parking")
            return when {
                amenity == "parking" || parking == "truck" -> TruckPoiKind.PARKING
                highway == "rest_area" || highway == "services" -> TruckPoiKind.REST
                amenity == "fuel" -> TruckPoiKind.FUEL
                else -> null
            }
        }

        private fun pointOf(obj: JsonObject): GeoPoint? {
            val lat = obj.doubleOrNull("lat")
            val lon = obj.doubleOrNull("lon")
            if (lat != null && lon != null) return GeoPoint(lat, lon)
            val center = obj["center"]?.jsonObject ?: return null
            val clat = center.doubleOrNull("lat") ?: return null
            val clon = center.doubleOrNull("lon") ?: return null
            return GeoPoint(clat, clon)
        }

        /** Wandelt OSM-maxheight-Werte wie "3.5", "3,5 m" oder "3.5m" um. */
        internal fun parseHeight(raw: String?): Double? {
            if (raw == null) return null
            val cleaned = raw.trim().lowercase()
            // Imperial (z. B. "12'6\"") ignorieren – in DE unueblich.
            if (cleaned.contains("'")) return null
            val number = Regex("""(\d+(?:[.,]\d+)?)""")
                .find(cleaned)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(',', '.')
                ?.toDoubleOrNull() ?: return null
            return number.takeIf { it in 1.0..6.0 }
        }
    }
}
