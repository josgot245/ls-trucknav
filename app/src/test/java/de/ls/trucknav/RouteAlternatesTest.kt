package de.ls.trucknav

import de.ls.trucknav.net.ValhallaApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prueft das Auslesen von Haupt- und Ausweichrouten aus einer Valhalla-Antwort. */
class RouteAlternatesTest {

    private val shape = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    private fun trip(lengthKm: Double, timeS: Double): String = """
        {
          "summary": { "length": $lengthKm, "time": $timeS },
          "legs": [ { "shape": "$shape", "maneuvers": [] } ]
        }
    """.trimIndent()

    @Test
    fun `liest Hauptroute und Alternativen in Reihenfolge`() {
        val body = """
            {
              "trip": ${trip(10.0, 600.0)},
              "alternates": [
                { "trip": ${trip(12.0, 720.0)} },
                { "trip": ${trip(14.0, 840.0)} }
              ]
            }
        """.trimIndent()

        val routes = ValhallaApi().parseRoutes(body, truck = true)

        assertEquals(3, routes.size)
        assertEquals(10_000.0, routes[0].distance, 0.5)
        assertEquals(12_000.0, routes[1].distance, 0.5)
        assertEquals(14_000.0, routes[2].distance, 0.5)
        assertEquals(600.0, routes[0].time, 0.5)
        assertTrue(routes.all { it.truckUsed })
    }

    @Test
    fun `ueberspringt fehlerhafte Alternativen`() {
        val body = """
            {
              "trip": ${trip(10.0, 600.0)},
              "alternates": [
                { "trip": { "legs": [] } },
                { "trip": ${trip(13.0, 780.0)} }
              ]
            }
        """.trimIndent()

        val routes = ValhallaApi().parseRoutes(body, truck = false)

        assertEquals(2, routes.size)
        assertEquals(10_000.0, routes[0].distance, 0.5)
        assertEquals(13_000.0, routes[1].distance, 0.5)
        assertTrue(routes.none { it.truckUsed })
    }

    @Test
    fun `ohne Alternativen bleibt nur die Hauptroute`() {
        val body = """{ "trip": ${trip(5.0, 300.0)} }"""

        val routes = ValhallaApi().parseRoutes(body, truck = true)

        assertEquals(1, routes.size)
        assertEquals(5_000.0, routes[0].distance, 0.5)
    }
}
