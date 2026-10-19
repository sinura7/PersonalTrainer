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
    fun resetAll() {
        RestTimerAppForeground.resetForTest()
        RestTimerOverlayController.resetForTest()
        RestTimerService.tickObserver = null
        RestTimerCompletion.ledger.clearForTest()
        shadowOf(Looper.getMainLooper()).idle()
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
        assertTrue(
            "RestTimerOverlayController leaked overlay state",
            RestTimerOverlayController.isCleanForTest(),
        )
        assertNull("RestTimerService.tickObserver leaked", RestTimerService.tickObserver)
        assertTrue(
            "RestTimerCompletion.ledger leaked claims",
            RestTimerCompletion.ledger.isCleanForTest(),
        )
    }
}
