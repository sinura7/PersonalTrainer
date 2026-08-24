package com.sinura.personaltrainer.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class CardioElapsedTest {
    @Test
    fun sameBootUsesElapsedRealtime() {
        val persisted = PersistedCardioTimer(
            sessionId = "c1",
            startedAtElapsedRealtime = 10_000L,
            startedAtWallClockMillis = 1_000_000L,
            bootMarker = 990_000L,
            baselineElapsedSeconds = 5L,
        )
        val seconds = CardioElapsed.seconds(
            persisted = persisted,
            sessionStartedAtMs = 1_000_000L,
            nowElapsedMs = 25_000L,
            nowWallMs = 1_015_000L,
            currentBootMarker = 990_000L,
        )
        assertEquals(20L, seconds)
    }

    @Test
    fun rebootFallsBackToWallClock() {
        val persisted = PersistedCardioTimer(
            sessionId = "c1",
            startedAtElapsedRealtime = 10_000L,
            startedAtWallClockMillis = 1_000_000L,
            bootMarker = 990_000L,
        )
        val seconds = CardioElapsed.seconds(
            persisted = persisted,
            sessionStartedAtMs = 1_000_000L,
            nowElapsedMs = 2_000L,
            nowWallMs = 1_030_000L,
            currentBootMarker = 1_028_000L,
        )
        assertEquals(30L, seconds)
    }
}
