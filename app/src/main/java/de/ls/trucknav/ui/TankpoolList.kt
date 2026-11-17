package de.ls.trucknav.ui

import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.TruckPoi
import de.ls.trucknav.util.distanceAndAlongToPolylineMeters
import de.ls.trucknav.util.distanceMeters
import kotlin.math.abs

/** Obergrenze der in der Tankpool24-Liste angezeigten Stationen. */
internal const val MAX_TANKPOOL_ENTRIES = 100

/**
 * Ein Eintrag der Tankpool24-Liste: die Station plus die Entfernungen, die in
 * der Liste angezeigt werden.
 */
data class TankpoolEntry(
    val poi: TruckPoi,
    /** Luftlinie zur aktuellen Position in Metern. */
    val distanceMeters: Double,
    /** Abstand zur Route in Metern (nur bei aktiver Zielfuehrung, sonst null). */
    val routeDistanceMeters: Double? = null,
)

/**
 * Tankpool24-Stationen im Umkreis [radiusMeters] um [position], naechste zuerst.
 * Wird ohne aktive Zielfuehrung verwendet (Standard: 100 km).
 */
internal fun tankpoolNearby(
    stations: List<TruckPoi>,
    position: GeoPoint,
    radiusMeters: Double,
    limit: Int = MAX_TANKPOOL_ENTRIES,
): List<TankpoolEntry> =
    stations.asSequence()
        .map { it to distanceMeters(position, it.point) }
        .filter { it.second <= radiusMeters }
        .sortedBy { it.second }
        .take(limit)
        .map { TankpoolEntry(poi = it.first, distanceMeters = it.second) }
        .toList()

/**
 * Tankpool24-Stationen im Umkreis [radiusMeters] um die Route [route], in
 * Fahrtrichtung zuerst. Wird bei aktiver Zielfuehrung verwendet (Standard:
 * 15 km).
 *
 * [position] dient der Sortierung: bereits passierte Stationen landen hinter
 * den noch bevorstehenden. Ohne Position wird ab dem Routenstart sortiert.
 */
internal fun tankpoolAlongRoute(
    stations: List<TruckPoi>,
    route: List<GeoPoint>,
    position: GeoPoint?,
    radiusMeters: Double,
    limit: Int = MAX_TANKPOOL_ENTRIES,
): List<TankpoolEntry> {
    if (route.isEmpty()) return emptyList()
    val vehicleAlong = position?.let { distanceAndAlongToPolylineMeters(it, route).second } ?: 0.0

    return stations.asSequence()
        .mapNotNull { poi ->
            val (routeDistance, along) = distanceAndAlongToPolylineMeters(poi.point, route)
            if (routeDistance > radiusMeters) return@mapNotNull null
            Triple(poi, routeDistance, along)
        }
        .sortedWith(
            compareBy<Triple<TruckPoi, Double, Double>>(
                // Bevorstehende Stationen (in Fahrtrichtung) zuerst.
                { it.third - vehicleAlong < 0.0 },
                // Dann die naechstgelegene entlang der Route.
                { abs(it.third - vehicleAlong) },
                // Bei gleicher Strecke die naehere an der Route.
                { it.second },
            )
        )
        .take(limit)
        .map { (poi, routeDistance, _) ->
            TankpoolEntry(
                poi = poi,
                distanceMeters = position?.let { distanceMeters(it, poi.point) } ?: routeDistance,
                routeDistanceMeters = routeDistance,
            )
        }
        .toList()
}
