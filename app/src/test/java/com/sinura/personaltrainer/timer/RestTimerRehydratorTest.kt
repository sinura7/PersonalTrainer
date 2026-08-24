package com.sinura.personaltrainer.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the recovery rules for a rest timer that outlived its process — the case that used to
 * silently cancel rest on an OEM swipe-kill.
 */
class RestTimerRehydratorTest {
    private fun stored(
        endsAtElapsed: Long,
        total: Int = 90,
        sessionId: String? = "s1",
        bootMarker: Long,
        endsAtWall: Long,
    ) = PersistedRestTimer(endsAtElapsed, total, sessionId, bootMarker, endsAtWall, timerId = "timer-1")

    @Test
    fun nothingStoredMeansNothingToRecover() {
        val outcome = RestTimerRehydrator.rehydrate(null, 10_000L, 1_700_000_000_000L)
        assertEquals(RestTimerRehydration.None, outcome)
    }

    @Test
    fun sameBootRestoresRemainingFromElapsedRealtime() {
        val wall = 1_700_000_000_000L
        val elapsed = 20_000L
        val boot = wall - elapsed
        val outcome = RestTimerRehydrator.rehydrate(
            stored(endsAtElapsed = 50_000L, bootMarker = boot, endsAtWall = wall + 30_000L),
            nowElapsedRealtime = elapsed,
            nowWallClockMillis = wall,
        )
        assertTrue(outcome is RestTimerRehydration.Running)
        val running = outcome as RestTimerRehydration.Running
        assertEquals(50_000L, running.endsAtElapsedRealtime)
        assertEquals(90, running.totalSeconds)
        assertEquals("s1", running.sessionId)
        assertEquals("timer-1", running.timerId)
    }

    @Test
    fun afterRebootAShortRestIsCleared() {
        val oldWall = 1_700_000_000_000L
        val oldBoot = oldWall - 500_000L
        val newWall = oldWall + 40_000L
        val newElapsed = 3_000L
        val outcome = RestTimerRehydrator.rehydrate(
            stored(endsAtElapsed = 560_000L, bootMarker = oldBoot, endsAtWall = oldWall + 60_000L),
            nowElapsedRealtime = newElapsed,
            nowWallClockMillis = newWall,
        )
        assertEquals(RestTimerRehydration.None, outcome)
    }

    @Test
    fun aRestThatEndedMomentsAgoIsStillAnnounced() {
        val wall = 1_700_000_000_000L
        val elapsed = 100_000L
        val boot = wall - elapsed
        val outcome = RestTimerRehydrator.rehydrate(
            stored(endsAtElapsed = 95_000L, bootMarker = boot, endsAtWall = wall - 5_000L),
            nowElapsedRealtime = elapsed,
            nowWallClockMillis = wall,
        )
        assertTrue(outcome is RestTimerRehydration.Expired)
        val expired = outcome as RestTimerRehydration.Expired
        assertEquals(5_000L, expired.lateByMs)
        // The end instant is carried through as the completion dedupe key, so the alarm
        // receiver's call for the same rest is recognised as already handled.
        assertEquals(95_000L, expired.endsAtElapsedRealtime)
        assertEquals("s1", expired.sessionId)
        assertEquals("timer-1", expired.timerId)
    }

    @Test
    fun aLongDeadRestIsDroppedSilently() {
        val wall = 1_700_000_000_000L
        val elapsed = 900_000L
        val boot = wall - elapsed
        val lateBy = RestTimerRehydrator.LATE_ALERT_GRACE_MS + 1_000L
        val outcome = RestTimerRehydrator.rehydrate(
            stored(endsAtElapsed = elapsed - lateBy, bootMarker = boot, endsAtWall = wall - lateBy),
            nowElapsedRealtime = elapsed,
            nowWallClockMillis = wall,
        )
        assertEquals(RestTimerRehydration.None, outcome)
    }

    @Test
    fun toPersistedCapturesBothClocks() {
        val persisted = RestTimerRehydrator.toPersisted(
            endsAtElapsedRealtime = 91_000L,
            totalSeconds = 90,
            sessionId = "s1",
            nowElapsedRealtime = 1_000L,
            nowWallClockMillis = 1_700_000_000_000L,
        )
        assertEquals(1_700_000_000_000L - 1_000L, persisted.bootMarker)
        assertEquals(1_700_000_000_000L + 90_000L, persisted.endsAtWallClockMillis)
    }
}
