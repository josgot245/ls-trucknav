package de.ls.trucknav.util

import de.ls.trucknav.model.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6371000.0

/** Entfernung in Metern (Haversine). */
fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
}

/** Peilung von a nach b in Grad (0 = Norden). */
fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
}

/** Kumulierte Entfernungen entlang einer Punktkette. */
fun cumulativeDistances(points: List<GeoPoint>): DoubleArray {
    if (points.isEmpty()) return DoubleArray(0)
    val result = DoubleArray(points.size)
    for (i in 1 until points.size) {
        result[i] = result[i - 1] + distanceMeters(points[i - 1], points[i])
    }
    return result
}

/**
 * Kuerzester Abstand eines Punktes zu einer Punktkette (Polylinie) in Metern.
 * Reicht fuer Abstaende im Bereich weniger hundert Meter.
 */
fun distanceToPolylineMeters(point: GeoPoint, points: List<GeoPoint>): Double {
    if (points.isEmpty()) return Double.MAX_VALUE
    if (points.size == 1) return distanceMeters(point, points[0])

    var best = Double.MAX_VALUE
    for (i in 0 until points.size - 1) {
        val distance = distanceToSegmentMeters(point, points[i], points[i + 1])
        if (distance < best) best = distance
    }
    return best
}

/** Abstand zu einem Liniensegment (lokale ebene Naeherung). */
private fun distanceToSegmentMeters(point: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
    val latRad = Math.toRadians(a.lat)
    val px = (point.lon - a.lon) * cos(latRad)
    val py = point.lat - a.lat
    val bx = (b.lon - a.lon) * cos(latRad)
    val by = b.lat - a.lat

    val lengthSquared = bx * bx + by * by
    val t = if (lengthSquared == 0.0) {
        0.0
    } else {
        ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
    }

    val dx = px - t * bx
    val dy = py - t * by
    // 1 Grad Breite entspricht naeherungsweise 111,32 km.
    return sqrt(dx * dx + dy * dy) * 111_320.0
}


/**
 * Dekodiert eine Valhalla/Google-Polyline mit 1e6-Genauigkeit ("polyline6").
 */
fun decodePolyline6(encoded: String): List<GeoPoint> {
    val points = ArrayList<GeoPoint>(encoded.length / 6)
    var index = 0
    var lat = 0
    var lon = 0
    while (index < encoded.length) {
        var shift = 0
        var result = 0
        var b: Int
        do {
            if (index >= encoded.length) break
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        lat += if (result and 1 != 0) (result shr 1).inv() else (result shr 1)

        shift = 0
        result = 0
        do {
            if (index >= encoded.length) break
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        lon += if (result and 1 != 0) (result shr 1).inv() else (result shr 1)

        points.add(GeoPoint(lat / 1e6, lon / 1e6))
    }
    return points
}
