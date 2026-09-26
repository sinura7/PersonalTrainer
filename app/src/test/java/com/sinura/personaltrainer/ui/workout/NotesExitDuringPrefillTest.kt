package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.FinishedWorkingSetRow
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Notes typed while a lift is still loading, and then Back.
 *
 * Back flushes the notes (`persistDraftForExit`) and pops the screen, but the ViewModel lives
 * until the exit transition ends and nothing stops a prefill still reading history, so the
 * prefill can finish, and save the lift's draft, after the flush. That save reads the notes
 * when it runs, so it carries the last words. This holds that order and that answer, and
 * that a process death after it brings the last words back.
 *
 * ActiveWorkoutViewModelTest.notesWaitForTypingPauseAndExitFlushesImmediately could meet this
 * order by chance. It failed once in seven class runs during W2b-2, never in 60 runs since, and
 * what failed was a tear: a save on one of Room's threads read the notes before the typing and
 * wrote them after it, which a phone's single main thread cannot do. This holds the order a
 * phone can make, and its answer: the prefill waits inside its first history read until the flush has
 * landed, and the row the flush sends back waits at the door, so the prefill's save is the last
 * to land and nothing after it can cover an older copy with the newest notes.
 *
 * Built as ActiveWorkoutViewModelTest builds it: real in-memory Room, a real SavedStateHandle,
 * and an unconfined Main, so the ViewModel's code runs on whichever thread resumes it, Room's
 * included.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotesExitDuringPrefillTest {
    private lateinit var dispatcher: TestDispatcher
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    /** Armed, the next finished-history read says so on [historyEntered] and waits for [releaseHistory]. */
    private val holdHistory = AtomicBoolean(false)
    private val historyEntered = CompletableDeferred<Unit>()
    private val releaseHistory = CompletableDeferred<Unit>()

    /** While true, each session row the database sends waits here before any reader hears of it. */
    private val sessionPaused = MutableStateFlow(false)

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            workoutDaoDecorator = { real -> HeldDoors(real) },
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        releaseHistory.complete(Unit)
        sessionPaused.value = false
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        if (::deps.isInitialized) deps.restTimerController.stop()
        if (::dispatcher.isInitialized) dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun notesTypedWhileTheLiftIsStillLoadingSurviveAProcessDeath() = runBlocking {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        deps.workoutRepository.updateSessionNotes(sessionId, "seeded")
        // The same database, read past the held door.
        val unheld = WorkoutRepository(deps.database, deps.database.workoutDao())
        val handle = SavedStateHandle(mapOf("sessionId" to sessionId))
        val saved = SavedStateWorkoutDraft(handle)
        holdHistory.set(true)
        val vm = viewModel(handle)

        // The row is read, so the notes on disk are known, and the lift is still loading.
        vm.awaitState { it.notes == "seeded" }
        try {
            withTimeout(TestWaits.FLOW_MS) { historyEntered.await() }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError(
                "the prefill never reached its first history read; uiState was ${vm.uiState.value}",
                timedOut,
            )
        }
        // The session collector saves right after it shows the row's notes. Once that save has
        // landed, nothing that read the notes before the typing below is still on its way.
        pollUntil(what = "the session collector's save of the notes it read", read = saved::sessionNotes) {
            it == "seeded"
        }
        // Let in, the row the flush sends back would have the session collector save the newest
        // notes once more, perhaps after the prefill's save, hiding a stale one. It waits.
        sessionPaused.value = true

        vm.setNotes("typed while loading")
        vm.setNotes("leave now")
        assertEquals("each keystroke is kept for a process death, exit or not", "leave now", saved.sessionNotes())
        vm.persistDraftForExit()
        unheld.awaitSession(sessionId) { it.notes == "leave now" }

        // The exit came first.
        assertNull(
            "the lift is still loading, so it has no draft of its own yet",
            deps.workoutDraftCache.getLift(sessionId, FLOOR_LIFT_ID),
        )
        assertEquals("the exit kept the last words for a process death", "leave now", saved.sessionNotes())

        // Then the prefill finishes and saves the lift's draft, last.
        releaseHistory.complete(Unit)
        val late = checkNotNull(
            pollUntil(
                what = "the prefill's save of the lift's draft",
                read = { deps.workoutDraftCache.getLift(sessionId, FLOOR_LIFT_ID) },
            ) { it != null },
        )
        assertEquals("the prefill's late save carries the last words", "leave now", late.notes)
        assertEquals(
            "the draft the screen comes back to carries them too",
            "leave now",
            deps.workoutDraftCache.get(sessionId)?.notes,
        )

        // The process dies after that save, and nothing of this ViewModel runs on.
        vm.clearAndJoinForTest()
        viewModels.remove(vm)
        assertEquals("the saved state the process dies with", "leave now", saved.sessionNotes())
        deps.workoutDraftCache.clearAll()
        sessionPaused.value = false // the revived screen reads its row as usual
        val revived = viewModel(handle)
        val state = revived.awaitState { it.loadState == SessionLoadState.FOUND }
        assertEquals("the last words come back with the process", "leave now", state.notes)
        // A typing pause on the revived screen, then every write it started has landed. This
        // bites only once the revived screen has read its row (a write needs the notes on disk
        // known); an older note coming back on screen is caught just above either way.
        dispatcher.scheduler.advanceTimeBy(401)
        dispatcher.scheduler.runCurrent()
        revived.clearAndJoinForTest()
        assertEquals(
            "the revived screen writes nothing older over them",
            "leave now",
            deps.workoutRepository.getSession(sessionId)?.notes,
        )
    }

    private fun viewModel(handle: SavedStateHandle) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = handle,
        container = deps,
        undoTimeout = UndoTimeoutProvider { it.toLong() },
    ).also(viewModels::add)

    /** [read] until [done] accepts it, bounded; on a timeout, says [what] never came and what was read last. */
    private suspend fun <T> pollUntil(what: String, read: () -> T, done: (T) -> Boolean): T {
        var last = read()
        try {
            withTimeout(TestWaits.FLOW_MS) {
                while (!done(last)) {
                    delay(10)
                    last = read()
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("$what never came within ${TestWaits.FLOW_MS} ms; last read: $last", timedOut)
        }
        return last
    }

    /**
     * The real DAO with two doors a test can hold. Armed, the next finished-history read says so
     * and waits: the prefill reads that history three times (its hint, the last session, the
     * history before this one) and only then saves the lift's draft, so holding the first holds
     * the save. Paused, each session row waits before anyone hears of it (the PausableSessionDao
     * pattern in ActiveWorkoutViewModelTest).
     */
    private inner class HeldDoors(private val real: WorkoutDao) : WorkoutDao by real {
        override suspend fun finishedWorkingSetsForExercises(
            exerciseIds: List<String>,
        ): List<FinishedWorkingSetRow> {
            if (holdHistory.compareAndSet(true, false)) {
                historyEntered.complete(Unit)
                releaseHistory.await()
            }
            return real.finishedWorkingSetsForExercises(exerciseIds)
        }

        override fun observeSession(id: String): Flow<SessionWithDetails?> =
            real.observeSession(id).onEach { sessionPaused.first { paused -> !paused } }
    }
}
