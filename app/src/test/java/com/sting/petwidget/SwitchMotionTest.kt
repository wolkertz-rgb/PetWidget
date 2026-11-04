package com.sting.petwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchMotionTest {
    @Test fun motionStaysInsideItsBoundsBeforeDuringAndAfterAction() {
        for (t in -1000L..4000L) {
            val pose = SwitchMotion.pose(t)
            assertTrue("reach at $t", pose.reach in 0f..1f)
            assertTrue("press at $t", pose.press in 0f..1f)
            // Лапка обязательно касается кнопки прежде, чем утапливать клавишу.
            if (pose.press > 0f) assertEquals(1f, pose.reach, 0.0001f)
        }
    }

    @Test fun contactAndClickMatchSimulationTiming() {
        assertEquals(0f, SwitchMotion.pose(0).reach, 0f)
        assertEquals(1f, SwitchMotion.pose(500).reach, 0f)
        assertEquals(0f, SwitchMotion.pose(500).press, 0f)
        val click = SwitchMotion.pose(PetSim.PULL_TOGGLE_MS)
        assertEquals(1f, click.reach, 0f)
        assertEquals(1f, click.press, 0f)
        assertEquals(0f, SwitchMotion.pose(1000).press, 0f)
        assertEquals(0f, SwitchMotion.pose(PetSim.PULL_DUR).reach, 0f)
    }

    @Test fun motionHasNoFrameJumps() {
        for (t in 0L..1400L) {
            val a = SwitchMotion.pose(t)
            val b = SwitchMotion.pose(t + 1)
            assertTrue(kotlin.math.abs(a.reach - b.reach) < 0.01f)
            assertTrue(kotlin.math.abs(a.press - b.press) < 0.01f)
        }
    }
}
