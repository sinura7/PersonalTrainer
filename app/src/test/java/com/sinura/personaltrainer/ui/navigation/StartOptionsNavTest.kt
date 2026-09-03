package com.sinura.personaltrainer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Host mapping for the start sheet. `past` is the strength composer;
 * live cardio is its own route, not a planned occurrence.
 */
class StartOptionsNavTest {
    @Test
    fun pastModeIsTheStrengthComposerRoute() {
        assertEquals("log/past", StartOptionsNav.composer(StartOptionsNav.PAST))
        assertNotEquals(StartOptionsNav.CARDIO, StartOptionsNav.PAST)
        assertNotEquals(StartOptionsNav.MIXED, StartOptionsNav.PAST)
    }

    @Test
    fun liveCardioAndWorkoutStayOnTheirRoutes() {
        assertEquals("cardio/live-1", StartOptionsNav.liveCardio("live-1"))
        assertEquals("session/w-1", StartOptionsNav.workout("w-1"))
        assertEquals("log/cardio", StartOptionsNav.composer(StartOptionsNav.CARDIO))
        assertEquals("log/mixed", StartOptionsNav.composer(StartOptionsNav.MIXED))
    }
}
