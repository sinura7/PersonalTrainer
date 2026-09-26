package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.FinishedWorkingSetRow
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A workout left and reopened in the same process: the new screen keeps the newest notes and
 * shows only the selected lift's own numbers (N1).
 *
 * Reopening from Home builds a new ActiveWorkoutViewModel on a fresh SavedStateHandle, and the
 * draft cache it reads is the process's own, still holding what the last screen staged. The
 * cache keeps one entry per lift, and each entry carries a copy of the session's notes taken
 * when that lift was last written, so a lift that was not selected while the notes grew keeps
 * the older words. The restore used to reach such a copy two ways:
 *  - the selected lift had no entry of its own (it was picked, and the screen was left before
 *    its reads finished), and the cache handed back another lift's entry instead: its notes,
 *    and its typed numbers marked as typed (NB1, NB2, NB4);
 *  - the lift the restore selected was an orphan: a swap leaves the replaced lift's entry in the
 *    cache, and once the workout has no lifts left the restore selects the first entry it holds
 *    (NB3).
 * Either way the field showed the older notes, the one-time hydrate kept a field that was not
 * empty, and 400 ms later the debounce wrote them over the newer notes in the database.
 *
 * Notes are now kept once per session in the cache. The rest holds what that must keep: words
 * only saved state had after a process death, when the rebuilt screen is left while it is still
 * loading (NB5); notes cleared, on a lift with its own entry or one still loading (NB6, NB7);
 * and words typed with no lift selected, left by an exit that does not flush (NB8).
 *
 * Built as ActiveWorkoutViewModelTest builds it: real in-memory Room, the ViewModel's scope
 * inline on the test thread, and the workout DAO wrapped so that a lift's history read can be
 * held (the prefill is then still running, as it is when the screen is left mid-load) and every
 * notes write is recorded. Back is [ActiveWorkoutViewModel.persistDraftForExit] then the
 * ViewModel's end. An exit that does not flush — the task swiped from Recents while the rest
 * timer keeps the process alive — is the ViewModel's end alone. The cache is never cleared,
 * because the process lives on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReopenMidPrefillTest {
    private lateinit var dispatcher: TestDispatcher
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    /** Every notes write that reached the DAO, in order. */
    private val notesWrites = CopyOnWriteArrayList<String>()

    /** While set, a history read signals [historyEntered] and waits for [releaseHistory]. */
    private val holdHistory = AtomicBoolean(false)
    private val historyEntered = CompletableDeferred<Unit>()
    private val releaseHistory = CompletableDeferred<Unit>()

    /** Checked when a session read starts: while set, that read never answers (a slow cold start). */
    private val holdSession = AtomicBoolean(false)

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            workoutDaoDecorator = { real -> RecordedNotesHeldHistory(real) },
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        holdSession.set(false)
        releaseHistory.complete(Unit)
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun aWorkoutReopenedWhileItsLiftWasStillLoadingKeepsTheNewestNotes() = runBlocking {
        val sessionId = seedWorkout(THREE_LIFTS)
        leave(pressStillLoading(sessionId, typedSquatKg = null))
        releaseTheHeldPrefill(sessionId)

        val mark = notesWrites.size
        val state = viewModel(sessionId).awaitState {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == PRESS &&
                it.liftReadiness.allowsCommit() && !it.entryLocked && it.notes.isNotEmpty()
        }
        assertEquals(
            "the reopened workout shows the newest notes, not the older copy another lift kept",
            NEWER_NOTES,
            state.notes,
        )
        pauseTyping()
        assertNoOlderNotesWrittenSince(mark)
        assertEquals(
            "the database still holds the newest notes",
            NEWER_NOTES,
            storedNotes(sessionId),
        )
    }

    @Test
    fun aReopenedWorkoutNeverShowsAnotherLiftsTypedNumbers() = runBlocking {
        val sessionId = seedWorkout(THREE_LIFTS)
        leave(pressStillLoading(sessionId, typedSquatKg = TYPED_SQUAT_KG))
        releaseTheHeldPrefill(sessionId)

        // Settled on a filled entry: the press's own prefill or, the defect, the squat's entry
        // restored onto it. The empty well reads 0 kg until one of them lands, and readiness
        // joins uiState stages after the draft does, so "ready" alone can be seen a beat before
        // the prefilled weight.
        val state = viewModel(sessionId).awaitState {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == PRESS &&
                it.liftReadiness.allowsCommit() && !it.entryLocked && it.draft.weightKg > 0.0
        }
        assertEquals(
            "the press shows its own suggestion, not the weight typed on the squat",
            PRESS_KG,
            state.draft.weightKg,
            WEIGHT_TOLERANCE,
        )
        assertFalse(
            "the press is not marked as typed with numbers nobody typed on it; state=$state",
            state.draftDirty,
        )
    }

    @Test
    fun aReopenedWorkoutNeverTakesItsNotesFromAnOrphanedLiftsCopy() = runBlocking {
        val sessionId = seedWorkout(listOf(SQUAT_PLAN))
        insertExercise(PRESS, "Press")
        val press = checkNotNull(deps.exerciseRepository.getById(PRESS)) { "the press is not stored" }
        val vm = viewModel(sessionId)
        vm.awaitSettledOn(SQUAT, SQUAT_KG)
        vm.setNotes(OLDER_NOTES)

        // Swap the squat for the press. The squat's entry, with the older words, stays behind.
        vm.requestSwap()
        vm.addExercise(press)
        deps.workoutRepository.awaitSession(sessionId) {
            it.exercises.singleOrNull()?.exercise?.id == PRESS
        }
        vm.awaitState {
            it.session?.exercises?.singleOrNull()?.exercise?.id == PRESS &&
                it.selectedExerciseId == PRESS && it.liftReadiness.allowsCommit() && !it.entryLocked
        }
        vm.setNotes(NEWER_NOTES)
        pauseTyping()
        deps.workoutRepository.awaitSession(sessionId) { it.notes == NEWER_NOTES }
        assertEquals(
            "precondition: the swapped-out squat's entry is still cached with the older notes",
            OLDER_NOTES,
            deps.workoutDraftCache.getLift(sessionId, SQUAT)?.notes,
        )

        // Remove the press: the workout has no lifts, so nothing is selected when Back is pressed.
        vm.removeSelectedLift()
        deps.workoutRepository.awaitSession(sessionId) { it.exercises.isEmpty() }
        vm.awaitState {
            it.session?.exercises?.isEmpty() == true && it.selectedExerciseId == null && !it.entryLocked
        }
        leave(vm)

        val mark = notesWrites.size
        val state = viewModel(sessionId).awaitState {
            it.loadState == SessionLoadState.FOUND && it.session?.exercises?.isEmpty() == true &&
                !it.entryLocked && it.notes.isNotEmpty()
        }
        assertEquals(
            "the reopened workout shows the newest notes, not the orphaned squat's older copy",
            NEWER_NOTES,
            state.notes,
        )
        pauseTyping()
        assertNoOlderNotesWrittenSince(mark)
        assertEquals(
            "the database still holds the newest notes",
            NEWER_NOTES,
            storedNotes(sessionId),
        )
    }

    @Test
    fun notesTypedOnALiftStillLoadingSurviveAnExitThatDoesNotFlush() = runBlocking {
        val sessionId = seedWorkout(THREE_LIFTS)
        val vm = pressStillLoading(sessionId, typedSquatKg = null)
        // Typed while the press has no entry yet, and the screen ends inside the typing pause.
        vm.setNotes(NEWEST_NOTES)
        end(vm)
        releaseTheHeldPrefill(sessionId)
        assertEquals(
            "precondition: the words typed on the press never reached the database",
            NEWER_NOTES,
            storedNotes(sessionId),
        )

        val mark = notesWrites.size
        val state = viewModel(sessionId).awaitState {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == PRESS &&
                it.liftReadiness.allowsCommit() && !it.entryLocked && it.notes.isNotEmpty()
        }
        assertEquals(
            "the reopened workout shows the words typed while the press was loading",
            NEWEST_NOTES,
            state.notes,
        )
        pauseTyping()
        assertNoOlderNotesWrittenSince(mark)
        val written = deps.workoutRepository.awaitSession(sessionId) { it.notes == NEWEST_NOTES }
        assertEquals(
            "the words typed while the press was loading reach the database",
            NEWEST_NOTES,
            written.notes,
        )
    }

    @Test
    fun notesOnlySavedStateHeldSurviveAScreenLeftWhileItWasStillLoading() = runBlocking {
        val sessionId = seedWorkout(listOf(SQUAT_PLAN))
        val saved = SavedStateHandle(mapOf("sessionId" to sessionId))
        val first = viewModel(sessionId, saved)
        first.awaitSettledOn(SQUAT, SQUAT_KG)
        first.setNotes(OLDER_NOTES)
        pauseTyping()
        deps.workoutRepository.awaitSession(sessionId) { it.notes == OLDER_NOTES }
        first.setNotes(NEWER_NOTES)
        assertEquals(
            "precondition: saved state holds the newer notes",
            NEWER_NOTES,
            SavedStateWorkoutDraft(saved).sessionNotes(),
        )
        // The process dies inside the typing pause: no flush, the cache goes, saved state stays.
        end(first)
        deps.workoutDraftCache.clearAll()
        assertEquals("precondition: the database still has the older notes", OLDER_NOTES, storedNotes(sessionId))

        // The screen comes back on the saved handle, its session read is slow, and Back while it
        // is still loading pops it without a flush.
        holdSession.set(true)
        val rebuilt = viewModel(sessionId, saved)
        assertEquals(
            "precondition: the rebuilt screen put the squat's entry back in the cache",
            setOf(SQUAT),
            deps.workoutDraftCache.all(sessionId).keys,
        )
        end(rebuilt)
        holdSession.set(false)

        // Reopened from Home in the same process: a fresh handle, the cache as the rebuilt screen left it.
        val mark = notesWrites.size
        val reopened = viewModel(sessionId)
        reopened.awaitState {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == SQUAT &&
                it.liftReadiness.allowsCommit() && !it.entryLocked
        }
        pauseTyping()
        assertEquals(
            "the reopened workout keeps the words only saved state had; writes since=${notesWrites.drop(mark)}",
            NEWER_NOTES,
            reopened.uiState.value.notes,
        )
        assertEquals(
            "and writes them over the older notes",
            NEWER_NOTES,
            deps.workoutRepository.awaitSession(sessionId) { it.notes == NEWER_NOTES }.notes,
        )
    }

    @Test
    fun clearedNotesStayClearedAfterAReopen() = runBlocking {
        val sessionId = seedWorkout(listOf(SQUAT_PLAN))
        val vm = viewModel(sessionId)
        vm.awaitSettledOn(SQUAT, SQUAT_KG)
        vm.setNotes(OLDER_NOTES)
        pauseTyping()
        deps.workoutRepository.awaitSession(sessionId) { it.notes == OLDER_NOTES }
        vm.setNotes("")
        leave(vm)
        deps.workoutRepository.awaitSession(sessionId) { it.notes.isEmpty() }

        val mark = notesWrites.size
        val reopened = viewModel(sessionId)
        reopened.awaitState {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == SQUAT &&
                it.liftReadiness.allowsCommit() && !it.entryLocked
        }
        pauseTyping()
        assertEquals("cleared notes stay cleared on the screen", "", reopened.uiState.value.notes)
        assertNoOlderNotesWrittenSince(mark)
        assertEquals("the database stays cleared", "", storedNotes(sessionId))
    }

    @Test
    fun notesClearedWhileALiftLoadsStayClearedAfterAReopen() = runBlocking {
        val sessionId = seedWorkout(THREE_LIFTS)
        val vm = viewModel(sessionId)
        vm.awaitSettledOn(SQUAT, SQUAT_KG)
        vm.setNotes(OLDER_NOTES)
        pauseTyping()
        deps.workoutRepository.awaitSession(sessionId) { it.notes == OLDER_NOTES }
        holdHistory.set(true)
        vm.selectExercise(PRESS)
        awaitHistoryHeld()
        // Cleared while the press has no entry of its own yet.
        vm.setNotes("")
        leave(vm)
        releaseTheHeldPrefill(sessionId)
        deps.workoutRepository.awaitSession(sessionId) { it.notes.isEmpty() }

        val mark = notesWrites.size
        val reopened = viewModel(sessionId)
        reopened.awaitState {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == PRESS &&
                it.liftReadiness.allowsCommit() && !it.entryLocked
        }
        pauseTyping()
        assertEquals("notes cleared on a loading lift stay cleared", "", reopened.uiState.value.notes)
        assertNoOlderNotesWrittenSince(mark)
        assertEquals("the database stays cleared", "", storedNotes(sessionId))
    }

    @Test
    fun notesTypedWithNoLiftSelectedSurviveAnExitThatDoesNotFlush() = runBlocking {
        val sessionId = deps.workoutRepository.startFreeWorkout().id
        val vm = viewModel(sessionId)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == null && !it.entryLocked }
        vm.setNotes(NEWEST_NOTES)
        end(vm)
        assertEquals("precondition: nothing reached the database", "", storedNotes(sessionId))

        val reopened = viewModel(sessionId)
        reopened.awaitState { it.loadState == SessionLoadState.FOUND && !it.entryLocked }
        pauseTyping()
        assertEquals("words typed with no lift selected survive", NEWEST_NOTES, reopened.uiState.value.notes)
        assertEquals(
            "and reach the database",
            NEWEST_NOTES,
            deps.workoutRepository.awaitSession(sessionId) { it.notes == NEWEST_NOTES }.notes,
        )
    }

    /**
     * Squat, then row, then press: the notes are begun on the squat and finished on the row, and
     * the press, which has nothing cached, is picked with its history read held, so its prefill
     * is still running. With [typedSquatKg], a weight is typed on the squat first, so its entry
     * is marked as typed.
     */
    private suspend fun pressStillLoading(sessionId: String, typedSquatKg: Double?): ActiveWorkoutViewModel {
        val vm = viewModel(sessionId)
        vm.awaitSettledOn(SQUAT, SQUAT_KG)
        if (typedSquatKg != null) {
            vm.setWeight(typedSquatKg)
            vm.awaitState { it.draftDirty && it.draft.weightKg == typedSquatKg }
        }
        vm.setNotes(OLDER_NOTES)

        vm.selectExercise(ROW)
        vm.awaitSettledOn(ROW, ROW_KG)
        vm.setNotes(NEWER_NOTES)
        pauseTyping()
        deps.workoutRepository.awaitSession(sessionId) { it.notes == NEWER_NOTES }
        val squat = deps.workoutDraftCache.getLift(sessionId, SQUAT)
        assertEquals("precondition: the squat's entry kept the older notes", OLDER_NOTES, squat?.notes)
        if (typedSquatKg != null) {
            assertEquals(
                "precondition: the squat's entry holds the typed weight",
                typedSquatKg,
                squat?.weightKg ?: -1.0,
                WEIGHT_TOLERANCE,
            )
        }

        holdHistory.set(true)
        vm.selectExercise(PRESS)
        awaitHistoryHeld()
        return vm
    }

    /** The old screen is gone; let its held read go, and check the press never got an entry. */
    private fun releaseTheHeldPrefill(sessionId: String) {
        releaseHistory.complete(Unit)
        assertNull(
            "precondition: the press's prefill never finished, so it has no entry of its own",
            deps.workoutDraftCache.getLift(sessionId, PRESS),
        )
        assertEquals(
            "precondition: the cache still has the press selected",
            PRESS,
            deps.workoutDraftCache.selectedExerciseId(sessionId),
        )
    }

    /** Back: flush the draft and the notes, then the screen ends. */
    private suspend fun leave(vm: ActiveWorkoutViewModel) {
        vm.persistDraftForExit()
        end(vm)
    }

    /** The ViewModel ends. The process, and its draft cache, live on. */
    private suspend fun end(vm: ActiveWorkoutViewModel) {
        vm.clearAndJoinForTest()
        viewModels.remove(vm)
    }

    /** A typing pause long enough for the notes debounce to write. */
    private fun pauseTyping() {
        dispatcher.scheduler.advanceTimeBy(TYPING_PAUSE_MS)
        dispatcher.scheduler.runCurrent()
    }

    private fun assertNoOlderNotesWrittenSince(mark: Int) {
        val since = notesWrites.drop(mark)
        assertFalse(
            "the reopened screen wrote the older notes over the newer ones; writes since reopening=$since",
            OLDER_NOTES in since,
        )
    }

    private suspend fun storedNotes(sessionId: String): String? {
        val stored = deps.workoutRepository.getSession(sessionId)
        assertNotNull("the session is still stored", stored)
        return stored?.notes
    }

    private suspend fun awaitHistoryHeld() {
        try {
            withTimeout(TestWaits.FLOW_MS) { historyEntered.await() }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("the press's prefill never reached its history read", timedOut)
        }
    }

    /** Selected, prefilled with [weightKg], ready to log and not locked. */
    private suspend fun ActiveWorkoutViewModel.awaitSettledOn(exerciseId: String, weightKg: Double) =
        awaitState {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == exerciseId &&
                it.draft.weightKg == weightKg && it.liftReadiness.allowsCommit() && !it.entryLocked
        }

    /**
     * A new screen for the workout, as reopening it from Home builds: a fresh handle, the same
     * cache. Given [handle], the screen Android rebuilds after a process death instead.
     */
    private fun viewModel(
        sessionId: String,
        handle: SavedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
    ) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = handle,
        container = deps,
        undoTimeout = UndoTimeoutProvider { it.toLong() },
    ).also(viewModels::add)

    private suspend fun seedWorkout(lifts: List<PlannedLift>): String {
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(id = ROUTINE, name = "Full body", notes = "", createdAt = STAMP, updatedAt = STAMP),
        )
        lifts.forEachIndexed { order, lift ->
            insertExercise(lift.id, lift.name)
            deps.database.routineDao().upsertRoutineExercise(
                RoutineExerciseEntity(
                    id = "re-${lift.id}",
                    routineId = ROUTINE,
                    exerciseId = lift.id,
                    sortOrder = order,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = lift.targetKg,
                    restSeconds = 90,
                ),
            )
        }
        val routine = checkNotNull(deps.routineRepository.getById(ROUTINE)) { "the routine is not stored" }
        return deps.workoutRepository.startRoutine(routine).id
    }

    private suspend fun insertExercise(id: String, name: String) {
        deps.database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = id,
                    name = name,
                    muscleGroup = "Legs",
                    notes = "",
                    isCustom = false,
                    loadType = "EXTERNAL",
                    nameKey = name.lowercase(),
                ),
            ),
        )
    }

    /**
     * Records every notes write, holds history reads while [holdHistory] is set, and leaves a
     * session read unanswered when it starts while [holdSession] is set.
     */
    private inner class RecordedNotesHeldHistory(private val real: WorkoutDao) : WorkoutDao by real {
        override fun observeSession(id: String): Flow<SessionWithDetails?> = flow {
            if (holdSession.get()) awaitCancellation()
            emitAll(real.observeSession(id))
        }

        override suspend fun updateSessionNotes(id: String, notes: String) {
            notesWrites.add(notes)
            real.updateSessionNotes(id, notes)
        }

        override suspend fun finishedWorkingSetsForExercises(
            exerciseIds: List<String>,
        ): List<FinishedWorkingSetRow> {
            if (holdHistory.get()) {
                historyEntered.complete(Unit)
                releaseHistory.await()
            }
            return real.finishedWorkingSetsForExercises(exerciseIds)
        }
    }

    private data class PlannedLift(val id: String, val name: String, val targetKg: Double)

    private companion object {
        const val SQUAT = "squat"
        const val ROW = "row"
        const val PRESS = "press"
        const val SQUAT_KG = 100.0
        const val ROW_KG = 80.0
        const val PRESS_KG = 60.0
        const val TYPED_SQUAT_KG = 112.5
        const val WEIGHT_TOLERANCE = 0.0001
        const val OLDER_NOTES = "felt good"
        const val NEWER_NOTES = "felt good, left knee sore"
        const val NEWEST_NOTES = "felt good, left knee sore, iced it"

        /** One millisecond past the screen's 400 ms notes debounce. */
        const val TYPING_PAUSE_MS = 401L
        const val ROUTINE = "routine-full-body"
        const val STAMP = 1_700_000_000_000L
        val SQUAT_PLAN = PlannedLift(id = SQUAT, name = "Squat", targetKg = SQUAT_KG)
        val THREE_LIFTS = listOf(
            SQUAT_PLAN,
            PlannedLift(id = ROW, name = "Row", targetKg = ROW_KG),
            PlannedLift(id = PRESS, name = "Press", targetKg = PRESS_KG),
        )
    }
}
