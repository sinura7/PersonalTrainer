package com.sinura.personaltrainer.timer

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestTimerCompletionTest {
    @Test
    fun aFiveMinuteLateClaimPostsRestDoneWithoutPlayingTheCue() {
        RestTimerCompletion.reset()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val claimed = runBlocking {
            RestTimerCompletion.completeOnce(
                context = context,
                incomingTimerId = "timer-1",
                expectedTimerId = "timer-1",
                deadlineElapsedRealtime = 0L,
                sessionId = "session-1",
                nowElapsedRealtime = 5 * 60_000L,
                playCue = false,
            )
        }
        assertTrue(claimed)
        val posted = context.getSystemService(NotificationManager::class.java)
            .activeNotifications
            .first { it.id == RestTimerNotifications.DONE_ID }
            .notification
        assertEquals("Rest done", posted.extras.getString(android.app.Notification.EXTRA_TITLE))
    }
}
