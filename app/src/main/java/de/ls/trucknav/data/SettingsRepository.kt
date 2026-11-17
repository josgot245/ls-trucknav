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
import de.ls.trucknav.model.Place
import kotlinx.coroutines.flow.first

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "lstrucknav_settings")

data class AppSettings(
    val profile: TruckProfile = TruckProfile.DEFAULT,
    val vehicleMode: VehicleMode = VehicleMode.TRUCK,
    val routingUrl: String = SettingsRepository.DEFAULT_ROUTING_URL,
    val carFallback: Boolean = true,
    val voiceEnabled: Boolean = true,
    val cameraWarnings: Boolean = true,
    val trafficWarnings: Boolean = true,
    val truckPois: Boolean = false,
    val tankpoolPois: Boolean = false,
    val heightWarnings: Boolean = true,
    val mapStyleMode: MapStyleMode = MapStyleMode.AUTO,
    val favorites: List<Place> = emptyList(),
    val history: List<Place> = emptyList(),
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
        val VEHICLE_MODE = stringPreferencesKey("vehicle_mode")
        val ROUTING_URL = stringPreferencesKey("routing_url")
        val CAR_FALLBACK = booleanPreferencesKey("car_fallback")
        val VOICE = booleanPreferencesKey("voice_enabled")
        val CAMERA_WARNINGS = booleanPreferencesKey("camera_warnings")
        val TRAFFIC_WARNINGS = booleanPreferencesKey("traffic_warnings")
        val TRUCK_POIS = booleanPreferencesKey("truck_pois")
        val TANKPOOL_POIS = booleanPreferencesKey("tankpool_pois")
        val HEIGHT_WARNINGS = booleanPreferencesKey("height_warnings")
        val MAP_STYLE_MODE = stringPreferencesKey("map_style_mode")
        val FAVORITES = stringPreferencesKey("favorites")
        val HISTORY = stringPreferencesKey("history")
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
            vehicleMode = p[Keys.VEHICLE_MODE]
                ?.let { name -> VehicleMode.entries.firstOrNull { it.name == name } }
                ?: VehicleMode.TRUCK,
            routingUrl = p[Keys.ROUTING_URL]?.takeIf { it.isNotBlank() } ?: DEFAULT_ROUTING_URL,
            carFallback = p[Keys.CAR_FALLBACK] ?: true,
            voiceEnabled = p[Keys.VOICE] ?: true,
            cameraWarnings = p[Keys.CAMERA_WARNINGS] ?: true,
            trafficWarnings = p[Keys.TRAFFIC_WARNINGS] ?: true,
            truckPois = p[Keys.TRUCK_POIS] ?: false,
            tankpoolPois = p[Keys.TANKPOOL_POIS] ?: false,
            heightWarnings = p[Keys.HEIGHT_WARNINGS] ?: true,
            mapStyleMode = p[Keys.MAP_STYLE_MODE]
                ?.let { name -> MapStyleMode.entries.firstOrNull { it.name == name } }
                ?: MapStyleMode.AUTO,
            favorites = PlaceCodec.decode(p[Keys.FAVORITES]),
            history = PlaceCodec.decode(p[Keys.HISTORY]),
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

    suspend fun saveVehicleMode(mode: VehicleMode) {
        context.settingsStore.edit { p -> p[Keys.VEHICLE_MODE] = mode.name }
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

    suspend fun saveTrafficWarnings(enabled: Boolean) {
        context.settingsStore.edit { p -> p[Keys.TRAFFIC_WARNINGS] = enabled }
    }

    suspend fun saveTruckPois(enabled: Boolean) {
        context.settingsStore.edit { p -> p[Keys.TRUCK_POIS] = enabled }
    }

    suspend fun saveTankpoolPois(enabled: Boolean) {
        context.settingsStore.edit { p -> p[Keys.TANKPOOL_POIS] = enabled }
    }

    suspend fun saveHeightWarnings(enabled: Boolean) {
        context.settingsStore.edit { p -> p[Keys.HEIGHT_WARNINGS] = enabled }
    }

    suspend fun saveMapStyleMode(mode: MapStyleMode) {
        context.settingsStore.edit { p -> p[Keys.MAP_STYLE_MODE] = mode.name }
    }

    suspend fun saveFavorites(favorites: List<Place>) {
        val encoded = PlaceCodec.encode(favorites)
        context.settingsStore.edit { p -> p[Keys.FAVORITES] = encoded }
    }

    suspend fun saveHistory(history: List<Place>) {
        val encoded = PlaceCodec.encode(history)
        context.settingsStore.edit { p -> p[Keys.HISTORY] = encoded }
    }

    companion object {
        const val DEFAULT_ROUTING_URL = "https://valhalla1.openstreetmap.de/route"

        /** Standard-Kartenstil (dunkel); siehe [MapStyles] fuer hell/dunkel. */
        const val DEFAULT_MAP_STYLE = MapStyles.DARK
    }
}
