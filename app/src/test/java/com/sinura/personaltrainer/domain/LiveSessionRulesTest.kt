package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSessionRulesTest {

    private val start = 1_700_000_000_000L
    private val hour = 60L * 60 * 1000

    @Test
    fun notStaleJustUnderFourHours() {
        val now = start + LiveSessionRules.STALE_AFTER_MS - 1_000
        assertFalse(LiveSessionRules.isStale(lastActivityMs = start, nowMs = now))
    }

    @Test
    fun staleAtExactlyFourHours() {
        val now = start + LiveSessionRules.STALE_AFTER_MS
        assertTrue(LiveSessionRules.isStale(lastActivityMs = start, nowMs = now))
    }

    @Test
    fun lastActivityPrefersLatestSetIncludingWarmups() {
        val warmup = start + 5 * 60 * 1000
        assertEquals(
            warmup,
            LiveSessionRules.lastActivityMs(startedAt = start, lastSetCompletedAt = warmup),
        )
    }

    @Test
    fun lastActivityFallsBackToStartedAtWithNoSets() {
        assertEquals(
            start,
            LiveSessionRules.lastActivityMs(startedAt = start, lastSetCompletedAt = null),
        )
    }

    @Test
    fun staleHoursFloorsAndNeverNegative() {
        assertEquals(
            5L,
            LiveSessionRules.staleHours(lastActivityMs = start, nowMs = start + 5 * hour + 59_000),
        )
        // A clock moved backwards must not render "-2h".
        assertEquals(
            0L,
            LiveSessionRules.staleHours(lastActivityMs = start, nowMs = start - 2 * hour),
        )
    }

    @Test
    fun formatElapsedUnderAnHourMatchesRestClock() {
        assertEquals("0:00", LiveSessionRules.formatElapsed(0))
        assertEquals("0:59", LiveSessionRules.formatElapsed(59))
        assertEquals("7:42", LiveSessionRules.formatElapsed(7 * 60 + 42))
        assertEquals("59:59", LiveSessionRules.formatElapsed(59 * 60 + 59))
    }

    @Test
    fun formatElapsedWithHours() {
        assertEquals("1:00:00", LiveSessionRules.formatElapsed(3600))
        assertEquals("1:07:42", LiveSessionRules.formatElapsed(3600 + 7 * 60 + 42))
        assertEquals("2:02:02", LiveSessionRules.formatElapsed(2 * 3600 + 2 * 60 + 2))
    }

    @Test
    fun formatElapsedClampsNegative() {
        assertEquals("0:00", LiveSessionRules.formatElapsed(-5))
    }
}
