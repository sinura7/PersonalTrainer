package com.sinura.personaltrainer.timer

import android.app.NotificationManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.RestTimerMarkers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
    }

    @After
    fun tearDown() {
        app.container.restTimerStatePersistence.clear()
        RestTimerCompletion.reset()
    }

    @Test
    fun expiredCooldownOnDiskCompletesAsCooldownDone() = runBlocking {
        val now = SystemClock.elapsedRealtime()
        val stored = RestTimerRehydrator.toPersisted(
            endsAtElapsedRealtime = now - 1_000L,
            totalSeconds = RestTimerMarkers.COOLDOWN_SECONDS,
            sessionId = null,
            nowElapsedRealtime = now,
            timerId = "expired-cd",
            sourceTag = RestTimerMarkers.COOLDOWN,
        )
        val outcome = RestTimerRehydrator.rehydrate(
            stored = stored,
            nowElapsedRealtime = now,
            nowWallClockMillis = System.currentTimeMillis(),
            nowBootCount = com.sinura.personaltrainer.timer.BootSession.count(app),
        )
        val expired = outcome as RestTimerRehydration.Expired
        assertTrue(RestTimerMarkers.isCooldownTag(expired.sourceTag))
        assertTrue(
            RestTimerCompletion.completeOnce(
                context = app,
                incomingTimerId = expired.timerId,
                expectedTimerId = expired.timerId,
                deadlineElapsedRealtime = expired.endsAtElapsedRealtime,
                sessionId = expired.sessionId,
                playCue = false,
                isCooldown = RestTimerMarkers.isCooldownTag(expired.sourceTag),
            ),
        )
        RestTimerNotifications.ensureChannels(app)
        val channel = app.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(RestTimerNotifications.CHANNEL_COOLDOWN_DONE)
        assertFalse(channel.canBypassDnd())
    }

    @Test
    fun liveCooldownCompletionUsesNonBypassChannel() = runBlocking {
        val store = app.container.restTimerStore
        store.start(
            totalSeconds = RestTimerMarkers.COOLDOWN_SECONDS,
            sessionId = null,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            sourceTag = RestTimerMarkers.COOLDOWN,
        )
        val snap = store.current()
        assertTrue(
            RestTimerCompletion.completeOnce(
                context = app,
                incomingTimerId = snap.timerId,
                expectedTimerId = snap.timerId,
                deadlineElapsedRealtime = snap.endsAtElapsedRealtime,
                sessionId = snap.sessionId,
                nowElapsedRealtime = snap.endsAtElapsedRealtime + 1L,
                isCooldown = true,
            ),
        )
        RestTimerNotifications.ensureChannels(app)
        val channel = app.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(RestTimerNotifications.CHANNEL_COOLDOWN_DONE)
        assertFalse(channel.canBypassDnd())
    }

    private fun shadowWait() {
        org.robolectric.Shadows.shadowOf(app.mainLooper).idle()
        Thread.sleep(50)
        org.robolectric.Shadows.shadowOf(app.mainLooper).idle()
    }
}
