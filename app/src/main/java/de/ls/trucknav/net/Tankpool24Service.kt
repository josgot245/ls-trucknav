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
 * Die ZIP-Datei wird einmal heruntergeladen, die enthaltene Garmin-CSV
 * entpackt und als Klartext im App-Verzeichnis zwischengespeichert. Danach
 * funktioniert die Anzeige auch ohne Netz. Die Daten stammen von tankpool24
 * (nicht OpenStreetMap) und sind ohne Gewaehr.
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
            val cached = runCatching { cache.readText(Charsets.ISO_8859_1) }.getOrNull()
            if (cached != null) return@withContext parseCsv(cached)
        }
        val downloaded = downloadCsv() ?: return@withContext null
        runCatching { cache.writeText(downloaded, Charsets.ISO_8859_1) }
        parseCsv(downloaded)
    }

    private fun downloadCsv(): String? = try {
        val request = Request.Builder()
            .url(SOURCE_URL)
            .header("User-Agent", Http.USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                response.body?.bytes()?.let { unzipCsv(it) }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Offizielle Navigationsdaten von tankpool24.eu (ZIP mit CSV/GPX). */
        const val SOURCE_URL =
            "https://www.tankpool24.eu/fileadmin/tankpool24/media/Navigationsdaten.zip"

        private const val CACHE_FILE = "tankpool24.csv"

        /** Sucht die Garmin-CSV in der ZIP und liefert sie als Text (Latin-1). */
        internal fun unzipCsv(bytes: ByteArray): String? {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    if (name.endsWith("garminCSV.csv", ignoreCase = true)) {
                        return zip.readBytes().toString(Charsets.ISO_8859_1)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            return null
        }

        /**
         * Liest die Garmin-CSV. Format je Zeile:
         * `Laengengrad,Breitengrad,<Nr> - <Ort>,`
         */
        internal fun parseCsv(text: String): List<TruckPoi> {
            val result = ArrayList<TruckPoi>()
            for (rawLine in text.lineSequence()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                val fields = line.split(',')
                if (fields.size < 3) continue
                val lon = fields[0].trim().toDoubleOrNull() ?: continue
                val lat = fields[1].trim().toDoubleOrNull() ?: continue
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                val label = fields[2].trim().trim('"').trim()
                if (label.isEmpty()) continue
                val id = label.substringBefore(' ').toLongOrNull() ?: continue
                val place = label.substringAfter('-', "").trim().ifBlank { label }
                result.add(
                    TruckPoi(
                        id = id,
                        point = GeoPoint(lat, lon),
                        name = "Tankpool24 $place",
                        kind = TruckPoiKind.TANKPOOL24,
                    )
                )
            }
            return result
        }
    }
}
