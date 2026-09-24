package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.util.IdFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerStoreTest {
    @Test
    fun anAdjustThatLosesTheRaceToACompletionLeavesTheRestFinished() {
        // The alarm's completion clears the store on its own thread. If it lands between adjust
        // reading the running timer and writing the replacement — the moment the replacement's
        // id is minted — the finished rest must stay finished, not come back under a new id.
        lateinit var store: RestTimerStore
        var minted = 0
        val clearsMidAdjust = IdFactory {
            minted += 1
            if (minted == 2) store.clear()
            "timer-$minted"
        }
        store = RestTimerStore(ids = clearsMidAdjust)
        store.start(60, "s", nowElapsedRealtime = 0L)

        store.adjust(15, nowElapsedRealtime = 10_000L)

        assertFalse("a completed rest must not be revived by a late +15", store.current().running)
    }

    @Test
    fun clearIfCurrentClearsItsOwnTimerOrAnIdleStoreButNeverAReplacement() {
        val store = RestTimerStore(ids = sequentialIds())
        store.start(60, "s", nowElapsedRealtime = 0L)
        val first = store.current().timerId
        store.adjust(15, nowElapsedRealtime = 1_000L)
        val replacement = store.current()

        assertNull("an old id must not clear its replacement", store.clearIfCurrent(first))
        assertEquals(replacement, store.current())

        assertEquals(replacement, store.clearIfCurrent(replacement.timerId))
        assertFalse(store.current().running)
        assertFalse("an idle store is already clear", store.clearIfCurrent(first)!!.running)
    }

    @Test
    fun adjustSaysWhatItDid() {
        val store = RestTimerStore(ids = sequentialIds())
        assertEquals(RestAdjustment.Idle, store.adjust(15, nowElapsedRealtime = 0L))
        store.start(60, "s", nowElapsedRealtime = 0L)
        val running = store.adjust(15, nowElapsedRealtime = 0L)
        assertTrue(running is RestAdjustment.Running)
        assertEquals(store.current(), (running as RestAdjustment.Running).snapshot)
        assertEquals(RestAdjustment.Ended, store.adjust(-500, nowElapsedRealtime = 0L))
        assertFalse(store.current().running)
    }

    private fun sequentialIds(): IdFactory {
        var next = 0
        return IdFactory { "timer-${++next}" }
    }

    @Test
    fun startAnchorsEndToElapsedRealtime() {
        val store = RestTimerStore()
        store.start(90, "session-1", nowElapsedRealtime = 1_000L)
        val snap = store.current()
        assertTrue(snap.running)
        assertEquals(91_000L, snap.endsAtElapsedRealtime)
        assertEquals(90, snap.totalSeconds)
        assertEquals("session-1", snap.sessionId)
        assertEquals(90, snap.remainingSeconds(1_000L))
        assertEquals(45, snap.remainingSeconds(46_000L))
    }

    @Test
    fun startHoldsTheFullFirstSecond() {
        val store = RestTimerStore()
        store.start(90, "s", nowElapsedRealtime = 0L)
        val snap = store.current()
        assertEquals(90, snap.remainingSeconds(1L))
        assertEquals(90, snap.remainingSeconds(999L))
        assertEquals(89, snap.remainingSeconds(1_001L))
    }

    @Test
    fun adjustChangesRemainingAndClearsAtZero() {
        val store = RestTimerStore()
        store.start(60, "s", nowElapsedRealtime = 0L)
        store.adjust(15, nowElapsedRealtime = 10_000L)
        assertEquals(65, store.current().remainingSeconds(10_000L))
        store.adjust(-15, nowElapsedRealtime = 10_000L)
        assertEquals(50, store.current().remainingSeconds(10_000L))
        store.adjust(-200, nowElapsedRealtime = 10_000L)
        assertFalse(store.current().running)
        assertEquals(0, store.current().remainingSeconds(10_000L))
    }

    @Test
    fun adjustOnIdleStoreNeverStartsAPhantomTimer() {
        // Regression: "+15s" on a stale notification, tapped just as rest completed, used to
        // spawn a running timer with a null session id and restart the foreground service.
        val store = RestTimerStore()
        store.adjust(15, nowElapsedRealtime = 5_000L)
        assertFalse(store.current().running)
        assertNull(store.current().sessionId)
        assertEquals(0, store.current().remainingSeconds(5_000L))

        store.start(60, "s", nowElapsedRealtime = 0L)
        store.clear()
        store.adjust(15, nowElapsedRealtime = 1_000L)
        assertFalse(store.current().running)
    }

    @Test
    fun startClampsNonPositiveDurations() {
        val store = RestTimerStore()
        store.start(0, "s", nowElapsedRealtime = 0L)
        assertTrue(store.current().running)
        assertEquals(1, store.current().totalSeconds)
    }

    @Test
    fun restoreRebuildsARunningTimerFromDisk() {
        val store = RestTimerStore()
        store.restore(
            endsAtElapsedRealtime = 50_000L,
            totalSeconds = 90,
            sessionId = "s1",
            nowElapsedRealtime = 20_000L,
            timerId = "timer-restored",
        )
        val snap = store.current()
        assertTrue(snap.running)
        assertEquals(30, snap.remainingSeconds(20_000L))
        assertEquals("s1", snap.sessionId)
        assertEquals("timer-restored", snap.timerId)
    }

    @Test
    fun startAndAdjustMintDistinctTimerIdsAndRestoreKeepsTheId() {
        var n = 0
        val store = RestTimerStore(ids = { "timer-${++n}" })
        store.start(60, "s", nowElapsedRealtime = 0L)
        val first = store.current().timerId
        store.adjust(15, nowElapsedRealtime = 5_000L)
        val second = store.current().timerId
        assertEquals("timer-1", first)
        assertEquals("timer-2", second)
        store.restore(
            endsAtElapsedRealtime = 80_000L,
            totalSeconds = 90,
            sessionId = "s",
            nowElapsedRealtime = 10_000L,
            timerId = first,
        )
        assertEquals(first, store.current().timerId)
    }
}
