package com.sinura.personaltrainer.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerStoreTest {
    /** Captures what the store wrote, so persistence can be asserted without Android. */
    private class FakePersistence : RestTimerStatePersistence {
        var saved: PersistedRestTimer? = null
        var cleared = 0
        override fun save(state: PersistedRestTimer) { saved = state }
        override fun load(): PersistedRestTimer? = saved
        override fun clear() { saved = null; cleared++ }
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
    fun everyMutationWritesThroughToPersistence() {
        val persistence = FakePersistence()
        val store = RestTimerStore(persistence)

        store.start(90, "s1", nowElapsedRealtime = 1_000L, nowWallClockMillis = 1_700_000_000_000L)
        val saved = persistence.saved
        assertNotNull(saved)
        assertEquals(91_000L, saved!!.endsAtElapsedRealtime)
        assertEquals(90, saved.totalSeconds)
        assertEquals("s1", saved.sessionId)
        // bootMarker = wall - elapsed; endsAtWall = wall + remaining
        assertEquals(1_700_000_000_000L - 1_000L, saved.bootMarker)
        assertEquals(1_700_000_000_000L + 90_000L, saved.endsAtWallClockMillis)

        store.clear()
        assertEquals(1, persistence.cleared)
        assertNull(persistence.saved)
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

    @Test
    fun persistenceWritesTheTimerId() {
        val persistence = FakePersistence()
        val store = RestTimerStore(persistence = persistence, ids = { "timer-persist" })
        store.start(90, "s1", nowElapsedRealtime = 1_000L, nowWallClockMillis = 1_700_000_000_000L)
        assertEquals("timer-persist", persistence.saved?.timerId)
    }
}
