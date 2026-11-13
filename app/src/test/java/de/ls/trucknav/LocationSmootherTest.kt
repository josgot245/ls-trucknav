package de.ls.trucknav

import de.ls.trucknav.location.LocationSmoother
import de.ls.trucknav.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSmootherTest {

    private val second = 1_000_000_000L

    @Test
    fun standstillKeepsPositionWithoutJitter() {
        val smoother = LocationSmoother()
        var result = smoother.smooth(GeoPoint(53.55, 9.99), 0f, 0.0, 0L)
        val start = result.point

        // GPS-Rauschen von +/- 1,5 m bei stehendem Fahrzeug
        for (i in 1..30) {
            val offset = if (i % 2 == 0) 0.0000135 else -0.0000135
            result = smoother.smooth(
                GeoPoint(start.lat + offset, start.lon),
                0f,
                0.3,
                i * second,
            )
        }

        assertEquals(start.lat, result.point.lat, 1e-9)
        assertEquals(start.lon, result.point.lon, 1e-9)
        assertEquals(0.0, result.speedMps, 1e-9)
    }

    @Test
    fun reducesLateralNoiseWhileDriving() {
        val smoother = LocationSmoother()
        val speedMps = 20.0
        // 20 m/s nach Osten, dazu +/- 10 m Rauschen auf der Breite
        val stepLon = speedMps / 111_320.0
        val noise = 10.0 / 111_132.0

        var result = smoother.smooth(GeoPoint(53.55, 9.99), 90f, speedMps, 0L)
        var lastDeviation = Double.MAX_VALUE
        for (i in 1..30) {
            val raw = GeoPoint(
                53.55 + if (i % 2 == 0) noise else -noise,
                9.99 + i * stepLon,
            )
            result = smoother.smooth(raw, 90f, speedMps, i * second)
            val deviationMeters = Math.abs(result.point.lat - 53.55) * 111_132.0
            lastDeviation = deviationMeters
            assertTrue("Abweichung war $deviationMeters m", deviationMeters < 10.0)
        }
        // Geglaettet deutlich naeher an der Ideallinie als das rohe Rauschen
        assertTrue("Abweichung war $lastDeviation m", lastDeviation < 7.0)
    }

    @Test
    fun smoothsBearingAcrossNorthWithoutFullTurn() {
        val smoother = LocationSmoother()
        smoother.smooth(GeoPoint(53.55, 9.99), 350f, 20.0, 0L)

        var result = smoother.smooth(GeoPoint(53.551, 9.99), 10f, 20.0, second)
        for (i in 2..15) {
            result = smoother.smooth(GeoPoint(53.551, 9.99), 10f, 20.0, i * second)
            assertTrue(
                "Kurs lief ueber Sueden: ${result.bearing}",
                result.bearing > 300f || result.bearing < 40f,
            )
        }
        assertEquals(10.0, result.bearing.toDouble(), 1.0)
    }

    @Test
    fun teleportResetsToRawPosition() {
        val smoother = LocationSmoother()
        smoother.smooth(GeoPoint(53.55, 9.99), 0f, 0.0, 0L)
        val far = GeoPoint(53.60, 10.05)
        val result = smoother.smooth(far, 45f, 15.0, second)
        assertEquals(far.lat, result.point.lat, 1e-9)
        assertEquals(far.lon, result.point.lon, 1e-9)
    }

    @Test
    fun longGapRestartsSmoothing() {
        val smoother = LocationSmoother()
        smoother.smooth(GeoPoint(53.55, 9.99), 0f, 0.0, 0L)
        val later = GeoPoint(53.5502, 9.9902)
        val result = smoother.smooth(later, 90f, 12.0, 20 * second)
        assertEquals(later.lat, result.point.lat, 1e-9)
        assertEquals(later.lon, result.point.lon, 1e-9)
    }
}
