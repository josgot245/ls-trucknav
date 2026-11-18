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
    /**
     * true, wenn die Warteschlange der Sprachausgabe geleert werden soll
     * (z. B. weil ein Manoever passiert wurde und dessen Ansagen nicht mehr
     * abgespielt werden sollen).
     */
    val flushSpeech: Boolean = false,
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

    /**
     * Pro Manoever die zuletzt angesagte Entfernungsstufe (0 = weiteste,
     * 2 = naechste). Dadurch wird jede Stufe genau einmal angesagt – auch wenn
     * die Geschwindigkeit um die Umschaltgrenze schwankt.
     */
    private val announcedStage = HashMap<Int, Int>()

    /** Manoever, das zuletzt als naechstes anstand (Flush-Erkennung). */
    private var lastManeuverIndex = -1

    fun setRoute(route: RouteResult) {
        this.route = route
        geometry = RouteGeometry(route.points)
        progress = 0.0
        offRouteCount = 0
        announcedStage.clear()
        lastManeuverIndex = -1
    }

    fun clear() {
        route = null
        geometry = null
        progress = 0.0
        offRouteCount = 0
        announcedStage.clear()
        lastManeuverIndex = -1
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
        var flushSpeech = false
        if (next != null && !offRoute) {
            // Wurde das zuvor anstehende Manoever passiert, sind dessen Ansagen
            // veraltet und werden verworfen, damit sie nicht nachtraeglich noch
            // abgespielt werden.
            if (next.index != lastManeuverIndex) {
                if (announcedStage.containsKey(lastManeuverIndex)) flushSpeech = true
                lastManeuverIndex = next.index
            }
            val thresholds = if (speedMps > HIGH_SPEED_MPS) {
                HIGH_SPEED_THRESHOLDS
            } else {
                LOW_SPEED_THRESHOLDS
            }
            // Stufe 0 = weiteste Ansage, 2 = unmittelbar vor dem Manoever.
            // Die Schwellen sind absteigend sortiert; gesucht ist die letzte,
            // die noch groesser/gleich der Restdistanz ist.
            val stage = thresholds.indexOfLast { distanceTo <= it }
            if (stage > (announcedStage[next.index] ?: -1)) {
                announcedStage[next.index] = stage
                announcement = if (distanceTo > CLOSE_ANNOUNCE_DISTANCE_M) {
                    "In ${roundedDistance(distanceTo)} Metern ${next.verbal}"
                } else {
                    next.verbal
                }
                // Neue Anweisung ersetzt noch nicht gesprochene aeltere.
                flushSpeech = true
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
            flushSpeech = flushSpeech,
        )
    }

    private companion object {
        /** Ab dieser Geschwindigkeit (m/s, ~68 km/h) wird frueher angesagt. */
        const val HIGH_SPEED_MPS = 19.0

        /** Ansagestufen bei hoher Geschwindigkeit (absteigend). */
        val HIGH_SPEED_THRESHOLDS = doubleArrayOf(1000.0, 400.0, 120.0)

        /** Ansagestufen bei niedriger Geschwindigkeit (absteigend). */
        val LOW_SPEED_THRESHOLDS = doubleArrayOf(500.0, 180.0, 60.0)

        /** Ab dieser Restdistanz wird die Entfernung mitgesprochen. */
        const val CLOSE_ANNOUNCE_DISTANCE_M = 120.0
    }
}
