package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.UndoKind
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.workout.SavedStateFloorUndo
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the workout screen asks of its ViewModel, held on the ViewModel's public face: the
 * commit's Next exercise moves the lifter, Apply keeps the coach's numbers through a process
 * death, a delete and its undo each tell the screen how they should feel, an undo tapped
 * while a delete is still being written is dropped (not queued to run after it: the lifter
 * taps again once the delete has landed), and the undo dwell comes back with its offers.
 *
 * These were lines of ActiveWorkoutViewModel.kt read as text (`NEXT_EXERCISE ->
 * action.identity.nextExerciseId?.let(::selectExercise)`, `persistDraft()` inside
 * `fun applyMicroRec()`, `undoMutex`, `withLock`, `DeleteFeedback`, `SavedStateFloorUndo`).
 * W2d moves undo, save and timed work out of that file, which would have failed every one
 * of those lines while the behaviour stood; these hold the behaviour wherever it moves.
 *
 * Built as ActiveWorkoutViewModelTest builds it: real in-memory Room, a real
 * SavedStateHandle, and the ViewModel's own scope inline on the test thread; it waits with
 * the same helpers, which live in FloorTestKit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FloorVmContractTest {
    private lateinit var dispatcher: TestDispatcher
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private val collectors = CoroutineScope(Job() + Dispatchers.Unconfined)

    /** When set, a set's delete waits here inside its write: a delete the lifter is still waiting on. */
    @Volatile private var deleteGate: CompletableDeferred<Unit>? = null

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            workoutDaoDecorator = { real -> GatedDeletes(real) },
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        deleteGate?.complete(Unit)
        collectors.cancel()
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun nextExerciseOnTheCommitSelectsTheNextLift() = runBlocking {
        val sessionId = seedTwoLifts(targetSets = 1)
        val vm = viewModel(sessionId)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == SQUAT && it.draft.weightKg > 0.0 }
        vm.logSetAndLand()
        val next = withTimeout(TestWaits.FLOW_MS) { vm.primaryAction.first { it.kind == WorkoutPrimaryKind.NEXT_EXERCISE } }
        assertEquals(ROW, next.identity.nextExerciseId)
        assertEquals("the plan stays on the finished lift until the lifter taps", SQUAT, vm.uiState.value.selectedExerciseId)
        assertTrue("the commit takes Next exercise", vm.performPrimary(next))
        assertEquals(ROW, vm.awaitState { it.selectedExerciseId == ROW }.selectedExerciseId)
    }

    @Test
    fun applyingTheCoachsCallSurvivesProcessDeath() = runBlocking {
        val sessionId = seedSquat(loggedSets = emptyList())
        val handle = handleFor(sessionId)
        val vm = viewModel(sessionId, handle)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.setWeight(100.0)
        vm.setRpe(8)
        vm.logSetAndLand()
        vm.skipRest()
        val rec = checkNotNull(
            withTimeout(TestWaits.FLOW_MS) {
                vm.microRec.first { it?.reasonCode == SetMicroRecCalculator.QUALITY && it.showApply && !it.previewOnly }
            },
        )
        // Walk the entry off the call, then take it back with Apply.
        vm.setWeight(80.0)
        vm.setReps(3)
        vm.awaitState { it.draft.weightKg == 80.0 && it.draft.reps == 3 }
        vm.applyMicroRec()
        vm.awaitState { it.draft.weightKg == rec.nextWeightKg && it.draft.reps == rec.nextReps }
        // The applied numbers are what a process death hands back, not the ones walked off.
        val saved = checkNotNull(SavedStateWorkoutDraft(handle).read(sessionId)) { "nothing was saved for the process to come back to" }
        assertEquals(rec.nextWeightKg, saved.weightKg, 1e-6)
        assertEquals(rec.nextReps, saved.reps)
        assertEquals(rec.nextRpe, saved.rpe)
        vm.clearAndJoinForTest()
        viewModels.remove(vm)
        deps.workoutDraftCache.clearAll()
        val revived = viewModel(sessionId, handle)
        val state = revived.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg == rec.nextWeightKg }
        assertEquals(rec.nextReps, state.draft.reps)
        assertEquals(rec.nextRpe, state.draft.rpe)
        assertEquals("Apply never logs", 1, storedSession(sessionId).sets.size)
    }

    @Test
    fun aDeleteLandsAsAWarningAndItsUndoAsACommit() = runBlocking {
        val sessionId = seedSquat(loggedSets = listOf(SET_100x5))
        val vm = viewModel(sessionId)
        val felt = mutableListOf<DeleteFeedback>()
        collectors.launch { vm.deleteFeedback.collect { felt += it } }
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 1 }
        val logged = storedSession(sessionId).sets.single()
        vm.awaitEntryUnlocked()
        vm.deleteSet(logged.id)
        vm.awaitOffer()
        deps.workoutRepository.awaitSession(sessionId) { it.sets.isEmpty() }
        assertEquals(listOf(DeleteFeedback.DELETED), felt)
        vm.undoTopOffer()
        deps.workoutRepository.awaitSession(sessionId) { it.sets.size == 1 }
        vm.awaitState { it.session?.sets?.size == 1 && !it.entryLocked }
        assertEquals(listOf(DeleteFeedback.DELETED, DeleteFeedback.UNDO), felt)
    }

    @Test
    fun removingALiftLandsAsAWarningToo() = runBlocking {
        val sessionId = seedTwoLifts(targetSets = 3)
        val vm = viewModel(sessionId)
        val felt = mutableListOf<DeleteFeedback>()
        collectors.launch { vm.deleteFeedback.collect { felt += it } }
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == SQUAT }
        vm.selectExercise(ROW)
        vm.awaitState { it.selectedExerciseId == ROW }
        vm.awaitEntryUnlocked()
        vm.removeSelectedLift()
        vm.awaitOffer()
        assertEquals(UndoKind.REMOVED_LIFT, vm.undoEntries.value.last().offer.kind)
        assertEquals(listOf(DeleteFeedback.REMOVED), felt)
    }

    @Test
    fun anUndoTappedWhileADeleteIsStillBeingWrittenIsDroppedNotQueued() = runBlocking {
        val sessionId = seedSquat(loggedSets = listOf(SET_100x5, SET_100x5))
        val vm = viewModel(sessionId)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 2 }
        val (first, second) = storedSession(sessionId).sets.sortedBy { it.completedAt }
        vm.awaitEntryUnlocked()
        vm.deleteSet(first.id)
        vm.awaitOffer()
        deps.workoutRepository.awaitSession(sessionId) { it.sets.size == 1 }
        vm.awaitEntryUnlocked()
        // The second delete parks inside its write, as a slow disk would hold it.
        val gate = CompletableDeferred<Unit>().also { deleteGate = it }
        vm.deleteSet(second.id)
        withTimeout(TestWaits.FLOW_MS) { vm.uiState.first { it.entryLocked } }
        // Undo now would put back the first set under a queue the second is about to join,
        // so the tap is refused outright: nothing is put back, now or once the delete lands.
        vm.undoTopOffer()
        assertEquals("the undo is dropped: the first offer still stands alone", 1, vm.undoEntries.value.size)
        deleteGate = null
        gate.complete(Unit)
        withTimeout(TestWaits.FLOW_MS) { vm.undoEntries.first { it.size == 2 } }
        vm.awaitEntryUnlocked()
        deps.workoutRepository.awaitSession(sessionId) { it.sets.isEmpty() }
        assertEquals("both deletes landed and nothing was put back", 2, vm.undoEntries.value.size)
        // Tapped again once the delete has landed, undo reverses the latest delete first.
        vm.undoTopOffer()
        assertEquals(second.id, deps.workoutRepository.awaitSession(sessionId) { it.sets.size == 1 }.sets.single().id)
    }

    @Test
    fun theUndoDwellComesBackWithItsOffersAfterProcessDeath() = runBlocking {
        val sessionId = seedSquat(loggedSets = listOf(SET_100x5))
        val handle = handleFor(sessionId)
        // TalkBack asks for twenty seconds; the offer is promised that long.
        val vm = viewModel(sessionId, handle, undoTimeout = UndoTimeoutProvider { TALKBACK_DWELL_MS })
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.session?.sets?.size == 1 }
        vm.awaitEntryUnlocked()
        vm.deleteSet(storedSession(sessionId).sets.single().id)
        vm.awaitOffer()
        assertEquals(TALKBACK_DWELL_MS, vm.undoDwellMs.value)
        // A process dies after its state is saved, and nothing of it runs on. Here the first
        // ViewModel's coroutines run on Room's two threads (the test's unconfined dispatcher
        // resumes them there) and write the same SavedStateHandle, a plain map, that the revived
        // one reads as it is built; reviving beside it read an empty queue once in three loaded
        // package runs. So wait until the saved state holds the offer, and end this ViewModel,
        // before the process comes back.
        vm.awaitSavedOffer(handle)
        vm.clearAndJoinForTest()
        viewModels.remove(vm)
        // The process comes back with a platform answer that would give only the base dwell.
        val revived = viewModel(sessionId, handle, undoTimeout = UndoTimeoutProvider { it.toLong() })
        revived.awaitState { it.loadState == SessionLoadState.FOUND }
        assertEquals("the offer comes back with the process", 1, revived.undoEntries.value.size)
        assertEquals("the offer keeps the dwell it was promised", TALKBACK_DWELL_MS, revived.undoDwellMs.value)
        assertTrue(TALKBACK_DWELL_MS > Motion.STATUS_DWELL_MS)
    }

    private fun viewModel(
        sessionId: String,
        handle: SavedStateHandle = handleFor(sessionId),
        undoTimeout: UndoTimeoutProvider = UndoTimeoutProvider { it.toLong() },
    ) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = handle,
        container = deps,
        undoTimeout = undoTimeout,
    ).also(viewModels::add)

    private fun handleFor(sessionId: String) = SavedStateHandle(mapOf("sessionId" to sessionId))

    private suspend fun seedSquat(loggedSets: List<TestSetInput>): String = seedTestWorkout(
        deps = deps,
        exerciseId = SQUAT,
        exerciseName = "Squat",
        targetSets = 3,
        targetReps = 5,
        targetWeightKg = 100.0,
        restSeconds = 90,
        loggedSets = loggedSets,
    ).session.id

    private suspend fun seedTwoLifts(targetSets: Int): String {
        val seeded = seedTestWorkout(
            deps = deps,
            exerciseId = SQUAT,
            exerciseName = "Squat",
            targetSets = targetSets,
            targetReps = 5,
            targetWeightKg = 100.0,
            restSeconds = 90,
        )
        val row = insertTestExercise(deps = deps, id = ROW, name = "Row")
        deps.workoutRepository.addExerciseToSession(seeded.session.id, row, targetSets = targetSets, targetReps = 5, targetWeightKg = 80.0, restSeconds = 90)
        return seeded.session.id
    }

    private suspend fun storedSession(sessionId: String): WorkoutSession =
        checkNotNull(deps.workoutRepository.getSession(sessionId))

    /**
     * Log a set, and fail at once, with the save's own state, if it did not land. The shared
     * [logSetAndSettle] returns on a FAILED or CONFLICT save too, which the write-failure tests
     * in ActiveWorkoutViewModelTest go on to assert; here nothing expects a failure, and a test
     * carrying on after one only timed out later, waiting for a lift or a call that could not
     * come, with nothing to say why.
     */
    private suspend fun ActiveWorkoutViewModel.logSetAndLand() {
        logSetAndSettle(deps.workoutRepository, dispatcher.scheduler)
        val save = uiState.value.save
        assertTrue(
            "the log never landed: $save",
            save.phase != WorkoutSavePhase.FAILED && save.phase != WorkoutSavePhase.CONFLICT,
        )
    }

    /** Until [handle], the state a process death hands back, holds this ViewModel's undo offers. */
    private suspend fun ActiveWorkoutViewModel.awaitSavedOffer(handle: SavedStateHandle) {
        val saved = SavedStateFloorUndo(handle)
        try {
            withTimeout(TestWaits.FLOW_MS) {
                while (saved.read().size != undoEntries.value.size) delay(SAVED_POLL_MS)
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("the saved state never held the ${undoEntries.value.size} offer(s); it held ${saved.read().size}", timedOut)
        }
    }

    /** The undo offer a delete or remove made, once the mutation that made it has released. */
    private suspend fun ActiveWorkoutViewModel.awaitOffer() {
        assertNotNull(undoEntries.awaitFirst { it.isNotEmpty() })
        awaitEntryUnlocked()
    }

    /** Every write the screen makes, except that a set's delete first waits for [deleteGate]. */
    private inner class GatedDeletes(private val real: WorkoutDao) : WorkoutDao by real {
        override suspend fun deleteSet(id: String) {
            deleteGate?.await()
            real.deleteSet(id)
        }
    }

    private companion object {
        const val SQUAT = "squat"
        const val ROW = "row"
        const val TALKBACK_DWELL_MS = 20_000L

        /** How often the saved state is looked at while a write is on its way into it. */
        const val SAVED_POLL_MS = 10L
        val SET_100x5 = TestSetInput(weightKg = 100.0, reps = 5, rpe = 8)
    }
}
