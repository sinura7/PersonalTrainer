package com.sinura.personaltrainer.testutil

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.timer.RestTimerAppForeground
import com.sinura.personaltrainer.timer.RestTimerCompletion
import com.sinura.personaltrainer.timer.RestTimerOverlayController
import com.sinura.personaltrainer.timer.RestTimerService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf

/** Resets rest-timer process globals shared across JVM unit tests. */
object RestTimerGlobals {
    const val LEAK_AT_TEST_END = "RestTimerGlobals leak at test end:"
    const val BASELINE_AT_TEST_START = "RestTimerGlobals baseline at test start:"

    enum class Phase {
        START,
        END,
    }

    private fun phasePrefix(phase: Phase): String =
        when (phase) {
            Phase.START -> BASELINE_AT_TEST_START
            Phase.END -> LEAK_AT_TEST_END
        }

    private fun canResetOverlay(): Boolean =
        runCatching {
            Looper.getMainLooper()
            true
        }.getOrDefault(false)

    fun resetAll() {
        val errors = mutableListOf<Throwable>()
        fun step(label: String, block: () -> Unit) {
            runCatching(block).onFailure { errors.add(IllegalStateException("RestTimerGlobals reset $label failed", it)) }
        }
        step("tickObserver") { RestTimerService.tickObserver = null }
        step("ledger") { RestTimerCompletion.ledger.clearForTest() }
        step("foreground") { RestTimerAppForeground.resetForTest() }
        runCatching {
            ApplicationProvider.getApplicationContext<Application>()
        }.onSuccess { app ->
            step("foregroundInstall") { RestTimerAppForeground.install(app) }
        }
        if (canResetOverlay()) {
            step("overlay") { RestTimerOverlayController.resetForTest() }
            step("looperIdle") { shadowOf(Looper.getMainLooper()).idle() }
        }
        if (errors.isNotEmpty()) {
            val primary = errors.first()
            errors.drop(1).forEach { primary.addSuppressed(it) }
            throw primary
        }
    }

    fun assertClean(phase: Phase = Phase.END, skipLedger: Boolean = false) {
        val prefix = phasePrefix(phase)
        assertFalse(
            "$prefix RestTimerAppForeground leaked isInForeground=${RestTimerAppForeground.isInForeground}",
            RestTimerAppForeground.isInForeground,
        )
        assertTrue(
            "$prefix RestTimerAppForeground leaked startedActivities",
            RestTimerAppForeground.startedActivitiesForTest() == 0,
        )
        assertTrue(
            "$prefix RestTimerAppForeground leaked listeners",
            RestTimerAppForeground.listenersForTest().isEmpty(),
        )
        val robolectricApp =
            runCatching { ApplicationProvider.getApplicationContext<Application>() }.getOrNull()
        if (robolectricApp != null) {
            assertSame(
                "$prefix RestTimerAppForeground not installed for the test Application",
                robolectricApp,
                RestTimerAppForeground.applicationForTest(),
            )
        }
        if (canResetOverlay()) {
            assertNull(
                "$prefix RestTimerOverlayController leaked lastSnapshot",
                RestTimerOverlayController.lastSnapshotForTest(),
            )
            assertTrue(
                "$prefix RestTimerOverlayController leaked other overlay state",
                RestTimerOverlayController.isCleanForTest(),
            )
        }
        assertNull("$prefix RestTimerService.tickObserver leaked", RestTimerService.tickObserver)
        if (!skipLedger) {
            assertTrue(
                "$prefix RestTimerCompletion.ledger leaked claims",
                RestTimerCompletion.ledger.isCleanForTest(),
            )
        }
    }
}
