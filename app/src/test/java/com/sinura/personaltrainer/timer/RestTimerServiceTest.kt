package com.sinura.personaltrainer.timer

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import java.time.Duration
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.RestTimerPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
        RestTimerService.tickObserver = null
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

    /**
     * A-03. The screen-on deadline used to leave a live Chronometer whose
     * base was already past, so the shade painted `-0:01` until Done
     * replaced it. Freeze first: last running frame is `0:00 remaining`.
     */
    @Test
    fun deadlineFreezesTheShadeClockAtZero() {
        val store = app.container.restTimerStore
        store.start(1, "session-1", nowElapsedRealtime = SystemClock.elapsedRealtime())

        val intent = Intent(app, RestTimerService::class.java)
            .setAction(RestTimerService.ACTION_SYNC)
        val controller = Robolectric.buildService(RestTimerService::class.java, intent)
        val service = controller.create().startCommand(0, 1).get()
        val looper = shadowOf(Looper.getMainLooper())
        looper.idle()

        val manager = app.getSystemService(NotificationManager::class.java)
        val live = manager.activeNotifications
            .first { it.id == RestTimerNotifications.RUNNING_ID }
            .notification
        assertTrue(live.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))

        looper.idleFor(Duration.ofSeconds(1))
        looper.idle()

        val after = manager.activeNotifications
            .firstOrNull { it.id == RestTimerNotifications.RUNNING_ID }
            ?.notification
        if (after != null) {
            assertFalse(after.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
            assertEquals(
                "0:00 remaining",
                after.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            )
        }
        assertNotNull(
            manager.activeNotifications.firstOrNull { it.id == RestTimerNotifications.DONE_ID },
        )
        controller.destroy()
    }

    /**
     * R-04. One tick per boundary, 5 down to 1, none above five and none at
     * zero — the cue owns zero. Walked on Robolectric's clock; the observer
     * is the only listener there is.
     */
    @Test
    fun theLastFiveSecondsTickOncePerSecond() {
        val ticks = mutableListOf<Int>()
        RestTimerService.tickObserver = { ticks += it }
        val store = app.container.restTimerStore
        store.start(90, "session-1", nowElapsedRealtime = SystemClock.elapsedRealtime())

        val intent = Intent(app, RestTimerService::class.java)
            .setAction(RestTimerService.ACTION_SYNC)
        val controller = Robolectric.buildService(RestTimerService::class.java, intent)
        controller.create().startCommand(0, 1)
        val looper = shadowOf(Looper.getMainLooper())
        looper.idle()

        looper.idleFor(Duration.ofSeconds(84))
        assertEquals(emptyList<Int>(), ticks)
        looper.idleFor(Duration.ofSeconds(1))
        assertEquals(listOf(5), ticks)
        looper.idleFor(Duration.ofSeconds(4))
        assertEquals(listOf(5, 4, 3, 2, 1), ticks)
        looper.idleFor(Duration.ofMillis(900))
        assertEquals(listOf(5, 4, 3, 2, 1), ticks)
        controller.destroy()
    }

    /** A +15 s at four seconds left moves the ticks with the deadline; none sound twice. */
    @Test
    fun addFifteenMovesTheTicksWithTheDeadline() {
        val ticks = mutableListOf<Int>()
        RestTimerService.tickObserver = { ticks += it }
        val store = app.container.restTimerStore
        store.start(30, "session-1", nowElapsedRealtime = SystemClock.elapsedRealtime())

        val intent = Intent(app, RestTimerService::class.java)
            .setAction(RestTimerService.ACTION_SYNC)
        val controller = Robolectric.buildService(RestTimerService::class.java, intent)
        val service = controller.create().startCommand(0, 1).get()
        val looper = shadowOf(Looper.getMainLooper())
        looper.idle()

        // 4.5 s left: the five-second tick has sounded, the four-second one is half a second out.
        looper.idleFor(Duration.ofMillis(25_500))
        assertEquals(listOf(5), ticks)
        store.adjust(15, nowElapsedRealtime = SystemClock.elapsedRealtime())
        service.onStartCommand(
            Intent(app, RestTimerService::class.java).setAction(RestTimerService.ACTION_SYNC),
            0,
            2,
        )
        looper.idle()
        // 20 s left now (adjust adds to the rounded-up 5): quiet until the new
        // five-second boundary.
        looper.idleFor(Duration.ofSeconds(13))
        assertEquals(listOf(5), ticks)
        looper.idleFor(Duration.ofSeconds(6))
        assertEquals(listOf(5, 5, 4, 3, 2, 1), ticks)
        controller.destroy()
    }

    /**
     * A -15 s from the app changes the store first; the ACTION_SYNC that
     * follows the disk write can be tens of milliseconds behind. The ticks
     * re-anchor from the store, and a countdown that lands on five ticks
     * five at once rather than waiting for four.
     */
    @Test
    fun minusFifteenReanchorsTheTicksFromTheStoreWithoutASync() {
        val ticks = mutableListOf<Int>()
        RestTimerService.tickObserver = { ticks += it }
        val store = app.container.restTimerStore
        store.start(30, "session-1", nowElapsedRealtime = SystemClock.elapsedRealtime())

        val intent = Intent(app, RestTimerService::class.java)
            .setAction(RestTimerService.ACTION_SYNC)
        val controller = Robolectric.buildService(RestTimerService::class.java, intent)
        controller.create().startCommand(0, 1)
        val looper = shadowOf(Looper.getMainLooper())
        looper.idle()

        looper.idleFor(Duration.ofSeconds(10))
        assertEquals(emptyList<Int>(), ticks)
        // 20 s left → 5 s left, and no intent follows.
        store.adjust(-15, nowElapsedRealtime = SystemClock.elapsedRealtime())
        looper.idle()
        assertEquals(listOf(5), ticks)
        looper.idleFor(Duration.ofSeconds(4))
        assertEquals(listOf(5, 4, 3, 2, 1), ticks)
        controller.destroy()
    }

    @Test
    fun theTickToggleOffKeepsTheLastFiveSecondsQuiet() {
        val ticks = mutableListOf<Int>()
        RestTimerService.tickObserver = { ticks += it }
        runBlocking { app.container.preferencesRepository.setRestTickEnabled(false) }
        try {
            val store = app.container.restTimerStore
            store.start(90, "session-1", nowElapsedRealtime = SystemClock.elapsedRealtime())

            val intent = Intent(app, RestTimerService::class.java)
                .setAction(RestTimerService.ACTION_SYNC)
            val controller = Robolectric.buildService(RestTimerService::class.java, intent)
            val service = controller.create().startCommand(0, 1).get()
            val looper = shadowOf(Looper.getMainLooper())
            // The service reads the toggle off DataStore on another thread; give
            // that read a bounded chance to land before the clock moves.
            val giveUpAt = System.nanoTime() + PREFS_WAIT_NANOS
            while (service.tickPreferences?.tickEnabled != false && System.nanoTime() < giveUpAt) {
                Thread.sleep(10)
                looper.idle()
            }
            assertFalse(
                "tick preference never reached the service",
                service.tickPreferences?.tickEnabled ?: true,
            )

            looper.idleFor(Duration.ofSeconds(89))
            assertEquals(emptyList<Int>(), ticks)
            controller.destroy()
        } finally {
            // The app's DataStore outlives the test; a false left behind would mute
            // every later rest in this JVM.
            runBlocking { app.container.preferencesRepository.setRestTickEnabled(true) }
        }
    }

    /**
     * The service used to start from RestTimerPreferences.DEFAULT — everything
     * on — so a boundary that fell before DataStore's first emission ticked
     * against whatever the owner had actually chosen. A tick with no
     * preferences yet stays silent and keeps counting.
     */
    @Test
    fun aTickBeforeThePreferencesLandStaysSilentAndKeepsCounting() {
        val ticks = mutableListOf<Int>()
        RestTimerService.tickObserver = { ticks += it }
        val store = app.container.restTimerStore
        store.start(30, "session-1", nowElapsedRealtime = SystemClock.elapsedRealtime())

        val intent = Intent(app, RestTimerService::class.java)
            .setAction(RestTimerService.ACTION_SYNC)
        val controller = Robolectric.buildService(RestTimerService::class.java, intent)
        val service = controller.create().startCommand(0, 1).get()
        val looper = shadowOf(Looper.getMainLooper())
        looper.idle()

        service.tickPreferences = null
        looper.idleFor(Duration.ofSeconds(26))
        assertEquals(emptyList<Int>(), ticks)

        service.tickPreferences = RestTimerPreferences.DEFAULT
        looper.idleFor(Duration.ofSeconds(3))
        assertEquals(listOf(3, 2, 1), ticks)
        controller.destroy()
    }

    private companion object {
        const val PREFS_WAIT_NANOS = 5_000_000_000L
    }
}
