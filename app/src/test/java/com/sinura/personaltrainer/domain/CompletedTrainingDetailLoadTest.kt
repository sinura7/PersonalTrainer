package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two detail screens share this fold so a Room fault cannot keep
 * reading as "no longer on this phone".
 */
class CompletedTrainingDetailLoadTest {
    @Test
    fun aSuccessfulNullIsMissingNotFailed() {
        val load = CompletedTrainingDetailLoad.from(DataHealth.Available<String?>(null))
        assertTrue(load.missing)
        assertFalse(load.failed)
        assertFalse(load.isLoading)
        assertNull(load.value)
    }

    @Test
    fun aSuccessfulRowIsReady() {
        val load = CompletedTrainingDetailLoad.from(DataHealth.Available("sess-1"))
        assertEquals("sess-1", load.value)
        assertFalse(load.missing)
        assertFalse(load.failed)
        assertFalse(load.isLoading)
    }

    @Test
    fun aThrowBeforeAnyValueIsFailedNotMissing() {
        val load = CompletedTrainingDetailLoad.from<String>(DataHealth.Unavailable("this session"))
        assertTrue(load.failed)
        assertFalse("a read fault must not read as a deleted session", load.missing)
        assertFalse(load.isLoading)
        assertNull(load.value)
    }

    @Test
    fun aLaterThrowKeepsTheLastRowAndStillFails() {
        val load = CompletedTrainingDetailLoad.from(
            DataHealth.Degraded(lastValue = "sess-1", what = "this session"),
        )
        assertTrue(load.failed)
        assertFalse(load.missing)
        assertEquals("sess-1", load.value)
    }

    @Test
    fun fromResultMatchesTheHealthFold() {
        assertTrue(CompletedTrainingDetailLoad.fromResult<String>(Result.success(null)).missing)
        assertEquals("sess-1", CompletedTrainingDetailLoad.fromResult(Result.success("sess-1")).value)
        val failed = CompletedTrainingDetailLoad.fromResult<String>(Result.failure(IllegalStateException("boom")))
        assertTrue(failed.failed)
        assertFalse(failed.missing)
    }
}
