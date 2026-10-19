package com.sinura.personaltrainer.timer

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestTimerMarkers
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestTimerNotificationsCooldownTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun resetOverlayState() {
        RestTimerAppForeground.setForegroundForTest(false)
    }

    @After
    fun tearDownOverlayState() {
        ShadowSettings.reset()
        RestTimerAppForeground.setForegroundForTest(false)
        RestOverlayDismissStore.clear(context)
    }

    @Test
    fun quietShadeUsesMinChannelWhenOverlayWouldShow() {
        ShadowSettings.setCanDrawOverlays(true)
        RestTimerAppForeground.setForegroundForTest(false)
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
        assertEquals(RestTimerNotifications.CHANNEL_RUNNING_IN_APP, channel)
    }

    @Test
    fun quietShadeUsesHighChannelWhenOverlayWasDismissedForThisRest() {
        ShadowSettings.setCanDrawOverlays(true)
        RestTimerAppForeground.setForegroundForTest(false)
        RestTimerNotifications.ensureChannels(context)
        val state = RestTimerSnapshot(
            running = true,
            endsAtElapsedRealtime = 90_000L,
            totalSeconds = 90,
            sessionId = null,
            timerId = "t2",
            sourceTag = RestTimerMarkers.COOLDOWN,
        )
        RestOverlayDismissStore.dismissForRest(context, state.timerId)
        val channel = RestTimerNotifications.runningChannelId(
            context,
            state,
            RestTimerRunningPresentation.BACKGROUND_UNLOCKED,
        )
        assertEquals(RestTimerNotifications.CHANNEL_RUNNING, channel)
    }

    @Test
    fun runningTitleSaysCooldownOrRest() {
        val cooldown = RestTimerSnapshot(sourceTag = RestTimerMarkers.COOLDOWN, running = true, totalSeconds = 90)
        val rest = RestTimerSnapshot(sourceTag = "Routine", running = true, totalSeconds = 90)
        assertEquals("Cool-down", RestTimerNotifications.runningTitle(context, cooldown))
        assertEquals("Rest", RestTimerNotifications.runningTitle(context, rest))
    }

    @Test
    fun quietChannelNameAndDescriptionAreUpdatedInPlace() {
        RestTimerNotifications.ensureChannels(context)
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(RestTimerNotifications.CHANNEL_RUNNING_IN_APP)
        assertEquals(context.getString(com.sinura.personaltrainer.R.string.rest_channel_quiet_name), channel.name)
        assertEquals(
            context.getString(com.sinura.personaltrainer.R.string.rest_channel_quiet_description),
            channel.description,
        )
    }

    @Test
    fun cooldownRunningNotificationHasSkipWithoutNudgeActions() {
        RestTimerNotifications.ensureChannels(context)
        val notification = RestTimerNotifications.runningNotification(
            context = context,
            state = RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = 90_000L,
                totalSeconds = 90,
                sessionId = null,
                timerId = "cd1",
                sourceTag = RestTimerMarkers.COOLDOWN,
            ),
            presentation = RestTimerRunningPresentation.BACKGROUND_UNLOCKED,
            nowElapsedRealtime = 0L,
        )
        val actions = notification.actions?.map { it.title }.orEmpty()
        assertTrue(actions.contains("Skip"))
        assertFalse(actions.contains("+15s"))
        assertFalse(actions.contains("−15s"))
    }
}
