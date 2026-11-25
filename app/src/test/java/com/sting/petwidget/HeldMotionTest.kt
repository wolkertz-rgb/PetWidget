package com.sting.petwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldMotionTest {
    @Test fun solidFoodKeepsItsSizeBetweenBites() {
        val moments = listOf(.42f, .43f, .60f, .61f, .78f, .79f)
        val poses = moments.map { HeldMotion.pose("apple", it, 1, food = true) }
        assertEquals(listOf(0, 1, 1, 2, 2, 3), poses.map { it.bitesTaken })
        assertTrue(poses.all { it.scale == 1f && it.tilt == 0f && it.visibility == 1f })
        assertEquals(0f, HeldMotion.pose("apple", .88f, 1, food = true).visibility, .0001f)
    }

    @Test fun cupReturnsWithoutBiteMarks() {
        val sip = HeldMotion.pose("coffee", .43f, -1, food = true)
        assertTrue(sip.tilt > 0f)
        assertEquals(0, sip.bitesTaken)
        assertEquals(1f, HeldMotion.pose("coffee", .96f, -1, food = true).visibility, 0f)
    }
}
