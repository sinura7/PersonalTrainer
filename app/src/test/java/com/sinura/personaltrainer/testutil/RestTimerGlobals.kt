package com.sinura.personaltrainer.testutil

import android.os.Looper
import com.sinura.personaltrainer.timer.RestTimerAppForeground
import com.sinura.personaltrainer.timer.RestTimerCompletion
import com.sinura.personaltrainer.timer.RestTimerOverlayController
import com.sinura.personaltrainer.timer.RestTimerService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf

/** Resets rest-timer process globals shared across JVM unit tests. */
object RestTimerGlobals {
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
        if (canResetOverlay()) {
            step("overlay") { RestTimerOverlayController.resetForTest() }
            step("looperIdle") { shadowOf(Looper.getMainLooper()).idle() }
        }
        if (errors.isNotEmpty()) {
            throw errors.single()
        }
    }

    fun assertClean() {
        assertFalse(
            "RestTimerAppForeground leaked isInForeground=${RestTimerAppForeground.isInForeground}",
            RestTimerAppForeground.isInForeground,
        )
        assertTrue(
            "RestTimerAppForeground leaked startedActivities",
            RestTimerAppForeground.startedActivitiesForTest() == 0,
        )
        assertTrue(
            "RestTimerAppForeground leaked listeners",
            RestTimerAppForeground.listenersForTest().isEmpty(),
        )
        assertNull(
            "RestTimerAppForeground leaked application",
            RestTimerAppForeground.applicationForTest(),
        )
        if (canResetOverlay()) {
            assertNull(
                "RestTimerOverlayController leaked lastSnapshot",
                RestTimerOverlayController.lastSnapshotForTest(),
            )
            assertTrue(
                "RestTimerOverlayController leaked other overlay state",
                RestTimerOverlayController.isCleanForTest(),
            )
        }
        assertNull("RestTimerService.tickObserver leaked", RestTimerService.tickObserver)
        assertTrue(
            "RestTimerCompletion.ledger leaked claims",
            RestTimerCompletion.ledger.isCleanForTest(),
        )
    }
}
