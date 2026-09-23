package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.FinishedWorkingSetRow
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.domain.FloorCompactChrome
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.FloorStepper
import com.sinura.personaltrainer.domain.FloorTimedMode
import com.sinura.personaltrainer.domain.FloorTimedModeResolver
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.LogCommitFeedback
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.UndoKind
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutAdvance
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.ControllableTimePort
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import kotlinx.coroutines.CompletableDeferred
import com.sinura.personaltrainer.workout.WorkoutDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
    private lateinit var dispatcher: TestDispatcher
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        runBlocking {
            viewModels.forEach { it.clearAndJoinForTest() }
        }
        viewModels.clear()
        if (::deps.isInitialized) deps.restTimerController.stop()
        if (::dispatcher.isInitialized) dispatcher.scheduler.advanceUntilIdle()
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
    fun selectingSelectedLiftDoesNotPrefillOrClearDraft() = runBlocking {
        val fixture = seedWorkout(targetWeightKg = 100.0)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg == 100.0 }

        vm.setWeight(155.0)
        vm.setReps(8)
        vm.setRpe(8)
        vm.awaitState {
            it.draft.weightKg == 155.0 && it.draft.reps == 8 && it.draft.rpe == 8
        }
        vm.selectExercise(SQUAT)

        val state = vm.awaitState { it.draft.weightKg == 155.0 }
        assertEquals(155.0, state.draft.weightKg, 0.0001)
        assertEquals(8, state.draft.reps)
        assertEquals(8, state.draft.rpe)
        assertFalse(state.draft.isWarmup)
        assertTrue(state.draftDirty)
    }

    @Test
    fun switchingLiftsRestoresEachDraftAndNeverResets() = runBlocking {
        val fixture = seedTwoLifts()
        val handle = handleFor(fixture.session.id)
        val vm = createViewModel(fixture.session.id, handle)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg == 100.0 }

        vm.setWeight(155.0)
        vm.setReps(8)
        vm.setRpe(8)
        vm.awaitState { it.draftDirty && it.draft.weightKg == 155.0 }

        vm.selectExercise(ROW)
        val row = vm.awaitState {
            it.selectedExerciseId == ROW && it.liftReadiness.allowsCommit() && it.draft.weightKg == 80.0
        }
        assertEquals(80.0, row.draft.weightKg, 0.0001)
        assertFalse(row.draftDirty)

        vm.setWeight(87.5)
        vm.setReps(6)
        vm.awaitState { it.selectedExerciseId == ROW && it.draft.weightKg == 87.5 }

        vm.selectExercise(SQUAT)
        val back = vm.awaitState {
            it.selectedExerciseId == SQUAT && it.draft.weightKg == 155.0
        }
        assertEquals(155.0, back.draft.weightKg, 0.0001)
        assertEquals(8, back.draft.reps)
        assertEquals(8, back.draft.rpe)
        assertTrue(back.draftDirty)

        vm.selectExercise(SQUAT)
        val same = vm.awaitState { it.selectedExerciseId == SQUAT }
        assertEquals(155.0, same.draft.weightKg, 0.0001)

        assertEquals(155.0, SavedStateWorkoutDraft(handle).readLift(fixture.session.id, SQUAT)?.weightKg)
        assertEquals(87.5, SavedStateWorkoutDraft(handle).readLift(fixture.session.id, ROW)?.weightKg)

        vm.clearAndJoinForTest()
        viewModels.remove(vm)
        deps.workoutDraftCache.clearAll()

        val recreated = createViewModel(fixture.session.id, handle)
        val restored = recreated.awaitState {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == SQUAT && it.draft.weightKg == 155.0
        }
        assertEquals(8, restored.draft.reps)
        recreated.selectExercise(ROW)
        val restoredRow = recreated.awaitState {
            it.selectedExerciseId == ROW && it.draft.weightKg == 87.5
        }
        assertEquals(6, restoredRow.draft.reps)
    }

    @Test
    fun logSetRejectsZeroWeightWorkingSetBeforeWriting() = runBlocking {
        val fixture = seedWorkout(targetWeightKg = 0.0)
        val vm = createViewModel(fixture.session.id)
        // Settled, not merely FOUND: a tap during prefill's tail is dropped without a word,
        // and then no refusal ever comes.
        vm.awaitPrefilled(weightKg = 0.0)

        vm.setWeight(0.0)
        // A refusal writes nothing, so there is no save to wait for; wait for the refusal.
        vm.logSet()

        val state = vm.awaitState { it.error != null }
        assertTrue(state.error.orEmpty().contains("weight", ignoreCase = true))
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
        assertFalse(state.logging)
        assertNull(vm.personalRecord.value)
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun logSetPersistsSetClearsErrorAndEmitsRecord() = runBlocking {
        val fixture = seedWorkout(priorWeightKg = 80.0)
        val vm = createViewModel(fixture.session.id)
        // Prefill can overwrite a typed 100 with the 80 kg + step suggestion if we log first.
        vm.awaitState {
            val suggested = it.hint?.suggestedWeightKg ?: return@awaitState false
            it.loadState == SessionLoadState.FOUND && it.draft.weightKg == suggested
        }

        vm.setWeight(100.0)
        vm.awaitState { it.draft.weightKg == 100.0 }
        vm.logSetAndSettle()

        val persisted = awaitSession(fixture.session.id) { it.sets.size == 1 }
        assertEquals(100.0, persisted.sets.single().weightKg, 0.0001)
        assertEquals(5, persisted.sets.single().reps)
        assertNull(vm.uiState.value.error)
        val record = checkNotNull(vm.personalRecord.awaitFirst { it != null })
        assertEquals("Squat", record.exerciseName)
        assertTrue(record.kinds.isNotEmpty())

        vm.onPersonalRecordShown()
        assertNull(vm.personalRecord.value)
    }

    @Test
    fun logSetWritesTheNumbersTheSteppersDisplay() = runBlocking {
        val fixture = seedWorkout(priorWeightKg = 80.0)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState {
            val suggested = it.hint?.suggestedWeightKg ?: return@awaitState false
            it.loadState == SessionLoadState.FOUND && it.draft.weightKg == suggested
        }

        val start = vm.uiState.value.draft
        val steppedKg = FloorStepper.nextWeightKg(
            currentKg = start.weightKg,
            unit = WeightUnit.KG,
            direction = 1,
            loadType = LoadType.EXTERNAL,
        )
        val steppedReps = FloorStepper.nextReps(start.reps, 1)
        vm.setWeight(steppedKg)
        vm.setReps(steppedReps)
        vm.awaitState { it.draft.weightKg == steppedKg && it.draft.reps == steppedReps }
        vm.logSetAndSettle()

        val persisted = awaitSession(fixture.session.id) { it.sets.size == 1 }
        assertEquals(steppedKg, persisted.sets.single().weightKg, 0.0001)
        assertEquals(steppedReps, persisted.sets.single().reps)
    }

    @Test
    fun typedEightySevenFiveIsWhatGetsLogged() = runBlocking {
        val fixture = seedWorkout(priorWeightKg = 80.0)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState {
            val suggested = it.hint?.suggestedWeightKg ?: return@awaitState false
            it.loadState == SessionLoadState.FOUND && it.draft.weightKg == suggested
        }

        vm.setWeight(87.5)
        vm.awaitState { it.draft.weightKg == 87.5 }
        vm.logSetAndSettle()

        val persisted = awaitSession(fixture.session.id) { it.sets.size == 1 }
        assertEquals(87.5, persisted.sets.single().weightKg, 0.0001)
    }

    @Test
    fun holdDraftSecondsStartTheWorkClockNotAFakeRep() = runBlocking {
        val fixture = seedHangWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.durationSeconds == 30
        }

        val stepped = FloorStepper.nextHoldSeconds(30, 1)
        vm.setHoldSeconds(stepped)
        vm.awaitState { it.draft.durationSeconds == stepped }
        vm.logSet()

        val hold = vm.holdTimer.value
        assertTrue(hold.running)
        assertEquals(stepped, hold.totalSeconds)
        assertEquals(stepped, hold.remainingSeconds)
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
    }

    @Test
    fun startingAHangSetStartsTheWorkTimerAndDoesNotLogAFakeRep() = runBlocking {
        val fixture = seedHangWorkout()
        assertEquals(30, fixture.session.exercises.single().targetSeconds)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.durationSeconds == 30
        }

        vm.logSet()

        val hold = vm.holdTimer.value
        assertTrue(hold.running)
        assertEquals(30, hold.totalSeconds)
        assertEquals(30, hold.remainingSeconds)
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun loggingAHangWritesSecondsNotAFakeOneRepAndThenRestStarts() = runBlocking {
        val fixture = seedHangWorkout()
        assertEquals(30, fixture.session.exercises.single().targetSeconds)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.durationSeconds == 30
        }

        vm.startHoldSet()
        assertTrue(vm.holdTimer.value.running)
        vm.logSetAndSettle()

        val persisted = awaitSession(fixture.session.id) { it.sets.size == 1 }
        val row = persisted.sets.single()
        assertNotNull(row.durationSeconds)
        assertTrue(row.durationSeconds!! >= 1)
        assertEquals(0, row.reps)
        assertTrue(
            "a hang must not log as 1 rep with no seconds",
            row.reps != 1 || row.durationSeconds != null,
        )
        assertFalse("a hang must not log as 1 rep with no seconds", row.reps == 1 && row.durationSeconds == null)
        awaitRestRunning()
        assertTrue(deps.restTimerStore.current().running)
        assertFalse(vm.holdTimer.value.running)
    }

    @Test
    fun loggingWithoutTheStopwatchLeavesDurationNull() = runBlocking {
        val fixture = seedWorkout(targetSets = 3)
        val vm = createViewModel(fixture.session.id)
        vm.awaitPrefilled()
        vm.logSetAndSettle()
        val row = awaitSession(fixture.session.id) { it.sets.size == 1 }.sets.single()
        assertNull(row.durationSeconds)
        assertEquals(5, row.reps)
    }

    @Test
    fun startingTheStopwatchThenLoggingKeepsRepsAndWritesSeconds() = runBlocking {
        val fixture = seedWorkout(targetSets = 3)
        val vm = createViewModel(fixture.session.id)
        vm.awaitPrefilled()
        vm.startSetStopwatch()
        assertTrue(vm.setStopwatch.value.running)
        assertTrue(vm.setStopwatch.value.used)
        vm.logSetAndSettle()
        val row = awaitSession(fixture.session.id) { it.sets.size == 1 }.sets.single()
        assertEquals(5, row.reps)
        assertNotNull(row.durationSeconds)
        assertTrue(row.durationSeconds!! >= 1)
        assertFalse(vm.setStopwatch.value.used)
        assertFalse(vm.setStopwatch.value.running)
    }

    @Test
    fun startingTheStopwatchCancelsARunningRestAlarm() = runBlocking {
        val fixture = seedWorkout(targetSets = 3, restSeconds = 75)
        val vm = createViewModel(fixture.session.id)
        vm.awaitPrefilled()
        vm.startSelectedRest()
        awaitRestRunning()
        vm.startSetStopwatch()
        assertTrue(vm.setStopwatch.value.running)
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun holdClockFollowsElapsedRealtimeAndDoesNotAutoLog() = runBlocking {
        val clock = ControllableTimePort()
        val fixture = seedHangWorkout()
        val vm = createViewModel(fixture.session.id, container = withClock(clock))
        vm.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.durationSeconds == 30
        }
        vm.startHoldSet()
        assertTrue(vm.holdTimer.value.running)
        assertEquals(0, vm.holdTimer.value.elapsedSeconds)
        assertEquals(30, vm.holdTimer.value.remainingSeconds)
        clock.advance(5_000)
        tickTimedWork()
        assertEquals(5, vm.holdTimer.value.elapsedSeconds)
        assertEquals(25, vm.holdTimer.value.remainingSeconds)
        clock.advance(25_000)
        tickTimedWork()
        assertTrue(vm.holdTimer.value.targetReached)
        assertFalse(vm.holdTimer.value.running)
        assertEquals(HoldWork.DONE, vm.holdTimer.value.clock)
        assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
        vm.logSetAndSettle()
        val row = awaitSession(fixture.session.id) { it.sets.size == 1 }.sets.single()
        assertEquals(30, row.durationSeconds)
        assertEquals(0, row.reps)
    }

    @Test
    fun stopwatchClockFollowsElapsedRealtime() = runBlocking {
        val clock = ControllableTimePort()
        val fixture = seedWorkout(targetSets = 3)
        val vm = createViewModel(fixture.session.id, container = withClock(clock))
        vm.awaitPrefilled()
        vm.startSetStopwatch()
        assertEquals(0, vm.setStopwatch.value.elapsedSeconds)
        clock.advance(3_000)
        tickTimedWork()
        assertEquals(3, vm.setStopwatch.value.elapsedSeconds)
        assertTrue(vm.setStopwatch.value.running)
        vm.stopSetStopwatch()
        assertFalse(vm.setStopwatch.value.running)
        assertEquals(3, vm.setStopwatch.value.elapsedSeconds)
        clock.advance(10_000)
        tickTimedWork()
        assertEquals(3, vm.setStopwatch.value.elapsedSeconds)
    }

    @Test
    fun startHoldCancelsRestAndBlocksTheStopwatch() = runBlocking {
        val fixture = seedHangWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.durationSeconds == 30
        }
        vm.startSelectedRest()
        awaitRestRunning()
        vm.startHoldSet()
        assertTrue(vm.holdTimer.value.running)
        assertFalse(deps.restTimerStore.current().running)
        vm.startSetStopwatch()
        assertTrue(vm.holdTimer.value.running)
        assertFalse(vm.setStopwatch.value.running)
        val mode = FloorTimedModeResolver.resolve(
            hasLifts = true,
            holdActive = vm.holdTimer.value.active,
            stopwatchRunning = vm.setStopwatch.value.running,
            restRunning = deps.restTimerStore.current().running,
            restComplete = false,
        )
        assertEquals(FloorTimedMode.HOLD_RUNNING, mode)
    }

    @Test
    fun switchingLiftWhileTheStopwatchRunsAsksFirst() = runBlocking {
        val fixture = seedTwoLifts(targetSets = 3)
        val vm = createViewModel(fixture.session.id)
        vm.awaitPrefilled()
        vm.startSetStopwatch()
        vm.selectExercise(ROW)
        assertEquals(ROW, vm.pendingLiftSwitch.value?.exerciseId)
        assertEquals(SQUAT, vm.uiState.value.selectedExerciseId)
        assertTrue(vm.setStopwatch.value.running)
        vm.cancelPendingLiftSwitch()
        assertNull(vm.pendingLiftSwitch.value)
        assertTrue(vm.setStopwatch.value.running)
        vm.selectExercise(ROW)
        vm.confirmStopTimingAndSwitch()
        assertNull(vm.pendingLiftSwitch.value)
        vm.awaitState { it.selectedExerciseId == ROW }
        assertFalse(vm.setStopwatch.value.running)
    }

    @Test
    fun processDeathRestoresARunningHoldFromSavedState() = runBlocking {
        val clock = ControllableTimePort()
        val fixture = seedHangWorkout()
        val handle = handleFor(fixture.session.id)
        val container = withClock(clock)
        val first = createViewModel(fixture.session.id, handle, container)
        first.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.durationSeconds == 30
        }
        first.startHoldSet()
        clock.advance(8_000)
        first.clearAndJoinForTest()
        val recreated = createViewModel(fixture.session.id, handle, container)
        recreated.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.durationSeconds == 30
        }
        assertTrue(recreated.holdTimer.value.running)
        assertEquals(8, recreated.holdTimer.value.elapsedSeconds)
        assertEquals(22, recreated.holdTimer.value.remainingSeconds)
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun rebootClearsAHoldWhenElapsedRealtimeWentBackwards() = runBlocking {
        val clock = ControllableTimePort()
        clock.advance(10_000)
        val fixture = seedHangWorkout()
        val handle = handleFor(fixture.session.id)
        val first = createViewModel(fixture.session.id, handle, container = withClock(clock))
        first.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.durationSeconds == 30
        }
        first.startHoldSet()
        assertTrue(first.holdTimer.value.running)
        first.clearAndJoinForTest()
        val rebooted = ControllableTimePort()
        val recreated = createViewModel(
            fixture.session.id,
            handle,
            container = withClock(rebooted),
        )
        recreated.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.durationSeconds == 30
        }
        assertFalse(recreated.holdTimer.value.running)
        assertEquals(0, recreated.holdTimer.value.totalSeconds)
    }

    @Test
    fun entryIsLockedDuringWriteAndEditableAgainAfterAcknowledgement() = runBlocking {
        // F3 makes the outstanding operation explicit. Disabled wells and stale callbacks
        // cannot replace its values; entry resumes after persistence acknowledges the save.
        val fixture = seedWorkout()
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(fixture.session.id, container = gatedLogSet(gate))
        try {
            vm.awaitPrefilled()
            vm.setWarmup(true)
            vm.awaitState { it.draft.isWarmup }

            vm.logSet()
            // The gate holds the insert open, so this is the window a lifter acts in: the tap
            // has happened, the row has not. Asserted, because a test that ran after the write
            // would prove nothing at all. No database read here — it would queue behind the
            // very transaction the gate is holding.
            val busy = vm.awaitState { it.logging }
            assertTrue(busy.entryLocked)
            vm.setWeight(110.0)
            vm.setReps(3)
            gate.complete(Unit)

            // The wait names the cleared warm-up as well as the finished write. `!logging`
            // alone is also true of the snapshot from before the tap, and a conflated stateIn
            // hands a new collector exactly that one first; the warm-up flag is what separates
            // "the log has run its course" from "the log has not started".
            val settled = vm.awaitState { !it.logging && !it.draft.isWarmup }
            assertNull(settled.error)
            assertEquals(100.0, settled.draft.weightKg, 0.0001)
            assertEquals(5, settled.draft.reps)
            assertFalse(settled.entryLocked)
            val persisted = awaitSession(fixture.session.id) { it.sets.size == 1 }
            assertEquals(100.0, persisted.sets.single().weightKg, 0.0001)
            assertEquals(5, persisted.sets.single().reps)
            vm.setWeight(110.0)
            vm.setReps(3)
            vm.awaitState { it.draft.weightKg == 110.0 && it.draft.reps == 3 }
            Unit
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun logClearsWarmupAndRpeWithoutTouchingTheNumbers() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitPrefilled()
        vm.setWeight(60.0)
        vm.setReps(12)
        vm.setWarmup(true)
        vm.awaitState { it.draft.isWarmup && it.draft.weightKg == 60.0 && it.draft.reps == 12 }

        vm.logSetAndSettle()

        // Warm-up and RPE are per-set and are spent by the log; the load and the reps stay,
        // because the next set starts from what the last one was. Every field asserted is
        // named in the wait — the pre-warm-up snapshot also has isWarmup false, and it is the
        // one a fresh collector can be handed first.
        val settled = vm.awaitState {
            !it.logging && !it.draft.isWarmup && it.draft.weightKg == 60.0 && it.draft.reps == 12
        }
        assertNull(settled.draft.rpe)
        assertEquals(60.0, settled.draft.weightKg, 0.0001)
        assertEquals(12, settled.draft.reps)
    }

    @Test
    fun rpeDoesNotMutateWeightReps() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitPrefilled()
        vm.setWeight(102.5)
        vm.setReps(6)
        vm.awaitState { it.draft.weightKg == 102.5 && it.draft.reps == 6 }

        vm.setRpe(8)
        val withRpe = vm.awaitState { it.draft.rpe == 8 }
        assertEquals(102.5, withRpe.draft.weightKg, 0.0001)
        assertEquals(6, withRpe.draft.reps)

        vm.setRpe(null)
        val cleared = vm.awaitState { it.draft.rpe == null }
        assertEquals(102.5, cleared.draft.weightKg, 0.0001)
        assertEquals(6, cleared.draft.reps)
    }

    @Test
    fun rpeHiddenInWarmupDoesNotKeepAWorkingEffort() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitPrefilled()
        vm.setRpe(9)
        vm.awaitState { it.draft.rpe == 9 }

        vm.setWarmup(true)
        val warmup = vm.awaitState { it.draft.isWarmup }
        assertNull(warmup.draft.rpe)
        assertFalse(FloorCompactChrome.showOptionalLogOptions(isWarmup = true))

        vm.setRpe(8)
        assertNull(vm.awaitState { it.draft.isWarmup }.draft.rpe)
    }

    @Test
    fun applyWarmupRampSetsWeightAndWarmupWithoutLoggingOrRest() = runBlocking {
        val fixture = seedWorkout(targetWeightKg = 100.0)
        val vm = createViewModel(fixture.session.id)
        vm.awaitPrefilled()
        vm.setRpe(7)
        vm.awaitState { it.draft.rpe == 7 }

        vm.applyWarmupRamp(40.0)
        val applied = vm.awaitState { it.draft.isWarmup && it.draft.weightKg == 40.0 }
        assertEquals(40.0, applied.draft.weightKg, 0.0001)
        assertTrue(applied.draft.isWarmup)
        assertNull(applied.draft.rpe)
        assertEquals(5, applied.draft.reps)
        assertTrue(applied.draftDirty)
        assertTrue(vm.uiState.value.session?.sets.isNullOrEmpty())

        vm.logSetAndSettle()
        val logged = awaitSession(fixture.session.id) { it.sets.size == 1 }
        assertTrue(logged.sets.single().isWarmup)
        assertEquals(40.0, logged.sets.single().weightKg, 0.0001)
        assertFalse(deps.restTimerStore.current().running)
        val after = vm.awaitState { !it.logging && !it.draft.isWarmup }
        assertEquals(40.0, after.draft.weightKg, 0.0001)
    }

    @Test
    fun rpeHelperDismissesPermanentlyWithoutARoomRow() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()
        assertTrue(vm.rpeHelperVisible.value)
        assertFalse(deps.preferencesRepository.rpeHelperDismissed.first())

        vm.dismissRpeHelper()
        assertTrue(deps.preferencesRepository.rpeHelperDismissed.first { it })
        assertFalse(
            withTimeout(TestWaits.FLOW_MS) { vm.rpeHelperVisible.first { !it } },
        )
    }

    @Test
    fun aTypedRepCountIsTheCountNotADistanceFromTheOldOne() = runBlocking {
        // setReps is what the keypad calls. It used to send a delta measured against the well
        // as it was when the keypad opened, so a well that moved in between landed the typed
        // number somewhere else entirely.
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitPrefilled()

        vm.setReps(8)
        assertEquals(8, vm.awaitState { it.draft.reps == 8 }.draft.reps)
        vm.setReps(20)
        assertEquals(20, vm.awaitState { it.draft.reps == 20 }.draft.reps)
        // The floor still holds: a set is at least one rep.
        vm.setReps(0)
        assertEquals(1, vm.awaitState { it.draft.reps == 1 }.draft.reps)
    }

    /**
     * The write fails at the row insert, with the session still present and FOUND.
     *
     * This used to delete the session row from under the ViewModel and log into the gap.
     * That was two different tests depending on which thread won: if the Log tap landed
     * first, Room refused the insert and the error surfaced as intended; if the session
     * reader observed the deletion first, the screen was already MISSING and F3's entry
     * lock refused the tap outright — correctly, and silently — so there was no error to
     * wait for and the wait ran out its 30 seconds (trunk run 35239125454). A DAO whose
     * insert throws is the failure this test is about, and it cannot lose that race.
     */
    @Test
    fun logSetWriteFailureSurfacesErrorInsteadOfPretendingSuccess() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id, container = failingInsert())
        vm.awaitFound()
        vm.setWeight(100.0)

        vm.logSetAndSettle()

        val state = vm.awaitState { it.error != null }
        assertEquals(LogCommitCopy.WRITE_FAILED, state.error)
        assertEquals(WorkoutSavePhase.FAILED, state.save.phase)
        assertEquals(100.0, state.draft.weightKg, 0.0001)
        assertFalse(state.logging)
        assertFalse(deps.restTimerStore.current().running)
        assertNull(vm.personalRecord.value)
        assertTrue(checkNotNull(deps.workoutRepository.getSession(fixture.session.id)).sets.isEmpty())
    }

    /**
     * Tapping Use on the progression strip must survive the process being reclaimed.
     *
     * Every other draft mutator on this class mirrors what it wrote, and nothing else
     * re-persists on its own: the session collector only persists on a Room emission, and
     * tapping a chip changes no row. `applySuggestedWeight` was the one that did not, so the
     * well read 82.5 while both mirrors still held the 70 the lifter had nudged it to — and a
     * phone reclaimed during the rest handed back the 70.
     */
    @Test
    fun tappingUseOnTheProgressionStripIsMirroredToTheDraft() = runBlocking {
        val fixture = seedWorkout(priorWeightKg = 80.0)
        val vm = createViewModel(fixture.session.id)
        val suggested = vm.awaitState {
            val hinted = it.hint?.suggestedWeightKg ?: return@awaitState false
            it.loadState == SessionLoadState.FOUND && it.draft.weightKg == hinted
        }.hint!!.suggestedWeightKg!!

        // The lifter nudges the well well away from the suggestion. That much was always
        // mirrored, which is what made the loss look like the app forgetting the LAST tap.
        vm.setWeight(70.0)
        vm.awaitState { it.draft.weightKg == 70.0 }
        assertEquals(70.0, checkNotNull(deps.workoutDraftCache.get(fixture.session.id)).weightKg, 0.0001)

        // Then changes their mind and takes the suggestion.
        vm.applySuggestedWeight()
        vm.awaitState { it.draft.weightKg == suggested }

        assertEquals(
            "the tap must reach the cache the recovery reads, not just the well on screen",
            suggested,
            checkNotNull(deps.workoutDraftCache.get(fixture.session.id)).weightKg,
            0.0001,
        )
    }

    @Test
    fun applyMicroRecFillsDraftAndDoesNotLog() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.setWeight(100.0)
        vm.setRpe(8)
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        vm.skipRest()

        val rec = checkNotNull(
            withTimeout(TestWaits.FLOW_MS) {
                vm.microRec.first {
                    it?.reasonCode == SetMicroRecCalculator.QUALITY &&
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
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 && it.draft.rpe == null }
        vm.skipRest()

        val rec = checkNotNull(
            withTimeout(TestWaits.FLOW_MS) {
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
    fun editingEarlierSetsAnnouncesTheirOwnOrdinalIncludingAChangedSetType() = runBlocking {
        val fixture = seedWorkout(targetSets = 3)
        repeat(5) { index ->
            val saved = deps.workoutRepository.logSet(sessionId = fixture.session.id, exerciseId = SQUAT,
                weightKg = 100.0, reps = 5, rpe = null, isWarmup = index < 2)
            val dao = deps.database.workoutDao()
            val row = checkNotNull(dao.getSet(saved.setId))
            // A clock correction may reverse timestamps without changing set order.
            dao.updateSet(row.copy(completedAt = STAMP + (5 - index) * 1_000L))
        }
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        val sets = vm.awaitState { it.session?.sets?.size == 5 }.session!!.setsFor(SQUAT)
        val firstWarmup = sets.first { it.isWarmup }
        val firstWorking = sets.first { !it.isWarmup }
        // Each edit taps after the previous save's tail has released the entry lock;
        // a tap during that tail is dropped by design (see awaitEntryUnlocked).
        vm.awaitEntryUnlocked()
        vm.editSet(firstWarmup.id)
        vm.awaitEditOpen(firstWarmup.id)
        vm.setWeight(45.0)
        vm.logSetAndSettle()
        withTimeout(TestWaits.FLOW_MS) { vm.logReceipt.first { it?.setId == firstWarmup.id && it.weightKg == 45.0 } }
        vm.awaitState { !it.logging && it.editingSetId == null }
        assertEquals(firstWarmup.id, vm.logReceipt.value?.setId)
        assertTrue(checkNotNull(vm.logReceipt.value).line.startsWith("Warm-up 1 logged"))
        vm.awaitEntryUnlocked()
        vm.editSet(firstWorking.id)
        vm.awaitEditOpen(firstWorking.id)
        vm.setWeight(75.0)
        vm.logSetAndSettle()
        withTimeout(TestWaits.FLOW_MS) { vm.logReceipt.first { it?.setId == firstWorking.id && it.weightKg == 75.0 } }
        vm.awaitState { !it.logging && it.editingSetId == null }
        assertEquals(firstWorking.id, vm.logReceipt.value?.setId)
        assertTrue(checkNotNull(vm.logReceipt.value).line.startsWith("Working set 1 of 3 logged"))
        vm.awaitEntryUnlocked()
        vm.editSet(firstWorking.id)
        vm.awaitEditOpen(firstWorking.id)
        vm.setWarmup(true)
        vm.logSetAndSettle()
        withTimeout(TestWaits.FLOW_MS) { vm.logReceipt.first { it?.setId == firstWorking.id && it.isWarmup } }
        assertTrue(checkNotNull(vm.logReceipt.value).line.startsWith("Warm-up 3 logged"))
        assertEquals(5, checkNotNull(deps.workoutRepository.getSession(fixture.session.id)).sets.size)
    }

    /**
     * Correcting the same set twice in a row, with the screen's copy of the session behind.
     *
     * The save carries the values the edit found, and the repository refuses it unless they
     * match the stored row. The screen's sets come from a Flow, and a correction changes no
     * set count, so there is nothing for anyone — test or lifter — to wait on between the
     * row landing and the Flow republishing it. An edit opened in that window used to carry
     * values one revision old and come back as "This set changed or was removed", on a set
     * the lifter had just corrected themselves.
     *
     * Freezing the Flow makes that window a fact rather than a race: without the fix this
     * fails every run, not two in three.
     */
    @Test
    fun aSecondCorrectionOfTheSameSetSavesWhileTheScreensCopyIsBehind() = runBlocking {
        val fixture = seedWorkout()
        val paused = MutableStateFlow(false)
        val vm = createViewModel(fixture.session.id, container = pausableSession(paused))
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.logSetAndSettle()
        val setId = vm.awaitState { it.session?.sets?.size == 1 }.session!!.sets.single().id
        val dao = deps.database.workoutDao()
        assertEquals(100.0, checkNotNull(dao.getSet(setId)).weightKg, 0.0001)

        paused.value = true

        vm.awaitEntryUnlocked()
        vm.editSet(setId)
        vm.awaitEditOpen(setId)
        vm.setWeight(110.0)
        // Not logSetAndSettle: it waits for the screen to show the saved rows, and this
        // test holds the screen's copy back on purpose. Wait on the stored row, then on the
        // edit closing — the pre-tap snapshot still had the edit open, so neither can pass
        // on stale state (a bare idle wait lost CI on 22 September, 110 read before the 120
        // write landed).
        vm.logSet()
        awaitSession(fixture.session.id) { it.sets.singleOrNull()?.weightKg == 110.0 }
        vm.awaitState { it.editingSetId == null && !it.entryLocked }
        dispatcher.scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS.toLong())
        dispatcher.scheduler.runCurrent()
        // The row moved; the screen's copy did not, and cannot until the Flow is released.
        assertEquals(110.0, checkNotNull(dao.getSet(setId)).weightKg, 0.0001)
        assertEquals(100.0, checkNotNull(vm.uiState.value.session?.sets?.single()).weightKg, 0.0001)

        vm.editSet(setId)
        vm.awaitEditOpen(setId)
        vm.setWeight(120.0)
        vm.logSet()
        awaitSession(fixture.session.id) { it.sets.singleOrNull()?.weightKg == 120.0 }
        val settled = vm.awaitState { it.editingSetId == null && !it.entryLocked }

        assertEquals(WorkoutSavePhase.IDLE, settled.save.phase)
        assertNull(settled.error)
        assertEquals(120.0, checkNotNull(dao.getSet(setId)).weightKg, 0.0001)
        assertEquals(1, checkNotNull(deps.workoutRepository.getSession(fixture.session.id)).sets.size)
    }

    /**
     * The quieter half of the same defect: a set the screen's copy has never carried.
     *
     * Reading the original from the observed session meant an Edit tap on a row the Flow had
     * not published yet found nothing and returned without a word — no edit, no error, a dead
     * tap. Reading the row answers it.
     */
    @Test
    fun editingOpensForASetTheScreensCopyHasNotSeenYet() = runBlocking {
        val fixture = seedWorkout()
        val paused = MutableStateFlow(false)
        val vm = createViewModel(fixture.session.id, container = pausableSession(paused))
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.awaitEntryUnlocked()

        paused.value = true
        val saved = deps.workoutRepository.logSet(
            sessionId = fixture.session.id,
            exerciseId = SQUAT,
            weightKg = 92.5,
            reps = 8,
            rpe = null,
            isWarmup = false,
        )
        assertTrue(vm.uiState.value.session?.sets.isNullOrEmpty())

        vm.editSet(saved.setId)

        val state = vm.awaitEditOpen(saved.setId)
        assertEquals(92.5, state.draft.weightKg, 0.0001)
        assertEquals(8, state.draft.reps)
    }

    @Test
    fun editingHidesMicroRec() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.logSetAndSettle()
        val persisted = awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        vm.skipRest()
        withTimeout(TestWaits.FLOW_MS) { vm.microRec.first { it != null && !it.previewOnly } }
        vm.editSet(persisted.sets.single().id)
        vm.awaitEditOpen(persisted.sets.single().id)
        withTimeout(TestWaits.FLOW_MS) { vm.microRec.first { it == null } }
        vm.cancelEdit()
        vm.awaitState { it.editingSetId == null }
        withTimeout(TestWaits.FLOW_MS) { vm.microRec.first { it != null } }
        Unit
    }

    @Test
    fun liftDoneHidesUse() = runBlocking {
        val fixture = seedWorkout(targetSets = 1)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        val rec = checkNotNull(
            withTimeout(TestWaits.FLOW_MS) {
                vm.microRec.first { it?.reasonCode == SetMicroRecCalculator.LIFT_DONE }
            },
        )
        assertFalse(rec.showApply)
        assertFalse(rec.previewOnly)
    }

    @Test
    fun selectingRpeOffersTheRecWithoutTouchingTheWells() = runBlocking {
        // Choosing an RPE used to fill the wells from the recommendation it unlocks, so a
        // load and a rep count the lifter had typed were replaced by numbers they had not
        // asked for. The recommendation is still raised — it sits above Log with its own
        // Use — but only that tap moves it into the wells.
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.setWeight(100.0)
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        vm.skipRest()
        withTimeout(TestWaits.FLOW_MS) { vm.microRec.first { it != null && !it.previewOnly } }

        // The numbers for the next set, dialled in by hand before the effort is rated.
        vm.setWeight(95.0)
        vm.setReps(8)
        vm.awaitState { it.draft.weightKg == 95.0 && it.draft.reps == 8 }

        vm.setRpe(6)

        val draft = vm.awaitState { it.draft.rpe == 6 }.draft
        assertEquals(95.0, draft.weightKg, 0.0001)
        assertEquals(8, draft.reps)
        // Offered, not applied: the rec the RPE unlocks is on screen with Use showing.
        val rec = checkNotNull(
            withTimeout(TestWaits.FLOW_MS) { vm.microRec.first { it != null && it.showApply } },
        )
        assertEquals(102.5, rec.nextWeightKg, 0.0001)
        assertEquals(1, deps.workoutRepository.getSession(fixture.session.id)!!.sets.size)
    }

    @Test
    fun usingTheOfferedRecIsWhatMovesItIntoTheWells() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.setWeight(100.0)
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        // The row lands mid-save; effort typed before the save releases is refused, by design.
        vm.awaitEntryUnlocked()
        vm.skipRest()
        vm.setRpe(6)
        withTimeout(TestWaits.FLOW_MS) { vm.microRec.first { it != null && it.showApply } }

        vm.applyMicroRec()

        val draft = vm.awaitState { it.draft.weightKg == 102.5 }.draft
        assertEquals(102.5, draft.weightKg, 0.0001)
    }

    @Test
    fun askingForAnExtraSetLeavesTheWellsAsTheyAre() = runBlocking {
        // Same rule as the RPE chip: arming an extra set raises a recommendation for it, and
        // the lifter takes it with Use. It does not reach in and retype the wells.
        val fixture = seedWorkout(targetSets = 1)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.setWeight(100.0)
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        // The row lands mid-save; typing and the ask are refused, by design, until the save
        // releases.
        vm.awaitEntryUnlocked()
        vm.setWeight(80.0)
        vm.setReps(12)
        vm.awaitState { it.draft.weightKg == 80.0 && it.draft.reps == 12 }

        vm.requestExtraSet()

        val draft = vm.awaitState { it.draft.weightKg == 80.0 && it.draft.reps == 12 }.draft
        assertEquals(80.0, draft.weightKg, 0.0001)
        assertEquals(12, draft.reps)
        assertTrue(vm.extraSetRequested.value)
    }

    @Test
    fun requestExtraSetReopensTheRecAndStartsRestOnTheExtraLog() = runBlocking {
        val fixture = seedWorkout(targetSets = 1)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.setWeight(100.0)
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        withTimeout(TestWaits.FLOW_MS) { vm.microRec.first { it?.reasonCode == SetMicroRecCalculator.LIFT_DONE } }
        assertFalse(deps.restTimerStore.current().running)

        // The row lands mid-save; the ask is refused, by design, until the save releases.
        vm.awaitEntryUnlocked()
        vm.requestExtraSet()
        assertTrue(vm.extraSetRequested.value)
        val rec = checkNotNull(
            withTimeout(TestWaits.FLOW_MS) {
                vm.microRec.first { it != null && it.reasonCode != SetMicroRecCalculator.LIFT_DONE }
            },
        )
        assertTrue(rec.showApply)
        assertEquals(100.0, rec.nextWeightKg, 0.0001)

        vm.setWeight(100.0)
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 2 }
        awaitRestRunning()
        assertFalse(vm.extraSetRequested.value)
    }

    @Test
    fun restWaitAdvancesAReceiptDelayScheduledAfterTheInitialClockAdvance() = runBlocking {
        val fixture = seedWorkout()
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(fixture.session.id, container = gatedLogSet(gate))
        vm.awaitPrefilled()

        vm.logSet()
        vm.awaitState { it.logging }
        // Room has not returned yet. Advancing here cannot run a rest job that
        // the ViewModel will only schedule after the insert completes.
        dispatcher.scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS.toLong())
        dispatcher.scheduler.runCurrent()
        assertFalse(deps.restTimerStore.current().running)
        gate.complete(Unit)
        vm.awaitState { !it.logging && vm.logReceipt.value != null }
        assertFalse(deps.restTimerStore.current().running)

        awaitRestRunning()

        assertEquals(1, awaitSession(fixture.session.id) { it.sets.size == 1 }.sets.size)
        assertTrue(deps.restTimerStore.current().running)
    }

    @Test
    fun advancingSelectsTheNextLiftAndClearsTheExtraAsk() = runBlocking {
        val fixture = seedTwoLifts(targetSets = 1)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == SQUAT }
        vm.setWeight(100.0)
        vm.awaitState { it.draft.weightKg == 100.0 }
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        // The row lands mid-save; the ask is refused, by design, until the save releases.
        vm.awaitEntryUnlocked()
        vm.requestExtraSet()
        assertTrue(vm.extraSetRequested.value)

        vm.advanceToNextLift(ROW)
        val state = vm.awaitState { it.selectedExerciseId == ROW }
        assertEquals(ROW, state.selectedExerciseId)
        assertFalse(vm.extraSetRequested.value)
    }

    @Test
    fun startNextLiftAdvancesWhenTheCurrentLiftIsDone() = runBlocking {
        val fixture = seedTwoLifts(targetSets = 1)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == SQUAT }
        vm.setWeight(100.0)
        vm.awaitState { it.draft.weightKg == 100.0 }
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        // The repository flow publishes the row before the ViewModel's own session
        // projection re-queries it; startNextLift decides "lift done" from the latter.
        vm.awaitState { it.session?.sets?.size == 1 }
        vm.startNextLift()
        vm.awaitState { it.selectedExerciseId == ROW }
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun finishingTargetSetsOffersStandingAdvanceClearedOnlyByChoice() = runBlocking {
        val fixture = seedTwoLifts(targetSets = 1)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == SQUAT }
        vm.setWeight(100.0)
        vm.awaitState { it.draft.weightKg == 100.0 }
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        val pending = withTimeout(TestWaits.FLOW_MS) {
            vm.pendingAdvance.first { it != null }
        }
        assertNotNull(pending)
        assertEquals(SQUAT, pending!!.finishedExerciseId)
        assertEquals(ROW, pending.nextExerciseId)
        assertEquals(SQUAT, vm.uiState.value.selectedExerciseId)

        // Dwell time must not move the loop; the offer stays until chosen.
        dispatcher.scheduler.advanceTimeBy(Motion.STATUS_DWELL_MS + 1_000L)
        assertNotNull(vm.pendingAdvance.value)
        assertEquals(SQUAT, vm.uiState.value.selectedExerciseId)

        vm.stayOnCurrentExercise()
        assertNull(vm.pendingAdvance.value)
        assertTrue(vm.extraSetRequested.value)
        assertEquals(SQUAT, vm.uiState.value.selectedExerciseId)
    }

    @Test
    fun lastLiftOffersFinishNotOpenEndedLog() = runBlocking {
        val fixture = seedWorkout(targetSets = 1)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == SQUAT }
        vm.setWeight(100.0)
        vm.awaitState { it.draft.weightKg == 100.0 }
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        val pending = withTimeout(TestWaits.FLOW_MS) { vm.pendingAdvance.first { it != null } }
        assertNotNull(pending)
        assertNull(pending!!.nextExerciseId)
        val session = checkNotNull(vm.uiState.value.session)
        val advance = WorkoutAdvance.forSelection(
            session = session,
            selectedExerciseId = SQUAT,
            wantAnother = vm.extraSetRequested.value,
            editing = false,
        )
        assertTrue(advance.liftComplete)
        assertTrue(advance.showFinish)
        assertFalse(advance.showNext)
        assertTrue(advance.showAnother)
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun loggedReceiptMatchesTheCapturedPayloadAndRestWaitsForSettle() = runBlocking {
        val fixture = seedWorkout(targetSets = 3, restSeconds = 75)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.setWeight(100.0)
        vm.setReps(5)
        vm.setRpe(8)
        vm.awaitState { it.draft.rpe == 8 }

        vm.logSet()
        vm.awaitState { !it.logging }
        val receipt = checkNotNull(vm.logReceipt.value)
        val row = checkNotNull(deps.workoutRepository.getSession(fixture.session.id)).sets.single()
        assertEquals(row.id, receipt.setId)
        assertEquals(100.0, receipt.weightKg, 0.0001)
        assertEquals(5, receipt.reps)
        assertEquals(8, receipt.rpe)
        assertTrue(receipt.line.contains("logged"))
        assertTrue(receipt.line.contains("100 kg × 5"))
        assertTrue(receipt.line.contains("RPE 8"))
        assertFalse(deps.restTimerStore.current().running)

        dispatcher.scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS.toLong())
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.advanceUntilIdle()
        awaitRestRunning()
        assertEquals(fixture.session.id, deps.restTimerStore.current().sessionId)
    }

    @Test
    fun advanceNowTakesTheStandingNextLift() = runBlocking {
        val fixture = seedTwoLifts(targetSets = 1)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == SQUAT }
        vm.setWeight(100.0)
        vm.awaitState { it.draft.weightKg == 100.0 }
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        withTimeout(TestWaits.FLOW_MS) { vm.pendingAdvance.first { it != null } }
        vm.advanceNow()
        vm.awaitState { it.selectedExerciseId == ROW }
        assertNull(vm.pendingAdvance.value)
        assertFalse(vm.extraSetRequested.value)
    }

    @Test
    fun stepperWithoutRpeDoesNotChangeMicroRec() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        vm.setWeight(100.0)
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        vm.skipRest()
        val rec = checkNotNull(
            withTimeout(TestWaits.FLOW_MS) {
                vm.microRec.first { it?.reasonCode == SetMicroRecCalculator.SKIP_RPE_HOLD }
            },
        )
        vm.adjustWeight(2.5)
        dispatcher.scheduler.advanceUntilIdle()
        val after = checkNotNull(
            withTimeout(TestWaits.FLOW_MS) {
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

        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        awaitRestRunning()

        val rest = deps.restTimerStore.current()
        assertEquals(fixture.session.id, rest.sessionId)
        assertEquals(150, rest.totalSeconds)
        assertEquals(75, fixture.session.exercises.single().restSeconds)
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
        warmupVm.logSetAndSettle()
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
        finalVm.logSetAndSettle()
        val finalSession = awaitSession(finalFixture.session.id) { it.sets.size == 1 }
        assertFalse(finalSession.sets.single().isWarmup)
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun manualRestUsesSelectedDurationAndCanBeSkipped() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        // Waits for prefill so this test is about starting and skipping, not load timing. A
        // duration chosen before prefill landed used to be replaced by it (105 became 150, 23
        // Sept); aRestLengthPickedWhileTheLiftIsStillLoadingOutlivesThePrefill holds the fix.
        vm.awaitPrefilled()

        vm.selectRestDuration(105)
        vm.startSelectedRest()
        awaitRestRunning()
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
        awaitRestRunning()
        withTimeout(TestWaits.FLOW_MS) { deps.preferencesRepository.restAlarmEligible.first { it } }
        Unit
    }

    @Test
    fun firstRestMentionsUnrestrictedBatteryUntilAcknowledged() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()

        assertFalse(deps.preferencesRepository.restBatteryHintShown.first())
        vm.startSelectedRest()
        awaitRestRunning()
        withTimeout(TestWaits.FLOW_MS) { vm.restTimerState.first { it.batteryHint } }
        assertTrue(vm.restTimerState.value.batteryHint)

        vm.acknowledgeRestBatteryHint()
        withTimeout(TestWaits.FLOW_MS) { vm.restTimerState.first { !it.batteryHint } }
        assertTrue(deps.preferencesRepository.restBatteryHintShown.first())
        assertTrue(vm.restTimerState.value.running)
    }

    @Test
    fun editUpdatesExistingSetWithoutStartingAnotherRestOrRecord() = runBlocking {
        val fixture = seedWorkout(targetSets = 1)
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()
        vm.setWeight(100.0)
        vm.logSetAndSettle()
        val logged = awaitSession(fixture.session.id) { it.sets.size == 1 }.sets.single()
        vm.awaitState { state -> state.session?.sets?.any { it.id == logged.id } == true }
        vm.onPersonalRecordShown()
        vm.skipRest()

        vm.editSet(logged.id)
        // Opening an edit reads the stored row; typing before it lands is typing into a
        // draft the open is about to replace with that row's own values.
        vm.awaitEditOpen(logged.id)
        vm.setWeight(105.0)
        vm.adjustReps(1)
        vm.logSetAndSettle()

        val updated = awaitSession(fixture.session.id) {
            it.sets.singleOrNull()?.weightKg == 105.0
        }.sets.single()
        assertEquals(logged.id, updated.id)
        assertEquals(6, updated.reps)
        // Room can publish the updated row before the presentation flow clears edit mode.
        vm.awaitState { !it.logging && it.editingSetId == null }
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
        vm.logSetAndSettle()
        val logged = awaitSession(fixture.session.id) { it.sets.size == 1 }.sets.single()
        vm.awaitState { state -> state.session?.sets?.any { it.id == logged.id } == true }
        awaitRestRunning()

        vm.awaitEntryUnlocked()
        vm.deleteSet(logged.id)
        vm.awaitOffer(vm.deletedSet)
        awaitSession(fixture.session.id) { it.sets.isEmpty() }
        assertFalse(deps.restTimerStore.current().running)

        vm.awaitEntryUnlocked()
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
        // Then wait for the view model to have seen it too. requestSwap and removeSelectedLift
        // below read the view model's own session flow (ActiveWorkoutViewModel:677, :685) and
        // silently `return` if the lift is not there yet; awaitSession polls the repository, a
        // different subscription that updates first. The selectedExerciseId barrier on the
        // next line does not close the gap — selectExercise runs synchronously inside
        // addExercise, so it flips true without the session having emitted. Miss it and
        // requestSwap does nothing, addExercise falls into the plain-add path, error is
        // cleared, and the `it.error != null` wait below can never come true: a full-ceiling
        // hang. The second add at :578 only looks correct today because the repository dedups
        // (WorkoutRepository:363); without that guard it would wait on a permanent three.
        vm.awaitState { it.session?.exercises?.size == 2 }
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
        vm.awaitEntryUnlocked()
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
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        vm.skipRest()
        vm.awaitState { it.session?.sets?.size == 1 }

        vm.awaitEntryUnlocked()
        vm.removeSelectedLift()

        val state = vm.awaitState { it.error != null }
        assertTrue(state.error.orEmpty().contains("set", ignoreCase = true))
        assertEquals(1, deps.workoutRepository.getSession(fixture.session.id)!!.exercises.size)
    }

    @Test
    fun removeUnloggedLiftUndoRestoresSamePosition() = runBlocking {
        val fixture = seedTwoLifts()
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()
        vm.selectExercise(ROW)
        vm.awaitState { it.selectedExerciseId == ROW }
        val before = checkNotNull(deps.workoutRepository.getSession(fixture.session.id))
        val rowLift = before.exercises.single { it.exercise.id == ROW }
        val rowItemId = rowLift.id
        val rowOrder = rowLift.sortOrder

        vm.awaitEntryUnlocked()
        vm.removeSelectedLift()
        awaitSession(fixture.session.id) { session ->
            session.exercises.none { it.exercise.id == ROW }
        }
        vm.awaitOffer(vm.removedLift)
        assertEquals(
            SQUAT,
            deps.workoutRepository.getSession(fixture.session.id)!!.exercises.single().exercise.id,
        )

        vm.awaitEntryUnlocked()
        vm.undoRemoveLift()
        val restored = awaitSession(fixture.session.id) { it.exercises.size == 2 }
        val restoredRow = restored.exercises.single { it.exercise.id == ROW }
        assertEquals(rowItemId, restoredRow.id)
        assertEquals(rowOrder, restoredRow.sortOrder)
        assertEquals(ROW, vm.awaitState { it.selectedExerciseId == ROW }.selectedExerciseId)
        assertNull(vm.removedLift.value)
    }

    @Test
    fun twoRapidDeletesStackLifoAndBothUndo() = runBlocking {
        val fixture = seedWorkout(targetSets = 3)
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()
        vm.setWeight(100.0)
        vm.logSetAndSettle()
        vm.logSetAndSettle()
        val sets = awaitSession(fixture.session.id) { it.sets.size == 2 }
            .sets.sortedBy { it.completedAt }
        val (first, second) = sets

        vm.deleteSet(first.id)
        vm.awaitOffer(vm.deletedSet)
        vm.awaitEntryUnlocked()
        vm.deleteSet(second.id)
        awaitSession(fixture.session.id) { it.sets.isEmpty() }

        // The second delete offers on top without expiring the first.
        assertEquals(2, vm.undoEntries.value.size)
        assertEquals(UndoKind.DELETED_SET, vm.undoEntries.value.last().offer.kind)

        vm.awaitEntryUnlocked()
        vm.undoTopOffer()
        val oneBack = awaitSession(fixture.session.id) { it.sets.size == 1 }
        assertEquals(second.id, oneBack.sets.single().id)
        assertEquals(1, vm.undoEntries.value.size)

        vm.awaitEntryUnlocked()
        vm.undoTopOffer()
        val bothBack = awaitSession(fixture.session.id) { it.sets.size == 2 }
        assertEquals(setOf(first.id, second.id), bothBack.sets.map { it.id }.toSet())
        assertTrue(vm.undoEntries.value.isEmpty())
        assertNull(vm.deletedSet.value)
    }

    @Test
    fun deleteThenRemoveKeepsBothOffersAndUndoesInOrder() = runBlocking {
        val fixture = seedTwoLifts()
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()
        vm.selectExercise(SQUAT)
        vm.awaitState { it.selectedExerciseId == SQUAT }
        vm.setWeight(100.0)
        vm.logSetAndSettle()
        val logged = awaitSession(fixture.session.id) { it.sets.size == 1 }.sets.single()

        vm.awaitEntryUnlocked()
        vm.deleteSet(logged.id)
        vm.awaitOffer(vm.deletedSet)
        vm.selectExercise(ROW)
        vm.awaitState { it.selectedExerciseId == ROW }
        vm.awaitEntryUnlocked()
        vm.removeSelectedLift()
        vm.awaitOffer(vm.removedLift)

        assertEquals(2, vm.undoEntries.value.size)
        assertEquals(UndoKind.REMOVED_LIFT, vm.undoEntries.value.last().offer.kind)

        // Latest first: the lift comes back, then the set.
        vm.awaitEntryUnlocked()
        vm.undoTopOffer()
        awaitSession(fixture.session.id) { it.exercises.size == 2 }
        assertEquals(1, vm.undoEntries.value.size)
        assertEquals(UndoKind.DELETED_SET, vm.undoEntries.value.last().offer.kind)

        vm.awaitEntryUnlocked()
        vm.undoTopOffer()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        assertTrue(vm.undoEntries.value.isEmpty())
    }

    @Test
    fun expiredTopOfferRevealsTheNextOneWithoutARestChange() = runBlocking {
        val fixture = seedWorkout(targetSets = 3)
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()
        vm.setWeight(100.0)
        vm.logSetAndSettle()
        vm.logSetAndSettle()
        val sets = awaitSession(fixture.session.id) { it.sets.size == 2 }
            .sets.sortedBy { it.completedAt }

        vm.awaitEntryUnlocked()
        vm.deleteSet(sets[0].id)
        vm.awaitOffer(vm.deletedSet)
        vm.awaitEntryUnlocked()
        vm.deleteSet(sets[1].id)
        awaitSession(fixture.session.id) { it.sets.isEmpty() }
        assertEquals(2, vm.undoEntries.value.size)

        // Timeout expires the top offer silently: nothing restored, next revealed.
        vm.onUndoOfferExpired()
        assertEquals(1, vm.undoEntries.value.size)
        assertEquals(UndoKind.DELETED_SET, vm.undoEntries.value.last().offer.kind)
        assertTrue(
            deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty(),
        )

        vm.onUndoOfferExpired()
        assertTrue(vm.undoEntries.value.isEmpty())
    }

    @Test
    fun undoQueueSurvivesProcessDeath() = runBlocking {
        val fixture = seedWorkout(targetSets = 3)
        val handle = handleFor(fixture.session.id)
        val vm = createViewModel(fixture.session.id, handle)
        vm.awaitFound()
        vm.setWeight(100.0)
        vm.logSetAndSettle()
        val logged = awaitSession(fixture.session.id) { it.sets.size == 1 }.sets.single()
        vm.awaitEntryUnlocked()
        vm.deleteSet(logged.id)
        vm.awaitOffer(vm.deletedSet)
        awaitSession(fixture.session.id) { it.sets.isEmpty() }

        // A new process over the same saved state still offers the delete back.
        val revived = createViewModel(fixture.session.id, handle)
        revived.awaitFound()
        assertEquals(1, revived.undoEntries.value.size)
        assertEquals(UndoKind.DELETED_SET, revived.undoEntries.value.last().offer.kind)

        revived.undoDeleteSet()
        val restored = awaitSession(fixture.session.id) { it.sets.size == 1 }.sets.single()
        assertEquals(logged.id, restored.id)
        assertTrue(revived.undoEntries.value.isEmpty())
    }

    @Test
    fun skipForNowMovesToNextUnfinishedLiftWithoutDeleting() = runBlocking {
        val fixture = seedTwoLifts()
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()
        vm.selectExercise(SQUAT)
        vm.awaitState { it.selectedExerciseId == SQUAT }

        vm.skipForNow()
        vm.awaitState { it.selectedExerciseId == ROW }

        // Nothing deleted, plan untouched, no undo offered: skip is not a destructive.
        val session = checkNotNull(deps.workoutRepository.getSession(fixture.session.id))
        assertEquals(2, session.exercises.size)
        assertTrue(session.sets.isEmpty())
        assertTrue(vm.undoEntries.value.isEmpty())
    }

    @Test
    fun skipForNowOnTheOnlyUnfinishedLiftStaysAndExplains() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        val selected = vm.awaitFound().selectedExerciseId

        vm.skipForNow()
        dispatcher.scheduler.runCurrent()

        assertEquals(selected, vm.awaitState { it.selectedExerciseId == selected }.selectedExerciseId)
        assertEquals(CurrentLiftCopy.SKIP_NOWHERE, vm.awaitState { it.error != null }.error)
        assertTrue(vm.undoEntries.value.isEmpty())
    }

    @Test
    fun undoDwellExtendsUnderAccessibilityTimeout() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(
            fixture.session.id,
            undoTimeout = UndoTimeoutProvider { 20_000L },
        )
        vm.awaitFound()
        assertEquals(20_000L, vm.undoDwellMs.value)
    }

    @Test
    fun undoDwellNeverDropsBelowTheSixSecondBase() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(
            fixture.session.id,
            undoTimeout = UndoTimeoutProvider { 1_000L },
        )
        vm.awaitFound()
        assertEquals(Motion.STATUS_DWELL_MS, vm.undoDwellMs.value)
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
        vm.logSetAndSettle()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        vm.awaitState { it.session?.sets?.size == 1 }
        vm.skipRest()
        assertNotNull(SavedStateWorkoutDraft(handle).read(fixture.session.id))

        vm.finishWorkout()

        val exit = checkNotNull(vm.exitRequested.awaitFirst { it != null })
        assertEquals(WorkoutExit.Finished(fixture.session.id), exit)
        assertNotNull(deps.workoutRepository.getSession(fixture.session.id)?.finishedAt)
        assertNull(deps.workoutRepository.getInProgress())
        assertNull(deps.workoutDraftCache.get(fixture.session.id))
        assertNull(SavedStateWorkoutDraft(handle).read(fixture.session.id))
        // `finished` is written before the exit request, but uiState is a combine of both
        // and can publish a beat later than the flow just awaited.
        assertTrue(vm.awaitState { it.finished }.finished)

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

        val exit = checkNotNull(vm.exitRequested.awaitFirst { it != null })
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

    @Test
    fun processDeathRestoresEditingSetId() = runBlocking {
        val fixture = seedWorkout()
        val handle = handleFor(fixture.session.id)
        val first = createViewModel(fixture.session.id, handle)
        first.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.weightKg == 100.0
        }
        first.logSetAndSettle()
        val persisted = awaitSession(fixture.session.id) { it.sets.size == 1 }
        dispatcher.scheduler.advanceUntilIdle()
        first.awaitState { it.session?.sets?.size == 1 }
        val setId = persisted.sets.single().id
        first.editSet(setId)
        first.awaitEditOpen(setId)
        assertEquals(setId, SavedStateWorkoutDraft(handle).editingSetId())
        first.clearAndJoinForTest()
        viewModels.remove(first)
        deps.workoutDraftCache.clearAll()

        val recreated = createViewModel(fixture.session.id, handle)
        val state = recreated.awaitState {
            it.loadState == SessionLoadState.FOUND && it.editingSetId == setId
        }
        assertEquals(setId, state.editingSetId)
        assertEquals(100.0, state.draft.weightKg, 0.0001)
    }

    @Test
    fun doubleTapLogSetRecordsOneSet() = runBlocking {
        val fixture = seedWorkout()
        val vm = createViewModel(fixture.session.id)
        vm.awaitState {
            it.loadState == SessionLoadState.FOUND && it.draft.weightKg == 100.0
        }
        vm.logSet()
        vm.logSet()
        val persisted = awaitSession(fixture.session.id) { it.sets.isNotEmpty() }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, persisted.sets.size)
        assertEquals(
            1,
            checkNotNull(deps.workoutRepository.getSession(fixture.session.id)).sets.size,
        )
    }

    @Test
    fun lastTimeChipFillsWellsFromThatSetAndDoesNotLog() = runBlocking {
        val fixture = seedWorkout(priorWeightKg = 87.5)
        val vm = createViewModel(fixture.session.id)
        // Prefill can overwrite a typed 100 with the 87.5 kg + step suggestion if we act first.
        val last = checkNotNull(
            vm.awaitState {
                val suggested = it.hint?.suggestedWeightKg ?: return@awaitState false
                it.loadState == SessionLoadState.FOUND &&
                    it.draft.weightKg == suggested &&
                    it.lastPerformance?.sets?.isNotEmpty() == true
            }.lastPerformance,
        )
        val set = last.sets.single()
        assertEquals(87.5, set.weightKg, 0.0001)
        assertEquals(5, set.reps)
        vm.setWeight(100.0)
        vm.awaitState { it.draft.weightKg == 100.0 }
        vm.applyLastTimeSet(set.weightKg, 8)
        val draft = vm.awaitState { it.draft.weightKg == 87.5 && it.draft.reps == 8 }.draft
        assertEquals(87.5, draft.weightKg, 0.0001)
        assertEquals(8, draft.reps)
        assertTrue(
            checkNotNull(deps.workoutRepository.getSession(fixture.session.id)).sets.isEmpty(),
        )
    }

    @Test
    fun removingALiftWithSavedSetsIsRefusedAfterSaveCompletes() = runBlocking {
        val fixture = seedWorkout(targetSets = 1)
        val vm = createViewModel(fixture.session.id)
        vm.awaitFound()
        vm.setWeight(100.0)
        // F3 serializes entry mutations while saving. Wait for that operation to
        // release before exercising the saved-set removal refusal.
        vm.logSet()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        vm.awaitState { it.session?.sets?.size == 1 && !it.logging && !it.save.pending }

        vm.awaitEntryUnlocked()
        vm.removeSelectedLift()

        val state = vm.awaitState { it.error != null }
        assertTrue(state.error.orEmpty().contains("set", ignoreCase = true))
        vm.awaitState { !it.logging }
        assertEquals(1, deps.workoutRepository.getSession(fixture.session.id)!!.exercises.size)
    }

    @Test
    fun sessionReadyDoesNotImplyLiftReady() = runBlocking {
        val fixture = seedWorkout()
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(fixture.session.id, container = gatedHistory(gate))
        try {
            val found = vm.awaitFound()
            assertEquals(SessionLoadState.FOUND, found.loadState)
            assertTrue(
                found.liftReadiness == LiftEntryReadiness.RESOLVING ||
                    found.liftReadiness == LiftEntryReadiness.NONE,
            )
            assertFalse(found.canLog)
            assertFalse(found.liftReadiness.allowsCommit())
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun logDisabledUntilLiftReady() = runBlocking {
        val fixture = seedWorkout()
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(fixture.session.id, container = gatedHistory(gate))
        try {
            vm.awaitFound()
            assertFalse(vm.uiState.value.canLog)
            vm.logSet()
            assertTrue(deps.workoutRepository.getSession(fixture.session.id)!!.sets.isEmpty())
            gate.complete(Unit)
            val ready = vm.awaitState {
                it.liftReadiness == LiftEntryReadiness.READY && it.draft.weightKg == 100.0
            }
            assertTrue(ready.canLog)
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun prefillDoesNotOverwriteDirtyDraft() = runBlocking {
        val fixture = seedWorkout(targetWeightKg = 100.0)
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(fixture.session.id, container = gatedHistory(gate))
        try {
            vm.awaitFound()
            vm.setWeight(155.0)
            vm.setReps(7)
            vm.awaitState { it.draftDirty && it.draft.weightKg == 155.0 }
            gate.complete(Unit)
            val state = vm.awaitState {
                it.liftReadiness.allowsCommit() && it.draft.weightKg == 155.0
            }
            assertEquals(155.0, state.draft.weightKg, 0.0001)
            assertEquals(7, state.draft.reps)
            assertTrue(state.draftDirty)
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun aRestLengthPickedWhileTheLiftIsStillLoadingOutlivesThePrefill() = runBlocking {
        val fixture = seedWorkout(restSeconds = 90)
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(fixture.session.id, container = gatedHistory(gate))
        try {
            // FOUND with the lift's history read held: the dock's rest card is on screen and
            // takes a pick, but prefill has not reached its planned-rest seed, which only runs
            // after that read returns.
            vm.awaitFound()
            vm.selectRestDuration(105)
            deps.preferencesRepository.restTimerPreferences.first { it.lastPresetSeconds == 105 }
            gate.complete(Unit)
            vm.awaitPrefilled()

            vm.startSelectedRest()
            awaitRestRunning()
            assertEquals(105, deps.restTimerStore.current().totalSeconds)
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun aRestPickedOnOneLiftDoesNotStopTheNextLiftSeedingItsOwn() = runBlocking {
        val fixture = seedTwoLifts(targetSets = 3)
        val vm = createViewModel(fixture.session.id)
        vm.awaitPrefilled()
        vm.startSelectedRest()
        awaitRestRunning()
        val seeded = deps.restTimerStore.current().totalSeconds
        vm.skipRest()
        assertTrue(seeded != 105)

        vm.selectRestDuration(105)
        deps.preferencesRepository.restTimerPreferences.first { it.lastPresetSeconds == 105 }
        vm.selectExercise(ROW)
        vm.awaitPrefilled(weightKg = 80.0)

        vm.startSelectedRest()
        awaitRestRunning()
        assertEquals(seeded, deps.restTimerStore.current().totalSeconds)
    }

    @Test
    fun stalePrefillForPreviousLiftIsIgnored() = runBlocking {
        val fixture = seedTwoLifts()
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(fixture.session.id, container = gatedHistory(gate))
        try {
            vm.awaitFound()
            vm.selectExercise(ROW)
            vm.awaitState { it.selectedExerciseId == ROW }
            gate.complete(Unit)
            val state = vm.awaitState {
                it.selectedExerciseId == ROW &&
                    it.liftReadiness.allowsCommit() &&
                    it.draft.weightKg == 80.0
            }
            assertEquals(ROW, state.selectedExerciseId)
            assertEquals(80.0, state.draft.weightKg, 0.0001)
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun prefillFailureDegradesAndLogStillWorks() = runBlocking {
        val fixture = seedWorkout(targetWeightKg = 100.0)
        assertEquals(
            100.0,
            fixture.session.exercises.single().targetWeightKg ?: -1.0,
            0.0001,
        )
        val vm = createViewModel(fixture.session.id, container = failingHistory())
        val state = vm.awaitState {
            it.loadState == SessionLoadState.FOUND &&
                it.liftReadiness == LiftEntryReadiness.DEGRADED &&
                it.draft.weightKg == 100.0
        }
        assertTrue(state.suggestionUnavailable)
        assertTrue(state.canLog)
        assertEquals(100.0, state.draft.weightKg, 0.0001)
        vm.logSetAndSettle()
        val persisted = awaitSession(fixture.session.id) { it.sets.size == 1 }
        assertEquals(100.0, persisted.sets.single().weightKg, 0.0001)
        assertFalse(vm.uiState.value.logging)
    }

    @Test
    fun emptySessionHidesRestAndOffersDiscard() = runBlocking {
        val session = deps.workoutRepository.startFreeWorkout()
        val vm = createViewModel(session.id)
        val state = vm.awaitFound()
        assertFalse(state.showRest)
        assertFalse(state.offerSetClock)
        assertFalse(state.canFinish)
        assertTrue(state.showDiscard)
        assertEquals(LiftEntryReadiness.NONE, state.liftReadiness)
        assertFalse(state.canLog)
        assertTrue(state.session?.sets.isNullOrEmpty())
    }

    @Test
    fun finishIsDisabledWithZeroSetsAndWhileLogging() = runBlocking {
        val fixture = seedWorkout()
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(fixture.session.id, container = gatedLogSet(gate))
        try {
            val ready = vm.awaitPrefilled()
            assertFalse(ready.canFinish)
            assertTrue(ready.showDiscard)
            vm.logSet()
            // The gate holds the insert open, so logging stays raised until it opens; the
            // combined uiState can publish it a beat after logSet set it.
            val busy = vm.awaitState { it.logging }
            assertFalse(busy.canFinish)
            assertFalse(busy.showDiscard)
            assertFalse(busy.canLog)
            vm.logSet()
            gate.complete(Unit)
            // canFinish needs the whole entry lock released, not only `logging`: the save's
            // tail can still hold it for a beat after the row is in.
            val settled = vm.awaitState { !it.entryLocked && it.session?.sets?.size == 1 }
            assertTrue(settled.canFinish)
            assertFalse(settled.showDiscard)
            assertEquals(
                1,
                checkNotNull(deps.workoutRepository.getSession(fixture.session.id)).sets.size,
            )
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun loggingFlagDisablesButtonAndAbsorbsSecondTap() = runBlocking {
        val fixture = seedWorkout()
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(fixture.session.id, container = gatedLogSet(gate))
        try {
            vm.awaitPrefilled()
            vm.logSet()
            val busy = vm.awaitState { it.logging }
            assertFalse(busy.canLog)
            assertFalse(busy.canFinish)
            vm.logSet()
            vm.logSet()
            gate.complete(Unit)
            val settled = vm.awaitState { !it.entryLocked && it.session?.sets?.size == 1 }
            assertEquals(
                1,
                checkNotNull(deps.workoutRepository.getSession(fixture.session.id)).sets.size,
            )
            assertTrue(settled.canFinish)
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun rapidLogTapsEmitOneSuccessHapticEvent() = runBlocking {
        val fixture = seedWorkout()
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(fixture.session.id, container = gatedLogSet(gate))
        val seen = mutableListOf<LogCommitFeedback>()
        val job = launch(dispatcher) { vm.logFeedback.collect { seen.add(it) } }
        try {
            vm.awaitPrefilled()
            vm.logSet()
            vm.logSet()
            // The insert is gated, so logging stays raised until the gate opens; the
            // combined uiState can publish it a beat after logSet set it.
            assertTrue(vm.awaitState { it.logging }.logging)
            gate.complete(Unit)
            vm.awaitState { !it.logging }
            assertEquals(
                1,
                checkNotNull(deps.workoutRepository.getSession(fixture.session.id)).sets.size,
            )
            assertEquals(listOf(LogCommitFeedback.SUCCESS), seen.toList())
        } finally {
            job.cancel()
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun writeFailureRetainsDraftAndEmitsReject() = runBlocking {
        val fixture = seedWorkout()
        // A throwing insert, not a deleted session: see
        // [logSetWriteFailureSurfacesErrorInsteadOfPretendingSuccess].
        val vm = createViewModel(fixture.session.id, container = failingInsert())
        vm.awaitPrefilled()
        vm.setWeight(100.0)
        val seen = mutableListOf<LogCommitFeedback>()
        val job = launch(dispatcher) { vm.logFeedback.collect { seen.add(it) } }
        try {
            vm.logSetAndSettle()
            val state = vm.awaitState { it.error != null }
            assertEquals(LogCommitCopy.WRITE_FAILED, state.error)
            assertEquals(100.0, state.draft.weightKg, 0.0001)
            assertEquals(listOf(LogCommitFeedback.REJECT), seen.toList())
            assertFalse(deps.restTimerStore.current().running)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun logSetRejectsZeroWeightWithoutSuccessHaptic() = runBlocking {
        val fixture = seedWorkout(targetWeightKg = 0.0)
        val vm = createViewModel(fixture.session.id)
        // Settled, not merely FOUND: a tap during prefill's tail is dropped without a word.
        vm.awaitPrefilled(weightKg = 0.0)
        val seen = mutableListOf<LogCommitFeedback>()
        val job = launch(dispatcher) { vm.logFeedback.collect { seen.add(it) } }
        try {
            vm.setWeight(0.0)
            // A refusal writes nothing, so there is no save to wait for; wait for the refusal.
            vm.logSet()
            vm.awaitState { it.error != null }
            assertEquals(listOf(LogCommitFeedback.REJECT), seen.toList())
            assertFalse(seen.contains(LogCommitFeedback.SUCCESS))
        } finally {
            job.cancel()
        }
    }

    private fun createViewModel(
        sessionId: String,
        handle: SavedStateHandle = handleFor(sessionId),
        container: AppDependencies = deps,
        undoTimeout: UndoTimeoutProvider = UndoTimeoutProvider { it.toLong() },
    ): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = handle,
            container = container,
            undoTimeout = undoTimeout,
        ).also(viewModels::add)

    private fun withClock(clock: ControllableTimePort): AppDependencies =
        object : AppDependencies by deps {
            override val time = clock
        }

    /** A copy of the graph whose set insert always throws; every read is the real thing. */
    private fun failingInsert(): AppDependencies {
        val repo = WorkoutRepository(
            deps.database,
            object : WorkoutDao by deps.database.workoutDao() {
                override suspend fun insertSet(set: SetLogEntity) {
                    error("Injected write failure")
                }
            },
        )
        return object : AppDependencies by deps {
            override val workoutRepository: WorkoutRepository = repo
        }
    }

    private fun tickTimedWork() {
        dispatcher.scheduler.advanceTimeBy(250)
        dispatcher.scheduler.runCurrent()
    }

    /**
     * A copy of the graph whose set insert parks until the gate opens, so a test can act in
     * the window a real lifter acts in: the seconds between the Log tap and the row landing.
     */
    private fun gatedLogSet(gate: CompletableDeferred<Unit>): AppDependencies {
        val repo = WorkoutRepository(deps.database, GatedInsertDao(deps.database.workoutDao(), gate))
        return object : AppDependencies by deps {
            override val workoutRepository: WorkoutRepository = repo
        }
    }

    /**
     * A copy of the graph whose session Flow can be frozen, so a test can act in the window
     * between a row landing in Room and the screen being told about it.
     */
    private fun pausableSession(paused: StateFlow<Boolean>): AppDependencies {
        val repo = WorkoutRepository(deps.database, PausableSessionDao(deps.database.workoutDao(), paused))
        return object : AppDependencies by deps {
            override val workoutRepository: WorkoutRepository = repo
        }
    }

    private fun gatedHistory(
        gate: CompletableDeferred<Unit>,
        fail: Boolean = false,
    ): AppDependencies {
        val repo = WorkoutRepository(
            deps.database,
            GatedHistoryDao(deps.database.workoutDao(), gate, fail),
        )
        return object : AppDependencies by deps {
            override val workoutRepository: WorkoutRepository = repo
        }
    }

    private fun failingHistory(): AppDependencies {
        val repo = WorkoutRepository(
            deps.database,
            object : WorkoutDao by deps.database.workoutDao() {
                override suspend fun finishedWorkingSetsForExercises(
                    exerciseIds: List<String>,
                ): List<FinishedWorkingSetRow> {
                    error("history unavailable")
                }
            },
        )
        return object : AppDependencies by deps {
            override val workoutRepository: WorkoutRepository = repo
        }
    }

    private class GatedInsertDao(
        private val delegate: WorkoutDao,
        private val gate: CompletableDeferred<Unit>,
    ) : WorkoutDao by delegate {
        override suspend fun insertSet(set: SetLogEntity) {
            gate.await()
            delegate.insertSet(set)
        }
    }

    /**
     * Holds each session emission at the door while paused. The row is already in Room; the
     * screen simply has not been told, which is exactly the state a fast second correction
     * finds and cannot wait out.
     */
    private class PausableSessionDao(
        private val delegate: WorkoutDao,
        private val paused: StateFlow<Boolean>,
    ) : WorkoutDao by delegate {
        override fun observeSession(id: String): Flow<SessionWithDetails?> =
            delegate.observeSession(id).onEach { paused.first { !it } }
    }

    private class GatedHistoryDao(
        private val delegate: WorkoutDao,
        private val gate: CompletableDeferred<Unit>,
        private val fail: Boolean,
    ) : WorkoutDao by delegate {
        override suspend fun finishedWorkingSetsForExercises(
            exerciseIds: List<String>,
        ): List<FinishedWorkingSetRow> {
            gate.await()
            if (fail) error("history unavailable")
            return delegate.finishedWorkingSetsForExercises(exerciseIds)
        }
    }

    private suspend fun ActiveWorkoutViewModel.awaitFound(): ActiveWorkoutUiState =
        awaitState { it.loadState == SessionLoadState.FOUND }

    /**
     * The moment a delete, remove or undo may be issued and will be acted on.
     *
     * Every entry mutation begins `if (!canChangeEntry()) return`: while a save is
     * outstanding, another mutation is in flight or the session is not FOUND, the tap is
     * dropped without a word, by design — a queued tap must never act on a screen that has
     * moved on. `entryLocked` projects those same flags, and `uiState` is collected for the
     * ViewModel's whole life by `primaryAction`, so it is live, not a snapshot. A test that
     * taps the instant a row or an offer appears is otherwise racing the tail of the
     * operation that produced it: that is how `undoQueueSurvivesProcessDeath` lost trunk
     * run 35239125454 and reproduced here, with the row still stored and nothing left
     * running.
     */
    private suspend fun ActiveWorkoutViewModel.awaitEntryUnlocked(): ActiveWorkoutUiState =
        awaitState { !it.entryLocked }

    /**
     * An edit is open when its mutation has released, not when [ActiveWorkoutUiState.editingSetId]
     * first appears. `editSet` publishes the id before it writes the draft and the saved-edit
     * record (so prefill cannot run over the values), and holds the entry lock until both land;
     * `setWeight` typed inside that window is refused, and the "correction" saves the old value.
     * Under a loaded machine that window is wide enough to hit: trunk's full gate failed
     * `aSecondCorrectionOfTheSameSetSavesWhileTheScreensCopyIsBehind` on 22 September with
     * 110 kg saved where 120 was typed, and it passed three times alone.
     */
    private suspend fun ActiveWorkoutViewModel.awaitEditOpen(setId: String): ActiveWorkoutUiState =
        awaitState { it.editingSetId == setId && !it.entryLocked }

    /**
     * FOUND is not settled. Prefill runs after the session resolves and replaces the whole
     * draft with the suggestion, so a test that types on FOUND is typing into a well that is
     * about to be overwritten — by the app, correctly, and not by the defect under test.
     * [seedWorkout] with no prior session prefills the 100 kg target at 5 reps.
     */
    private suspend fun ActiveWorkoutViewModel.awaitPrefilled(
        weightKg: Double = 100.0,
        reps: Int = 5,
    ): ActiveWorkoutUiState = awaitState {
        // Settled, not merely filled: the prefill's tail can still hold the entry lock for a
        // beat after the draft lands, and the flags a test reads next (canLog, canFinish,
        // showDiscard) all derive from that lock.
        it.loadState == SessionLoadState.FOUND &&
            it.draft.weightKg == weightKg &&
            it.draft.reps == reps &&
            !it.entryLocked
    }

    /**
     * Log a set and wait for the whole action, not just for its row.
     *
     * The session Flow publishes the moment Room commits, which is the middle of
     * [ActiveWorkoutViewModel.logSet]'s coroutine and not its end: the personal-record
     * moment, `wantAnotherSet`, `error`, the draft reset and the double-tap guard are all
     * written after that. Carrying on at the row raced the rest of the action — whatever the
     * test set next could be taken back by the tail, and a second logSet() could be swallowed
     * by a guard still true from the first.
     *
     * That is what wedged this class intermittently. The wait below is for an outcome only
     * this log can produce, not for flags an earlier snapshot also shows.
     */
    private suspend fun ActiveWorkoutViewModel.logSetAndSettle() {
        val sessionId = checkNotNull(uiState.value.session?.id) { "logSetAndSettle before the session loaded" }
        val storedBefore = deps.workoutRepository.getSession(sessionId)?.sets.orEmpty().toSet()
        val saveBefore = uiState.value.save
        logSet()
        // Wait for THIS log, not for a quiet screen. "Not logging, not saving" is also true of
        // the snapshot from before the tap: `uiState` combines Room flows on Room's threads,
        // so for a moment after logSet() it can still show the pre-log flags, and waiting on
        // them returned before the save began. The next logSet() was then refused by the save
        // still holding the entry lock (expiredTopOfferRevealsTheNextOneWithoutARestChange,
        // 23 Sept). So wait for an outcome only this log can produce: the stored rows changed
        // and the screen shows those rows with the entry unlocked, or this save came to rest
        // as FAILED / CONFLICT, which the write-failure tests go on to assert.
        try {
            withTimeout(TestWaits.FLOW_MS) {
                while (true) {
                    val state = uiState.value
                    val failed = state.save != saveBefore &&
                        (state.save.phase == WorkoutSavePhase.FAILED || state.save.phase == WorkoutSavePhase.CONFLICT)
                    if (failed) break
                    val stored = deps.workoutRepository.getSession(sessionId)?.sets.orEmpty().toSet()
                    val landed = stored != storedBefore && state.session?.sets.orEmpty().toSet() == stored
                    if (landed && !state.entryLocked) break
                    delay(10)
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("logSetAndSettle: the log neither landed nor failed; uiState was ${uiState.value}", timedOut)
        }
        dispatcher.scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS.toLong())
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.advanceUntilIdle()
    }

    private suspend fun awaitRestRunning() {
        try {
            withTimeout(TestWaits.FLOW_MS) {
                while (!deps.restTimerStore.current().running) {
                    // Room uses real threads; its completion may schedule the receipt
                    // delay after logSetAndSettle's last virtual-clock advance. Drive
                    // that delay while yielding to Room, retaining the actual rest
                    // state as the success condition and the wall-clock failure bound.
                    dispatcher.scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS.toLong())
                    dispatcher.scheduler.runCurrent()
                    if (!deps.restTimerStore.current().running) delay(10)
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError(
                "Rest did not start; snapshot=${deps.restTimerStore.current()}, " +
                    "virtualTime=${dispatcher.scheduler.currentTime}",
                timedOut,
            )
        }
    }

    /**
     * A bare timeout here reports only "Timed out waiting for 30000 ms", which is the one
     * thing already known. The state the wait never reached is what says whether the action
     * under test did nothing, did the wrong thing, or did the right thing into a value that
     * was overwritten before this collector saw it. Reading it in the catch costs nothing on
     * the happy path and cannot perturb the race that got us here — it has already lost.
     */
    private suspend fun ActiveWorkoutViewModel.awaitState(
        predicate: (ActiveWorkoutUiState) -> Boolean,
    ): ActiveWorkoutUiState = try {
        withTimeout(TestWaits.FLOW_MS) { uiState.first(predicate) }
    } catch (timedOut: TimeoutCancellationException) {
        throw AssertionError("awaitState gave up; last uiState was ${uiState.value}", timedOut)
    }

    /**
     * The undo offer for a delete or remove, once the mutation that made it has released.
     *
     * The offer is pushed inside the mutation, while `mutating` is still true, so a test
     * that issues its next delete the instant the offer appears is racing the tail of the
     * first one — and the entry lock refuses that second tap silently, by design, because a
     * queued tap must not act while another operation is outstanding. That is what
     * `expiredTopOfferRevealsTheNextOneWithoutARestChange` lost locally: the second row was
     * never deleted and the wait for an empty session ran out. Waiting for `mutating` to
     * clear after the offer is deterministic; `mutating` is live in `uiState`.
     *
     * An offer that never appears is the same refusal one step earlier. The generic wait can
     * only say "last value was null"; the screen state says which lock was still held.
     */
    private suspend fun <T : Any> ActiveWorkoutViewModel.awaitOffer(offer: StateFlow<T?>): T = try {
        val value = checkNotNull(offer.awaitFirst { it != null })
        awaitEntryUnlocked()
        value
    } catch (gaveUp: AssertionError) {
        throw AssertionError("${gaveUp.message}\nuiState was ${uiState.value}", gaveUp)
    }

    private suspend fun awaitSession(
        sessionId: String,
        predicate: (WorkoutSession) -> Boolean,
    ): WorkoutSession = try {
        withTimeout(TestWaits.FLOW_MS) {
            checkNotNull(
                deps.workoutRepository.observeSession(sessionId).first { session ->
                    session != null && predicate(session)
                },
            )
        }
    } catch (timedOut: TimeoutCancellationException) {
        val stored = runCatching { deps.workoutRepository.getSession(sessionId) }
        throw AssertionError("awaitSession gave up; stored row was ${stored.getOrNull()}", timedOut)
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

    private suspend fun seedTwoLifts(targetSets: Int = 1): SeededWorkout {
        insertExercise(SQUAT, "Squat")
        insertExercise(ROW, "Row")
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
                targetWeightKg = 100.0,
                restSeconds = 90,
            ),
        )
        deps.database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-$ROW",
                routineId = ROUTINE,
                exerciseId = ROW,
                sortOrder = 1,
                targetSets = targetSets,
                targetReps = 5,
                targetWeightKg = 80.0,
                restSeconds = 90,
            ),
        )
        val routine = checkNotNull(deps.routineRepository.getById(ROUTINE))
        return SeededWorkout(deps.workoutRepository.startRoutine(routine))
    }

    private suspend fun insertExercise(
        id: String,
        name: String,
        loadType: String = "EXTERNAL",
    ) {
        deps.database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = id,
                    name = name,
                    muscleGroup = "Legs",
                    notes = "",
                    isCustom = false,
                    loadType = loadType,
                    nameKey = name.lowercase(),
                ),
            ),
        )
    }

    private suspend fun seedHangWorkout(
        targetSeconds: Int = 30,
        restSeconds: Int = 75,
    ): SeededWorkout {
        insertExercise(HANG, "Dead Hang", loadType = "BODYWEIGHT")
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(
                id = ROUTINE,
                name = "Hangs",
                notes = "",
                createdAt = STAMP,
                updatedAt = STAMP,
            ),
        )
        deps.database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-$HANG",
                routineId = ROUTINE,
                exerciseId = HANG,
                sortOrder = 0,
                targetSets = 2,
                targetReps = 1,
                targetWeightKg = null,
                restSeconds = restSeconds,
                targetSeconds = targetSeconds,
            ),
        )
        val routine = checkNotNull(deps.routineRepository.getById(ROUTINE))
        return SeededWorkout(deps.workoutRepository.startRoutine(routine))
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
        const val HANG = "ex-dead-hang"
        const val ROUTINE = "routine-lower"
        const val STAMP = 1_700_000_000_000L
    }
}
