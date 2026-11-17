package de.ls.trucknav.ui

import de.ls.trucknav.data.MapStyleMode
import de.ls.trucknav.data.MapStyles
import de.ls.trucknav.data.RouteSort
import de.ls.trucknav.data.SettingsRepository
import de.ls.trucknav.data.TruckProfile
import de.ls.trucknav.data.VehicleMode
import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.HeightRestriction
import de.ls.trucknav.model.Place
import de.ls.trucknav.model.RouteResult
import de.ls.trucknav.model.SpeedCamera
import de.ls.trucknav.model.TrafficWarning
import de.ls.trucknav.model.TruckPoi
import de.ls.trucknav.nav.NavUpdate
import de.ls.trucknav.net.UpdateInfo

data class UiState(
    val profile: TruckProfile = TruckProfile.DEFAULT,

    /** LKW- oder PKW-Modus (steuert Routing und Tempolimits). */
    val vehicleMode: VehicleMode = VehicleMode.TRUCK,

    val routingUrl: String = SettingsRepository.DEFAULT_ROUTING_URL,
    val carFallback: Boolean = true,
    val voiceEnabled: Boolean = true,
    val cameraWarnings: Boolean = true,

    /** Gewaehlter Kartenstil-Modus (automatisch nach Tageszeit, hell oder dunkel). */
    val mapStyleMode: MapStyleMode = MapStyleMode.AUTO,

    /** Aktuell wirksame Kartenstil-URL (aus Modus + Tageszeit abgeleitet). */
    val mapStyleUrl: String = MapStyles.DARK,

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

    /** Verkehrsmeldungen der Autobahn GmbH (Stau, Sperrung, Baustelle). */
    val trafficEnabled: Boolean = true,
    val trafficWarnings: List<TrafficWarning> = emptyList(),
    val trafficWarning: TrafficWarning? = null,
    val trafficWarningDistance: Double = 0.0,

    /** LKW-Punkte (Parkplatz, Raststaette, Tankstelle) aus OpenStreetMap. */
    val truckPoisEnabled: Boolean = false,
    val truckPois: List<TruckPoi> = emptyList(),

    /** Tankpool24-Stationen (offizielle Liste, zusaetzlich zu den OSM-Punkten). */
    val tankpoolPoisEnabled: Boolean = false,

    /** Durchfahrtshoehen-Warnungen aus OpenStreetMap. */
    val heightWarningsEnabled: Boolean = true,
    val heightRestrictions: List<HeightRestriction> = emptyList(),
    val heightWarning: HeightRestriction? = null,
    val heightWarningDistance: Double = 0.0,

    val query: String = "",
    val searching: Boolean = false,
    val results: List<Place> = emptyList(),
    val destination: Place? = null,

    /** Gespeicherte Ziele (Favoriten), neueste zuerst. */
    val favorites: List<Place> = emptyList(),

    /** Zuletzt gestartete Ziele (Verlauf), neueste zuerst. */
    val history: List<Place> = emptyList(),

    val route: RouteResult? = null,

    /** Alle vorgeschlagenen Routen (Hauptroute zuerst), zum Vergleichen. */
    val routeOptions: List<RouteResult> = emptyList(),

    /** Index der ausgewaehlten Route in [routeOptions]. */
    val selectedRouteIndex: Int = 0,

    /** Sortierung der Vorschlagsliste (schnellste oder kuerzeste zuerst). */
    val routeSort: RouteSort = RouteSort.DURATION,

    val routing: Boolean = false,
    val routeError: String? = null,
    val notice: String? = null,

    val navActive: Boolean = false,
    val nav: NavUpdate? = null,
    val arrived: Boolean = false,

    val showSearch: Boolean = false,
    val showSettings: Boolean = false,

    /** Installierte App-Version (aus BuildConfig). */
    val currentVersion: String = "",

    /** Laeuft gerade eine Update-Pruefung? */
    val updateChecking: Boolean = false,

    /** Verfuegbare neuere Version (null = keine). */
    val updateInfo: UpdateInfo? = null,

    /** Wird gerade ein Update heruntergeladen? */
    val updateDownloading: Boolean = false,

    /** Downloadfortschritt 0..1 (null = unbekannt). */
    val updateProgress: Float? = null,

    /** Fehlermeldung der Update-Pruefung/-Installation. */
    val updateError: String? = null,

    /** Neutrale Rueckmeldung der Update-Pruefung (z. B. "App ist aktuell"). */
    val updateMessage: String? = null,

    /** Update-Dialog anzeigen. */
    val showUpdateDialog: Boolean = false,
)

/** Buendelt die App-weiten Schalter aus dem Einstellungsdialog. */
data class AppOptions(
    val routingUrl: String,
    val vehicleMode: VehicleMode,
    val carFallback: Boolean,
    val voiceEnabled: Boolean,
    val cameraWarnings: Boolean,
    val trafficWarnings: Boolean,
    val truckPois: Boolean,
    val tankpoolPois: Boolean,
    val heightWarnings: Boolean,
    val mapStyleMode: MapStyleMode,
)
