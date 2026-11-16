package de.ls.trucknav.net

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Eine auf GitHub gefundene neuere Version. */
data class UpdateInfo(
    /** Versionsnummer ohne fuehrendes "v", z. B. "0.4.0". */
    val version: String,
    /** Tag-Name des Releases, z. B. "v0.4.0". */
    val tag: String,
    /** Direkter Download-Link der APK (null, wenn kein Asset vorhanden ist). */
    val downloadUrl: String?,
    /** Web-Seite des Releases. */
    val pageUrl: String,
    /** Versionshinweise (Changelog). */
    val notes: String,
)

/**
 * Prueft die neueste Veroeffentlichung im GitHub-Repository und laedt die
 * APK herunter. Das Repository ist oeffentlich, daher ist kein Token noetig.
 */
class UpdateService(private val client: OkHttpClient = Http.client) {

    /** Liefert die neueste Version oder `null` bei Fehler/keinem Release. */
    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("User-Agent", Http.USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                parse(response.body?.string().orEmpty())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Laedt die APK in den Cache. [onProgress] meldet den Fortschritt (0..1),
     * sofern die Groesse bekannt ist. Liefert die Datei oder `null` bei Fehler.
     */
    suspend fun download(
        url: String,
        context: Context,
        onProgress: (Float) -> Unit = {},
    ): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "ls-trucknav-update.apk")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Http.USER_AGENT)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val total = body.contentLength()
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                val percent = (written * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(written.toFloat() / total)
                                }
                            }
                        }
                    }
                }
            }
            target
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** Repository, aus dem die App ihre Updates bezieht. */
        const val REPO = "josgot245/ls-trucknav"
        const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

        /** Liest die Release-Antwort der GitHub-API. */
        internal fun parse(body: String): UpdateInfo? {
            val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return null
            // Entwuerfe und Vorabversionen werden von der API bereits
            // ausgeschlossen; zur Sicherheit dennoch pruefen.
            if (root.booleanOrNull("draft") == true) return null
            if (root.booleanOrNull("prerelease") == true) return null

            val tag = root.stringOrNull("tag_name") ?: return null
            val version = tag.removePrefix("v").removePrefix("V").trim()
            if (version.isBlank()) return null

            val assets = root["assets"]?.jsonArray
            val apk = assets
                ?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                ?.firstOrNull { obj ->
                    obj.stringOrNull("name")?.endsWith(".apk", ignoreCase = true) == true
                }

            return UpdateInfo(
                version = version,
                tag = tag,
                downloadUrl = apk?.stringOrNull("browser_download_url"),
                pageUrl = root.stringOrNull("html_url").orEmpty(),
                notes = root.stringOrNull("body").orEmpty(),
            )
        }
    }
}
