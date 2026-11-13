package de.ls.trucknav.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "lstrucknav_settings")

data class AppSettings(
    val profile: TruckProfile = TruckProfile.DEFAULT,
    val routingUrl: String = SettingsRepository.DEFAULT_ROUTING_URL,
    val carFallback: Boolean = true,
    val voiceEnabled: Boolean = true,
    val cameraWarnings: Boolean = true,
)

/** Persistente Einstellungen (LKW-Profil, Routing-Server, Sprache). */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val HEIGHT = doublePreferencesKey("profile_height")
        val WIDTH = doublePreferencesKey("profile_width")
        val LENGTH = doublePreferencesKey("profile_length")
        val WEIGHT = doublePreferencesKey("profile_weight")
        val AXLE_LOAD = doublePreferencesKey("profile_axle_load")
        val AXLES = intPreferencesKey("profile_axles")
        val HAZMAT = booleanPreferencesKey("profile_hazmat")
        val AVOID_TOLLS = booleanPreferencesKey("profile_avoid_tolls")
        val AVOID_HIGHWAYS = booleanPreferencesKey("profile_avoid_highways")
        val ROUTING_URL = stringPreferencesKey("routing_url")
        val CAR_FALLBACK = booleanPreferencesKey("car_fallback")
        val VOICE = booleanPreferencesKey("voice_enabled")
        val CAMERA_WARNINGS = booleanPreferencesKey("camera_warnings")
    }

    suspend fun load(): AppSettings {
        val p = context.settingsStore.data.first()
        val defaults = TruckProfile.DEFAULT
        return AppSettings(
            profile = TruckProfile(
                height = p[Keys.HEIGHT] ?: defaults.height,
                width = p[Keys.WIDTH] ?: defaults.width,
                length = p[Keys.LENGTH] ?: defaults.length,
                weight = p[Keys.WEIGHT] ?: defaults.weight,
                axleLoad = p[Keys.AXLE_LOAD] ?: defaults.axleLoad,
                axles = p[Keys.AXLES] ?: defaults.axles,
                hazmat = p[Keys.HAZMAT] ?: defaults.hazmat,
                avoidTolls = p[Keys.AVOID_TOLLS] ?: defaults.avoidTolls,
                avoidHighways = p[Keys.AVOID_HIGHWAYS] ?: defaults.avoidHighways,
            ).sanitized(),
            routingUrl = p[Keys.ROUTING_URL]?.takeIf { it.isNotBlank() } ?: DEFAULT_ROUTING_URL,
            carFallback = p[Keys.CAR_FALLBACK] ?: true,
            voiceEnabled = p[Keys.VOICE] ?: true,
            cameraWarnings = p[Keys.CAMERA_WARNINGS] ?: true,
        )
    }

    suspend fun saveProfile(profile: TruckProfile) {
        val safe = profile.sanitized()
        context.settingsStore.edit { p ->
            p[Keys.HEIGHT] = safe.height
            p[Keys.WIDTH] = safe.width
            p[Keys.LENGTH] = safe.length
            p[Keys.WEIGHT] = safe.weight
            p[Keys.AXLE_LOAD] = safe.axleLoad
            p[Keys.AXLES] = safe.axles
            p[Keys.HAZMAT] = safe.hazmat
            p[Keys.AVOID_TOLLS] = safe.avoidTolls
            p[Keys.AVOID_HIGHWAYS] = safe.avoidHighways
        }
    }

    suspend fun saveRoutingUrl(url: String) {
        context.settingsStore.edit { p -> p[Keys.ROUTING_URL] = url }
    }

    suspend fun saveCarFallback(enabled: Boolean) {
        context.settingsStore.edit { p -> p[Keys.CAR_FALLBACK] = enabled }
    }

    suspend fun saveVoiceEnabled(enabled: Boolean) {
        context.settingsStore.edit { p -> p[Keys.VOICE] = enabled }
    }

    suspend fun saveCameraWarnings(enabled: Boolean) {
        context.settingsStore.edit { p -> p[Keys.CAMERA_WARNINGS] = enabled }
    }

    companion object {
        const val DEFAULT_ROUTING_URL = "https://valhalla1.openstreetmap.de/route"
        const val DEFAULT_MAP_STYLE = "https://tiles.openfreemap.org/styles/dark"
    }
}
