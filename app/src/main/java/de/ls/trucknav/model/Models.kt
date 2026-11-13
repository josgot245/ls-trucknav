package de.ls.trucknav.model

/** Ein geografischer Punkt in Grad. */
data class GeoPoint(val lat: Double, val lon: Double)

/** Ein Suchtreffer bzw. ein Ziel. */
data class Place(
    val name: String,
    val subtitle: String,
    val point: GeoPoint,
)

/** Ein Fahranweisungsschritt aus der Route. */
data class Maneuver(
    val index: Int,
    val type: Int,
    val instruction: String,
    val verbal: String,
    val streetNames: List<String>,
    val startDistance: Double,
    val length: Double,
    val time: Double,
)

/** Ein stationaerer Blitzer aus OpenStreetMap. */
data class SpeedCamera(
    val id: Long,
    val point: GeoPoint,
    val maxSpeedKmh: Int?,
    val name: String?,
)

/** Eine berechnete Route inklusive Fahranweisungen. */
data class RouteResult(
    val points: List<GeoPoint>,
    val maneuvers: List<Maneuver>,
    val distance: Double,
    val time: Double,
    val destination: Place?,
    val truckUsed: Boolean,
)
