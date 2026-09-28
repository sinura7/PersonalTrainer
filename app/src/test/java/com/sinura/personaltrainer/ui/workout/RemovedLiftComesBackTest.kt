package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.FinishedWorkingSetRow
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.workout.WorkoutDraft
import java.util.concurrent.CopyOnWriteArrayList
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
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A lift removed and brought back, by Undo or from the picker, is ready to log at once, with its own
 * numbers (N2).
 *
 * Two defects met here. The prefill was driven by `selectedExerciseId.filterNotNull()
 * .distinctUntilChanged()`, which read the only lift removed and brought back (X, then none, then
 * X) as X twice and dropped the second: the lift's entry, reset to loading by the selection, was
 * never read again, and Log stayed off (L1, L2). And the prefill read the session as it stood, the
 * row from before the Undo or the add wrote the lift, found no plan for it and filled 0 kg and 5
 * reps (L2, L3).
 *
 * The rest, from the reviews, holds what the new wait for a row that holds the lift must still
 * accept, and what the reading of every selection must keep: a lift with a set logged and no plan,
 * a working set or only a warm-up (B1a, B1b); a set of such a lift opened for correction (B2); and
 * a lift picked while another still loads, which must not wait for that load (B3).
 *
 * Built as ReopenMidPrefillTest builds it: real in-memory Room, the ViewModel's scope unconfined,
 * with the workout DAO wrapped so that session rows can be held at a door ([rowsHeld]) before the
 * screen hears of them, as a slow phone delivers the row an Undo or an add wrote only after the
 * lift is already selected. The stored row is read past the door ([unheld]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RemovedLiftComesBackTest {
    private lateinit var dispatcher: TestDispatcher
    private lateinit var deps: FakeAppDependencies
    private lateinit var unheld: WorkoutRepository
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    /** While true, each session row waits at the door before the screen hears of it. */
    private val rowsHeld = MutableStateFlow(false)

    /** Every session row that reached the door, held or not, in order. */
    private val rowsAtDoor = CopyOnWriteArrayList<SessionWithDetails?>()

    /** Armed, the next hint read of the bench says so on [benchHintHeld] and waits for [releaseBenchHint]. */
    private val holdBenchHint = AtomicBoolean(false)
    private val benchHintHeld = CompletableDeferred<Unit>()
    private val releaseBenchHint = CompletableDeferred<Unit>()

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            workoutDaoDecorator = { real -> HeldRows(real) },
        )
        // The same database, past the door.
        unheld = WorkoutRepository(deps.database, deps.database.workoutDao())
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        rowsHeld.value = false
        releaseBenchHint.complete(Unit)
        if (::dispatcher.isInitialized) runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        if (::deps.isInitialized) deps.restTimerController.stop()
        if (::dispatcher.isInitialized) dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    /** L1. */
    @Test
    fun undoingTheRemovalOfTheOnlyLiftBringsItBackReadyWithItsNumbers() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        val vm = viewModel(sessionId)
        val before = vm.awaitReadyOn(exerciseId = FLOOR_LIFT_ID, weightKg = FLOOR_KG70)

        vm.removeSelectedLift()
        vm.awaitNoLiftLeftToUndo()
        vm.undoRemoveLift()
        unheld.awaitSession(sessionId) { session -> session.exercises.any { it.exercise.id == FLOOR_LIFT_ID } }

        val back = settleOn(vm = vm, sessionId = sessionId, exerciseId = FLOOR_LIFT_ID)
        assertEquals(
            "the only lift, brought back by Undo, is ready to log, not left loading; state=${back.state}",
            LiftEntryReadiness.READY,
            back.state.liftReadiness,
        )
        assertTrue("Log is on for the lift Undo brought back; state=${back.state}", back.state.canLog)
        assertEquals(
            "it comes back with the weight it had before it was removed",
            before.draft.weightKg,
            back.state.draft.weightKg,
            WEIGHT_TOLERANCE,
        )
        assertEquals("and the reps", before.draft.reps, back.state.draft.reps)
    }

    /** L2. */
    @Test
    fun theOnlyLiftRemovedAndAddedBackFromThePickerIsReadyWithItsRowsReps() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList())
        val legExtension = checkNotNull(deps.exerciseRepository.getById(FLOOR_LIFT_ID)) { "the leg extension is not stored" }
        val vm = viewModel(sessionId)
        vm.awaitReadyOn(exerciseId = FLOOR_LIFT_ID, weightKg = FLOOR_KG70)

        vm.removeSelectedLift()
        vm.awaitNoLiftLeftToUndo()
        // The row the add writes reaches the screen only after the add has selected the lift.
        rowsHeld.value = true
        val doorMark = rowsAtDoor.size
        vm.addExercise(legExtension)
        val added = awaitRowAtDoor(what = "the row the add wrote", since = doorMark) { row -> row.holds(FLOOR_LIFT_ID) }
        val rowReps = checkNotNull(added.exercises.firstOrNull { it.exercise.id == FLOOR_LIFT_ID }) {
            "the added row holds no leg extension"
        }.item.targetReps
        assertNotEquals("precondition: the row's reps are not the 5 a lift with no plan falls back to", 5, rowReps)
        val picked = awaitPicked(vm = vm, sessionId = sessionId, exerciseId = FLOOR_LIFT_ID)
        assertTrue(
            "precondition: the screen has not heard of the added row yet; state=$picked",
            picked.session?.exercises?.isEmpty() == true,
        )
        rowsHeld.value = false

        val back = settleOn(vm = vm, sessionId = sessionId, exerciseId = FLOOR_LIFT_ID)
        assertEquals(
            "the only lift, added back from the picker, is ready to log, not left loading; state=${back.state}",
            LiftEntryReadiness.READY,
            back.state.liftReadiness,
        )
        assertTrue("Log is on for the lift added back; state=${back.state}", back.state.canLog)
        assertEquals("it takes the reps its row was added with", rowReps, back.state.draft.reps)
    }

    /** L3. */
    @Test
    fun aRemovedLiftBroughtBackByUndoKeepsItsOwnWeightNotZero() = runBlocking<Unit> {
        val sessionId = seedLegExtension(deps = deps, loggedSets = emptyList(), withNextLift = true)
        val vm = viewModel(sessionId)
        vm.awaitReadyOn(exerciseId = FLOOR_LIFT_ID, weightKg = FLOOR_KG70)
        vm.selectExercise(FLOOR_NEXT_LIFT_ID)
        vm.awaitReadyOn(exerciseId = FLOOR_NEXT_LIFT_ID, weightKg = DEADLIFT_KG)
        val saved = deps.workoutDraftCache.getLift(sessionId, FLOOR_NEXT_LIFT_ID)
        assertEquals("precondition: the deadlift's saved entry is 40 kg", DEADLIFT_KG, saved?.weightKg ?: -1.0, WEIGHT_TOLERANCE)
        assertEquals("precondition: at 8 reps", DEADLIFT_REPS, saved?.reps)

        vm.removeSelectedLift()
        vm.awaitState { state ->
            state.selectedExerciseId == FLOOR_LIFT_ID && state.session?.holds(FLOOR_NEXT_LIFT_ID) == false &&
                state.liftReadiness.allowsCommit() && !state.entryLocked
        }
        vm.awaitUndoOffered()
        // The row the Undo writes reaches the screen only after Undo has selected the lift.
        rowsHeld.value = true
        val doorMark = rowsAtDoor.size
        vm.undoRemoveLift()
        awaitRowAtDoor(what = "the row the Undo wrote", since = doorMark) { row -> row.holds(FLOOR_NEXT_LIFT_ID) }
        val picked = awaitPicked(vm = vm, sessionId = sessionId, exerciseId = FLOOR_NEXT_LIFT_ID)
        assertTrue(
            "precondition: the screen has not heard of the row Undo wrote yet; state=$picked",
            picked.session?.holds(FLOOR_NEXT_LIFT_ID) == false,
        )
        rowsHeld.value = false

        val back = settleOn(vm = vm, sessionId = sessionId, exerciseId = FLOOR_NEXT_LIFT_ID)
        assertTrue("the deadlift is back and ready; state=${back.state}", back.state.liftReadiness.allowsCommit())
        assertEquals(
            "the deadlift Undo brought back shows its own 40 kg, not 0 kg; state=${back.state}",
            DEADLIFT_KG,
            back.state.draft.weightKg,
            WEIGHT_TOLERANCE,
        )
        assertEquals("and its own 8 reps, not 5; state=${back.state}", DEADLIFT_REPS, back.state.draft.reps)
        assertNotNull("its entry is saved", back.entry)

        vm.persistDraftForExit() // Back
        end(vm)
        val reopened = viewModel(sessionId).awaitState { state ->
            state.loadState == SessionLoadState.FOUND && state.selectedExerciseId == FLOOR_NEXT_LIFT_ID &&
                state.liftReadiness.allowsCommit() && !state.entryLocked
        }
        assertEquals("the reopened workout still shows 40 kg", DEADLIFT_KG, reopened.draft.weightKg, WEIGHT_TOLERANCE)
        assertEquals("and 8 reps", DEADLIFT_REPS, reopened.draft.reps)
    }

    /** B1a (review, l10). */
    @Test
    fun aLiftWithOnlyAWorkingSetLoggedElsewhereLoads() = aLiftWithOnlyASetLoggedElsewhereLoads(warmUp = false)

    /** B1b (review, l32). */
    @Test
    fun aLiftWithOnlyAWarmUpLoggedElsewhereLoads() = aLiftWithOnlyASetLoggedElsewhereLoads(warmUp = true)

    /**
     * A free workout with no lifts, and a squat set logged elsewhere (another screen, a restore): the
     * row that brings it selects the squat from its set alone, and the squat must load, though it has
     * no plan.
     */
    private fun aLiftWithOnlyASetLoggedElsewhereLoads(warmUp: Boolean) = runBlocking<Unit> {
        val sessionId = unheld.startFreeWorkout().id
        insertTestExercise(deps = deps, id = SQUAT, name = "Squat", muscleGroup = "Legs")
        val vm = viewModel(sessionId)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == null && !it.entryLocked }

        unheld.logSet(sessionId = sessionId, exerciseId = SQUAT, weightKg = SQUAT_KG, reps = 5, rpe = null, isWarmup = warmUp)
        val loaded = settleOn(vm = vm, sessionId = sessionId, exerciseId = SQUAT)
        val kind = if (warmUp) "a warm-up" else "a working set"
        assertEquals(
            "a lift selected from $kind alone, with no plan, loads and is ready to log; state=${loaded.state}",
            LiftEntryReadiness.READY,
            loaded.state.liftReadiness,
        )
        assertTrue("Log is on; state=${loaded.state}", loaded.state.canLog)
    }

    /** B2 (review, l31). */
    @Test
    fun aSetOfALiftWithNoPlanOpenedForCorrectionLoadsItsHint() = runBlocking<Unit> {
        val sessionId = seedSquatAndBench(priorBench = true, priorSquat = false)
        unheld.logSet(sessionId = sessionId, exerciseId = BENCH, weightKg = BENCH_KG, reps = 5, rpe = null, isWarmup = false)
        // Sync drops the bench's plan row; its set stays.
        val benchItem = checkNotNull(unheld.getSession(sessionId)).exercises.first { it.exercise.id == BENCH }.id
        deps.database.workoutDao().deleteSessionExercise(benchItem)
        val vm = viewModel(sessionId)
        vm.awaitReadyOn(exerciseId = SQUAT, weightKg = SQUAT_KG)
        // No later row reaches the screen, so nothing re-selects while the set is open.
        rowsHeld.value = true

        val setId = checkNotNull(unheld.getSession(sessionId)).sets.single { it.exerciseId == BENCH }.id
        vm.editSet(setId)
        val opened = settleState(vm) { state ->
            state.editingSetId == setId && !state.entryLocked && state.hint?.exerciseId == BENCH && state.lastPerformance != null
        }
        assertEquals("precondition: the bench's set is open for correction; state=$opened", setId, opened.editingSetId)
        assertEquals(
            "the bench's hint loads for its set, though the bench has no plan; state=$opened",
            BENCH,
            opened.hint?.exerciseId,
        )
        assertEquals(
            "and last time's sets",
            listOf(BENCH_KG),
            opened.lastPerformance?.sets?.map { it.weightKg },
        )
    }

    /** B3 (review, l15). */
    @Test
    fun aLiftPickedWhileAnotherStillLoadsLoadsWithoutWaitingForIt() = runBlocking<Unit> {
        val sessionId = seedSquatAndBench(priorBench = true, priorSquat = true)
        val vm = viewModel(sessionId)
        vm.awaitState { state ->
            state.loadState == SessionLoadState.FOUND && state.selectedExerciseId == SQUAT &&
                state.liftReadiness.allowsCommit() && !state.entryLocked &&
                state.hint?.exerciseId == SQUAT && state.lastPerformance != null
        }

        holdBenchHint.set(true)
        vm.selectExercise(BENCH)
        try {
            withTimeout(TestWaits.FLOW_MS) { benchHintHeld.await() }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("the bench's load never reached its hint read; uiState was ${vm.uiState.value}", timedOut)
        }
        vm.selectExercise(SQUAT) // back on the squat while the bench's hint read is still held

        val back = settleState(vm) { state ->
            state.selectedExerciseId == SQUAT && !state.entryLocked &&
                state.hint?.exerciseId == SQUAT && state.lastPerformance != null
        }
        assertFalse("precondition: the bench's hint read has not been let go", releaseBenchHint.isCompleted)
        assertEquals(
            "the squat's hint is back while the bench's read is still held; state=$back",
            SQUAT,
            back.hint?.exerciseId,
        )
        assertEquals("and last time's sets", listOf(SQUAT_PRIOR_KG), back.lastPerformance?.sets?.map { it.weightKg })
        releaseBenchHint.complete(Unit)
    }

    private fun viewModel(sessionId: String): ActiveWorkoutViewModel =
        floorViewModel(deps = deps, sessionId = sessionId).also(viewModels::add)

    private suspend fun end(vm: ActiveWorkoutViewModel) {
        vm.clearAndJoinForTest()
        viewModels.remove(vm)
    }

    /** Selected, prefilled with [weightKg], ready to log and not locked. */
    private suspend fun ActiveWorkoutViewModel.awaitReadyOn(exerciseId: String, weightKg: Double): ActiveWorkoutUiState =
        awaitState { state ->
            state.loadState == SessionLoadState.FOUND && state.selectedExerciseId == exerciseId &&
                state.draft.weightKg == weightKg && state.liftReadiness.allowsCommit() && !state.entryLocked
        }

    /** The only lift is gone, nothing is selected, and its removal is offered back. */
    private suspend fun ActiveWorkoutViewModel.awaitNoLiftLeftToUndo() {
        awaitState { it.session?.exercises?.isEmpty() == true && it.selectedExerciseId == null && !it.entryLocked }
        awaitUndoOffered()
    }

    private suspend fun ActiveWorkoutViewModel.awaitUndoOffered() {
        try {
            withTimeout(TestWaits.FLOW_MS) { undoEntries.first { it.isNotEmpty() } }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("the removal was never offered back; uiState was ${uiState.value}", timedOut)
        }
    }

    /**
     * [exerciseId] is selected and the action that selected it is done: the screen's state then. The
     * selection is read where applySelection records it, the draft cache, because the screen names
     * the selected lift as resolved against the row it shows, and that row does not hold the lift
     * while its own row waits at the door.
     */
    private suspend fun awaitPicked(vm: ActiveWorkoutViewModel, sessionId: String, exerciseId: String): ActiveWorkoutUiState {
        val picked = {
            deps.workoutDraftCache.selectedExerciseId(sessionId) == exerciseId && !vm.uiState.value.entryLocked
        }
        try {
            withTimeout(TestWaits.FLOW_MS) {
                while (!picked()) delay(POLL_MS)
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError(
                "$exerciseId was never selected with the entry unlocked; cache selection " +
                    "${deps.workoutDraftCache.selectedExerciseId(sessionId)}, uiState ${vm.uiState.value}",
                timedOut,
            )
        }
        return vm.uiState.value
    }

    /**
     * The screen once it shows [exerciseId] back, out of loading, unlocked, with the entry its load
     * saved; or, when that never comes within [TestWaits.FLOW_MS], the last it showed. The load's save
     * lands after the screen hears of the ready entry, and the screen's numbers can trail the ready
     * mark by a beat, so both are read until they agree. The assertions after it say what was wrong.
     */
    private suspend fun settleOn(vm: ActiveWorkoutViewModel, sessionId: String, exerciseId: String): Settled {
        val read = { Settled(state = vm.uiState.value, entry = deps.workoutDraftCache.getLift(sessionId, exerciseId)) }
        var last = read()
        withTimeoutOrNull(TestWaits.FLOW_MS) {
            while (!last.isBackOn(exerciseId)) {
                delay(POLL_MS)
                last = read()
            }
        }
        return last
    }

    /** The screen once [done] accepts it, or, when it never does within [TestWaits.FLOW_MS], the last it showed. */
    private suspend fun settleState(
        vm: ActiveWorkoutViewModel,
        done: (ActiveWorkoutUiState) -> Boolean,
    ): ActiveWorkoutUiState {
        var last = vm.uiState.value
        withTimeoutOrNull(TestWaits.FLOW_MS) {
            while (!done(last)) {
                delay(POLL_MS)
                last = vm.uiState.value
            }
        }
        return last
    }

    /**
     * A live free workout with the squat (3 × 5 at 100 kg) and the bench (3 × 8 at 60 kg) planned; with
     * [priorSquat] or [priorBench], a finished workout before it where that lift was done at 100 kg × 5
     * or 60 kg × 8, so its hint and last time have something to read. The live session's id.
     */
    private suspend fun seedSquatAndBench(priorBench: Boolean, priorSquat: Boolean): String {
        val squat = insertTestExercise(deps = deps, id = SQUAT, name = "Squat", muscleGroup = "Legs")
        val bench = insertTestExercise(deps = deps, id = BENCH, name = "Bench Press", muscleGroup = "Chest")
        if (priorBench || priorSquat) {
            val prior = unheld.startFreeWorkout().id
            if (priorSquat) {
                unheld.logSet(sessionId = prior, exerciseId = SQUAT, weightKg = SQUAT_PRIOR_KG, reps = 5, rpe = null, isWarmup = false)
            }
            if (priorBench) {
                unheld.logSet(sessionId = prior, exerciseId = BENCH, weightKg = BENCH_KG, reps = 8, rpe = null, isWarmup = false)
            }
            unheld.finishSession(prior, "")
        }
        val live = unheld.startFreeWorkout().id
        unheld.addExerciseToSession(
            sessionId = live,
            exercise = squat,
            targetSets = 3,
            targetReps = 5,
            targetWeightKg = SQUAT_KG,
            restSeconds = 150,
        )
        unheld.addExerciseToSession(
            sessionId = live,
            exercise = bench,
            targetSets = 3,
            targetReps = 8,
            targetWeightKg = BENCH_KG,
            restSeconds = 90,
        )
        return live
    }

    /** The first row to reach the door after [since] that [holds] accepts; bounded. */
    private suspend fun awaitRowAtDoor(
        what: String,
        since: Int,
        holds: (SessionWithDetails) -> Boolean,
    ): SessionWithDetails {
        val arrived = {
            rowsAtDoor.drop(since).filterNotNull().firstOrNull(holds)
        }
        var found = arrived()
        try {
            withTimeout(TestWaits.FLOW_MS) {
                while (found == null) {
                    delay(POLL_MS)
                    found = arrived()
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("$what never reached the door within ${TestWaits.FLOW_MS} ms; rows at the door: ${rowsAtDoor.size}", timedOut)
        }
        return checkNotNull(found) { "$what is missing" }
    }

    /** Back on [exerciseId], out of loading, unlocked, showing the numbers its load saved. */
    private fun Settled.isBackOn(exerciseId: String): Boolean {
        val saved = entry ?: return false
        return state.loadState == SessionLoadState.FOUND && state.selectedExerciseId == exerciseId &&
            state.session?.holds(exerciseId) == true &&
            state.liftReadiness != LiftEntryReadiness.RESOLVING && !state.entryLocked &&
            state.draft.weightKg == saved.weightKg && state.draft.reps == saved.reps
    }

    private fun SessionWithDetails.holds(exerciseId: String): Boolean = exercises.any { it.exercise.id == exerciseId }

    /** Planned or logged: the lifts the screen's wait for its row counts as in the session. */
    private fun WorkoutSession.holds(exerciseId: String): Boolean =
        exercises.any { it.exercise.id == exerciseId } || sets.any { it.exerciseId == exerciseId }

    /** What the screen shows, and the entry saved for the lift it waits on. */
    private data class Settled(val state: ActiveWorkoutUiState, val entry: WorkoutDraft?)

    /** The real DAO, with a door each session row waits at while [rowsHeld] is true. */
    private inner class HeldRows(private val real: WorkoutDao) : WorkoutDao by real {
        override fun observeSession(id: String): Flow<SessionWithDetails?> =
            real.observeSession(id).onEach { row ->
                rowsAtDoor.add(row)
                rowsHeld.first { held -> !held }
            }

        override suspend fun finishedWorkingSetsForExercises(
            exerciseIds: List<String>,
        ): List<FinishedWorkingSetRow> {
            // The hint's read, told from last time's and the history's by its caller.
            val hintRead = Throwable().stackTrace.any { it.methodName.startsWith("progressionFor") }
            if (hintRead && BENCH in exerciseIds && holdBenchHint.compareAndSet(true, false)) {
                benchHintHeld.complete(Unit)
                releaseBenchHint.await()
            }
            return real.finishedWorkingSetsForExercises(exerciseIds)
        }
    }

    private companion object {
        /** The Romanian deadlift after the leg extension: 3 × 8 at 40 kg (seedLegExtension). */
        const val DEADLIFT_KG = 40.0
        const val DEADLIFT_REPS = 8
        const val WEIGHT_TOLERANCE = 0.0001
        const val SQUAT = "squat"
        const val BENCH = "bench"
        const val SQUAT_KG = 100.0
        const val SQUAT_PRIOR_KG = 100.0
        const val BENCH_KG = 60.0
        const val POLL_MS = 10L
    }
}
