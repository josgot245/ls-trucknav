package de.ls.trucknav.data

import de.ls.trucknav.model.Place
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serialisiert Favoriten und Verlauf fuer DataStore. Fehlerhafte oder fehlende
 * Daten liefern eine leere Liste, damit die App nie an kaputtem JSON haengt.
 */
object PlaceCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(places: List<Place>): String =
        runCatching { json.encodeToString(places) }.getOrDefault("[]")

    fun decode(text: String?): List<Place> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Place>>(text) }.getOrDefault(emptyList())
    }
}
