package de.ls.trucknav.ui

import de.ls.trucknav.data.SettingsRepository
import de.ls.trucknav.data.TruckProfile
import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.Place
import de.ls.trucknav.model.RouteResult
import de.ls.trucknav.model.SpeedCamera
import de.ls.trucknav.nav.NavUpdate

data class UiState(
    val profile: TruckProfile = TruckProfile.DEFAULT,
    val routingUrl: String = SettingsRepository.DEFAULT_ROUTING_URL,
    val carFallback: Boolean = true,
    val voiceEnabled: Boolean = true,
    val cameraWarnings: Boolean = true,

    val hasPermission: Boolean = false,
    val location: GeoPoint? = null,
    val bearing: Float = 0f,
    val accuracy: Float = 0f,
    val speedKmh: Double = 0.0,

    /** Zulaessiges Tempo des aktuellen Abschnitts (StVO-Schild 274). */
    val speedLimitKmh: Int? = null,
    val speedLimitRoad: String? = null,

    val cameras: List<SpeedCamera> = emptyList(),
    val cameraWarning: SpeedCamera? = null,
    val cameraWarningDistance: Double = 0.0,

    val query: String = "",
    val searching: Boolean = false,
    val results: List<Place> = emptyList(),
    val destination: Place? = null,

    val route: RouteResult? = null,
    val routing: Boolean = false,
    val routeError: String? = null,
    val notice: String? = null,

    val navActive: Boolean = false,
    val nav: NavUpdate? = null,
    val arrived: Boolean = false,

    val showSearch: Boolean = false,
    val showSettings: Boolean = false,
)
