package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestIdleCopyTest {
    @Test
    fun idleCopyNeverSoundsLikeACountdown() {
        assertEquals("Not running", RestIdleCopy.KICKER)
        assertEquals("Rest 1:30", RestIdleCopy.planned("1:30"))
        assertEquals("Rest 1:00", RestIdleCopy.dockDuration("1:00", afterWarmup = false))
        val spoken = RestIdleCopy.spoken("1:30", afterWarmup = false)
        assertTrue(spoken, spoken.startsWith("Rest is not running."))
        assertTrue(spoken, spoken.contains("Rest 1:30"))
        assertFalse(spoken, spoken.contains("Start next"))
        assertTrue(spoken, spoken.contains("Start starts rest only"))
        assertFalse(spoken, spoken.contains("remaining"))
        assertEquals("Start next", RestIdleCopy.START_NEXT)
        assertEquals("Start", RestIdleCopy.START)
        assertEquals("Rest length", RestIdleCopy.SHEET_TITLE)
        assertEquals(
            "Rest is not running. Rest 1:30. Tap to change duration.",
            RestIdleCopy.dockSpoken("1:30", afterWarmup = false),
        )
        assertEquals("Start rest, 2 minutes 30 seconds", RestIdleCopy.startSpoken(150))
        assertEquals("Start rest, 1 minute", RestIdleCopy.startSpoken(60))
        assertEquals("Start rest, 30 seconds", RestIdleCopy.startSpoken(30))
    }

    @Test
    fun warmupIdleNamesThatRestDidNotStart() {
        assertEquals("Warm-up · 1:00", RestIdleCopy.dockDuration("1:00", afterWarmup = true))
        assertEquals("Warm-ups do not start rest", RestIdleCopy.afterWarmupHint())
        val spoken = RestIdleCopy.spoken("1:00", afterWarmup = true)
        assertTrue(spoken, spoken.contains("Warm-ups do not start rest"))
        assertFalse(spoken, spoken.contains("Start next"))
        assertTrue(spoken, spoken.contains("Start starts rest only"))
        val dockSpoken = RestIdleCopy.dockSpoken("1:00", afterWarmup = true)
        assertTrue(dockSpoken, dockSpoken.contains("Warm-ups do not start rest"))
        assertTrue(dockSpoken, dockSpoken.contains("Tap to change duration"))
        assertFalse(dockSpoken, dockSpoken.contains("Start next"))
    }
}
