package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.testutil.RestTimerGlobalsRule
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestTimerMarkers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = com.sinura.personaltrainer.PersonalTrainerApp::class)
class RestTimerCompletionCooldownTest {
    @get:Rule val restTimerGlobals = RestTimerGlobalsRule(ownsLedger = true)
    private val context = ApplicationProvider.getApplicationContext<com.sinura.personaltrainer.PersonalTrainerApp>()

    @Test
    fun cooldownCompletionPostsCooldownDoneWithoutFullScreenIntent() = runBlocking {
        val app = context as com.sinura.personaltrainer.PersonalTrainerApp
        val store = app.container.restTimerStore
        val controller = app.container.restTimerController
        store.start(
            totalSeconds = RestTimerMarkers.COOLDOWN_SECONDS,
            sessionId = null,
            nowElapsedRealtime = 1_000L,
            sourceTag = RestTimerMarkers.COOLDOWN,
        )
        val snap = store.current()
        assertTrue(
            RestTimerCompletion.completeOnce(
                context = context,
                incomingTimerId = snap.timerId,
                expectedTimerId = snap.timerId,
                deadlineElapsedRealtime = snap.endsAtElapsedRealtime,
                sessionId = snap.sessionId,
                nowElapsedRealtime = snap.endsAtElapsedRealtime + 1L,
            ),
        )
        assertEquals(1, RestTimerCompletion.ledger.claimedCountForTest())
        assertTrue(RestTimerCompletion.ledger.hasClaimForTest(snap.timerId))
        val posted = context.getSystemService(NotificationManager::class.java)
            .activeNotifications
            .first { it.id == RestTimerNotifications.DONE_ID }
            .notification
        assertNull(posted.fullScreenIntent)
        assertEquals(RestTimerNotifications.CHANNEL_COOLDOWN_DONE, posted.channelId)
        assertEquals(
            context.getString(com.sinura.personaltrainer.R.string.rest_notification_cooldown_done_title),
            posted.extras.getString(android.app.Notification.EXTRA_TITLE),
        )
        assertTrue(controller.lastCompletedWasCooldown.value)
    }

    @Test
    fun restCompletionStillPostsRestDoneWithBypassChannel() = runBlocking {
        val app = context as com.sinura.personaltrainer.PersonalTrainerApp
        val store = app.container.restTimerStore
        store.start(
            totalSeconds = 90,
            sessionId = "session-1",
            nowElapsedRealtime = 1_000L,
            sourceTag = "Routine",
        )
        val snap = store.current()
        assertTrue(
            RestTimerCompletion.completeOnce(
                context = context,
                incomingTimerId = snap.timerId,
                expectedTimerId = snap.timerId,
                deadlineElapsedRealtime = snap.endsAtElapsedRealtime,
                sessionId = snap.sessionId,
                nowElapsedRealtime = snap.endsAtElapsedRealtime + 1L,
                playCue = false,
            ),
        )
        assertEquals(1, RestTimerCompletion.ledger.claimedCountForTest())
        assertTrue(RestTimerCompletion.ledger.hasClaimForTest(snap.timerId))
        RestTimerNotifications.ensureChannels(context)
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(RestTimerNotifications.CHANNEL_DONE)
        assertTrue(channel.canBypassDnd())
        val posted = context.getSystemService(NotificationManager::class.java)
            .activeNotifications
            .first { it.id == RestTimerNotifications.DONE_ID }
            .notification
        assertEquals("Rest done", posted.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertFalse(app.container.restTimerController.lastCompletedWasCooldown.value)
    }
}
