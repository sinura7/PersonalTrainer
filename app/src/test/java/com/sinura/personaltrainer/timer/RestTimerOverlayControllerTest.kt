package com.sinura.personaltrainer.timer

import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
class RestTimerOverlayControllerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun grantOverlay() {
        ShadowSettings.setCanDrawOverlays(true)
        RestTimerAppForeground.setForegroundForTest(false)
        RestTimerOverlayController.release()
        shadowOf(context.mainLooper).idle()
    }

    @After
    fun tearDown() {
        RestTimerOverlayController.release()
        shadowOf(context.mainLooper).idle()
    }

    @Test
    fun syncWhenBackgroundAndRunningAttachesWithoutThrowing() {
        val endsAt = SystemClock.elapsedRealtime() + 90_000L
        RestTimerOverlayController.sync(
            context,
            RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = endsAt,
                totalSeconds = 90,
                sessionId = "session",
                timerId = "timer",
            ),
        )
        shadowOf(context.mainLooper).idle()
        assertNull(RestTimerOverlayController.lastFailureReason)
        assertTrue(Settings.canDrawOverlays(context))
    }

    @Test
    fun syncWhenForegroundDetachesWithoutThrowing() {
        RestTimerAppForeground.setForegroundForTest(true)
        RestTimerOverlayController.sync(
            context,
            RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = SystemClock.elapsedRealtime() + 60_000L,
                totalSeconds = 60,
            ),
        )
        shadowOf(context.mainLooper).idle()
        RestTimerAppForeground.setForegroundForTest(false)
        RestTimerOverlayController.sync(
            context,
            RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = SystemClock.elapsedRealtime() + 60_000L,
                totalSeconds = 60,
            ),
        )
        shadowOf(context.mainLooper).idle()
        assertNull(RestTimerOverlayController.lastFailureReason)
    }

    @Test
    fun syncDoesNotAttachWhenUserDismissedOverlayForThisRest() {
        RestOverlayDismissStore.dismissForRest(context, "timer-dismissed")
        RestTimerOverlayController.sync(
            context,
            RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = SystemClock.elapsedRealtime() + 60_000L,
                totalSeconds = 60,
                timerId = "timer-dismissed",
            ),
        )
        shadowOf(context.mainLooper).idle()
        assertNull(RestTimerOverlayController.lastFailureReason)
    }
}
