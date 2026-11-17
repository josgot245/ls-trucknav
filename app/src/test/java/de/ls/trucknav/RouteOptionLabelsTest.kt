package de.ls.trucknav

import de.ls.trucknav.model.RouteResult
import de.ls.trucknav.ui.labelRouteOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteOptionLabelsTest {

    private fun route(distanceKm: Double, timeS: Double) = RouteResult(
        points = emptyList(),
        maneuvers = emptyList(),
        distance = distanceKm * 1000.0,
        time = timeS,
        destination = null,
        truckUsed = true,
    )

    @Test
    fun `schnellste zuerst, kuerzeste danach, Rest als Alternative`() {
        // Route 0 ist laengste und mittelschnell, Route 1 die schnellste,
        // Route 2 die kuerzeste.
        val options = listOf(
            route(660.0, 25000.0),
            route(700.0, 20000.0),
            route(640.0, 24000.0),
        )

        val entries = labelRouteOptions(options)

        assertEquals(listOf(1, 2, 0), entries.map { it.index })
        assertEquals(listOf("Schnellste", "Kürzeste", "Alternative"), entries.map { it.label })
    }

    @Test
    fun `ist die schnellste auch die kuerzeste, gibt es keine eigene Kuerzeste-Zeile`() {
        val options = listOf(
            route(649.2, 24300.0),
            route(690.0, 25260.0),
            route(665.8, 25140.0),
        )

        val entries = labelRouteOptions(options)

        assertEquals(listOf(0, 1, 2), entries.map { it.index })
        assertEquals(listOf("Schnellste", "Alternative 1", "Alternative 2"), entries.map { it.label })
    }

    @Test
    fun `einzelne Route ist die schnellste`() {
        val entries = labelRouteOptions(listOf(route(100.0, 3600.0)))

        assertEquals(1, entries.size)
        assertEquals(0, entries[0].index)
        assertEquals("Schnellste", entries[0].label)
    }

    @Test
    fun `leere Liste bleibt leer`() {
        assertEquals(0, labelRouteOptions(emptyList()).size)
    }
}
