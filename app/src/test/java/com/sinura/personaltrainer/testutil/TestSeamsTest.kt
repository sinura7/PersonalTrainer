package com.sinura.personaltrainer.testutil

import com.sinura.personaltrainer.timer.RestTimerStore
import com.sinura.personaltrainer.util.AppClock
import com.sinura.personaltrainer.util.IdFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSeamsTest {
    @Test
    fun fakeClockIsDeterministicAndAdvances() {
        val clock = FakeClock(1_000L)
        assertEquals(1_000L, clock.nowMs())
        clock.advance(250)
        assertEquals(1_250L, clock.nowMs())
        assertEquals(1_250L, clock.nowMs())
    }

    @Test
    fun productionClockAndIdCompanionsAreCallable() {
        assertTrue(AppClock.System.nowMs() > 0L)
        assertNotEquals(IdFactory.Uuid.newId(), IdFactory.Uuid.newId())
    }

    @Test
    fun sequentialIdsDoNotUseRandomUuids() {
        val ids = SequentialIds()
        assertEquals("id-1", ids.newId())
        assertEquals("id-2", ids.newId())
        assertEquals("id-3", SequentialIds(3).newId())
    }

    @Test
    fun controllableElapsedRealtimeExpiresARestWithoutSleeping() {
        val elapsed = ControllableElapsedRealtime(10_000L)
        val store = RestTimerStore()
        store.start(30, "session-1", nowElapsedRealtime = elapsed.nowMs)
        assertTrue(store.current().running)
        assertEquals(30, store.current().remainingSeconds(elapsed.nowMs))
        elapsed.advance(30_000L)
        assertEquals(0, store.current().remainingSeconds(elapsed.nowMs))
        assertFalse(store.current().remainingSeconds(elapsed.nowMs) > 0)
    }
}
