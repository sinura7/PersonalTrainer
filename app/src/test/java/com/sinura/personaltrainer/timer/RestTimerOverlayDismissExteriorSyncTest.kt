package com.sinura.personaltrainer.timer

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
class RestTimerOverlayDismissExteriorSyncTest {
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
    fun dismissForRestRequestsExteriorSync() {
        val state = RestTimerSnapshot(
            running = true,
            endsAtElapsedRealtime = SystemClock.elapsedRealtime() + 60_000L,
            totalSeconds = 60,
            sessionId = "s",
            timerId = "timer-sync",
        )
        RestOverlayDismissStore.dismissForRest(context, state.timerId)
        RestTimerOverlayController.sync(context, state, fromService = false)
        shadowOf(context.mainLooper).idle()
        val started = shadowOf(context).nextStartedService
        assertEquals(RestTimerService.ACTION_EXTERIOR_SYNC, started?.action)
        assertNull(started?.getStringExtra(RestTimerService.EXTRA_SESSION_ID))
        RestTimerOverlayController.sync(context, state, fromService = true)
        shadowOf(context.mainLooper).idle()
        org.junit.Assert.assertNull(shadowOf(context).nextStartedService)
    }
}
