package de.ls.trucknav.nav

import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.Maneuver
import de.ls.trucknav.model.RouteResult
import de.ls.trucknav.util.roundedDistance

data class NavUpdate(
    val snapped: GeoPoint,
    val distanceFromRoute: Double,
    val remainingDistance: Double,
    val remainingTime: Double,
    val nextManeuver: Maneuver?,
    val distanceToManeuver: Double,
    val offRoute: Boolean,
    val arrived: Boolean,
    val announcement: String?,
)

/**
 * Zustandsautomat fuer die laufende Navigation: Fortschritt, naechste
 * Fahranweisung, Abweichungserkennung und Sprachansaagen.
 */
class NavigationEngine {

    private var route: RouteResult? = null
    private var geometry: RouteGeometry? = null
    private var progress = 0.0
    private var offRouteCount = 0
    private val announced = HashSet<String>()

    fun setRoute(route: RouteResult) {
        this.route = route
        geometry = RouteGeometry(route.points)
        progress = 0.0
        offRouteCount = 0
        announced.clear()
    }

    fun clear() {
        route = null
        geometry = null
        progress = 0.0
        offRouteCount = 0
        announced.clear()
    }

    fun initialUpdate(): NavUpdate? {
        val r = route ?: return null
        val start = r.points.firstOrNull() ?: return null
        val next = r.maneuvers.firstOrNull { it.type != 0 && it.type != 1 }
            ?: r.maneuvers.getOrNull(1)
        return NavUpdate(
            snapped = start,
            distanceFromRoute = 0.0,
            remainingDistance = r.distance,
            remainingTime = r.time,
            nextManeuver = next,
            distanceToManeuver = next?.startDistance ?: 0.0,
            offRoute = false,
            arrived = false,
            announcement = null,
        )
    }

    fun update(location: GeoPoint, speedMps: Double): NavUpdate? {
        val r = route ?: return null
        val g = geometry ?: return null

        val snap = g.snap(location)
        if (snap.distanceFromRoute > 65.0) offRouteCount++ else offRouteCount = 0
        val offRoute = offRouteCount >= 3 || snap.distanceFromRoute > 150.0

        // Fortschritt laeuft nur vorwaerts, damit GPS-Rauschen an Kreuzungen
        // die Route nicht rueckwaerts springen laesst.
        progress = if (offRoute) {
            progress
        } else {
            maxOf(progress, snap.distanceAlong - 3.0).coerceAtMost(g.total)
        }

        val remaining = (g.total - progress).coerceAtLeast(0.0)
        val next = r.maneuvers.firstOrNull { it.startDistance > progress + 5.0 }
        val distanceTo = next?.let { (it.startDistance - progress).coerceAtLeast(0.0) } ?: remaining

        var announcement: String? = null
        if (next != null && !offRoute) {
            val thresholds = if (speedMps > 19.0) {
                doubleArrayOf(1000.0, 400.0, 120.0)
            } else {
                doubleArrayOf(500.0, 180.0, 60.0)
            }
            val matching = thresholds.filter { distanceTo <= it }.sorted()
            val fresh = matching.filter { announced.add("${next.index}:${it.toInt()}") }
            if (fresh.isNotEmpty()) {
                announcement = if (distanceTo > 120.0) {
                    "In ${roundedDistance(distanceTo)} Metern ${next.verbal}"
                } else {
                    next.verbal
                }
            }
        }

        val arrived = remaining < 35.0 && !offRoute
        val remainingTime = if (r.distance > 1.0) r.time * (remaining / r.distance) else 0.0

        return NavUpdate(
            snapped = snap.point,
            distanceFromRoute = snap.distanceFromRoute,
            remainingDistance = remaining,
            remainingTime = remainingTime,
            nextManeuver = next,
            distanceToManeuver = distanceTo,
            offRoute = offRoute,
            arrived = arrived,
            announcement = announcement,
        )
    }
}
