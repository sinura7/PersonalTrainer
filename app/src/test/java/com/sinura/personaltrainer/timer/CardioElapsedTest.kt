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
    fun aMillisecondMarkerSkewStaysOnElapsedRealtime() {
        // Two non-atomic clock reads shift the recomputed marker by a few ms.
        // Exact equality used to drop to the wall clock over that skew.
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
            nowWallMs = 1_015_001L,
            currentBootMarker = 990_001L,
        )
        assertEquals(20L, seconds)
    }

    @Test
    fun aClockResyncMidRunStaysOnElapsedRealtime() {
        // The wall clock stepped back two minutes mid-run; elapsedRealtime kept
        // counting past the start, so the elapsed path still owns the duration.
        val persisted = PersistedCardioTimer(
            sessionId = "c1",
            startedAtElapsedRealtime = 10_000L,
            startedAtWallClockMillis = 1_000_000L,
            bootMarker = 990_000L,
        )
        val seconds = CardioElapsed.seconds(
            persisted = persisted,
            sessionStartedAtMs = 1_000_000L,
            nowElapsedMs = 70_000L,
            nowWallMs = 940_000L,
            currentBootMarker = 870_000L,
        )
        assertEquals(60L, seconds)
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

    @Test
    fun aChangedBootCountForcesTheWallClockEvenWhenElapsedLooksMonotonic() {
        // Run started 10 s into the previous boot; after a reboot the current
        // uptime (5 min) already exceeds that start, so the monotonic heuristic
        // alone would misread same-boot and report new-uptime minus old-uptime.
        // The boot counter says otherwise and wins.
        val persisted = PersistedCardioTimer(
            sessionId = "c1",
            startedAtElapsedRealtime = 10_000L,
            startedAtWallClockMillis = 1_000_000L,
            bootMarker = 990_000L,
            bootCount = 41L,
        )
        val seconds = CardioElapsed.seconds(
            persisted = persisted,
            sessionStartedAtMs = 1_000_000L,
            nowElapsedMs = 300_000L,
            nowWallMs = 1_600_000L,
            currentBootMarker = 1_300_000L,
            nowBootCount = 42L,
        )
        assertEquals(600L, seconds)
    }

    @Test
    fun aMatchingBootCountStaysOnElapsedRealtimeThroughAnyWallStep() {
        // Same boot, wall clock stepped an hour: the marker heuristic would
        // call it a reboot, the boot counter says it is not.
        val persisted = PersistedCardioTimer(
            sessionId = "c1",
            startedAtElapsedRealtime = 10_000L,
            startedAtWallClockMillis = 1_000_000L,
            bootMarker = 990_000L,
            bootCount = 42L,
        )
        val seconds = CardioElapsed.seconds(
            persisted = persisted,
            sessionStartedAtMs = 1_000_000L,
            nowElapsedMs = 70_000L,
            nowWallMs = 4_600_000L,
            currentBootMarker = 4_530_000L,
            nowBootCount = 42L,
        )
        assertEquals(60L, seconds)
    }

    @Test
    fun anUnstampedRowKeepsTheHeuristics() {
        // A row written by a build before the boot stamp existed: UNKNOWN on
        // either side must leave the clock heuristics deciding, exactly as
        // rebootFallsBackToWallClock above.
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
            nowBootCount = 42L,
        )
        assertEquals(30L, seconds)
    }
}
