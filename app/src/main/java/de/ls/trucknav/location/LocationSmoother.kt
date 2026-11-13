package de.ls.trucknav.location

import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.util.bearingDegrees
import de.ls.trucknav.util.distanceMeters
import kotlin.math.abs
import kotlin.math.exp

data class SmoothedFix(
    val point: GeoPoint,
    val bearing: Float,
    val speedMps: Double,
)

/**
 * Glaettet Position, Fahrtrichtung und Geschwindigkeit zeitbasiert.
 *
 * GPS liefert jede Sekunde einen um einige Meter streuenden Punkt. Ungefiltert
 * fuehrt das zu einem sichtbaren Zappeln von Fahrzeugpunkt und Kamera.
 * Die Glaettung ist zeitbasiert (nicht fix pro Messwert), damit sie auch bei
 * unregelmaessigen Fix-Intervallen gleich stark wirkt.
 */
class LocationSmoother(
    private val positionTau: Double = 0.9,
    private val bearingTau: Double = 0.35,
    private val speedTau: Double = 0.6,
) {

    private var hasFix = false
    private var point = GeoPoint(0.0, 0.0)
    private var lastRaw = GeoPoint(0.0, 0.0)
    private var bearing = 0f
    private var speed = 0.0
    private var lastNanos = 0L

    fun reset() {
        hasFix = false
        bearing = 0f
        speed = 0.0
        lastNanos = 0L
    }

    fun smooth(
        rawPoint: GeoPoint,
        rawBearing: Float?,
        rawSpeedMps: Double,
        timeNanos: Long,
    ): SmoothedFix {
        val rawSpeed = rawSpeedMps.coerceAtLeast(0.0)

        if (!hasFix) {
            return restart(rawPoint, rawBearing, rawSpeed, timeNanos)
        }

        val dt = (timeNanos - lastNanos) / 1_000_000_000.0
        if (dt <= 0.0) {
            // gleicher Zeitstempel oder Zeitrechnung kaputt: unveraendert lassen
            return SmoothedFix(point, bearing, speed)
        }
        if (dt > RESET_GAP_S) {
            // lange Luecke (Tunnel, App im Hintergrund): neu aufsetzen
            return restart(rawPoint, rawBearing, rawSpeed, timeNanos)
        }

        val moved = distanceMeters(point, rawPoint)
        if (moved > TELEPORT_M && dt < 2.0) {
            return restart(rawPoint, rawBearing, rawSpeed, timeNanos)
        }

        lastNanos = timeNanos

        // Standstill-Deadband: unter ~3 km/h und kaum Bewegung bleibt der
        // Punkt stehen, statt auf der Stelle zu tanzen.
        if (rawSpeed < STANDSTILL_SPEED_MPS && moved < STANDSTILL_RADIUS_M) {
            speed = 0.0
            lastRaw = rawPoint
            return SmoothedFix(point, bearing, speed)
        }

        val alphaPos = 1.0 - exp(-dt / positionTau)
        point = GeoPoint(
            point.lat + (rawPoint.lat - point.lat) * alphaPos,
            point.lon + (rawPoint.lon - point.lon) * alphaPos,
        )

        val alphaSpeed = 1.0 - exp(-dt / speedTau)
        speed += (rawSpeed - speed) * alphaSpeed
        if (speed < 0.15) speed = 0.0

        val targetBearing = rawBearing?.let { it.toDouble() }
            ?: if (rawSpeed > 1.0 && distanceMeters(lastRaw, rawPoint) > 0.5) {
                bearingDegrees(lastRaw, rawPoint)
            } else {
                null
            }
        if (targetBearing != null && (speed > BEARING_SPEED_MPS || rawSpeed > BEARING_SPEED_MPS)) {
            val delta = shortestAngleDelta(bearing.toDouble(), targetBearing)
            val alphaBearing = 1.0 - exp(-dt / bearingTau)
            bearing = (((bearing + delta * alphaBearing) % 360.0 + 360.0) % 360.0).toFloat()
        }

        lastRaw = rawPoint
        return SmoothedFix(point, bearing, speed)
    }

    private fun restart(
        rawPoint: GeoPoint,
        rawBearing: Float?,
        rawSpeedMps: Double,
        timeNanos: Long,
    ): SmoothedFix {
        hasFix = true
        point = rawPoint
        lastRaw = rawPoint
        speed = rawSpeedMps
        if (rawBearing != null) {
            bearing = rawBearing
        } else if (speed < 0.5) {
            bearing = 0f
        }
        lastNanos = timeNanos
        return SmoothedFix(point, bearing, speed)
    }

    private fun shortestAngleDelta(from: Double, to: Double): Double {
        val delta = (to - from + 540.0) % 360.0 - 180.0
        return if (abs(delta) < 0.001) 0.0 else delta
    }

    private companion object {
        /** Nach dieser Pause wird nicht mehr geglaettet, sondern neu gestartet. */
        const val RESET_GAP_S = 5.0

        /** Ab hier ist ein Positionssprung kein Rauschen mehr. */
        const val TELEPORT_M = 150.0

        const val STANDSTILL_SPEED_MPS = 0.8
        const val STANDSTILL_RADIUS_M = 2.0

        /** Unterhalb dieser Geschwindigkeit wird die Peilung nicht nachgezogen. */
        const val BEARING_SPEED_MPS = 1.5
    }
}
