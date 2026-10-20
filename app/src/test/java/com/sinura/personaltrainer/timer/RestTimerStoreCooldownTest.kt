package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.testutil.RestTimerGlobalsRule
import com.sinura.personaltrainer.domain.RestTimerMarkers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerStoreCooldownTest {
    @get:Rule val restTimerGlobals = RestTimerGlobalsRule()
    private val store = RestTimerStore()

    @Test
    fun startCooldownIfIdleWhenEmpty() {
        assertTrue(store.startCooldownIfIdle(nowElapsedRealtime = 1_000L))
        assertTrue(RestTimerMarkers.isCooldown(store.current()))
    }

    @Test
    fun startCooldownIfIdleDoesNotReplaceRunningRest() {
        store.start(60, "session", nowElapsedRealtime = 1_000L)
        val restId = store.current().timerId
        assertFalse(store.startCooldownIfIdle(nowElapsedRealtime = 2_000L))
        assertEquals(restId, store.current().timerId)
        assertFalse(RestTimerMarkers.isCooldown(store.current()))
    }
}
