package com.sinura.personaltrainer.timer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestTimerMarkers
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestTimerNotificationsCooldownTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun quietShadeUsesMinChannelWhenOverlayWouldShow() {
        RestTimerNotifications.ensureChannels(context)
        val state = RestTimerSnapshot(
            running = true,
            endsAtElapsedRealtime = 90_000L,
            totalSeconds = 90,
            sessionId = null,
            timerId = "t1",
            sourceTag = RestTimerMarkers.COOLDOWN,
        )
        val channel = RestTimerNotifications.runningChannelId(
            context,
            state,
            RestTimerRunningPresentation.BACKGROUND_UNLOCKED,
        )
        if (RestTimerOverlayController.canDrawOverlays(context)) {
            assertEquals(RestTimerNotifications.CHANNEL_RUNNING_IN_APP, channel)
        }
    }

    @Test
    fun runningTitleSaysCooldownOrRest() {
        val cooldown = RestTimerSnapshot(sourceTag = RestTimerMarkers.COOLDOWN, running = true, totalSeconds = 90)
        val rest = RestTimerSnapshot(sourceTag = "Routine", running = true, totalSeconds = 90)
        assertEquals("Cool-down", RestTimerNotifications.runningTitle(context, cooldown))
        assertEquals("Rest", RestTimerNotifications.runningTitle(context, rest))
    }
}
