package de.ls.trucknav.net

import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.TruckPoi
import de.ls.trucknav.model.TruckPoiKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Tankpool24-Stationen aus der offiziellen Navigationsdatei von tankpool24.eu.
 *
 * Die ZIP-Datei wird einmal heruntergeladen, die enthaltene KML-Datei
 * entpackt und als Klartext im App-Verzeichnis zwischengespeichert. Danach
 * funktioniert die Anzeige auch ohne Netz. Die Daten stammen von tankpool24
 * (nicht OpenStreetMap) und sind ohne Gewaehr.
 *
 * Die KML enthaelt neben Name und Koordinaten auch die Anschrift, die fuer die
 * Info-Karte beim Antippen einer Station angezeigt wird.
 */
class Tankpool24Service(
    private val cacheDir: File,
    private val client: OkHttpClient = Http.slowClient,
) {

    /**
     * Laedt die Stationen aus dem Zwischenspeicher oder – falls noch nicht
     * vorhanden – aus dem Netz.
     *
     * @return die Stationen oder `null`, wenn weder Cache noch Download
     *         verfuegbar sind.
     */
    suspend fun load(): List<TruckPoi>? = withContext(Dispatchers.IO) {
        val cache = File(cacheDir, CACHE_FILE)
        if (cache.isFile && cache.length() > 0L) {
            val cached = runCatching { cache.readText(Charsets.UTF_8) }.getOrNull()
            if (cached != null) return@withContext parseKml(cached)
        }
        val downloaded = downloadKml() ?: return@withContext null
        runCatching { cache.writeText(downloaded, Charsets.UTF_8) }
        parseKml(downloaded)
    }

    private fun downloadKml(): String? = try {
        val request = Request.Builder()
            .url(SOURCE_URL)
            .header("User-Agent", Http.USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                response.body?.bytes()?.let { unzipKml(it) }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Offizielle Navigationsdaten von tankpool24.eu (ZIP mit KML/CSV/GPX). */
        const val SOURCE_URL =
            "https://www.tankpool24.eu/fileadmin/tankpool24/media/Navigationsdaten.zip"

        private const val CACHE_FILE = "tankpool24.kml"

        /**
         * Schluesselwoerter, die in der Beschreibung auf die Anschrift folgen.
         * Ab dem ersten Treffer endet der Adressblock.
         */
        private val META_KEYWORDS = listOf(
            "GEO-Daten",
            "Auf dem Gel",
            "Hotline",
            "ffnungszeiten",
            "Produkte",
            "Anbindung",
            "Kassensystem",
            "EC Tankungen",
            "Betreiber",
            "Im Gewerbegebiet",
        )

        private val PLACEMARK = Regex(
            "<Placemark>(.*?)</Placemark>",
            RegexOption.DOT_MATCHES_ALL,
        )
        private val NAME = Regex("<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL)
        private val DESCRIPTION = Regex(
            "<description><!\\[CDATA\\[(.*?)\\]\\]></description>",
            RegexOption.DOT_MATCHES_ALL,
        )
        private val COORDINATES = Regex(
            "<coordinates>(.*?)</coordinates>",
            RegexOption.DOT_MATCHES_ALL,
        )

        /** Sucht die KML in der ZIP und liefert sie als Text (UTF-8). */
        internal fun unzipKml(bytes: ByteArray): String? {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    if (name.endsWith(".kml", ignoreCase = true)) {
                        return zip.readBytes().toString(Charsets.UTF_8)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            return null
        }

        /**
         * Liest die KML. Je `<Placemark>` stehen Name, Anschrift (in der
         * Beschreibung) und Koordinaten. Nicht lesbare Eintraege werden
         * uebersprungen.
         */
        internal fun parseKml(text: String): List<TruckPoi> {
            val result = ArrayList<TruckPoi>()
            for (match in PLACEMARK.findAll(text)) {
                val block = match.groupValues[1]
                val name = NAME.find(block)?.groupValues?.get(1)?.trim().orEmpty()
                if (name.isEmpty()) continue
                val id = name.substringBefore(' ').toLongOrNull() ?: continue
                val place = name.substringAfter('-', "").trim().ifBlank { name }

                val coordinate = COORDINATES.find(block)?.groupValues?.get(1) ?: continue
                val parts = coordinate.split(',')
                val lon = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: continue
                val lat = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: continue
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue

                val description = DESCRIPTION.find(block)?.groupValues?.get(1)
                result.add(
                    TruckPoi(
                        id = id,
                        point = GeoPoint(lat, lon),
                        name = "Tankpool24 $place",
                        kind = TruckPoiKind.TANKPOOL24,
                        address = description?.let { addressFromDescription(it) },
                    )
                )
            }
            return result
        }

        /**
         * Zieht die Anschrift ("Strasse, PLZ Ort") aus der KML-Beschreibung.
         * Der Block vor den Metadaten-Schluesselwoertern ist aufgebaut als
         * `Betreiber / Strasse / PLZ Ort / Region`.
         */
        internal fun addressFromDescription(raw: String): String? {
            val text = raw
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
            val lines = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filterNot { it.startsWith("<img", ignoreCase = true) }
                .toList()
            val block = lines.takeWhile { line ->
                META_KEYWORDS.none { line.startsWith(it, ignoreCase = true) }
            }
            // block[0] = Betreiber, block[1] = Strasse, block[2] = PLZ Ort
            val street = block.getOrNull(1)
            val city = block.getOrNull(2)
            return listOfNotNull(street, city)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { null }
        }
    }
}
