package com.sinura.personaltrainer.timer

import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.RestTimerMarkers
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class)
class RestTimerCooldownExpiredCompletionTest {
    private lateinit var app: PersonalTrainerApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        RestTimerCompletion.reset()
        app.container.restTimerController.stop(fromService = true)
        app.container.restTimerStatePersistence.clear()
    }

    @After
    fun tearDown() {
        RestTimerService.tickObserver = null
        app.container.restTimerController.stop(fromService = true)
        RestTimerOverlayController.release()
        RestTimerAppForeground.setForegroundForTest(false)
        app.container.restTimerStatePersistence.clear()
        RestTimerCompletion.reset()
        shadowOf(app.mainLooper).idle()
    }

    @Test
    fun expiredCooldownRehydratePostsNonBypassDoneNotification() = runBlocking {
        val now = SystemClock.elapsedRealtime()
        val timerId = "expired-cd-rehydrate"
        assertTrue(
            app.container.restTimerStatePersistence.save(
                RestTimerRehydrator.toPersisted(
                    endsAtElapsedRealtime = now - 1_000L,
                    totalSeconds = RestTimerMarkers.COOLDOWN_SECONDS,
                    sessionId = null,
                    nowElapsedRealtime = now,
                    timerId = timerId,
                    sourceTag = RestTimerMarkers.COOLDOWN,
                ),
            ),
        )
        assertFalse(app.container.restTimerController.rehydrate())
        awaitDoneNotification(app)

        val posted = app.getSystemService(NotificationManager::class.java)
            .activeNotifications
            .first { it.id == RestTimerNotifications.DONE_ID }
            .notification
        assertNull(posted.fullScreenIntent)
        assertEquals(RestTimerNotifications.CHANNEL_COOLDOWN_DONE, posted.channelId)
        assertEquals(
            app.getString(com.sinura.personaltrainer.R.string.rest_notification_cooldown_done_title),
            posted.extras.getString(android.app.Notification.EXTRA_TITLE),
        )
        RestTimerNotifications.ensureChannels(app)
        val channel = app.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(RestTimerNotifications.CHANNEL_COOLDOWN_DONE)
        assertFalse(channel.canBypassDnd())
    }

    @Test
    fun alarmReceiverCompletesExpiredCooldownFromDiskRow() = runBlocking {
        val now = SystemClock.elapsedRealtime()
        val timerId = "expired-cd-alarm"
        assertTrue(
            app.container.restTimerStatePersistence.save(
                RestTimerRehydrator.toPersisted(
                    endsAtElapsedRealtime = now - 1_000L,
                    totalSeconds = RestTimerMarkers.COOLDOWN_SECONDS,
                    sessionId = null,
                    nowElapsedRealtime = now,
                    timerId = timerId,
                    sourceTag = RestTimerMarkers.COOLDOWN,
                ),
            ),
        )
        val intent = Intent(app, RestTimerAlarmReceiver::class.java).apply {
            action = RestTimerAlarmReceiver.ACTION_REST_COMPLETE
            putExtra(RestTimerService.EXTRA_TIMER_ID, timerId)
        }
        RestTimerAlarmReceiver().onReceive(app, intent)
        awaitDoneNotification(app)

        val posted = app.getSystemService(NotificationManager::class.java)
            .activeNotifications
            .first { it.id == RestTimerNotifications.DONE_ID }
            .notification
        assertNull(posted.fullScreenIntent)
        assertEquals(RestTimerNotifications.CHANNEL_COOLDOWN_DONE, posted.channelId)
        assertEquals(
            app.getString(com.sinura.personaltrainer.R.string.rest_notification_cooldown_done_title),
            posted.extras.getString(android.app.Notification.EXTRA_TITLE),
        )
    }

    private suspend fun awaitDoneNotification(context: Context, timeoutMs: Long = 5_000) {
        withTimeout(timeoutMs) {
            val manager = context.getSystemService(NotificationManager::class.java)
            while (manager.activeNotifications.none { it.id == RestTimerNotifications.DONE_ID }) {
                shadowOf(context.mainLooper).idle()
                delay(25)
            }
        }
    }
}
