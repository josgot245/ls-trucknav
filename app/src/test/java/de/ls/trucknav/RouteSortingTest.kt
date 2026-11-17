package de.ls.trucknav

import de.ls.trucknav.data.RouteSort
import de.ls.trucknav.model.RouteResult
import de.ls.trucknav.ui.sortRouteOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteSortingTest {

    private fun route(distanceKm: Double, timeS: Double) = RouteResult(
        points = emptyList(),
        maneuvers = emptyList(),
        distance = distanceKm * 1000.0,
        time = timeS,
        destination = null,
        truckUsed = true,
    )

    // Route 1: 649 km / 24300 s, Route 2: 690 km / 25260 s, Route 3: 666 km / 25140 s
    private val options = listOf(
        route(649.2, 24300.0),
        route(690.0, 25260.0),
        route(665.8, 25140.0),
    )

    @Test
    fun `sortiert nach Dauer und behaelt den Originalindex`() {
        val sorted = sortRouteOptions(options, RouteSort.DURATION)

        assertEquals(listOf(0, 2, 1), sorted.map { it.index })
        assertEquals(24300.0, sorted[0].value.time, 0.5)
        assertEquals(25140.0, sorted[1].value.time, 0.5)
        assertEquals(25260.0, sorted[2].value.time, 0.5)
    }

    @Test
    fun `sortiert nach Laenge und behaelt den Originalindex`() {
        val sorted = sortRouteOptions(options, RouteSort.LENGTH)

        assertEquals(listOf(0, 2, 1), sorted.map { it.index })
        assertEquals(649_200.0, sorted[0].value.distance, 0.5)
        assertEquals(665_800.0, sorted[1].value.distance, 0.5)
        assertEquals(690_000.0, sorted[2].value.distance, 0.5)
    }

    @Test
    fun `leere Liste bleibt leer`() {
        assertEquals(0, sortRouteOptions(emptyList(), RouteSort.DURATION).size)
    }
}
