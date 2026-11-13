package de.ls.trucknav.net

import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Adresssuche ueber Photon (OpenStreetMap-basiert, ohne API-Key). */
class PhotonApi(private val client: OkHttpClient = Http.client) {

    suspend fun search(query: String, near: GeoPoint?, limit: Int = 8): List<Place> =
        withContext(Dispatchers.IO) {
            val builder = HttpUrl.Builder()
                .scheme("https")
                .host("photon.komoot.io")
                .addPathSegment("api")
                .addQueryParameter("q", query)
                .addQueryParameter("lang", "de")
                .addQueryParameter("limit", limit.toString())
            if (near != null) {
                builder.addQueryParameter("lat", near.lat.toString())
                builder.addQueryParameter("lon", near.lon.toString())
            }

            val request = Request.Builder()
                .url(builder.build())
                .header("User-Agent", Http.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Suche fehlgeschlagen (HTTP ${response.code})")
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = runCatching { Json.parseToJsonElement(body).jsonObject }
                    .getOrElse { return@withContext emptyList() }
                val features = root["features"]?.jsonArray ?: return@withContext emptyList()

                features.mapNotNull { element ->
                    val feature = element.jsonObject
                    val coordinates = feature["geometry"]
                        ?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
                    val lon = coordinates.getOrNull(0)?.toString()?.toDoubleOrNull()
                        ?: return@mapNotNull null
                    val lat = coordinates.getOrNull(1)?.toString()?.toDoubleOrNull()
                        ?: return@mapNotNull null
                    val props = feature["properties"]?.jsonObject ?: JsonObject(emptyMap())
                    toPlace(props, GeoPoint(lat, lon))
                }.distinctBy {
                    "${it.name}|${it.subtitle}|${"%.4f".format(it.point.lat)}|${"%.4f".format(it.point.lon)}"
                }
            }
        }

    private fun toPlace(props: JsonObject, point: GeoPoint): Place {
        val street = props.stringOrNull("street")
        val houseNumber = props.stringOrNull("housenumber")
        val postcode = props.stringOrNull("postcode")
        val city = props.stringOrNull("city")
        val state = props.stringOrNull("state")
        val country = props.stringOrNull("country")

        val streetLine = listOfNotNull(street, houseNumber).joinToString(" ")
        val cityLine = listOfNotNull(postcode, city).joinToString(" ")

        val name = props.stringOrNull("name")
            ?: streetLine.takeIf { it.isNotBlank() }
            ?: cityLine.takeIf { it.isNotBlank() }
            ?: "Unbekannter Ort"

        val subtitle = listOf(streetLine, cityLine, state, country)
            .filterNotNull()
            .filter { it.isNotBlank() && it != name }
            .distinct()
            .joinToString(", ")

        return Place(name = name, subtitle = subtitle, point = point)
    }
}
