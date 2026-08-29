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
        bootCount: Long = BootSession.UNKNOWN,
    ) = PersistedRestTimer(
        endsAtElapsed,
        total,
        sessionId,
        bootMarker,
        endsAtWall,
        timerId = "timer-1",
        bootCount = bootCount,
    )

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
    fun aWallClockStepMidRestIsNotARebootAndKeepsTheTimer() {
        // elapsedRealtime kept counting (370s at save-time start, 400s now), but a
        // carrier resync stepped the wall clock +45s, moving the boot marker far
        // past the tolerance. That is the same boot; the rest must survive.
        val wall = 1_700_000_000_000L
        val nowElapsed = 400_000L
        val currentBoot = wall - nowElapsed
        val outcome = RestTimerRehydrator.rehydrate(
            stored(
                endsAtElapsed = 460_000L,
                bootMarker = currentBoot - 45_000L,
                endsAtWall = wall + 60_000L,
            ),
            nowElapsedRealtime = nowElapsed,
            nowWallClockMillis = wall,
        )
        assertTrue(outcome is RestTimerRehydration.Running)
        assertEquals(460_000L, (outcome as RestTimerRehydration.Running).endsAtElapsedRealtime)
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
    fun aChangedBootCountDropsTheRestEvenWhenElapsedLooksMonotonic() {
        // Rest started 10 s into the previous boot; the phone rebooted and the
        // new boot's uptime is already past that start point, so the monotonic
        // heuristic alone would resurrect a rest ADR-012 §7 says to drop.
        val outcome = RestTimerRehydrator.rehydrate(
            stored(
                endsAtElapsed = 100_000L,
                bootMarker = 1_700_000_000_000L - 10_000L,
                endsAtWall = 1_700_000_090_000L,
                bootCount = 7L,
            ),
            nowElapsedRealtime = 50_000L,
            nowWallClockMillis = 1_700_000_400_000L,
            nowBootCount = 8L,
        )
        assertEquals(RestTimerRehydration.None, outcome)
    }

    @Test
    fun aMatchingBootCountKeepsTheRestThroughAnyWallStep() {
        // Same boot by counter; the wall clock stepped an hour so the marker
        // heuristic would call it a reboot. The counter wins.
        val wall = 1_700_003_600_000L
        val nowElapsed = 400_000L
        val outcome = RestTimerRehydrator.rehydrate(
            stored(
                endsAtElapsed = 460_000L,
                bootMarker = wall - nowElapsed - 3_600_000L,
                endsAtWall = wall + 60_000L,
                bootCount = 7L,
            ),
            nowElapsedRealtime = nowElapsed,
            nowWallClockMillis = wall,
            nowBootCount = 7L,
        )
        assertTrue(outcome is RestTimerRehydration.Running)
        assertEquals(460_000L, (outcome as RestTimerRehydration.Running).endsAtElapsedRealtime)
    }

    @Test
    fun anUnstampedRowKeepsTheHeuristics() {
        // Update boundary: the stored row predates the boot stamp. The clock
        // heuristics must still decide, as in afterRebootAShortRestIsCleared.
        val oldWall = 1_700_000_000_000L
        val outcome = RestTimerRehydrator.rehydrate(
            stored(
                endsAtElapsed = 560_000L,
                bootMarker = oldWall - 500_000L,
                endsAtWall = oldWall + 60_000L,
            ),
            nowElapsedRealtime = 3_000L,
            nowWallClockMillis = oldWall + 40_000L,
            nowBootCount = 8L,
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
