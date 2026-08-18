package com.sinura.personaltrainer.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerStoreTest {
    @Test
    fun startUsesWallClockEnd() {
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
}
