package de.ls.trucknav.nav

import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.util.cumulativeDistances
import kotlin.math.cos
import kotlin.math.sqrt

data class SnapResult(
    val point: GeoPoint,
    val distanceAlong: Double,
    val distanceFromRoute: Double,
)

/**
 * Geometrie einer Route: laengentreue Projektion der GPS-Position auf die Route.
 * Die Berechnung ist bewusst einfach (lokale ebene Naeherung) und fuer
 * Geschwindigkeiten bis ~200 km/h voellig ausreichend.
 */
class RouteGeometry(val points: List<GeoPoint>) {

    val cumulative: DoubleArray = cumulativeDistances(points)
    val total: Double = cumulative.lastOrNull() ?: 0.0

    fun snap(location: GeoPoint): SnapResult {
        if (points.size < 2) {
            return SnapResult(location, 0.0, 0.0)
        }
        val metersPerDegLat = 111132.0
        val metersPerDegLon = 111320.0 * cos(Math.toRadians(location.lat))

        fun x(p: GeoPoint) = (p.lon - location.lon) * metersPerDegLon
        fun y(p: GeoPoint) = (p.lat - location.lat) * metersPerDegLat

        var bestDistanceSq = Double.MAX_VALUE
        var bestSegment = 0
        var bestT = 0.0

        for (i in 0 until points.size - 1) {
            val ax = x(points[i])
            val ay = y(points[i])
            val bx = x(points[i + 1])
            val by = y(points[i + 1])
            val dx = bx - ax
            val dy = by - ay
            val lengthSq = dx * dx + dy * dy
            val t = if (lengthSq < 1e-6) {
                0.0
            } else {
                ((-ax * dx - ay * dy) / lengthSq).coerceIn(0.0, 1.0)
            }
            val px = ax + t * dx
            val py = ay + t * dy
            val distanceSq = px * px + py * py
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                bestSegment = i
                bestT = t
            }
        }

        val a = points[bestSegment]
        val b = points[bestSegment + 1]
        val snapped = GeoPoint(
            lat = a.lat + (b.lat - a.lat) * bestT,
            lon = a.lon + (b.lon - a.lon) * bestT,
        )
        val segmentLength = cumulative[bestSegment + 1] - cumulative[bestSegment]
        val along = cumulative[bestSegment] + segmentLength * bestT
        return SnapResult(snapped, along, sqrt(bestDistanceSq))
    }
}
