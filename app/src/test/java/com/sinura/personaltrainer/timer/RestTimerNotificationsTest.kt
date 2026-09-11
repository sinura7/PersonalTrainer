package com.sinura.personaltrainer.timer

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestTimerNotificationsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun runningChannelIsHighPublicAndReplacesTheLowLegacy() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                "rest_timer_running",
                "legacy",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        RestTimerNotifications.ensureChannels(context)

        assertNull(manager.getNotificationChannel("rest_timer_running"))
        val running = checkNotNull(
            manager.getNotificationChannel(RestTimerNotifications.CHANNEL_RUNNING),
        )
        assertEquals(NotificationManager.IMPORTANCE_HIGH, running.importance)
        assertEquals(Notification.VISIBILITY_PUBLIC, running.lockscreenVisibility)
    }

    @Test
    fun runningNotificationCountsDownOnTheLockScreen() {
        RestTimerNotifications.ensureChannels(context)
        val notification = RestTimerNotifications.runningNotification(
            context = context,
            state = RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = 90_000L,
                totalSeconds = 90,
                sessionId = "session-1",
                timerId = "timer-1",
            ),
            nowElapsedRealtime = 0L,
            nowWallClockMillis = 1_000L,
        )
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(91_000L, notification.`when`)
        assertEquals("1:30 remaining", notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertEquals(Notification.CATEGORY_STOPWATCH, notification.category)
        assertNotNull(notification.contentIntent)
        assertNull(notification.fullScreenIntent)
    }

    @Test
    fun runningNotificationFreezesAtZeroInsteadOfCountingThrough() {
        RestTimerNotifications.ensureChannels(context)
        val notification = RestTimerNotifications.runningNotification(
            context = context,
            state = RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = 90_000L,
                totalSeconds = 90,
                sessionId = "session-1",
                timerId = "timer-1",
            ),
            nowElapsedRealtime = 120_000L,
            nowWallClockMillis = 1_000L,
        )
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals("0:00 remaining", notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertFalse(
            notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.contains("-") == true,
        )
    }

    @Test
    fun doneNotificationSkipsFullScreenWhenTheApi34GateIsClosed() {
        RestTimerNotifications.ensureChannels(context)
        RestTimerNotifications.showDone(context, "session-1")
        val posted = context.getSystemService(NotificationManager::class.java)
            .activeNotifications
            .first { it.id == RestTimerNotifications.DONE_ID }
            .notification
        assertNull(posted.fullScreenIntent)
        assertEquals("Rest done", posted.extras.getString(Notification.EXTRA_TITLE))
    }
}
