package com.sinura.personaltrainer.timer

import android.app.NotificationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.PersonalTrainerApp
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A rest that outlived its process comes back at the next start: still running, or already
 * finished. Neither is a rest this process held, so W2b-1b's "never bring back a skipped rest"
 * must leave both alone.
 *
 * Each test drives its own controller over a row kept in memory. The app's own controller runs
 * a recovery at start-up that clears the shared row on its own thread, and a row planted there
 * could be wiped before the test reads it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class)
class RestTimerRecoveryTest {
    private lateinit var app: PersonalTrainerApp
    private val persistence = RowInMemory()
    private lateinit var controller: RestTimerController

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        RestTimerCompletion.reset()
        // Well past zero, so a rest that "already ran out" has a positive deadline.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10))
        controller = RestTimerController(
            context = app,
            store = RestTimerStore(),
            persistence = persistence,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        RestTimerCompletion.reset()
        controller.stop(fromService = true)
        app.container.restTimerController.stop(fromService = true)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun aRestRestoredAfterProcessDeathStillFinishes() {
        val now = SystemClock.elapsedRealtime()
        persistence.saved = RestTimerRehydrator.toPersisted(
            endsAtElapsedRealtime = now + 30_000L,
            totalSeconds = 90,
            sessionId = "session-1",
            timerId = "timer-restored",
        )

        assertTrue("the rest runs again", controller.rehydrate())
        assertTrue(controller.completeIfCurrent("timer-restored", fromService = true))

        assertEquals("timer-restored", controller.lastCompletedTimerId.value)
    }

    @Test
    fun aRestThatRanOutWhileTheProcessWasDeadSaysRestDoneOnRecovery() {
        persistence.saved = RestTimerRehydrator.toPersisted(
            endsAtElapsedRealtime = SystemClock.elapsedRealtime() - 1_000L,
            totalSeconds = 90,
            sessionId = "session-1",
            timerId = "timer-from-a-dead-process",
        )

        assertFalse(controller.rehydrate())
        // Recovery must not mark the rest as one this process held: on the phone one controller
        // both recovers it and takes its finish.
        assertTrue(
            "the controller that recovered it still takes its finish",
            controller.completeIfCurrent("timer-from-a-dead-process", fromService = true),
        )
        // The expired rest completes through RestTimerCompletion on a real thread: the app's
        // controller marks it done, the cue plays, then Rest done is posted. Wait for all of it.
        val giveUpAt = System.nanoTime() + COMPLETION_WAIT_NANOS
        while (!restDoneShown() && System.nanoTime() < giveUpAt) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }

        assertTrue("Rest done is posted", restDoneShown())
        assertEquals("timer-from-a-dead-process", app.container.restTimerController.lastCompletedTimerId.value)
    }

    private fun restDoneShown(): Boolean = app.getSystemService(NotificationManager::class.java)
        .activeNotifications.any { it.id == RestTimerNotifications.DONE_ID }

    /** The row this test's controller reads and writes, kept off the app's shared preferences. */
    private class RowInMemory : RestTimerStatePersistence {
        @Volatile var saved: PersistedRestTimer? = null
        override fun save(state: PersistedRestTimer): Boolean {
            saved = state
            return true
        }
        override fun load(): PersistedRestTimer? = saved
        override fun clear(): Boolean {
            saved = null
            return true
        }
    }

    private companion object {
        const val COMPLETION_WAIT_NANOS = 5_000_000_000L
    }
}
