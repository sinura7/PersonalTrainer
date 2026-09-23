package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestIdleCopyTest {
    @Test
    fun idleCopyNeverSoundsLikeACountdown() {
        assertEquals("Not running", RestIdleCopy.NOT_RUNNING)
        // One wording for the planned length (design audit D11): "Planned rest · 1:30" on
        // screen, the same words without the dot for TalkBack.
        assertEquals("Planned rest", RestIdleCopy.PLANNED_REST)
        assertEquals("Planned rest · 1:30", RestIdleCopy.planned("1:30"))
        // The dock card's short form sits under its REST kicker.
        assertEquals("Planned", RestIdleCopy.PLANNED)
        assertEquals("Planned 1:30", RestIdleCopy.plannedBeside("1:30"))
        assertEquals("Planned rest 1:30", RestIdleCopy.plannedSpoken("1:30"))
        val spoken = RestIdleCopy.spoken(clock = "1:30", afterWarmup = false)
        assertEquals("Rest is not running. Planned rest 1:30. Start starts rest only.", spoken)
        assertFalse(spoken, spoken.contains("Start next"))
        assertTrue(spoken, spoken.contains("Start starts rest only"))
        assertFalse(spoken, spoken.contains("remaining"))
        assertEquals("Start next", RestIdleCopy.START_NEXT)
        assertEquals("Start", RestIdleCopy.START)
        assertEquals("Rest length", RestIdleCopy.SHEET_TITLE)
        assertEquals("Tap to change duration.", RestIdleCopy.CHANGE_DURATION)
    }

    @Test
    fun idleRestCardSpeaksThePlannedLengthAndTheStartAction() {
        // ADR-027: the idle rest card in the dock speaks dockSpoken on its face and
        // startSpoken on its Start rest control; tapping the clock edits the duration.
        assertEquals(
            "Rest is not running. Planned rest 1:30. Tap to change duration.",
            RestIdleCopy.dockSpoken(clock = "1:30", afterWarmup = false),
        )
        assertEquals("Start rest, 2 minutes 30 seconds", RestIdleCopy.startSpoken(150))
        assertEquals("Start rest, 1 minute", RestIdleCopy.startSpoken(60))
        assertEquals("Start rest, 30 seconds", RestIdleCopy.startSpoken(30))
        assertEquals("Start rest, 0 seconds", RestIdleCopy.startSpoken(-5))
        assertEquals("1 second", RestIdleCopy.spokenAmount(1))
        assertEquals("2 minutes", RestIdleCopy.spokenAmount(120))
        assertEquals("1 minute 1 second", RestIdleCopy.spokenAmount(61))
    }

    @Test
    fun warmupIdleNamesThatRestDidNotStart() {
        // After a warm-up the idle card's kicker is Warm-up and its caption is the hint.
        assertEquals("Warm-up", RestIdleCopy.WARMUP_KICKER)
        assertEquals("Warm-ups do not start rest", RestIdleCopy.afterWarmupHint())
        val spoken = RestIdleCopy.spoken(clock = "1:00", afterWarmup = true)
        assertTrue(spoken, spoken.contains("Warm-ups do not start rest"))
        assertFalse(spoken, spoken.contains("Start next"))
        assertTrue(spoken, spoken.contains("Start starts rest only"))
        val dockSpoken = RestIdleCopy.dockSpoken(clock = "1:00", afterWarmup = true)
        assertTrue(dockSpoken, dockSpoken.startsWith("Rest is not running."))
        assertTrue(dockSpoken, dockSpoken.contains("Warm-ups do not start rest"))
        assertTrue(dockSpoken, dockSpoken.contains("Planned rest 1:00"))
        assertTrue(dockSpoken, dockSpoken.contains("Tap to change duration"))
        assertFalse(dockSpoken, dockSpoken.contains("Start next"))
    }
}
