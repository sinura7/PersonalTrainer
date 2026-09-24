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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

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
        // DataStore outlives a test. A backup/settings case that left ticks
        // off would mute theLastFiveSecondsTickOncePerSecond in the suite.
        runBlocking { app.container.preferencesRepository.setRestTickEnabled(true) }
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
    fun aStopSentForAnEarlierRestLeavesTheNewOneRunning() {
        // Skip, then a new rest (the next set's auto-rest) before the service reads the Skip's
        // STOP. The STOP names the skipped rest, so the new one keeps running.
        val store = app.container.restTimerStore
        val rest = app.container.restTimerController
        rest.start(90, "session-1")
        val skipped = store.current().timerId
        rest.stop()
        rest.start(120, "session-1")
        val next = store.current().timerId

        val lateStop = Intent(app, RestTimerService::class.java)
            .setAction(RestTimerService.ACTION_STOP)
            .putExtra(RestTimerService.EXTRA_TIMER_ID, skipped)
        val controller = Robolectric.buildService(RestTimerService::class.java, lateStop)
        val service = controller.create().startCommand(0, 1).get()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(store.current().running)
        assertEquals(next, store.current().timerId)
        assertFalse(shadowOf(service).isStoppedBySelf)
        val manager = app.getSystemService(NotificationManager::class.java)
        assertNotNull(
            manager.activeNotifications.firstOrNull { it.id == RestTimerNotifications.RUNNING_ID },
        )
        controller.destroy()
    }

    @Test
    fun aStopForTheRestItNamesStopsTheService() {
        val store = app.container.restTimerStore
        val rest = app.container.restTimerController
        rest.start(90, "session-1")
        val skipped = store.current().timerId
        rest.stop()

        val stop = Intent(app, RestTimerService::class.java)
            .setAction(RestTimerService.ACTION_STOP)
            .putExtra(RestTimerService.EXTRA_TIMER_ID, skipped)
        val controller = Robolectric.buildService(RestTimerService::class.java, stop)
        val service = controller.create().startCommand(0, 1).get()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(store.current().running)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun aStopThatNamesNoRestEndsWhateverRuns() {
        // The form every STOP had before W2b-1b; the service still honours it.
        val store = app.container.restTimerStore
        app.container.restTimerController.start(90, "session-1")

        val stop = Intent(app, RestTimerService::class.java).setAction(RestTimerService.ACTION_STOP)
        val controller = Robolectric.buildService(RestTimerService::class.java, stop)
        val service = controller.create().startCommand(0, 1).get()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(store.current().running)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun aLateStopAfterTheNextRestFinishedKeepsItsRestDone() {
        val store = app.container.restTimerStore
        val rest = app.container.restTimerController
        rest.start(90, "session-1")
        val skipped = store.current().timerId
        rest.stop()
        rest.start(60, "session-1")
        val next = store.current()
        runBlocking {
            assertTrue(
                RestTimerCompletion.completeOnce(
                    context = app,
                    incomingTimerId = next.timerId,
                    expectedTimerId = next.timerId,
                    deadlineElapsedRealtime = next.endsAtElapsedRealtime,
                    sessionId = "session-1",
                    nowElapsedRealtime = next.endsAtElapsedRealtime + 1L,
                    playCue = false,
                ),
            )
        }

        val lateStop = Intent(app, RestTimerService::class.java)
            .setAction(RestTimerService.ACTION_STOP)
            .putExtra(RestTimerService.EXTRA_TIMER_ID, skipped)
        val controller = Robolectric.buildService(RestTimerService::class.java, lateStop)
        val service = controller.create().startCommand(0, 1).get()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("the next rest stays finished", next.timerId, rest.lastCompletedTimerId.value)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun aSkipOnACardLeftAfterItsRestFinishedKeepsItDone() {
        // Some phones keep the running card a beat after the rest ends; a Skip tapped on it
        // must not turn "rest done" into a skip.
        val store = app.container.restTimerStore
        val rest = app.container.restTimerController
        rest.start(90, "session-1")
        val finished = store.current()
        runBlocking {
            assertTrue(
                RestTimerCompletion.completeOnce(
                    context = app,
                    incomingTimerId = finished.timerId,
                    expectedTimerId = finished.timerId,
                    deadlineElapsedRealtime = finished.endsAtElapsedRealtime,
                    sessionId = "session-1",
                    nowElapsedRealtime = finished.endsAtElapsedRealtime + 1L,
                    playCue = false,
                ),
            )
        }

        val skip = Intent(app, RestTimerService::class.java).setAction(RestTimerService.ACTION_SKIP)
        val controller = Robolectric.buildService(RestTimerService::class.java, skip)
        val service = controller.create().startCommand(0, 1).get()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("still done, not skipped", finished.timerId, rest.lastCompletedTimerId.value)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun aSkipFromACardForAnEarlierRestLeavesTheNewOneRunning() {
        // The next set's rest started before the card caught up. The card's Skip names the rest
        // it shows, so the new one keeps running, and the card moves to it.
        val store = app.container.restTimerStore
        val rest = app.container.restTimerController
        rest.start(90, "session-1")
        val (controller, service) = runningService()
        val staleSkip = skipOnTheCard()
        rest.start(120, "session-1")
        val next = store.current().timerId

        service.onStartCommand(staleSkip, 0, 2)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("the new rest keeps running", store.current().running)
        assertEquals(next, store.current().timerId)
        assertFalse(shadowOf(service).isStoppedBySelf)
        assertEquals(
            "the card moved to the new rest",
            next,
            skipOnTheCard().getStringExtra(RestTimerService.EXTRA_TIMER_ID),
        )
        controller.destroy()
    }

    @Test
    fun aSkipSentJustBeforeAPlusFifteenStillEndsTheRest() = skipSentJustBefore(RestTimerService.ACTION_ADD_15)

    @Test
    fun aSkipSentJustBeforeAMinusFifteenStillEndsTheRest() = skipSentJustBefore(RestTimerService.ACTION_MINUS_15)

    /** Skip tapped as a ±15 landed, before the card was rebuilt: it names the rest before the ±15. */
    private fun skipSentJustBefore(adjust: String) {
        val store = app.container.restTimerStore
        app.container.restTimerController.start(90, "session-1")
        val (controller, service) = runningService()
        val skip = skipOnTheCard()

        service.onStartCommand(Intent(app, RestTimerService::class.java).setAction(adjust), 0, 2)
        shadowOf(Looper.getMainLooper()).idle()
        service.onStartCommand(skip, 0, 3)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("to the owner a ±15 is the same rest", store.current().running)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun aSkipFromTheCardRebuiltAfterAPlusFifteenEndsTheExtendedRest() {
        val store = app.container.restTimerStore
        app.container.restTimerController.start(90, "session-1")
        val (controller, service) = runningService()
        service.onStartCommand(Intent(app, RestTimerService::class.java).setAction(RestTimerService.ACTION_ADD_15), 0, 2)
        shadowOf(Looper.getMainLooper()).idle()
        val extended = store.current().timerId

        val skip = skipOnTheCard()
        assertEquals("the card names the extended rest", extended, skip.getStringExtra(RestTimerService.EXTRA_TIMER_ID))
        service.onStartCommand(skip, 0, 3)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(store.current().running)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    /** The service, started for the running rest and showing its card. */
    private fun runningService(): Pair<ServiceController<RestTimerService>, RestTimerService> {
        val sync = Intent(app, RestTimerService::class.java).setAction(RestTimerService.ACTION_SYNC)
        val controller = Robolectric.buildService(RestTimerService::class.java, sync)
        val service = controller.create().startCommand(0, 1).get()
        shadowOf(Looper.getMainLooper()).idle()
        return controller to service
    }

    /** What a tap on the running card's Skip sends now (a copy: the card may be rebuilt). */
    private fun skipOnTheCard(): Intent {
        val card = app.getSystemService(NotificationManager::class.java)
            .activeNotifications.first { it.id == RestTimerNotifications.RUNNING_ID }.notification
        val skip = card.actions.single { it.title.toString() == "Skip" }
        return Intent(shadowOf(skip.actionIntent).savedIntent)
    }

    @Test
    fun aSkipNamingTheRunningRestEndsIt() {
        val store = app.container.restTimerStore
        val rest = app.container.restTimerController
        rest.start(90, "session-1")
        val shown = store.current().timerId

        val skip = Intent(app, RestTimerService::class.java)
            .setAction(RestTimerService.ACTION_SKIP)
            .putExtra(RestTimerService.EXTRA_TIMER_ID, shown)
        val controller = Robolectric.buildService(RestTimerService::class.java, skip)
        val service = controller.create().startCommand(0, 1).get()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(store.current().running)
        assertNull("a skip is not a finish", rest.lastCompletedTimerId.value)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun aSkipFromACardThatNamesNoRestEndsTheRunningOne() {
        // A card built before the Skip carried its rest's id: it ends the rest running when the
        // service reads it.
        val store = app.container.restTimerStore
        app.container.restTimerController.start(90, "session-1")

        val skip = Intent(app, RestTimerService::class.java).setAction(RestTimerService.ACTION_SKIP)
        val controller = Robolectric.buildService(RestTimerService::class.java, skip)
        val service = controller.create().startCommand(0, 1).get()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(store.current().running)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun aSkipNamingARestThatJustFinishedKeepsItDone() {
        // The rest ran out as the Skip was tapped, and its finish landed first: the Skip finds
        // nothing of its own to end, so "rest done" stays.
        val store = app.container.restTimerStore
        val rest = app.container.restTimerController
        rest.start(90, "session-1")
        val finished = store.current()
        runBlocking {
            assertTrue(
                RestTimerCompletion.completeOnce(
                    context = app,
                    incomingTimerId = finished.timerId,
                    expectedTimerId = finished.timerId,
                    deadlineElapsedRealtime = finished.endsAtElapsedRealtime,
                    sessionId = "session-1",
                    nowElapsedRealtime = finished.endsAtElapsedRealtime + 1L,
                    playCue = false,
                ),
            )
        }

        val skip = Intent(app, RestTimerService::class.java)
            .setAction(RestTimerService.ACTION_SKIP)
            .putExtra(RestTimerService.EXTRA_TIMER_ID, finished.timerId)
        val controller = Robolectric.buildService(RestTimerService::class.java, skip)
        val service = controller.create().startCommand(0, 1).get()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("still done, not skipped", finished.timerId, rest.lastCompletedTimerId.value)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun anAlarmAlreadyOnItsWayAfterASkipPostsNoRestDone() {
        // The receiver found the skipped rest's disk row before the Skip's clear landed.
        val store = app.container.restTimerStore
        val rest = app.container.restTimerController
        rest.start(90, "session-1")
        val skipped = store.current()
        rest.stop()

        runBlocking {
            val claimed = RestTimerCompletion.completeOnce(
                context = app,
                incomingTimerId = skipped.timerId,
                expectedTimerId = skipped.timerId,
                deadlineElapsedRealtime = skipped.endsAtElapsedRealtime,
                sessionId = "session-1",
                nowElapsedRealtime = skipped.endsAtElapsedRealtime + 1_000L,
                playCue = false,
            )
            assertFalse(claimed)
        }
        shadowOf(Looper.getMainLooper()).idle()

        val manager = app.getSystemService(NotificationManager::class.java)
        assertTrue(manager.activeNotifications.none { it.id == RestTimerNotifications.DONE_ID })
        assertNull(rest.lastCompletedTimerId.value)
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
        // completeOnce may suspend on DataStore before posting DONE; one idle
        // after the freeze is not always enough on a busy runner.
        val giveUpAt = System.nanoTime() + PREFS_WAIT_NANOS
        while (
            manager.activeNotifications.none { it.id == RestTimerNotifications.DONE_ID } &&
            System.nanoTime() < giveUpAt
        ) {
            Thread.sleep(10)
            looper.idle()
        }
        assertNotNull(
            "DONE_ID never posted after screen-on deadline",
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
        val service = controller.create().startCommand(0, 1).get()
        val looper = shadowOf(Looper.getMainLooper())
        looper.idle()
        awaitTickPreferences(service, looper, enabled = true)

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
        awaitTickPreferences(service, looper, enabled = true)

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
        val service = controller.create().startCommand(0, 1).get()
        val looper = shadowOf(Looper.getMainLooper())
        looper.idle()
        awaitTickPreferences(service, looper, enabled = true)

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
            awaitTickPreferences(service, looper, enabled = false)

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
        // The real read lands on its own thread. Let it land first, or it can overwrite the
        // "not landed yet" this test stages and tick during the silent stretch below.
        awaitTickPreferences(service, looper, enabled = true)

        service.tickPreferences = null
        looper.idleFor(Duration.ofSeconds(26))
        assertEquals(emptyList<Int>(), ticks)

        service.tickPreferences = RestTimerPreferences.DEFAULT
        looper.idleFor(Duration.ofSeconds(3))
        assertEquals(listOf(3, 2, 1), ticks)
        controller.destroy()
    }

    /**
     * Blocks, in real time, until the service has read the tick toggle off DataStore.
     *
     * That read happens on a real thread; everything else in these tests runs on
     * Robolectric's virtual clock, which `idleFor` advances instantly. A boundary that
     * fires before the preferences have landed is dropped by design (see
     * `aTickBeforeThePreferencesLandStaysSilentAndKeepsCounting`), so a test that moves
     * the clock first is racing the runner: `addFifteenMovesTheTicksWithTheDeadline` lost
     * that race on trunk (run 35272784785, `expected [5] but was []`). Bounded, so a
     * preference that never arrives fails the test rather than hanging it.
     */
    private fun awaitTickPreferences(service: RestTimerService, looper: ShadowLooper, enabled: Boolean) {
        val giveUpAt = System.nanoTime() + PREFS_WAIT_NANOS
        while (service.tickPreferences?.tickEnabled != enabled && System.nanoTime() < giveUpAt) {
            Thread.sleep(10)
            looper.idle()
        }
        assertEquals(
            "tick preference never reached the service",
            enabled,
            service.tickPreferences?.tickEnabled,
        )
    }

    private companion object {
        const val PREFS_WAIT_NANOS = 5_000_000_000L
    }
}
