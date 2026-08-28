package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerTest {
    @Test
    fun remainingUsesElapsedRealtimeCeiling() {
        assertEquals(0, RestTimer.remainingSeconds(0, 1_000))
        assertEquals(90, RestTimer.remainingSeconds(90_000, 0))
        // 1.4s left rounds UP to 2, so the clock never shows less time than remains.
        assertEquals(2, RestTimer.remainingSeconds(2_400, 1_000))
        assertEquals(0, RestTimer.remainingSeconds(1_000, 1_000))
        assertEquals(0, RestTimer.remainingSeconds(900, 1_000))
    }

    @Test
    fun remainingCeilsPartialSeconds() {
        assertEquals(1, RestTimer.remainingSeconds(10_000, 9_001))
        assertEquals(1, RestTimer.remainingSeconds(10_000, 9_999))
        assertEquals(9, RestTimer.remainingSeconds(10_000, 1_000))
    }

    @Test
    fun completionBoundaryIsExactlyZeroMillis() {
        // The service completes on `remaining <= 0`; under ceiling that is true only once
        // the end instant has actually arrived, never up to a second early.
        val end = 60_000L
        assertEquals(1, RestTimer.remainingSeconds(end, end - 1))
        assertEquals(0, RestTimer.remainingSeconds(end, end))
        assertEquals(0, RestTimer.remainingSeconds(end, end + 500))
    }

    @Test
    fun remainingNeverGoesNegative() {
        assertEquals(0, RestTimer.remainingSeconds(1_000, 60_000))
        assertEquals(0, RestTimer.remainingSeconds(1_000, Long.MAX_VALUE / 2))
    }

    @Test
    fun formatClockNeverShowsNegative() {
        assertEquals("0:00", RestTimer.formatClock(-12))
        assertEquals("0:00", RestTimer.formatClock(-1))
    }

    @Test
    fun sweepFractionIsFullAtStartAndEmptyAtZero() {
        assertEquals(1f, RestTimer.sweepFraction(90, 90), 0.0001f)
        assertEquals(0.5f, RestTimer.sweepFraction(45, 90), 0.0001f)
        assertEquals(0f, RestTimer.sweepFraction(0, 90), 0.0001f)
        assertEquals(0f, RestTimer.sweepFraction(30, 0), 0.0001f)
        assertEquals(0f, RestTimer.sweepFraction(-1, 90), 0.0001f)
        assertEquals(1f, RestTimer.sweepFraction(120, 90), 0.0001f)
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

    @Test
    fun restStartsAfterWorkingSetsUntilTheLiftIsDone() {
        assertFalse(RestTimer.shouldStartAfterLog(isWarmup = true, workingSetsAfterLog = 1, targetSets = 4))
        assertTrue(RestTimer.shouldStartAfterLog(isWarmup = false, workingSetsAfterLog = 1, targetSets = 4))
        assertTrue(RestTimer.shouldStartAfterLog(isWarmup = false, workingSetsAfterLog = 3, targetSets = 4))
        assertFalse(RestTimer.shouldStartAfterLog(isWarmup = false, workingSetsAfterLog = 4, targetSets = 4))
        assertTrue(RestTimer.shouldStartAfterLog(isWarmup = false, workingSetsAfterLog = 1, targetSets = 0))
    }

    @Test
    fun lastPrescribedSetStillDoesNotStartRest() {
        // Condensed bar + floor page must not "helpfully" rest after the lift is done.
        assertFalse(RestTimer.shouldStartAfterLog(isWarmup = false, workingSetsAfterLog = 3, targetSets = 3))
        assertFalse(RestTimer.shouldStartAfterLog(isWarmup = true, workingSetsAfterLog = 0, targetSets = 3))
    }

    @Test
    fun extraSetPastThePlanStartsRest() {
        assertTrue(RestTimer.shouldStartAfterExtra(isWarmup = false, workingSetsAfterLog = 4, targetSets = 3))
        assertFalse(RestTimer.shouldStartAfterExtra(isWarmup = false, workingSetsAfterLog = 3, targetSets = 3))
        assertFalse(RestTimer.shouldStartAfterExtra(isWarmup = true, workingSetsAfterLog = 4, targetSets = 3))
        assertFalse(RestTimer.shouldStartAfterExtra(isWarmup = false, workingSetsAfterLog = 2, targetSets = 0))
    }

    @Test
    fun endsAtWallClockAddsRemainingMs() {
        assertEquals(
            10_000L + 1_500L,
            RestTimer.endsAtWallClockMillis(
                endsAtElapsedRealtime = 5_000L,
                nowElapsedRealtime = 3_500L,
                nowWallClockMillis = 10_000L,
            ),
        )
        assertEquals(
            10_000L,
            RestTimer.endsAtWallClockMillis(
                endsAtElapsedRealtime = 1_000L,
                nowElapsedRealtime = 2_000L,
                nowWallClockMillis = 10_000L,
            ),
        )
    }
}
