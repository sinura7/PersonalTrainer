package com.sinura.personaltrainer.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestFinishFlash
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The gold flash and lock glance key on a completion id, not on running
 * going false. Skip must not look finished. Persist and arm are one
 * ordered IO job: the snapshot is live before disk, and the alarm is
 * never scheduled before the row is durable.
 */
@RunWith(RobolectricTestRunner::class)
class RestTimerControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    @Test
    fun completeIfCurrentPublishesTheIdBeforeTheStoreClears() {
        val store = RestTimerStore()
        val controller = RestTimerController(context, store)
        try {
            controller.start(90, "session-1")
            val timerId = store.current().timerId
            assertTrue(timerId.isNotBlank())
            assertNull(controller.lastCompletedTimerId.value)

            assertTrue(controller.completeIfCurrent(timerId, fromService = true))
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
            override fun save(state: PersistedRestTimer) {
                inSave.countDown()
                check(allowSave.await(3, TimeUnit.SECONDS))
                events += "save"
                saved = state
            }
            override fun load(): PersistedRestTimer? = saved
            override fun clear() {
                events += "clear"
                saved = null
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

    private class EventPersistence(
        private val events: MutableList<String>,
    ) : RestTimerStatePersistence {
        var saved: PersistedRestTimer? = null
        var cleared = 0
        override fun save(state: PersistedRestTimer) {
            events += "save"
            saved = state
        }
        override fun load(): PersistedRestTimer? = saved
        override fun clear() {
            events += "clear"
            saved = null
            cleared++
        }
    }

    private class EventCapability(
        private val events: MutableList<String>,
        private val alarmManager: AlarmManager,
    ) : ExactAlarmCapability {
        override val sdkInt: Int = 35
        override fun nowElapsedRealtime(): Long = 1_000L
        override fun alarmManagerOrNull(): AlarmManager? = alarmManager
        override fun canScheduleExactAlarms(): Boolean = true
        override fun setExactElapsed(triggerAtElapsed: Long, operation: PendingIntent) {
            events += "arm"
        }
        override fun setInexactElapsed(triggerAtElapsed: Long, operation: PendingIntent) {
            events += "arm"
        }
        override fun cancel(operation: PendingIntent) {
            events += "cancel"
        }
    }
}
