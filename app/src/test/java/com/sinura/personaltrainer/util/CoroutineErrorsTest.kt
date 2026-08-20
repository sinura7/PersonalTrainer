package com.sinura.personaltrainer.util

import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Pins the one property that `catch (_: Exception)` got wrong everywhere in this codebase:
 * CancellationException extends Exception, so the old pattern swallowed cancellation and let
 * a cancelled `mapLatest` transform keep computing with fallback values.
 */
class CoroutineErrorsTest {
    private val logged = mutableListOf<String>()

    @Before
    fun captureLogs() {
        logged.clear()
        AppLog.sink = { _, tag, message, _ -> logged += "$tag: $message" }
    }

    @Test
    fun successPassesTheValueThrough() {
        assertEquals(42, runCatchingCancellable { 42 }.getOrNull())
    }

    @Test
    fun ordinaryFailuresAreCaptured() {
        val result = runCatchingCancellable { error("boom") }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun cancellationIsRethrownNotCaptured() {
        try {
            runCatchingCancellable { throw CancellationException("cancelled") }
            fail("CancellationException must propagate, not be captured as a failure")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
    }

    @Test
    fun recoverWithReturnsTheFallbackAndLogs() {
        val value = recoverWith("PT/Test", "Computing something", fallback = emptyList<String>()) {
            error("db is on fire")
        }
        assertEquals(emptyList<String>(), value)
        assertEquals(1, logged.size)
        assertTrue(logged.single().startsWith("PT/Test: Computing something failed"))
    }

    @Test
    fun recoverWithDoesNotLogOnSuccess() {
        assertEquals(listOf("a"), recoverWith("PT/Test", "x", emptyList()) { listOf("a") })
        assertTrue(logged.isEmpty())
    }

    @Test
    fun recoverWithStillLetsCancellationThrough() {
        try {
            recoverWith("PT/Test", "x", fallback = 0) { throw CancellationException("stop") }
            fail("cancellation must not be converted into the fallback")
        } catch (_: CancellationException) {
            assertTrue(logged.isEmpty())
        }
    }
}
