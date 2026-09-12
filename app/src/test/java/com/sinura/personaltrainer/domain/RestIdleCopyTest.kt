package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestIdleCopyTest {
    @Test
    fun idleCopyNeverSoundsLikeACountdown() {
        assertEquals("Not running", RestIdleCopy.KICKER)
        assertEquals("1:30 planned", RestIdleCopy.planned("1:30"))
        assertEquals("1:00 planned", RestIdleCopy.dockDuration("1:00", afterWarmup = false))
        val spoken = RestIdleCopy.spoken("1:30", afterWarmup = false)
        assertTrue(spoken, spoken.startsWith("Rest is not running."))
        assertTrue(spoken, spoken.contains("1:30 planned"))
        assertTrue(spoken, spoken.contains("Start next to keep going"))
        assertTrue(spoken, spoken.contains("Start starts rest only"))
        assertFalse(spoken, spoken.contains("remaining"))
        assertEquals("Start next", RestIdleCopy.START_NEXT)
        assertEquals("Start", RestIdleCopy.START)
    }

    @Test
    fun warmupIdleNamesThatRestDidNotStart() {
        assertEquals("Warm-up · 1:00", RestIdleCopy.dockDuration("1:00", afterWarmup = true))
        assertEquals("Warm-ups do not start rest", RestIdleCopy.afterWarmupHint())
        val spoken = RestIdleCopy.spoken("1:00", afterWarmup = true)
        assertTrue(spoken, spoken.contains("Warm-ups do not start rest"))
        assertTrue(spoken, spoken.contains("Start next to keep going"))
        assertTrue(spoken, spoken.contains("Start starts rest only"))
    }
}
