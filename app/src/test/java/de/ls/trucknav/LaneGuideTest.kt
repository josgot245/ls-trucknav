package de.ls.trucknav

import de.ls.trucknav.model.LaneGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneGuideTest {

    @Test
    fun singleDirectionsAreRecognised() {
        assertEquals(LaneGuide.THROUGH, LaneGuide.primaryDirection(LaneGuide.THROUGH))
        assertEquals(LaneGuide.LEFT, LaneGuide.primaryDirection(LaneGuide.LEFT))
        assertEquals(LaneGuide.RIGHT, LaneGuide.primaryDirection(LaneGuide.RIGHT))
        assertEquals(LaneGuide.SLIGHT_LEFT, LaneGuide.primaryDirection(LaneGuide.SLIGHT_LEFT))
        assertEquals(LaneGuide.SLIGHT_RIGHT, LaneGuide.primaryDirection(LaneGuide.SLIGHT_RIGHT))
        assertEquals(LaneGuide.SHARP_LEFT, LaneGuide.primaryDirection(LaneGuide.SHARP_LEFT))
        assertEquals(LaneGuide.SHARP_RIGHT, LaneGuide.primaryDirection(LaneGuide.SHARP_RIGHT))
        assertEquals(LaneGuide.U_TURN, LaneGuide.primaryDirection(LaneGuide.U_TURN))
        assertEquals(LaneGuide.MERGE_LEFT, LaneGuide.primaryDirection(LaneGuide.MERGE_LEFT))
        assertEquals(LaneGuide.MERGE_RIGHT, LaneGuide.primaryDirection(LaneGuide.MERGE_RIGHT))
    }

    @Test
    fun turnWinsOverThrough() {
        // Spur erlaubt geradeaus und rechts -> Anzeige "rechts".
        val directions = LaneGuide.THROUGH or LaneGuide.RIGHT
        assertEquals(LaneGuide.RIGHT, LaneGuide.primaryDirection(directions))
    }

    @Test
    fun sharperTurnHasPriority() {
        val directions = LaneGuide.LEFT or LaneGuide.SHARP_LEFT
        assertEquals(LaneGuide.SHARP_LEFT, LaneGuide.primaryDirection(directions))
    }

    @Test
    fun uTurnHasHighestPriority() {
        val directions = LaneGuide.THROUGH or LaneGuide.LEFT or LaneGuide.U_TURN
        assertEquals(LaneGuide.U_TURN, LaneGuide.primaryDirection(directions))
    }

    @Test
    fun unknownDirectionFallsBackToNone() {
        assertEquals(LaneGuide.NONE, LaneGuide.primaryDirection(LaneGuide.NONE))
        assertEquals(LaneGuide.NONE, LaneGuide.primaryDirection(0))
    }

    @Test
    fun hasChecksIndividualBits() {
        val lane = LaneGuide(directions = LaneGuide.THROUGH or LaneGuide.RIGHT, active = true)
        assertTrue(lane.has(LaneGuide.THROUGH))
        assertTrue(lane.has(LaneGuide.RIGHT))
        assertFalse(lane.has(LaneGuide.LEFT))
        assertTrue(lane.active)
    }
}
