package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestTimerTest {
    @Test
    fun remainingUsesElapsedRealtimeFloor() {
        assertEquals(0, RestTimer.remainingSeconds(0, 1_000))
        assertEquals(90, RestTimer.remainingSeconds(90_000, 0))
        assertEquals(1, RestTimer.remainingSeconds(2_400, 1_000))
        assertEquals(0, RestTimer.remainingSeconds(1_000, 1_000))
        assertEquals(0, RestTimer.remainingSeconds(900, 1_000))
    }

    @Test
    fun formatClockNeverShowsNegative() {
        assertEquals("0:00", RestTimer.formatClock(-12))
        assertEquals("0:00", RestTimer.formatClock(-1))
    }

    @Test
    fun parseCustomAcceptsSecondsAndMmSs() {
        assertEquals(90, RestTimer.parseCustom("90"))
        assertEquals(90, RestTimer.parseCustom("1:30"))
        assertEquals(60, RestTimer.parseCustom("1:00"))
        assertEquals(120, RestTimer.parseCustom(" 2:00 "))
        assertNull(RestTimer.parseCustom(""))
        assertNull(RestTimer.parseCustom("1:99"))
        assertNull(RestTimer.parseCustom("5"))
        assertNull(RestTimer.parseCustom("9999"))
    }

    @Test
    fun autoStartPrefersExerciseThenLastPresetThenDefault() {
        val prefs = RestTimerPreferences(defaultRestSeconds = 120, lastPresetSeconds = 60)
        assertEquals(90, RestTimer.secondsToStart(90, prefs))
        assertEquals(60, RestTimer.secondsToStart(null, prefs))
        assertEquals(120, RestTimer.secondsToStart(0, RestTimerPreferences(defaultRestSeconds = 120)))
    }
}
