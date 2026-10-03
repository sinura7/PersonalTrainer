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
import org.robolectric.Shadows.shadowOf
import android.app.KeyguardManager
import android.os.PowerManager

@RunWith(RobolectricTestRunner::class)
class RestTimerNotificationsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun runningChannelIsHighPublicAndReplacesLegacyIds() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                "rest_timer_running",
                "legacy",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                "rest_timer_running_v2",
                "legacy v2",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        RestTimerNotifications.ensureChannels(context)

        assertNull(manager.getNotificationChannel("rest_timer_running"))
        assertNull(manager.getNotificationChannel("rest_timer_running_v2"))
        val running = checkNotNull(
            manager.getNotificationChannel(RestTimerNotifications.CHANNEL_RUNNING),
        )
        assertEquals(NotificationManager.IMPORTANCE_HIGH, running.importance)
        assertEquals(Notification.VISIBILITY_PUBLIC, running.lockscreenVisibility)
        val inApp = checkNotNull(
            manager.getNotificationChannel(RestTimerNotifications.CHANNEL_RUNNING_IN_APP),
        )
        assertEquals(NotificationManager.IMPORTANCE_MIN, inApp.importance)
    }

    @Test
    fun inAppRunningNotificationIsQuietAndDeferred() {
        RestTimerAppForeground.setForegroundForTest(true)
        RestTimerNotifications.ensureChannels(context)
        val notification = RestTimerNotifications.runningNotification(
            context = context,
            presentation = RestTimerRunningPresentation.FOREGROUND_IN_APP,
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
        assertNull(notification.headsUpContentView)
        assertNull(notification.fullScreenIntent)
        assertEquals(Notification.PRIORITY_MIN, notification.priority)
        assertEquals(Notification.VISIBILITY_SECRET, notification.visibility)
    }

    @Test
    fun runningNotificationCountsDownOnTheLockScreen() {
        RestTimerNotifications.ensureChannels(context)
        val notification = RestTimerNotifications.runningNotification(
            context = context,
            presentation = RestTimerRunningPresentation.BACKGROUND_UNLOCKED,
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
    fun theRunningCardsSkipNamesItsRest() {
        // The service skips only the rest the card names: a newer rest the card has not caught
        // up with keeps running, and a rest that finished first keeps its "rest done".
        RestTimerNotifications.ensureChannels(context)
        val notification = RestTimerNotifications.runningNotification(
            context = context,
            presentation = RestTimerRunningPresentation.BACKGROUND_UNLOCKED,
            state = RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = 90_000L,
                totalSeconds = 90,
                sessionId = "session-1",
                timerId = "timer-shown",
            ),
            nowElapsedRealtime = 0L,
            nowWallClockMillis = 1_000L,
        )

        val skip = notification.actions.single { it.title.toString() == "Skip" }
        val sent = shadowOf(skip.actionIntent).savedIntent
        assertEquals(RestTimerService.ACTION_SKIP, sent.action)
        assertEquals("timer-shown", sent.getStringExtra(RestTimerService.EXTRA_TIMER_ID))
    }

    @Test
    fun aRebuiltCardsSkipNamesTheNewRestNotTheFirst() {
        // One Skip link serves every card; each rebuild must rename it, or every Skip after the
        // first rest would name that first rest and end nothing.
        RestTimerNotifications.ensureChannels(context)
        runningCard("timer-first")

        val skip = runningCard("timer-second").actions.single { it.title.toString() == "Skip" }

        assertEquals("timer-second", shadowOf(skip.actionIntent).savedIntent.getStringExtra(RestTimerService.EXTRA_TIMER_ID))
    }

    private fun runningCard(timerId: String): Notification = RestTimerNotifications.runningNotification(
        context = context,
        presentation = RestTimerRunningPresentation.BACKGROUND_UNLOCKED,
        state = RestTimerSnapshot(
            running = true,
            endsAtElapsedRealtime = 90_000L,
            totalSeconds = 90,
            sessionId = "session-1",
            timerId = timerId,
        ),
        nowElapsedRealtime = 0L,
        nowWallClockMillis = 1_000L,
    )

    @Test
    fun runningNotificationFreezesAtZeroInsteadOfCountingThrough() {
        RestTimerNotifications.ensureChannels(context)
        val notification = RestTimerNotifications.runningNotification(
            context = context,
            presentation = RestTimerRunningPresentation.BACKGROUND_UNLOCKED,
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
    fun runningNotificationUsesHeadsUpLockGlanceWhenKeyguardLocked() {
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        RestTimerNotifications.ensureChannels(context)
        val notification = sampleRunningCard(RestTimerRunningPresentation.LOCKED)
        assertNotNull(notification.headsUpContentView)
        assertEquals(Notification.PRIORITY_HIGH, notification.priority)
        // Full-screen intent follows the same API 34 gate as rest-done (see doneNotification…).
        if (RestTimerNotifications.canUseFullScreenIntent(context)) {
            assertNotNull(notification.fullScreenIntent)
            val fsi = shadowOf(notification.fullScreenIntent).savedIntent
            assertEquals(RestLockActivity::class.java.name, fsi.component?.className)
            assertFalse(fsi.getBooleanExtra(RestTimerNotifications.EXTRA_FINISHED, true))
        } else {
            assertNull(notification.fullScreenIntent)
        }
    }

    @Test
    fun runningNotificationStaysShadeOnlyWhenUnlocked() {
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        RestTimerNotifications.ensureChannels(context)
        val notification = sampleRunningCard(RestTimerRunningPresentation.BACKGROUND_UNLOCKED)
        assertNull(notification.fullScreenIntent)
        assertNull(notification.headsUpContentView)
        assertEquals(Notification.PRIORITY_DEFAULT, notification.priority)
    }

    private fun sampleRunningCard(
        presentation: RestTimerRunningPresentation,
    ): Notification = RestTimerNotifications.runningNotification(
        context = context,
        presentation = presentation,
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
