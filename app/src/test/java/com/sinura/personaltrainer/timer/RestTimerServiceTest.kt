package com.sinura.personaltrainer.timer

import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.PersonalTrainerApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The rest service follows the store. Alarm-path completion used to leave
 * a negative chronometer in the shade because `fromService` skipped
 * ACTION_STOP. A +15s claimed as the old generation completed used to
 * kill the card under the extension.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class)
class RestTimerServiceTest {
    private lateinit var app: PersonalTrainerApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        RestTimerCompletion.reset()
        app.container.restTimerController.stop(fromService = true)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        RestTimerCompletion.reset()
        app.container.restTimerController.stop(fromService = true)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun alarmPathCompletionStopsTheServiceAndRemovesTheRunningCard() {
        val store = app.container.restTimerStore
        store.start(90, "session-1", nowElapsedRealtime = SystemClock.elapsedRealtime())
        val timerId = store.current().timerId
        assertTrue(store.current().running)

        val intent = Intent(app, RestTimerService::class.java)
            .setAction(RestTimerService.ACTION_SYNC)
        val controller = Robolectric.buildService(RestTimerService::class.java, intent)
        val service = controller.create().startCommand(0, 1).get()
        shadowOf(Looper.getMainLooper()).idle()

        val manager = app.getSystemService(NotificationManager::class.java)
        assertNotNull(
            manager.activeNotifications.firstOrNull { it.id == RestTimerNotifications.RUNNING_ID },
        )

        runBlocking {
            val claimed = RestTimerCompletion.completeOnce(
                context = app,
                incomingTimerId = timerId,
                expectedTimerId = timerId,
                deadlineElapsedRealtime = store.current().endsAtElapsedRealtime,
                sessionId = "session-1",
                nowElapsedRealtime = SystemClock.elapsedRealtime() + 120_000L,
                playCue = false,
            )
            assertTrue(claimed)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(store.current().running)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertTrue(
            manager.activeNotifications.none { it.id == RestTimerNotifications.RUNNING_ID },
        )
        assertNotNull(
            manager.activeNotifications.firstOrNull { it.id == RestTimerNotifications.DONE_ID },
        )
        controller.destroy()
    }

    @Test
    fun addFifteenClaimedMidCompletionLeavesTheServiceAlive() {
        val store = app.container.restTimerStore
        store.start(90, "session-1", nowElapsedRealtime = SystemClock.elapsedRealtime())
        val first = store.current()
        assertTrue(first.running)

        val intent = Intent(app, RestTimerService::class.java)
            .setAction(RestTimerService.ACTION_SYNC)
        val controller = Robolectric.buildService(RestTimerService::class.java, intent)
        val service = controller.create().startCommand(0, 1).get()
        shadowOf(Looper.getMainLooper()).idle()

        store.adjust(15, nowElapsedRealtime = SystemClock.elapsedRealtime())
        val second = store.current()
        assertTrue(second.running)
        assertTrue(first.timerId != second.timerId)

        // Old generation is due; the live timer is the +15s mint. Completing
        // the captured snapshot must not tear the extension down.
        service.handleDeadline(
            first.copy(endsAtElapsedRealtime = SystemClock.elapsedRealtime() - 1_000L),
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(store.current().running)
        assertFalse(shadowOf(service).isStoppedBySelf)
        val manager = app.getSystemService(NotificationManager::class.java)
        assertNotNull(
            manager.activeNotifications.firstOrNull { it.id == RestTimerNotifications.RUNNING_ID },
        )
        controller.destroy()
    }
}
