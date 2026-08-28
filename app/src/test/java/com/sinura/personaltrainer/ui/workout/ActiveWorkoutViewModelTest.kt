package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import com.sinura.personaltrainer.workout.WorkoutDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterizes the workout orchestrator against real in-memory Room,
 * SavedStateHandle, draft cache, and rest store.
 *
 * Assertions are on durable rows and public state/effects. They deliberately
 * do not count coroutines or depend on private collector ordering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ActiveWorkoutViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        runBlocking {
            viewModels.forEach { it.clearAndJoinForTest() }
        }
        if (::deps.isInitialized) deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun blankSessionId_resolvesMissingWithError() = runBlocking {
        val vm = createViewModel("")
        val state = vm.awaitState { it.loadState == SessionLoadState.MISSING }

        assertEquals("This workout is no longer available.", state.error)
        assertNull(state.session)
    }

    @Test
    fun validSession_emitsFoundWithSessionGraph() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        val state = vm.awaitFound()

        assertEquals(fixture.session.id, state.session?.id)
        assertEquals(SQUAT, state.selectedExerciseId)
        assertEquals(3, state.session?.exercises?.single()?.targetSets)
    }

    @Test
    fun discardedSession_emitsMissingInsteadOfSpinning() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()

        deps.workoutRepository.discardSession(fixture.session.id)

        val state = vm.awaitState { it.loadState == SessionLoadState.MISSING }
        assertNull(state.session)
        assertFalse(state.isLoading)
    }

    @Test
    fun inMemoryDraftWinsOverSavedStateOnConstruct() = runBlocking {
        val fixture = seedWorkout()
        val handle = handleFor(fixture.session.id)
        SavedStateWorkoutDraft(handle).write(draft(fixture.session.id, 70.0, 4))
        deps.workoutDraftCache.put(draft(fixture.session.id, 92.5, 8))

        val vm = createViewModel(fixture.session.id, handle)
        val state = vm.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.weightKg == 92.5
        }

        assertEquals(92.5, state.draft.weightKg, 0.0001)
        assertEquals(8, state.draft.reps)
    }

    @Test
    fun savedStateDraftRestoresWhenCacheIsEmpty() = runBlocking {
        val fixture = seedWorkout()
        val handle = handleFor(fixture.session.id)
        SavedStateWorkoutDraft(handle).write(draft(fixture.session.id, 77.5, 9))

        val vm = createViewModel(fixture.session.id, handle)
        val state = vm.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.weightKg == 77.5
        }

        assertEquals(9, state.draft.reps)
        assertEquals(SQUAT, state.selectedExerciseId)
    }

    @Test
    fun staleRecoveredSelectionReconcilesToARealLift() = runBlocking {
        val fixture = seedWorkout()
        deps.workoutDraftCache.put(
            draft(fixture.session.id, 60.0, 6).copy(exerciseId = "deleted-lift"),
        )

        val vm = createViewModel(fixture.session.id)
        val state = vm.awaitState {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == SQUAT
        }

        assertEquals(SQUAT, state.session?.exercises?.single()?.exercise?.id)
    }

    @Test
    fun reselectingTheSameLiftRunsPrefillAgain() = runBlocking {
        val fixture = seedWorkout(targetWeightKg = 100.0)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg == 100.0 }

        vm.setWeight(155.0)
        vm.awaitState { it.draft.weightKg == 155.0 }
        vm.selectExercise(SQUAT)

        val state = vm.awaitState { it.draft.weightKg == 100.0 }
        assertEquals(5, state.draft.reps)
    }

    @Test
    fun logSetRejectsZeroWeightWorkingSetBeforeWriting() = runBlocking {
        val fixture = seedWorkout(targetWeightKg = 0.0)
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()

        vm.setWeight(0.0)
        vm.logSet()

        val state = vm.awaitState { it.error != null }
        assertTrue(state.error.orEmpty().contains("weight", ignoreCase = true))
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
    }

    @Test
    fun logSetPersistsSetClearsErrorAndEmitsRecord() = runBlocking {
        val fixture = seedWorkout(priorWeightKg = 80.0)
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()

        vm.setWeight(100.0)
        vm.logSet()

        val persisted = awaitSession(fixture.session.id) { it.sets.size == 1 }
        assertEquals(100.0, persisted.sets.single().weightKg, 0.0001)
        assertEquals(5, persisted.sets.single().reps)
        assertNull(vm.uiState.value.error)
        val record = eventually { vm.personalRecord.value }
        assertEquals("Squat", record.exerciseName)
        assertTrue(record.kinds.isNotEmpty())

        vm.onPersonalRecordShown()
        assertNull(vm.personalRecord.value)
    }

    @Test
    fun logSetWriteFailureSurfacesErrorInsteadOfPretendingSuccess() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()
        vm.setWeight(100.0)

        deps.database.workoutDao().deleteSession(fixture.session.id)
        vm.logSet()

        val state = vm.awaitState { it.error != null }
        assertTrue(state.error.orEmpty().contains("no longer available", ignoreCase = true))
        assertNull(deps.workoutRepository.getSession(fixture.session.id))
    }

    @Test
    fun applyMicroRecFillsDraftAndDoesNotLog() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.setWeight(100.0)
        vm.logSet()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        vm.skipRest()

        val rec = checkNotNull(
            withTimeout(5_000) {
                vm.microRec.first {
                    it?.reasonCode == SetMicroRecCalculator.SKIP_RPE_HOLD &&
                        it.showApply &&
                        !it.previewOnly
                }
            },
        )
        assertEquals(100.0, rec.nextWeightKg, 0.0001)
        assertEquals(5, rec.nextReps)
        assertEquals(8, rec.nextRpe)

        vm.setWeight(80.0)
        dispatcher.scheduler.advanceUntilIdle()
        vm.applyMicroRec()
        dispatcher.scheduler.advanceUntilIdle()
        val draft = vm.awaitState { it.draft.weightKg == 100.0 && it.draft.rpe == 8 }.draft
        assertEquals(100.0, draft.weightKg, 0.0001)
        assertEquals(5, draft.reps)
        assertEquals(8, draft.rpe)
        assertEquals(1, deps.workoutRepository.getSession(fixture.session.id)!!.sets.size)
    }

    @Test
    fun logDoesNotAutoApplyInTank() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.setWeight(100.0)
        vm.setRpe(6)
        vm.logSet()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 && it.draft.rpe == null }
        vm.skipRest()

        val rec = checkNotNull(
            withTimeout(5_000) {
                vm.microRec.first {
                    it?.reasonCode == SetMicroRecCalculator.IN_TANK &&
                        it.showApply &&
                        !it.previewOnly
                }
            },
        )
        assertEquals(102.5, rec.nextWeightKg, 0.0001)
        assertTrue(rec.showApply)
        val draft = vm.awaitState { it.draft.rpe == null && it.session?.sets?.size == 1 }.draft
        assertEquals(100.0, draft.weightKg, 0.0001)
        assertNull(draft.rpe)
    }

    @Test
    fun editingHidesMicroRec() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.logSet()
        val persisted = awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        vm.skipRest()
        withTimeout(5_000) { vm.microRec.first { it != null && !it.previewOnly } }
        vm.editSet(persisted.sets.single().id)
        vm.awaitState { it.editingSetId != null }
        withTimeout(5_000) { vm.microRec.first { it == null } }
        vm.cancelEdit()
        vm.awaitState { it.editingSetId == null }
        withTimeout(5_000) { vm.microRec.first { it != null } }
        Unit
    }

    @Test
    fun liftDoneHidesUse() = runBlocking {
        val fixture = seedWorkout(targetSets = 1)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.logSet()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        val rec = checkNotNull(
            withTimeout(5_000) {
                vm.microRec.first { it?.reasonCode == SetMicroRecCalculator.LIFT_DONE }
            },
        )
        assertFalse(rec.showApply)
        assertFalse(rec.previewOnly)
    }

    @Test
    fun stepperWithoutRpeDoesNotChangeMicroRec() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.setWeight(100.0)
        vm.logSet()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        vm.skipRest()
        val rec = checkNotNull(
            withTimeout(5_000) {
                vm.microRec.first { it?.reasonCode == SetMicroRecCalculator.SKIP_RPE_HOLD }
            },
        )
        vm.adjustWeight(2.5)
        dispatcher.scheduler.advanceUntilIdle()
        val after = checkNotNull(
            withTimeout(5_000) {
                vm.microRec.first { it?.reasonCode == SetMicroRecCalculator.SKIP_RPE_HOLD }
            },
        )
        assertEquals(rec.nextWeightKg, after.nextWeightKg, 0.0001)
        vm.awaitState { it.draft.weightKg == 102.5 }
        Unit
    }

    @Test
    fun workingSetBeforeTargetStartsPlannedRest() = runBlocking {
        val fixture = seedWorkout(targetSets = 3, restSeconds = 75)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }

        vm.logSet()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        eventually { deps.restTimerStore.current().takeIf { it.running } }

        val rest = deps.restTimerStore.current()
        assertEquals(fixture.session.id, rest.sessionId)
        assertEquals(75, rest.totalSeconds)
    }

    @Test
    fun warmupAndLastTargetSetDoNotAutoStartRest() = runBlocking {
        val warmupFixture = seedWorkout(targetSets = 3)
        val warmupVm = createViewModel(warmupFixture.session.id)
        // Prefill writes the whole draft, including isWarmup = false. Wait for it
        // to finish so it cannot clobber the warmup flag after the user sets it.
        warmupVm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        deps.restTimerController.stop()
        warmupVm.setWeight(40.0)
        warmupVm.awaitState { it.draft.weightKg == 40.0 }
        warmupVm.setWarmup(true)
        warmupVm.awaitState { it.draft.isWarmup }
        warmupVm.logSet()
        val warmupSession = awaitSession(warmupFixture.session.id) { it.sets.size == 1 }
        assertTrue(warmupSession.sets.single().isWarmup)
        assertFalse(deps.restTimerStore.current().running)

        deps.workoutRepository.discardSession(warmupFixture.session.id)
        deps.restTimerController.stop()
        val finalFixture = seedWorkout(targetSets = 1)
        val finalVm = createViewModel(finalFixture.session.id)
        finalVm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        deps.restTimerController.stop()
        finalVm.setWeight(100.0)
        finalVm.awaitState { it.draft.weightKg == 100.0 }
        finalVm.logSet()
        val finalSession = awaitSession(finalFixture.session.id) { it.sets.size == 1 }
        assertFalse(finalSession.sets.single().isWarmup)
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun manualRestUsesSelectedDurationAndCanBeSkipped() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()

        vm.selectRestDuration(105)
        vm.startSelectedRest()
        eventually { deps.restTimerStore.current().takeIf { it.running } }
        assertEquals(105, deps.restTimerStore.current().totalSeconds)

        vm.skipRest()
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun startingRestMarksExactAlarmPromptEligible() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()

        assertFalse(deps.preferencesRepository.restAlarmEligible.first())
        vm.startSelectedRest()
        eventually { deps.restTimerStore.current().takeIf { it.running } }
        withTimeout(5_000) { deps.preferencesRepository.restAlarmEligible.first { it } }
        Unit
    }

    @Test
    fun editUpdatesExistingSetWithoutStartingAnotherRestOrRecord() = runBlocking {
        val fixture = seedWorkout(targetSets = 1)
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()
        vm.setWeight(100.0)
        vm.logSet()
        val logged = awaitSession(fixture.session.id) { it.sets.size == 1 }.sets.single()
        vm.awaitState { state -> state.session?.sets?.any { it.id == logged.id } == true }
        vm.onPersonalRecordShown()
        vm.skipRest()

        vm.editSet(logged.id)
        vm.setWeight(105.0)
        vm.adjustReps(1)
        vm.logSet()

        val updated = awaitSession(fixture.session.id) {
            it.sets.singleOrNull()?.weightKg == 105.0
        }.sets.single()
        assertEquals(logged.id, updated.id)
        assertEquals(6, updated.reps)
        assertNull(vm.personalRecord.value)
        assertFalse(deps.restTimerStore.current().running)
        assertNull(vm.uiState.value.editingSetId)
    }

    @Test
    fun deleteLatestOffersUndoStopsRestAndUndoDoesNotRestartIt() = runBlocking {
        val fixture = seedWorkout(targetSets = 3)
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()
        vm.setWeight(100.0)
        vm.logSet()
        val logged = awaitSession(fixture.session.id) { it.sets.size == 1 }.sets.single()
        vm.awaitState { state -> state.session?.sets?.any { it.id == logged.id } == true }
        eventually { deps.restTimerStore.current().takeIf { it.running } }

        vm.deleteSet(logged.id)
        eventually { vm.deletedSet.value }
        awaitSession(fixture.session.id) { it.sets.isEmpty() }
        assertFalse(deps.restTimerStore.current().running)

        vm.undoDeleteSet()
        val restored = awaitSession(fixture.session.id) { it.sets.size == 1 }.sets.single()
        assertEquals(logged.id, restored.id)
        assertNull(vm.deletedSet.value)
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun notesWaitForTypingPauseAndExitFlushesImmediately() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()

        vm.setNotes("first")
        dispatcher.scheduler.advanceTimeBy(399)
        assertEquals("", deps.workoutRepository.getSession(fixture.session.id)!!.notes)
        vm.setNotes("final note")
        dispatcher.scheduler.advanceTimeBy(401)
        dispatcher.scheduler.runCurrent()
        val debounced = awaitSession(fixture.session.id) { it.notes == "final note" }
        assertEquals("final note", debounced.notes)

        vm.setNotes("leave now")
        vm.persistDraftForExit()
        val flushed = awaitSession(fixture.session.id) { it.notes == "leave now" }
        assertEquals("leave now", flushed.notes)
        assertEquals("leave now", deps.workoutDraftCache.get(fixture.session.id)?.notes)
    }

    @Test
    fun addDuplicateSwapAndRemoveFollowSessionRules() = runBlocking {
        val fixture = seedWorkout()
        insertExercise(ROW, "Row")
        val row = checkNotNull(deps.exerciseRepository.getById(ROW))
        val squat = checkNotNull(deps.exerciseRepository.getById(SQUAT))
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()

        vm.setPickerVisible(true)
        vm.addExercise(row)
        var session = awaitSession(fixture.session.id) { it.exercises.size == 2 }
        assertEquals(ROW, vm.awaitState { it.selectedExerciseId == ROW }.selectedExerciseId)
        assertFalse(vm.uiState.value.showExercisePicker)

        vm.setPickerVisible(true)
        vm.addExercise(row)
        session = awaitSession(fixture.session.id) { it.exercises.size == 2 }
        assertEquals(2, session.exercises.size)

        vm.requestSwap()
        vm.addExercise(squat)
        val blocked = vm.awaitState { it.error != null }
        assertTrue(blocked.error.orEmpty().contains("already", ignoreCase = true))
        assertEquals(2, deps.workoutRepository.getSession(fixture.session.id)!!.exercises.size)

        vm.setPickerVisible(false)
        vm.removeSelectedLift()
        session = awaitSession(fixture.session.id) { it.exercises.size == 1 }
        assertEquals(SQUAT, session.exercises.single().exercise.id)
    }

    @Test
    fun swapUnloggedLiftKeepsPositionAndTargets() = runBlocking {
        val fixture = seedWorkout(targetSets = 4, restSeconds = 120)
        insertExercise(ROW, "Row")
        val row = checkNotNull(deps.exerciseRepository.getById(ROW))
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()

        vm.requestSwap()
        vm.addExercise(row)

        val session = awaitSession(fixture.session.id) {
            it.exercises.singleOrNull()?.exercise?.id == ROW
        }
        with(session.exercises.single()) {
            assertEquals(0, sortOrder)
            assertEquals(4, targetSets)
            assertEquals(120, restSeconds)
            assertNull(targetWeightKg)
        }
    }

    @Test
    fun removeLoggedLiftIsBlockedWithUserMessage() = runBlocking {
        val fixture = seedWorkout(targetSets = 1)
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()
        vm.setWeight(100.0)
        vm.logSet()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        vm.skipRest()
        vm.awaitState { it.session?.sets?.size == 1 }

        vm.removeSelectedLift()

        val state = vm.awaitState { it.error != null }
        assertTrue(state.error.orEmpty().contains("set", ignoreCase = true))
        assertEquals(1, deps.workoutRepository.getSession(fixture.session.id)!!.exercises.size)
    }

    @Test
    fun finishWithNoSetsStaysLiveAndShowsGuard() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()

        vm.finishWorkout()

        val state = vm.awaitState { it.error != null }
        assertEquals("Log at least one set before finishing.", state.error)
        assertNull(vm.exitRequested.value)
        assertNull(deps.workoutRepository.getSession(fixture.session.id)?.finishedAt)
    }

    @Test
    fun finishWithSetPersistsHistoryClearsDraftAndEmitsOneShotExit() = runBlocking {
        val fixture = seedWorkout(targetSets = 1)
        val handle = handleFor(fixture.session.id)
        val vm = createViewModel(fixture.session.id, handle)
        vm.awaitFound()
        vm.setWeight(100.0)
        vm.logSet()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        vm.skipRest()
        assertNotNull(SavedStateWorkoutDraft(handle).read(fixture.session.id))

        vm.finishWorkout()

        val exit = eventually { vm.exitRequested.value }
        assertEquals(WorkoutExit.Finished(fixture.session.id), exit)
        assertNotNull(deps.workoutRepository.getSession(fixture.session.id)?.finishedAt)
        assertNull(deps.workoutRepository.getInProgress())
        assertNull(deps.workoutDraftCache.get(fixture.session.id))
        assertNull(SavedStateWorkoutDraft(handle).read(fixture.session.id))
        assertTrue(vm.uiState.value.finished)

        vm.onExitHandled()
        assertNull(vm.exitRequested.value)
    }

    @Test
    fun discardDeletesSessionClearsDraftAndEmitsOneShotExit() = runBlocking {
        val fixture = seedWorkout()
        val handle = handleFor(fixture.session.id)
        val vm = createViewModel(fixture.session.id, handle)
        vm.awaitFound()
        vm.setWeight(87.5)
        assertNotNull(SavedStateWorkoutDraft(handle).read(fixture.session.id))

        vm.discardWorkout()

        val exit = eventually { vm.exitRequested.value }
        assertEquals(WorkoutExit.Discarded, exit)
        assertNull(deps.workoutRepository.getSession(fixture.session.id))
        assertNull(deps.workoutDraftCache.get(fixture.session.id))
        assertNull(SavedStateWorkoutDraft(handle).read(fixture.session.id))

        vm.onExitHandled()
        assertNull(vm.exitRequested.value)
    }

    @Test
    fun processRecreationRestoresDraftFromSavedStateWithoutCache() = runBlocking {
        val fixture = seedWorkout()
        val handle = handleFor(fixture.session.id)
        val first = createViewModel(fixture.session.id, handle)
        first.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.weightKg == 100.0
        }
        first.setWeight(132.5)
        first.adjustReps(3)
        first.setRpe(9)
        first.setNotes("survive process")
        assertEquals(
            132.5,
            SavedStateWorkoutDraft(handle).read(fixture.session.id)?.weightKg ?: -1.0,
            0.0001,
        )
        first.clearAndJoinForTest()
        viewModels.remove(first)
        deps.workoutDraftCache.clearAll()

        val recreated = createViewModel(fixture.session.id, handle)
        val state = recreated.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.weightKg == 132.5
        }

        assertEquals(8, state.draft.reps)
        assertEquals(9, state.draft.rpe)
        assertEquals("survive process", state.notes)
    }

    private fun createViewModel(
        sessionId: String,
        handle: SavedStateHandle = handleFor(sessionId),
    ): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = handle,
            container = deps,
        ).also(viewModels::add)

    private suspend fun ActiveWorkoutViewModel.awaitFound(): ActiveWorkoutUiState =
        awaitState { it.loadState == SessionLoadState.FOUND }

    private suspend fun ActiveWorkoutViewModel.awaitState(
        predicate: (ActiveWorkoutUiState) -> Boolean,
    ): ActiveWorkoutUiState = withTimeout(5_000) {
        uiState.first(predicate)
    }

    private suspend fun awaitSession(
        sessionId: String,
        predicate: (WorkoutSession) -> Boolean,
    ): WorkoutSession = eventually {
        deps.workoutRepository.getSession(sessionId)?.takeIf(predicate)
    }

    private suspend fun <T : Any> eventually(block: suspend () -> T?): T =
        withTimeout(5_000) {
            while (true) {
                dispatcher.scheduler.runCurrent()
                block()?.let { return@withTimeout it }
                delay(10)
            }
            error("unreachable")
        }

    private suspend fun seedWorkout(
        targetSets: Int = 3,
        targetWeightKg: Double = 100.0,
        restSeconds: Int = 90,
        priorWeightKg: Double? = null,
    ): SeededWorkout {
        insertExercise(SQUAT, "Squat")
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(
                id = ROUTINE,
                name = "Lower",
                notes = "",
                createdAt = STAMP,
                updatedAt = STAMP,
            ),
        )
        deps.database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-$SQUAT",
                routineId = ROUTINE,
                exerciseId = SQUAT,
                sortOrder = 0,
                targetSets = targetSets,
                targetReps = 5,
                targetWeightKg = targetWeightKg.takeIf { it > 0.0 },
                restSeconds = restSeconds,
            ),
        )
        val routine = checkNotNull(deps.routineRepository.getById(ROUTINE))
        if (priorWeightKg != null) {
            val prior = deps.workoutRepository.startRoutine(routine)
            deps.workoutRepository.logSet(
                sessionId = prior.id,
                exerciseId = SQUAT,
                weightKg = priorWeightKg,
                reps = 5,
                rpe = null,
                isWarmup = false,
            )
            deps.workoutRepository.finishSession(prior.id, notes = "")
        }
        return SeededWorkout(deps.workoutRepository.startRoutine(routine))
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
                    nameKey = name.lowercase(),
                ),
            ),
        )
    }

    private fun handleFor(sessionId: String): SavedStateHandle =
        SavedStateHandle(mapOf("sessionId" to sessionId))

    private fun draft(sessionId: String, weightKg: Double, reps: Int) =
        WorkoutDraft(
            sessionId = sessionId,
            exerciseId = SQUAT,
            weightKg = weightKg,
            reps = reps,
            rpe = null,
            isWarmup = false,
            notes = "",
        )

    private data class SeededWorkout(val session: WorkoutSession)

    private companion object {
        const val SQUAT = "squat"
        const val ROW = "row"
        const val ROUTINE = "routine-lower"
        const val STAMP = 1_700_000_000_000L
    }
}
