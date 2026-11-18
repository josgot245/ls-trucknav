package de.ls.trucknav

import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.Maneuver
import de.ls.trucknav.model.RouteResult
import de.ls.trucknav.nav.NavigationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueft die Ansagelogik des [NavigationEngine]: Jede Entfernungsstufe wird
 * genau einmal angesagt, und veraltete Ansagen passierter Manoever werden
 * verworfen (flushSpeech).
 */
class NavigationEngineTest {

    /** Punkt auf einer gedachten Nordlinie in [meters] Entfernung vom Start. */
    private fun pointAt(meters: Double) = GeoPoint(50.0 + meters / 111132.0, 8.0)

    private fun maneuver(index: Int, type: Int, startDistance: Double) = Maneuver(
        index = index,
        type = type,
        instruction = "Manoever $index",
        verbal = "weiter",
        streetNames = emptyList(),
        startDistance = startDistance,
        length = 1000.0,
        time = 60.0,
    )

    private fun route(turn1: Double = 1000.0, turn2: Double = 2000.0) = RouteResult(
        points = (0..60).map { pointAt(it * 50.0) },
        maneuvers = listOf(
            maneuver(0, 0, 0.0),
            maneuver(1, 2, turn1),
            maneuver(2, 3, turn2),
        ),
        distance = 3000.0,
        time = 180.0,
        destination = null,
        truckUsed = true,
    )

    @Test
    fun `jede Entfernungsstufe wird genau einmal angesagt`() {
        val engine = NavigationEngine()
        engine.setRoute(route())

        // ~900 m vor dem Manoever: weiteste Stufe (High-Speed-Schwelle 1000 m).
        val far = engine.update(pointAt(100.0), 25.0)!!
        assertNotNull(far.announcement)
        assertTrue(far.announcement!!.contains("900"))

        // Etwas naeher, aber noch in derselben Stufe: keine erneute Ansage.
        assertNull(engine.update(pointAt(150.0), 25.0)!!.announcement)

        // Mittlere Stufe (~250 m) wird einmal angesagt ...
        assertNotNull(engine.update(pointAt(750.0), 25.0)!!.announcement)
        // ... und danach nicht wiederholt.
        assertNull(engine.update(pointAt(800.0), 25.0)!!.announcement)

        // Naechste Stufe (~100 m) wird angesagt, danach nicht wiederholt.
        assertNotNull(engine.update(pointAt(900.0), 25.0)!!.announcement)
        assertNull(engine.update(pointAt(950.0), 25.0)!!.announcement)
    }

    @Test
    fun `passiertes Manoever leert die Ansage-Warteschlange`() {
        val engine = NavigationEngine()
        engine.setRoute(route())

        // Ansage fuer das erste Manoever ausloesen.
        assertNotNull(engine.update(pointAt(900.0), 25.0)!!.announcement)

        // Manoever passiert: das naechste Manoever wird aktiv, alte Ansagen
        // muessen verworfen werden.
        val after = engine.update(pointAt(1050.0), 25.0)!!
        assertEquals(2, after.nextManeuver?.index)
        assertTrue(after.flushSpeech)
    }

    @Test
    fun `ohne Ansage wird beim Manoeverwechsel nicht geleert`() {
        // Beide Manoever liegen so weit entfernt, dass keine Ansage erfolgt.
        val engine = NavigationEngine()
        engine.setRoute(route(turn1 = 1500.0, turn2 = 3000.0))

        val before = engine.update(pointAt(0.0), 25.0)!!
        assertNull(before.announcement)
        assertFalse(before.flushSpeech)

        // Manoever passieren, ohne dass je angesagt wurde.
        val after = engine.update(pointAt(1550.0), 25.0)!!
        assertFalse(after.flushSpeech)
    }

    @Test
    fun `setRoute setzt den Ansagezustand zurueck`() {
        val engine = NavigationEngine()
        engine.setRoute(route())
        assertNotNull(engine.update(pointAt(900.0), 25.0)!!.announcement)

        // Neue Route: dieselbe Entfernung wird erneut angesagt.
        engine.setRoute(route())
        assertNotNull(engine.update(pointAt(900.0), 25.0)!!.announcement)
    }
}
