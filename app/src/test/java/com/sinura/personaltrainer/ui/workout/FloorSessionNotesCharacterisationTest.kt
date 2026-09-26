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
import java.util.concurrent.atomic.AtomicInteger
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The session notes' writes, held where no other test holds them.
 *
 * ActiveWorkoutViewModelTest's notes test now starts from stored notes, so it no longer shows
 * that a typing pause saves into a session that had none, and it polls the draft cache after
 * Back where it read it at once; a process killed while a lift loads, with saved state the only
 * copy of the last words, was held nowhere; and the typing pause was checked by reading the row,
 * which a write racing that read could satisfy. Each is held here, counted at the DAO where it
 * matters.
 *
 * Built as NotesExitDuringPrefillTest builds it. Where a wait rests on the order the unconfined
 * test Main runs queued work in, rather than on a contract, the comment says "in practice": the
 * prefill's draft save comes several Room round trips after the session collector's first
 * callback, which records the notes on disk in the same step as it shows them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FloorSessionNotesCharacterisationTest {
    private lateinit var dispatcher: TestDispatcher
    private lateinit var deps: FakeAppDependencies
    private lateinit var unheld: WorkoutRepository
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    /** Armed, the next finished-history read says so on [historyEntered] and waits for [releaseHistory]. */
    private val holdHistory = AtomicBoolean(false)
    private val historyEntered = CompletableDeferred<Unit>()
    private val releaseHistory = CompletableDeferred<Unit>()

    /** While true, each session row the database sends waits here before any reader hears of it. */
    private val sessionPaused = MutableStateFlow(false)

    /** Every notes write that reached the DAO. */
    private val notesWrites = AtomicInteger(0)

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            workoutDaoDecorator = { real -> Doors(real) },
        )
        // The same database, read past the held doors.
        unheld = WorkoutRepository(deps.database, deps.database.workoutDao())
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
    fun aTypingPauseSavesNotesIntoASessionThatHadNone() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        val vm = viewModel(SavedStateHandle(mapOf("sessionId" to sessionId)))
        // In practice: the prefill's save lands after the session collector has recorded the
        // (empty) notes on disk, so a pause may write.
        awaitPrefillSave(sessionId)

        vm.setNotes("typed")
        dispatcher.scheduler.advanceTimeBy(401)
        dispatcher.scheduler.runCurrent()

        assertEquals(
            "a typing pause saves into a session that had no notes",
            "typed",
            unheld.awaitSession(sessionId) { it.notes == "typed" }.notes,
        )
    }

    @Test
    fun theFirstWriteIsAttemptedAtTheTypingPauseAndNotBefore() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        deps.workoutRepository.updateSessionNotes(sessionId, "seeded")
        val vm = viewModel(SavedStateHandle(mapOf("sessionId" to sessionId)))
        vm.awaitState { it.notes == "seeded" }
        awaitPrefillSave(sessionId)
        notesWrites.set(0)

        vm.setNotes("first")
        dispatcher.scheduler.advanceTimeBy(399)
        assertEquals("no notes write before the typing pause", 0, notesWrites.get())
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()
        // The repository calls the DAO before its first suspension, and the unconfined Main runs
        // the pause's write inline, so the count is exact the moment the clock reaches 400 ms.
        assertEquals("one notes write at the typing pause", 1, notesWrites.get())
        assertEquals(
            "and it carries the words typed",
            "first",
            unheld.awaitSession(sessionId) { it.notes == "first" }.notes,
        )
    }

    @Test
    fun backLeavesTheLastWordsInTheDraftCacheAtOnce() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        deps.workoutRepository.updateSessionNotes(sessionId, "seeded")
        val vm = viewModel(SavedStateHandle(mapOf("sessionId" to sessionId)))
        vm.awaitState { it.notes == "seeded" }
        awaitPrefillSave(sessionId) // in practice, no draft save is still on its way after this
        // And no row can have the session collector save again: what the cache holds when Back
        // returns is Back's own doing, read at once, where ActiveWorkoutViewModelTest polls.
        sessionPaused.value = true
        vm.setNotes("mid")
        dispatcher.scheduler.advanceTimeBy(401)
        dispatcher.scheduler.runCurrent()
        unheld.awaitSession(sessionId) { it.notes == "mid" }

        vm.setNotes("leave now")
        vm.persistDraftForExit()
        assertEquals(
            "the draft the screen comes back to, as Back returns",
            "leave now",
            deps.workoutDraftCache.get(sessionId)?.notes,
        )
        assertEquals(
            "the lift's own copy, as Back returns",
            "leave now",
            deps.workoutDraftCache.getLift(sessionId, FLOOR_LIFT_ID)?.notes,
        )
        unheld.awaitSession(sessionId) { it.notes == "leave now" }
        assertEquals("still, once the flush has landed", "leave now", deps.workoutDraftCache.get(sessionId)?.notes)
    }

    @Test
    fun aProcessKilledWhileTheLiftLoadsBringsTheTypedWordsBackOverTheOlderRow() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        deps.workoutRepository.updateSessionNotes(sessionId, "seeded")
        val handle = SavedStateHandle(mapOf("sessionId" to sessionId))
        val saved = SavedStateWorkoutDraft(handle)
        holdHistory.set(true)
        val vm = viewModel(handle)
        vm.awaitState { it.notes == "seeded" }
        awaitHistoryEntered(vm)
        // The session collector saves right after it shows the row's notes. Once that save has
        // landed, nothing that read the notes before the typing below is still on its way.
        pollUntil(what = "the session collector's save of the notes it read", read = saved::sessionNotes) {
            it == "seeded"
        }

        vm.setNotes("typed while loading")
        vm.setNotes("leave now")
        // Killed in a pocket: no Back, no typing pause. The parked prefill goes with the scope.
        vm.clearAndJoinForTest()
        viewModels.remove(vm)
        assertEquals("the row still holds the older words", "seeded", unheld.getSession(sessionId)?.notes)
        assertEquals("saved state holds the last words", "leave now", saved.sessionNotes())

        deps.workoutDraftCache.clearAll()
        sessionPaused.value = true // the revived screen has not read its row yet
        val revived = viewModel(handle)
        revived.awaitState { it.notes == "leave now" } // from saved state alone
        sessionPaused.value = false
        revived.awaitState { it.loadState == SessionLoadState.FOUND }
        // In practice: the revived prefill's save comes after its session collector has read
        // the older row, so the one-time fill from the row has had its chance.
        awaitPrefillSave(sessionId)
        assertEquals("the older row does not replace the typed words", "leave now", revived.uiState.value.notes)

        dispatcher.scheduler.advanceTimeBy(401)
        dispatcher.scheduler.runCurrent()
        assertEquals(
            "the revived screen writes the last words over the older row",
            "leave now",
            unheld.awaitSession(sessionId) { it.notes == "leave now" }.notes,
        )
    }

    private fun viewModel(handle: SavedStateHandle) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = handle,
        container = deps,
        undoTimeout = UndoTimeoutProvider { it.toLong() },
    ).also(viewModels::add)

    private suspend fun awaitPrefillSave(sessionId: String) {
        pollUntil(
            what = "the prefill's save of the lift's draft",
            read = { deps.workoutDraftCache.getLift(sessionId, FLOOR_LIFT_ID) },
        ) { it != null }
    }

    private suspend fun awaitHistoryEntered(vm: ActiveWorkoutViewModel) {
        try {
            withTimeout(TestWaits.FLOW_MS) { historyEntered.await() }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError(
                "the prefill never reached its first history read; uiState was ${vm.uiState.value}",
                timedOut,
            )
        }
    }

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

    /** The real DAO with doors a test can hold, and a count of the notes writes that reach it. */
    private inner class Doors(private val real: WorkoutDao) : WorkoutDao by real {
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

        override suspend fun updateSessionNotes(id: String, notes: String) {
            notesWrites.incrementAndGet()
            real.updateSessionNotes(id, notes)
        }
    }
}
