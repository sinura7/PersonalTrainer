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
        RestTimerAppForeground.resetForTest()
        if (canResetOverlay()) {
            RestTimerOverlayController.resetForTest()
            shadowOf(Looper.getMainLooper()).idle()
        }
        RestTimerService.tickObserver = null
        RestTimerCompletion.ledger.clearForTest()
    }

    fun assertClean() {
        assertFalse(
            "RestTimerAppForeground leaked (isInForeground=${RestTimerAppForeground.isInForeground})",
            RestTimerAppForeground.isInForeground,
        )
        assertTrue(
            "RestTimerAppForeground leaked startedActivities or listeners",
            RestTimerAppForeground.isCleanForTest(),
        )
        if (canResetOverlay()) {
            assertTrue(
                "RestTimerOverlayController leaked overlay state",
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
