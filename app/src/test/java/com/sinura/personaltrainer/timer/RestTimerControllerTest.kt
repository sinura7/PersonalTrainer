package com.sinura.personaltrainer.timer

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.AlarmScheduleResult
import com.sinura.personaltrainer.domain.RestFinishFlash
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.IdFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The gold flash and lock glance key on a completion id, not on running
 * going false. Skip must not look finished. Persist and arm are one
 * ordered IO job: the snapshot is live before disk, and the alarm is
 * never scheduled before the row is durable — nor at all when the row
 * did not commit, because the receiver reads a missing row as done.
 */
@RunWith(RobolectricTestRunner::class)
class RestTimerControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val logged = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val previousSink = AppLog.sink

    @Before
    fun captureLogs() {
        logged.clear()
        AppLog.sink = { priority, tag, _, _ -> logged += "$priority $tag" }
    }

    @After
    fun restoreSink() {
        AppLog.sink = previousSink
    }

    @Test
    fun aCompletionThatLosesTheRaceToAnAdjustLeavesTheReplacementRunning() {
        // ADR-012 decision 1: a completion for the old timer id cannot clear a replacement. The
        // owner's +15 lands between the completion's check and its clear — the moment the
        // completion id is published, which a collector on Unconfined sees inline.
        val store = RestTimerStore()
        val controller = RestTimerController(context, store)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            controller.start(90, "session-1")
            val first = store.current().timerId
            val racer = scope.launch {
                controller.lastCompletedTimerId.collect { completed ->
                    if (completed == first) controller.adjust(15)
                }
            }

            val completed = controller.completeIfCurrent(first, fromService = true)
            racer.cancel()

            assertTrue("the +15 minted a replacement timer", store.current().running)
            assertFalse("the old id's completion must not claim it", completed)
            assertTrue(store.current().timerId != first)
            assertNull("no rest finished: the flash and glance must not show one", controller.lastCompletedTimerId.value)
        } finally {
            scope.cancel()
            controller.stop()
        }
    }

    @Test
    fun anAdjustThatLosesTheRaceToACompletionKeepsTheRestDoneAndSendsNoStop() {
        // The mirror of the race above: the alarm's completion lands between the +15's read and
        // its write (the id the +15 mints is that moment). The rest stays finished, and the +15
        // sends the service no STOP: the service's stop() would wipe the "rest done" just
        // published, and a finished rest would look skipped.
        var racing: RestTimerController? = null
        var minted = 0
        val completesMidAdjust = IdFactory {
            minted += 1
            if (minted == 2) racing?.completeIfCurrent("timer-1", fromService = true)
            "timer-$minted"
        }
        val store = RestTimerStore(ids = completesMidAdjust)
        val controller = RestTimerController(context = context, store = store, ioDispatcher = Dispatchers.Unconfined)
        racing = controller
        try {
            controller.start(90, "session-1")
            startedServiceActions()

            controller.adjust(15)

            assertFalse("the finished rest stays finished", store.current().running)
            assertEquals("timer-1", controller.lastCompletedTimerId.value)
            assertFalse(RestTimerService.ACTION_STOP in startedServiceActions())
        } finally {
            controller.stop()
        }
    }

    @Test
    fun aRestAdjustedToZeroCancelsItsAlarmClearsItsRowAndStopsTheService() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            controller.start(90, "session-1")
            val ending = store.current().timerId
            events.clear()
            startedServiceActions()

            controller.adjust(-10_000)

            assertFalse(store.current().running)
            // A row left behind would rehydrate after a process death as a silent "Rest done".
            assertNull(persistence.saved)
            val cancelAt = events.indexOf("cancel")
            val clearAt = events.indexOf("clear")
            assertTrue(cancelAt >= 0)
            assertTrue("the wakeup goes before the row", cancelAt < clearAt)
            assertEquals("the STOP names the rest that ended", listOf(ending), stopsSent())
        } finally {
            controller.stop()
        }
    }

    @Test
    fun aSkipTellsTheServiceWhichRestItStopped() {
        // A STOP that named no rest ended whatever was running when the service got to it:
        // a rest started in between was killed by the Skip before it.
        val store = RestTimerStore()
        val controller = RestTimerController(context = context, store = store, ioDispatcher = Dispatchers.Unconfined)
        try {
            controller.start(90, "session-1")
            val skipped = store.current().timerId
            startedServiceActions()

            controller.stop()

            assertEquals(listOf(skipped), stopsSent())
        } finally {
            controller.stop()
        }
    }

    @Test
    fun anAlarmAlreadyOnItsWayAfterASkipIsNotAFinish() {
        // The Skip empties the store at once and clears the disk row a moment later; an alarm
        // that fired in between reads the row and asks to complete a rest the owner skipped.
        var n = 0
        val sequential = IdFactory { "timer-${++n}" }
        val store = RestTimerStore(ids = sequential)
        val controller = RestTimerController(context = context, store = store, ioDispatcher = Dispatchers.Unconfined)
        val published = mutableListOf<String>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            controller.start(90, "session-1")
            controller.adjust(15)
            assertEquals("timer-2", store.current().timerId)

            controller.stop()
            // The lock glance reads this flow; "rest done" must not flash, even for a frame.
            scope.launch { controller.lastCompletedTimerId.collect { id -> id?.let { published += it } } }

            assertFalse("the skipped rest does not finish", controller.completeIfCurrent("timer-2", fromService = true))
            assertFalse("nor the one the +15 replaced", controller.completeIfCurrent("timer-1", fromService = true))
            assertEquals(emptyList<String>(), published)
            assertNull(controller.lastCompletedTimerId.value)
        } finally {
            scope.cancel()
            controller.stop()
        }
    }

    @Test
    fun aSkipLandingAsTheRestFinishesLeavesItSkipped() {
        // The Skip lands after the completion's check, the moment it publishes "rest done"
        // (seen inline by a collector on Unconfined). The rest was skipped: nothing finishes.
        val store = RestTimerStore()
        val controller = RestTimerController(context = context, store = store, ioDispatcher = Dispatchers.Unconfined)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            controller.start(90, "session-1")
            val timerId = store.current().timerId
            scope.launch { controller.lastCompletedTimerId.collect { if (it == timerId) controller.stop() } }

            val finished = controller.completeIfCurrent(timerId, fromService = true)

            assertFalse("no Rest done for a skipped rest", finished)
            assertFalse(store.current().running)
            assertNull(controller.lastCompletedTimerId.value)
        } finally {
            scope.cancel()
            controller.stop()
        }
    }

    @Test
    fun aRecoveryNeverBringsBackARestThisProcessSkipped() {
        // A Skip whose row clear did not land (a refused commit), then a recovery in the same
        // process (the exact-alarm permission changing, say): the skipped rest stays skipped.
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            controller.start(90, "session-1")
            val row = checkNotNull(persistence.saved)
            controller.stop()
            persistence.saved = row

            assertFalse(controller.rehydrate())

            assertFalse("the skipped rest is not running again", store.current().running)
            assertNull("its row is cleared again", persistence.saved)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun aRecoveryNeverAnnouncesARestThisProcessSkipped() {
        // As above, with the skipped rest's time already up: nothing says Rest done for it, and
        // its row goes, so a later process death cannot announce it either.
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            controller.start(90, "session-1")
            val skipped = store.current().timerId
            controller.stop()
            val now = SystemClock.elapsedRealtime()
            persistence.saved = RestTimerRehydrator.toPersisted(
                endsAtElapsedRealtime = now - 1_000L,
                totalSeconds = 90,
                sessionId = "session-1",
                timerId = skipped,
            )

            assertFalse(controller.rehydrate())

            assertNull("its row is cleared, not left for a later process to announce", persistence.saved)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun aRestThatRanOutWhileTheProcessWasDeadStillFinishes() {
        // A process started after death holds nothing; the alarm (or rehydration) completes the
        // rest from the disk row, and it must still say Rest done.
        val store = RestTimerStore()
        val controller = RestTimerController(context = context, store = store, ioDispatcher = Dispatchers.Unconfined)
        try {
            assertTrue(controller.completeIfCurrent("timer-from-disk", fromService = true))
            assertEquals("timer-from-disk", controller.lastCompletedTimerId.value)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun completeIfCurrentPublishesTheIdBeforeTheStoreClears() {
        val store = RestTimerStore()
        val controller = RestTimerController(context, store)
        val order = mutableListOf<String>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            controller.start(90, "session-1")
            val timerId = store.current().timerId
            assertTrue(timerId.isNotBlank())
            assertNull(controller.lastCompletedTimerId.value)
            // The lock glance collects the two separately; both are seen inline here.
            scope.launch { controller.lastCompletedTimerId.collect { if (it == timerId) order += "published" } }
            scope.launch { store.snapshot.collect { if (!it.running) order += "cleared" } }

            assertTrue(controller.completeIfCurrent(timerId, fromService = true))
            assertEquals(listOf("published", "cleared"), order)
            assertFalse(store.current().running)
            assertEquals(timerId, controller.lastCompletedTimerId.value)
            assertTrue(
                RestFinishFlash.lockShowsFinished(
                    running = store.current().running,
                    finishedLaunch = false,
                    completedTimerId = controller.lastCompletedTimerId.value,
                ),
            )
        } finally {
            scope.cancel()
            controller.stop()
        }
    }

    @Test
    fun skipClearsAPriorCompletionSoTheLockDoesNotStayFinished() {
        val store = RestTimerStore()
        val controller = RestTimerController(context, store)
        try {
            controller.start(90, "session-1")
            val first = store.current().timerId
            assertTrue(controller.completeIfCurrent(first, fromService = true))
            assertEquals(first, controller.lastCompletedTimerId.value)

            controller.start(90, "session-1")
            assertNull(controller.lastCompletedTimerId.value)
            controller.stop()
            assertFalse(store.current().running)
            assertNull(controller.lastCompletedTimerId.value)
            assertFalse(
                RestFinishFlash.lockShowsFinished(
                    running = store.current().running,
                    finishedLaunch = false,
                    completedTimerId = controller.lastCompletedTimerId.value,
                ),
            )
            assertTrue(
                RestFinishFlash.lockShouldDismiss(
                    running = store.current().running,
                    finishedLaunch = false,
                    completedTimerId = controller.lastCompletedTimerId.value,
                ),
            )
        } finally {
            controller.stop()
        }
    }

    @Test
    fun completeIfCurrentLeavesANewerTimerAlone() {
        var n = 0
        val store = RestTimerStore(ids = { "timer-${++n}" })
        val controller = RestTimerController(context, store)
        try {
            controller.start(90, "session-1")
            val first = store.current().timerId
            controller.adjust(15)
            val second = store.current().timerId
            assertTrue(store.current().running)
            assertTrue(first != second)

            assertFalse(controller.completeIfCurrent(first, fromService = true))
            assertTrue(store.current().running)
            assertEquals(second, store.current().timerId)
            assertNull(controller.lastCompletedTimerId.value)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun everyMutationWritesThroughToPersistence() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            controller.start(90, "s1")
            val saved = checkNotNull(persistence.saved)
            assertEquals(90, saved.totalSeconds)
            assertEquals("s1", saved.sessionId)
            assertTrue(saved.endsAtElapsedRealtime > 0L)

            controller.stop()
            assertEquals(1, persistence.cleared)
            assertNull(persistence.saved)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun persistenceWritesTheTimerId() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val store = RestTimerStore(ids = { "timer-persist" })
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            controller.start(90, "s1")
            assertEquals("timer-persist", persistence.saved?.timerId)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun alarmIsNeverArmedBeforeTheRowIsWritten() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            controller.start(90, "session-1")
            assertEquals(listOf("save", "arm"), events.filter { it == "save" || it == "arm" })
        } finally {
            controller.stop()
        }
    }

    @Test
    fun startPublishesTheSnapshotBeforePersistReturns() {
        val allowSave = CountDownLatch(1)
        val inSave = CountDownLatch(1)
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val persistence = object : RestTimerStatePersistence {
            @Volatile var saved: PersistedRestTimer? = null
            override fun save(state: PersistedRestTimer): Boolean {
                inSave.countDown()
                check(allowSave.await(3, TimeUnit.SECONDS))
                events += "save"
                saved = state
                return true
            }
            override fun load(): PersistedRestTimer? = saved
            override fun clear(): Boolean {
                events += "clear"
                saved = null
                return true
            }
        }
        val capability = EventCapability(events, alarmManager)
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, capability),
            ioDispatcher = Dispatchers.IO,
        )
        try {
            controller.start(90, "session-1")
            assertTrue(store.current().running)
            assertTrue(inSave.await(3, TimeUnit.SECONDS))
            assertTrue(events.none { it == "arm" })
            allowSave.countDown()
            val deadline = System.currentTimeMillis() + 3_000
            while (!events.contains("arm") && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }
            assertTrue(events.contains("arm"))
            assertEquals("save", events.first { it == "save" || it == "arm" })
        } finally {
            allowSave.countDown()
            controller.stop()
        }
    }

    @Test
    fun haltCancelsTheAlarmBeforeTheClearJobRuns() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            controller.start(90, "session-1")
            events.clear()
            controller.stop()
            assertFalse(store.current().running)
            val cancelAt = events.indexOf("cancel")
            val clearAt = events.indexOf("clear")
            assertTrue(cancelAt >= 0)
            assertTrue(clearAt >= 0)
            assertTrue(cancelAt < clearAt)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun aRowThatDidNotCommitLeavesTheWakeupUnarmed() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            controller.start(90, "session-1")
            assertEquals(AlarmScheduleResult.EXACT, controller.lastAlarmSchedule.value)
            assertTrue(controller.persistenceHealthy.value)
            events.clear()

            persistence.saveResult = false
            controller.adjust(15)
            assertTrue(store.current().running)
            assertEquals(listOf("save"), events.filter { it == "save" || it == "arm" })
            assertEquals(AlarmScheduleResult.FAILED, controller.lastAlarmSchedule.value)
            assertFalse(controller.persistenceHealthy.value)
            assertTrue(logged.any { it == "${AppLog.WARN} PT/RestTimer" })
        } finally {
            controller.stop()
        }
    }

    @Test
    fun theNextCommitThatLandsArmsAgainAndClearsTheFlag() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            persistence.saveResult = false
            controller.start(90, "session-1")
            assertFalse(controller.persistenceHealthy.value)
            assertTrue(events.none { it == "arm" })

            persistence.saveResult = true
            events.clear()
            controller.adjust(15)
            assertEquals(listOf("save", "arm"), events.filter { it == "save" || it == "arm" })
            assertEquals(AlarmScheduleResult.EXACT, controller.lastAlarmSchedule.value)
            assertTrue(controller.persistenceHealthy.value)
            assertEquals(store.current().timerId, persistence.saved?.timerId)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun refreshingCapabilityWhileUnhealthyRetriesTheRowBeforeArming() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            persistence.saveResult = false
            controller.start(90, "session-1")
            assertFalse(controller.persistenceHealthy.value)

            persistence.saveResult = true
            events.clear()
            controller.refreshAlarmCapability()
            assertEquals(listOf("save", "arm"), events.filter { it == "save" || it == "arm" })
            assertTrue(controller.persistenceHealthy.value)
        } finally {
            controller.stop()
        }
    }

    /**
     * Audit RT-4. A resume (or an exact-alarm grant) while a start's job is still queued armed
     * the wakeup from the store at once, before the row: a kill then left a wakeup with no row.
     */
    @Test
    fun aRefreshWhileTheStartsJobWaitsArmsNothingBeforeTheRow() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val io = StandardTestDispatcher()
        val controller = RestTimerController(
            context = context,
            store = RestTimerStore(),
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = io,
        )
        try {
            controller.start(90, "session-1")
            controller.refreshAlarmCapability()
            assertEquals("nothing armed before the row", emptyList<String>(), events.filter { it == "save" || it == "arm" })

            io.scheduler.advanceUntilIdle()
            assertEquals(listOf("save", "arm"), events.filter { it == "save" || it == "arm" })
        } finally {
            controller.stop()
        }
    }

    /** Audit RT-4. An early delivery re-arms the same way: never before the row. */
    @Test
    fun anEarlyDeliveryWhileTheStartsJobWaitsArmsNothingBeforeTheRow() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val io = StandardTestDispatcher()
        val controller = RestTimerController(
            context = context,
            store = RestTimerStore(),
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = io,
        )
        try {
            controller.start(90, "session-1")
            controller.rescheduleCurrent()
            assertEquals("nothing armed before the row", emptyList<String>(), events.filter { it == "save" || it == "arm" })

            io.scheduler.advanceUntilIdle()
            assertEquals(listOf("save", "arm"), events.filter { it == "save" || it == "arm" })
        } finally {
            controller.stop()
        }
    }

    /**
     * Audit RT-5. An early delivery re-armed even when the row had not been written, so after a
     * kill the wakeup found no row and the rest ended in silence. It now tries the row again and
     * arms only once it lands.
     */
    @Test
    fun anEarlyDeliveryWhileTheRowIsMissingArmsOnlyOnceTheRowLands() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val controller = RestTimerController(
            context = context,
            store = RestTimerStore(),
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            persistence.saveResult = false
            controller.start(90, "session-1")
            assertFalse(controller.persistenceHealthy.value)

            events.clear()
            controller.rescheduleCurrent()
            assertEquals("the row is tried again, nothing armed", listOf("save"), events.filter { it == "save" || it == "arm" })
            assertFalse(controller.persistenceHealthy.value)

            persistence.saveResult = true
            events.clear()
            controller.rescheduleCurrent()
            assertEquals(listOf("save", "arm"), events.filter { it == "save" || it == "arm" })
            assertTrue(controller.persistenceHealthy.value)
        } finally {
            controller.stop()
        }
    }

    /**
     * Audit RT-4. A job armed whatever rest ran when it finished, not the one whose row it wrote:
     * a rest started after that job's number, but before its own, got a wakeup over the older
     * rest's row. The older job now arms nothing (its rest no longer runs), and the newer rest's
     * own job arms it.
     */
    @Test
    fun anOlderJobNeverArmsANewerRestOverItsOwnRow() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val capability = EventCapability(events, alarmManager)
        val io = StandardTestDispatcher()
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, capability),
            ioDispatcher = io,
        )
        try {
            controller.start(90, "session-1")
            var rowAfterOlderJob: PersistedRestTimer? = null
            var armedByOlderJob: List<Long> = emptyList()
            controller.beforePersistNumberTaken = {
                controller.beforePersistNumberTaken = null
                // The newer rest is published and has no number yet: the older job runs now.
                io.scheduler.advanceUntilIdle()
                rowAfterOlderJob = persistence.saved
                armedByOlderJob = capability.armedFor.toList()
            }
            controller.start(60, "session-1")

            val olderRow = checkNotNull(rowAfterOlderJob) { "the older job wrote its row" }
            assertEquals(
                "the older job armed a wakeup though its rest (${olderRow.endsAtElapsedRealtime}) no longer runs",
                emptyList<Long>(),
                armedByOlderJob,
            )

            io.scheduler.advanceUntilIdle()
            val newer = store.current()
            assertEquals("the newer rest's row landed", newer.timerId, persistence.saved?.timerId)
            assertEquals("and its wakeup is armed", newer.endsAtElapsedRealtime, capability.armedFor.last())
        } finally {
            controller.beforePersistNumberTaken = null
            controller.stop()
        }
    }

    /**
     * The other side of RT-4. A job that runs after a Skip has ended its rest (the Skip's own job
     * not yet numbered) does not arm that rest.
     */
    @Test
    fun aJobDoesNotArmARestSkippedBeforeItRuns() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val io = StandardTestDispatcher()
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = io,
        )
        try {
            controller.start(90, "session-1")
            val rest = store.current().timerId
            controller.beforePersistNumberTaken = {
                controller.beforePersistNumberTaken = null
                // The Skip has emptied the store and dropped the wakeup; the rest's own job runs now.
                io.scheduler.advanceUntilIdle()
            }
            assertTrue(controller.stopIfCurrent(rest, fromService = true))
            io.scheduler.advanceUntilIdle()

            assertEquals("no wakeup armed for the skipped rest", emptyList<String>(), events.filter { it == "arm" })
            assertNull("and the row is cleared", persistence.saved)
        } finally {
            controller.beforePersistNumberTaken = null
            controller.stop()
        }
    }

    @Test
    fun aClearThatDoesNotCommitIsRetriedOnceAndLogged() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            controller.start(90, "session-1")
            persistence.clearResult = false
            events.clear()
            logged.clear()

            controller.stop()
            assertFalse(store.current().running)
            assertEquals(2, events.count { it == "clear" })
            assertEquals(0, persistence.cleared)
            assertFalse(controller.persistenceHealthy.value)
            assertTrue(logged.count { it == "${AppLog.WARN} PT/RestTimer" } >= 2)

            persistence.clearResult = true
            controller.stop()
            assertEquals(1, persistence.cleared)
            assertNull(persistence.saved)
            assertTrue(controller.persistenceHealthy.value)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun aPersistenceThatThrowsIsUnhealthyNotFatal() {
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            persistence.saveError = IllegalStateException("prefs file unwritable")
            controller.start(90, "session-1")
            assertTrue(store.current().running)
            assertTrue(events.none { it == "arm" })
            assertEquals(AlarmScheduleResult.FAILED, controller.lastAlarmSchedule.value)
            assertFalse(controller.persistenceHealthy.value)
            assertTrue(logged.any { it == "${AppLog.WARN} PT/RestTimer" })

            persistence.saveError = null
            persistence.clearError = IllegalStateException("prefs file unwritable")
            controller.stop()
            assertFalse(store.current().running)
            assertEquals(2, events.count { it == "clear" })
            assertFalse(controller.persistenceHealthy.value)
        } finally {
            persistence.clearError = null
            controller.stop()
        }
    }

    @Test
    fun aStartAndAnAdjustEachTellTheService() {
        // The card in the shade follows a rest only once the service is told: a start, and a
        // ±15 that leaves the rest running, each send one SYNC after the row lands.
        val events = mutableListOf<String>()
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = EventPersistence(events),
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            startedServiceIntents()
            controller.start(90, "session-1")
            assertEquals(listOf(RestTimerService.ACTION_SYNC), startedServiceActions())
            controller.adjust(15)
            assertEquals(listOf(RestTimerService.ACTION_SYNC), startedServiceActions())
        } finally {
            controller.stop()
        }
    }

    @Test
    fun aRestStartedAsTheLastOneFinishesStillReachesTheService() {
        // The owner logs a set as the last rest finishes on another thread: the next rest starts
        // after the finish emptied the store and before the finish queued its own disk job. That
        // job is the newest, so it decides what lands. The new rest must still reach the service,
        // or it counts down with no card in the shade.
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val capability = EventCapability(events, alarmManager)
        val io = StandardTestDispatcher()
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, capability),
            ioDispatcher = io,
        )
        try {
            controller.start(90, "session-1")
            io.scheduler.advanceUntilIdle()
            val finished = store.current().timerId
            startedServiceIntents()
            capability.onCancel = { controller.start(60, "session-1") }

            assertTrue(controller.completeIfCurrent(finished, fromService = true))
            val next = store.current()
            io.scheduler.advanceUntilIdle()

            assertTrue(next.running)
            assertEquals("the new rest's row landed", next.timerId, persistence.saved?.timerId)
            assertEquals("and its wakeup is armed", "arm", events.last { it == "arm" || it == "cancel" })
            assertEquals(
                "and the service was told about it, once",
                listOf(RestTimerService.ACTION_SYNC),
                startedServiceActions(),
            )
        } finally {
            capability.onCancel = null
            controller.stop()
        }
    }

    @Test
    fun aFinishThatTookItsNumberFirstNeverCancelsTheNextRestsSave() {
        // The finish takes its number, then the next set's rest starts in full before the finish
        // reads the store and queues its job. A finish that cancelled the job before its own
        // cancelled the start's, and its own then did nothing: no row, no wakeup, no card.
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val io = StandardTestDispatcher()
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = io,
        )
        try {
            controller.start(90, "session-1")
            io.scheduler.advanceUntilIdle()
            val finished = store.current().timerId
            startedServiceIntents()
            controller.afterPersistNumberTaken = {
                controller.afterPersistNumberTaken = null
                controller.start(60, "session-1")
            }

            assertTrue(controller.completeIfCurrent(finished, fromService = true))
            val next = store.current()
            io.scheduler.advanceUntilIdle()

            assertTrue(next.running)
            assertEquals("the new rest's row landed", next.timerId, persistence.saved?.timerId)
            assertEquals("and its wakeup is armed", "arm", events.last { it == "arm" || it == "cancel" })
            assertEquals("and the service was told, once", listOf(RestTimerService.ACTION_SYNC), startedServiceActions())
        } finally {
            controller.afterPersistNumberTaken = null
            controller.stop()
        }
    }

    @Test
    fun aFinishLandingInsideAnInAppSkipKeepsTheRestDone() {
        // W2b-3: the lock glance and the rest page end only the rest they show. A finish that
        // lands after the Skip has found its rest running, but before it clears it, empties the
        // store first. The Skip then ended nothing: it says so (the lock glance stays open on
        // "Back to the bar") and leaves the finish as it is. Before, the empty snapshot counted
        // as a skip and the glance closed.
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val io = StandardTestDispatcher()
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = io,
        )
        try {
            controller.start(90, "session-1")
            io.scheduler.advanceUntilIdle()
            val shown = store.current().timerId
            startedServiceIntents()
            controller.betweenSkipReadAndClear = {
                controller.betweenSkipReadAndClear = null
                assertTrue("the rest finishes inside the Skip", controller.completeIfCurrent(shown, fromService = true))
            }

            assertFalse("the Skip ended nothing", controller.skipIfShown(shown, fromService = false))
            io.scheduler.advanceUntilIdle()

            assertEquals("the rest stays done", shown, controller.lastCompletedTimerId.value)
            assertFalse(store.current().running)
            assertTrue("the Skip sent no stop", stopsSent().isEmpty())
        } finally {
            controller.betweenSkipReadAndClear = null
            controller.stop()
        }
    }

    @Test
    fun aRestStartedJustBeforeTheFinishTakesItsNumberStillLands() {
        // The finish reads the store after it takes its number. Read before, it would hold the
        // empty store while the next rest's start, landing just before the number, took an
        // older one: the finish's job, the newest, would then clear the row, and the new rest
        // would have none.
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val io = StandardTestDispatcher()
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = io,
        )
        try {
            controller.start(90, "session-1")
            io.scheduler.advanceUntilIdle()
            val finished = store.current().timerId
            startedServiceIntents()
            controller.beforePersistNumberTaken = {
                controller.beforePersistNumberTaken = null
                controller.start(60, "session-1")
            }

            assertTrue(controller.completeIfCurrent(finished, fromService = true))
            val next = store.current()
            io.scheduler.advanceUntilIdle()

            assertTrue(next.running)
            assertEquals("the new rest's row landed", next.timerId, persistence.saved?.timerId)
            assertEquals("and its wakeup is armed", "arm", events.last { it == "arm" || it == "cancel" })
            assertEquals("and the service was told, once", listOf(RestTimerService.ACTION_SYNC), startedServiceActions())
        } finally {
            controller.beforePersistNumberTaken = null
            controller.stop()
        }
    }

    @Test
    fun aStartOvertakenByALaterCallStillReachesTheService() {
        // The SYNC is owed before the call takes its number. A later call that takes a newer
        // number and runs its job first must find it owed, or the rest never reaches the shade.
        // On the phone that later call is a finish on another thread; a single-threaded test
        // cannot pause a finish there, so a disk retry (the exact-alarm permission changing)
        // stands in for it.
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            persistence.saveResult = false
            controller.start(90, "session-1")
            assertFalse(controller.persistenceHealthy.value)
            persistence.saveResult = true
            startedServiceIntents()
            controller.afterPersistNumberTaken = {
                controller.afterPersistNumberTaken = null
                controller.refreshAlarmCapability()
            }

            controller.adjust(15)

            assertEquals(store.current().timerId, persistence.saved?.timerId)
            assertEquals(listOf(RestTimerService.ACTION_SYNC), startedServiceActions())
        } finally {
            controller.afterPersistNumberTaken = null
            controller.stop()
        }
    }

    @Test
    fun aJobANewerCallOvertookBeforeItRanWritesNothing() {
        // Two queued jobs can start in either order on the IO pool. When a Skip's job runs first
        // and clears the row, the start's job, run after it, must not write the skipped rest
        // back: after the process dies it would come back as "Rest done". Nor may either job
        // tell the service about a rest after its STOP: only a job that writes a running rest
        // sends the SYNC it owes.
        val events = mutableListOf<String>()
        val persistence = EventPersistence(events)
        val io = NewestFirstDispatcher()
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, EventCapability(events, alarmManager)),
            ioDispatcher = io,
        )
        startedServiceIntents()
        controller.start(90, "session-1")
        controller.stop()
        io.runAll()

        assertNull("the skipped rest's row stays cleared", persistence.saved)
        assertEquals(listOf("clear"), events.filter { it == "save" || it == "clear" })
        assertEquals("nothing follows the STOP", listOf(RestTimerService.ACTION_STOP), startedServiceActions())
    }

    @Test
    fun aSaveThatANewerChangeOvertakesArmsNothing() {
        // A +15 lands while the start's row is being written. The start's job must then leave
        // the wakeup to the +15's job: arming now would arm the +15's rest before its row exists.
        val io = StandardTestDispatcher()
        val store = RestTimerStore()
        lateinit var controller: RestTimerController
        val persistence = object : RestTimerStatePersistence {
            @Volatile var saved: PersistedRestTimer? = null
            var overtake = true
            override fun save(state: PersistedRestTimer): Boolean {
                if (overtake) {
                    overtake = false
                    controller.adjust(15)
                }
                saved = state
                return true
            }
            override fun load(): PersistedRestTimer? = saved
            override fun clear(): Boolean {
                saved = null
                return true
            }
        }
        val armedWithoutRow = mutableListOf<String?>()
        val capability = object : ExactAlarmCapability {
            override val sdkInt: Int = 35
            override fun nowElapsedRealtime(): Long = SystemClock.elapsedRealtime()
            override fun alarmManagerOrNull(): AlarmManager? = alarmManager
            override fun canScheduleExactAlarms(): Boolean = true
            override fun setExactElapsed(triggerAtElapsed: Long, operation: PendingIntent) {
                val armed = shadowOf(operation).savedIntent.getStringExtra(RestTimerService.EXTRA_TIMER_ID)
                if (persistence.saved?.timerId != armed) armedWithoutRow += armed
            }
            override fun setInexactElapsed(triggerAtElapsed: Long, operation: PendingIntent) {
                setExactElapsed(triggerAtElapsed, operation)
            }
            override fun cancel(operation: PendingIntent) = Unit
        }
        controller = RestTimerController(
            context = context,
            store = store,
            persistence = persistence,
            alarms = RestTimerAlarmScheduler(context, capability),
            ioDispatcher = io,
        )
        try {
            controller.start(90, "session-1")
            io.scheduler.advanceUntilIdle()

            assertEquals("no wakeup armed ahead of its row", emptyList<String?>(), armedWithoutRow)
            assertEquals(store.current().timerId, persistence.saved?.timerId)
        } finally {
            controller.stop()
            io.scheduler.advanceUntilIdle()
        }
    }

    @Test
    fun aFinishOnAnotherThreadNeverCostsTheNextRestItsRowOrWakeup() {
        // A finish on another thread queued its disk job while the next rest's start queued its
        // own, and could cancel the start's job: the new rest counted down with no row and no
        // wakeup, and nothing would end it. aFinishThatTookItsNumberFirstNeverCancelsTheNextRestsSave
        // holds that through the seam; this runs the same race on real threads and the IO
        // dispatcher, as a smoke test that cannot promise to catch a cancel coming back.
        repeat(RACE_ROUNDS) { round ->
            val events = java.util.Collections.synchronizedList(mutableListOf<String>())
            val persistence = EventPersistence(events)
            val capability = EventCapability(events, alarmManager)
            val store = RestTimerStore()
            val controller = RestTimerController(
                context = context,
                store = store,
                persistence = persistence,
                alarms = RestTimerAlarmScheduler(context, capability),
                ioDispatcher = Dispatchers.IO,
            )
            try {
                controller.start(90, "session-1")
                assertTrue("round $round: the first rest settles", settles(store, persistence, events))
                val finished = store.current().timerId
                val emptied = CountDownLatch(1)
                capability.onCancel = { emptied.countDown() }

                val finisher = thread { controller.completeIfCurrent(finished, fromService = true) }
                assertTrue(emptied.await(3, TimeUnit.SECONDS))
                controller.start(60, "session-1")
                finisher.join(3_000)

                assertTrue(
                    "round $round: the next rest has its row and its wakeup",
                    settles(store, persistence, events),
                )
            } finally {
                capability.onCancel = null
                controller.stop()
            }
        }
    }

    /** Bounded wait for the running rest's row to be on disk with a wakeup armed after it. */
    private fun settles(store: RestTimerStore, persistence: EventPersistence, events: List<String>): Boolean {
        val giveUpAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (System.nanoTime() < giveUpAt) {
            val current = store.current()
            val landed = synchronized(events) {
                val lastSave = events.lastIndexOf("save")
                lastSave >= 0 && events.subList(lastSave, events.size).contains("arm")
            }
            if (current.running && persistence.saved?.timerId == current.timerId && landed) return true
            Thread.sleep(5)
        }
        return false
    }

    /** The actions sent to the rest service since the last call, in order. */
    private fun startedServiceActions(): List<String?> = startedServiceIntents().map { it.action }

    /** The rest each STOP sent since the last call names; null for a STOP that names none. */
    private fun stopsSent(): List<String?> = startedServiceIntents()
        .filter { it.action == RestTimerService.ACTION_STOP }
        .map { it.getStringExtra(RestTimerService.EXTRA_TIMER_ID) }

    /** The intents sent to the rest service since the last call, in order. */
    private fun startedServiceIntents(): List<Intent> {
        val shadow = shadowOf(context as Application)
        return generateSequence { shadow.nextStartedService }.toList()
    }

    /** Runs queued jobs newest first, the way two IO threads may pick them up. */
    private class NewestFirstDispatcher : CoroutineDispatcher() {
        private val queued = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queued.addLast(block)
        }
        fun runAll() {
            while (queued.isNotEmpty()) queued.removeLast().run()
        }
    }

    /**
     * A false result leaves the row as it was, the way a refused commit()
     * does; a set error throws before anything is recorded.
     */
    private class EventPersistence(
        private val events: MutableList<String>,
    ) : RestTimerStatePersistence {
        @Volatile var saved: PersistedRestTimer? = null
        var cleared = 0
        var saveResult = true
        var clearResult = true
        var saveError: Throwable? = null
        var clearError: Throwable? = null
        override fun save(state: PersistedRestTimer): Boolean {
            events += "save"
            saveError?.let { throw it }
            if (saveResult) saved = state
            return saveResult
        }
        override fun load(): PersistedRestTimer? = saved
        override fun clear(): Boolean {
            events += "clear"
            clearError?.let { throw it }
            if (clearResult) {
                saved = null
                cleared++
            }
            return clearResult
        }
    }

    private class EventCapability(
        private val events: MutableList<String>,
        private val alarmManager: AlarmManager,
    ) : ExactAlarmCapability {
        /** Runs once, on the thread that cancels the wakeup: a halt, after it emptied the store. */
        @Volatile var onCancel: (() -> Unit)? = null
        override val sdkInt: Int = 35
        override fun nowElapsedRealtime(): Long = 1_000L
        override fun alarmManagerOrNull(): AlarmManager? = alarmManager
        override fun canScheduleExactAlarms(): Boolean = true
        /** The deadline each wakeup was armed for, in order. */
        val armedFor = mutableListOf<Long>()
        override fun setExactElapsed(triggerAtElapsed: Long, operation: PendingIntent) {
            events += "arm"
            armedFor += triggerAtElapsed
        }
        override fun setInexactElapsed(triggerAtElapsed: Long, operation: PendingIntent) {
            events += "arm"
            armedFor += triggerAtElapsed
        }
        override fun cancel(operation: PendingIntent) {
            events += "cancel"
            onCancel?.let { hook ->
                onCancel = null
                hook()
            }
        }
    }

    private companion object {
        const val RACE_ROUNDS = 25
    }
}
