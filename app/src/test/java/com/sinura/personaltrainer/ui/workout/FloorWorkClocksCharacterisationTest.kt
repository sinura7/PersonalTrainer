package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.domain.FloorTimerCue
import com.sinura.personaltrainer.domain.HoldTimerUiState
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.SetStopwatchUiState
import com.sinura.personaltrainer.domain.UndoKind
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.ControllableTimePort
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.workout.SavedStateFloorTimer
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the floor's timed work as it stands before audit W2d-3a moves it out of
 * [ActiveWorkoutViewModel]: the hold countdown and the set clock across a process death, the rest
 * they stop, the cues the screen feels, the cap, the commit a timed change retires, and what a
 * lift switch, a removal, an edit or a rest does to them. ActiveWorkoutViewModelTest holds a
 * running hold's restore, the target not logging a set and a start stopping a running rest;
 * these are what it leaves open. Written and passed on the code before the move; the five the
 * move's review asked for (a running clock removed and brought back, a timed set logged before a
 * process death, a stop and the commit drawn before it, a lift switch after a log) were added
 * after it, and pass on both.
 *
 * The work clocks read elapsed realtime from a [ControllableTimePort] and wake on the
 * ViewModel's own Main scheduler, so a tick is the clock moved, then the scheduler moved one
 * beat (250 ms). A process death is the ViewModel cleared, the draft cache emptied (it is
 * process memory) and a new ViewModel on the same saved state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FloorWorkClocksCharacterisationTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = ControllableTimePort()
    private lateinit var deps: FakeAppDependencies
    private val models = mutableListOf<ActiveWorkoutViewModel>()
    private val collectors = CoroutineScope(Job() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            time = clock,
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        collectors.cancel()
        runBlocking { models.forEach { it.clearAndJoinForTest() } }
        models.clear()
        deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun aRunningSetClockComesBackRunningAfterProcessDeath() = runBlocking {
        val handle = handleFor(seed(SQUAT_LIFT))
        val original = viewModel(handle)
        original.ready()
        original.startSetStopwatch()
        tick(7_000)
        assertEquals("the clock counts before the process dies", 7, original.setStopwatch.value.elapsedSeconds)

        val revived = processDeath(original, handle)
        revived.ready()
        tick(2_000)

        val watch = revived.setStopwatch.value
        assertTrue("a set clock that was running comes back running: $watch", watch.running)
        assertTrue("and still counts as used: $watch", watch.used)
        assertEquals("on the lift it was started on", SQUAT, watch.exerciseId)
        assertEquals("counting from its first start, not from the restore", 9, watch.elapsedSeconds)
        val commit = revived.primaryAction.awaitFirst {
            it.kind == WorkoutPrimaryKind.LOG_SET && it.durationSeconds == 9
        }
        assertTrue("the commit logs the clock's seconds: $commit", commit.enabled)
    }

    @Test
    fun aStoppedSetClockComesBackStoppedAndItsSecondsAreLogged() = runBlocking {
        val sessionId = seed(SQUAT_LIFT)
        val handle = handleFor(sessionId)
        val original = viewModel(handle)
        original.ready()
        original.startSetStopwatch()
        clock.advance(5_000)
        original.stopSetStopwatch()
        assertEquals("the stop freezes five seconds", 5, original.setStopwatch.value.elapsedSeconds)
        clock.advance(10_000)

        val revived = processDeath(original, handle)
        revived.ready()

        val watch = revived.setStopwatch.value
        assertFalse("a stopped clock comes back stopped: $watch", watch.running)
        assertTrue("and used: $watch", watch.used)
        assertEquals("with the seconds it was stopped at, not the time since", 5, watch.elapsedSeconds)
        revived.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        val row = deps.workoutRepository.awaitSession(sessionId) { it.sets.size == 1 }.sets.single()
        assertEquals("the set logs the stopped clock's seconds", 5, row.durationSeconds)
        assertEquals("and keeps its reps", 5, row.reps)
    }

    @Test
    fun aHoldThatReachedItsTargetComesBackDoneAndIsNotRestarted() = runBlocking {
        val sessionId = seed(HANG_LIFT)
        val handle = handleFor(sessionId)
        val original = viewModel(handle)
        original.readyHold()
        original.startHoldSet()
        tick(31_000)
        assertTrue("the hold reaches its target", original.holdTimer.value.targetReached)
        val kept = SavedStateFloorTimer(handle).readHold(HANG, clock.elapsedRealtimeMillis())
        assertNotNull("the finished hold is kept for a process death", kept)
        assertTrue("kept as reached: $kept", kept!!.targetReached)
        assertFalse("kept as no longer running: $kept", kept.running)

        val revived = processDeath(original, handle)
        val seen = cuesOf(revived)
        revived.readyHold()
        tick(5_000)

        val hold = revived.holdTimer.value
        assertTrue("it comes back done: $hold", hold.targetReached)
        assertFalse("and is not restarted: $hold", hold.running)
        assertEquals("the dock says so", HoldWork.DONE, hold.clock)
        assertEquals("the whole target was held", 30, hold.elapsedSeconds)
        assertTrue("a finished hold does not cue its target again: $seen", seen.isEmpty())
        val commit = revived.primaryAction.awaitFirst { it.kind == WorkoutPrimaryKind.LOG_HOLD }
        assertEquals("the commit logs the target's seconds", 30, commit.durationSeconds)
        assertTrue(
            "reaching the target logs nothing on its own",
            deps.workoutRepository.getSession(sessionId)!!.sets.isEmpty(),
        )
    }

    @Test
    fun aRestoredSetClockStopsARestThatStartedWhileTheAppWasStopped() = runBlocking {
        val sessionId = seed(SQUAT_LIFT)
        val handle = handleFor(sessionId)
        val original = viewModel(handle)
        original.ready()
        original.startSetStopwatch()
        tick(3_000)
        stopTheProcess(original)
        deps.restTimerController.start(90, sessionId)
        assertTrue("a rest started from the notification while the app was stopped", deps.restTimerStore.current().running)

        val revived = viewModel(handle)

        assertTrue("the set clock comes back running", revived.setStopwatch.value.running)
        assertFalse("and the rest it overlaps is stopped", deps.restTimerStore.current().running)
        revived.ready()
        assertFalse("and stays stopped", deps.restTimerStore.current().running)
    }

    @Test
    fun aRestoredHoldStopsARestThatStartedWhileTheAppWasStopped() = runBlocking {
        val sessionId = seed(HANG_LIFT)
        val handle = handleFor(sessionId)
        val original = viewModel(handle)
        original.readyHold()
        original.startHoldSet()
        tick(5_000)
        stopTheProcess(original)
        deps.restTimerController.start(90, sessionId)
        assertTrue("a rest started from the notification while the app was stopped", deps.restTimerStore.current().running)

        val revived = viewModel(handle)

        assertTrue("the hold comes back running", revived.holdTimer.value.running)
        assertFalse("and the rest it overlaps is stopped", deps.restTimerStore.current().running)
        revived.readyHold()
        assertFalse("and stays stopped", deps.restTimerStore.current().running)
    }

    /** "A hidden alarm must not ring mid-set" (FloorTimerSurface): the rest a logged set schedules. */
    @Test
    fun startingTheSetClockCancelsARestStillWaitingOnItsReceipt() = runBlocking {
        val vm = viewModel(handleFor(seed(SQUAT_LIFT)))
        vm.ready()
        vm.logSet()
        // The virtual clock does not move here, so the rest this set scheduled is still waiting
        // out the row's settle before it starts.
        vm.awaitState { !it.logging && it.session?.sets?.size == 1 }
        assertFalse("the receipt's rest has not started yet", deps.restTimerStore.current().running)

        vm.startSetStopwatch()
        assertTrue("the set clock runs", vm.setStopwatch.value.running)
        dispatcher.scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS + 1L)
        dispatcher.scheduler.runCurrent()

        assertFalse("the waiting rest never starts under the clock", deps.restTimerStore.current().running)
        assertTrue("and the clock keeps running", vm.setStopwatch.value.running)
    }

    @Test
    fun startingAHoldCancelsARestStillWaitingOnItsReceipt() = runBlocking {
        val vm = viewModel(handleFor(seed(HANG_LIFT)))
        vm.readyHold()
        vm.startHoldSet()
        vm.logSet()
        vm.awaitState { !it.logging && it.session?.sets?.size == 1 }
        assertFalse("the saved hold stopped", vm.holdTimer.value.running)
        assertFalse("the receipt's rest has not started yet", deps.restTimerStore.current().running)

        vm.startHoldSet()
        assertTrue("the next hold runs", vm.holdTimer.value.running)
        dispatcher.scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS + 1L)
        dispatcher.scheduler.runCurrent()

        assertFalse("the waiting rest never starts under the hold", deps.restTimerStore.current().running)
        assertTrue("and the hold keeps running", vm.holdTimer.value.running)
    }

    @Test
    fun aLiftSwitchStopsTheOldHoldWithNoLateTickOrTargetCue() = runBlocking {
        val handle = handleFor(seed(HANG_LIFT, SQUAT_LIFT))
        val vm = viewModel(handle)
        val seen = cuesOf(vm)
        vm.readyHold()
        vm.startHoldSet()
        tick(5_000)
        assertEquals("the hold counts", 5, vm.holdTimer.value.elapsedSeconds)

        vm.selectExercise(SQUAT)
        assertEquals("a running hold asks before the switch", SQUAT, vm.pendingLiftSwitch.value?.exerciseId)
        vm.confirmStopTimingAndSwitch()
        vm.awaitState { it.selectedExerciseId == SQUAT }

        assertEquals("the switch ends the hold", HoldTimerUiState(), vm.holdTimer.value)
        assertNull(
            "and forgets its saved copy",
            SavedStateFloorTimer(handle).readHold(null, clock.elapsedRealtimeMillis()),
        )
        tick(60_000)
        tick(60_000)
        assertEquals("no late tick brings it back", HoldTimerUiState(), vm.holdTimer.value)
        assertEquals("and its target never cues", listOf<FloorTimerCue>(FloorTimerCue.HoldStarted), seen.toList())
    }

    @Test
    fun theTimerCuesFireOnceEachAndCarryTheSoundSetting() = runBlocking {
        deps.preferencesRepository.setRestSoundEnabled(false)
        val vm = viewModel(handleFor(seed(HANG_LIFT, SQUAT_LIFT)))
        val seen = cuesOf(vm)
        vm.readyHold()
        vm.startHoldSet()
        tick(31_000)
        awaitCues(seen, 2)

        vm.selectExercise(SQUAT)
        vm.awaitState { it.selectedExerciseId == SQUAT && it.canLog && it.draft.weightKg == 100.0 }
        vm.startSetStopwatch()
        vm.stopSetStopwatch()
        vm.stopSetStopwatch()

        assertEquals(
            "one cue per start, target and stop; the target carries the sound setting; a second stop is silent",
            listOf(
                FloorTimerCue.HoldStarted,
                FloorTimerCue.HoldTarget(soundEnabled = false),
                FloorTimerCue.StopwatchStarted,
                FloorTimerCue.StopwatchStopped,
            ),
            seen.toList(),
        )
    }

    @Test
    fun theSetClockNeverLogsASetAndStopsItselfAtItsCap() = runBlocking {
        val sessionId = seed(SQUAT_LIFT)
        val handle = handleFor(sessionId)
        val vm = viewModel(handle)
        val seen = cuesOf(vm)
        vm.ready()
        vm.startSetStopwatch()
        vm.stopSetStopwatch()
        assertTrue("a start and a stop log nothing", deps.workoutRepository.getSession(sessionId)!!.sets.isEmpty())

        vm.startSetStopwatch()
        tick(HoldWork.MAX_SECONDS * 1_000L)

        val watch = vm.setStopwatch.value
        assertFalse("the clock stops itself at its cap: $watch", watch.running)
        assertEquals("at thirty minutes", HoldWork.MAX_SECONDS, watch.elapsedSeconds)
        assertTrue("still used, so Log writes its seconds: $watch", watch.used)
        assertEquals(
            "the cap stops it like the Stop button does",
            listOf(
                FloorTimerCue.StopwatchStarted,
                FloorTimerCue.StopwatchStopped,
                FloorTimerCue.StopwatchStarted,
                FloorTimerCue.StopwatchStopped,
            ),
            seen.toList(),
        )
        val kept = SavedStateFloorTimer(handle).readStopwatch(SQUAT, clock.elapsedRealtimeMillis())
        assertNotNull("the capped clock is kept for a process death", kept)
        assertFalse("kept stopped: $kept", kept!!.running)
        assertEquals("kept frozen at the cap", HoldWork.MAX_SECONDS, kept.frozenElapsedSeconds)
        assertEquals("showing the cap", HoldWork.MAX_SECONDS, kept.elapsedSeconds)
        assertTrue("the cap logs nothing either", deps.workoutRepository.getSession(sessionId)!!.sets.isEmpty())
    }

    @Test
    fun aTimedTransitionRetiresACommitRenderedBeforeIt() = runBlocking {
        val sessionId = seed(SQUAT_LIFT)
        val vm = viewModel(handleFor(sessionId))
        vm.ready()
        val before = vm.primaryAction.awaitFirst {
            it.enabled && it.kind == WorkoutPrimaryKind.LOG_SET && it.identity.draft.weightKg == 100.0
        }

        vm.startSetStopwatch()
        val timed = vm.primaryAction.awaitFirst {
            it.kind == WorkoutPrimaryKind.LOG_SET && it.durationSeconds == 1
        }

        assertNotEquals(
            "starting the clock moves the commit's generation",
            before.identity.timedGeneration,
            timed.identity.timedGeneration,
        )
        assertEquals(
            "and nothing else about the commit",
            before.identity,
            timed.identity.copy(timedGeneration = before.identity.timedGeneration),
        )
        assertFalse("a commit drawn before the clock started is refused", vm.performPrimary(before))
        assertTrue(
            "and writes nothing",
            deps.workoutRepository.getSession(sessionId)!!.sets.isEmpty(),
        )
        tick(4_000)
        val fresh = vm.primaryAction.awaitFirst {
            it.enabled && it.kind == WorkoutPrimaryKind.LOG_SET && it.durationSeconds == 4
        }
        assertTrue("the commit drawn now is taken", vm.performPrimary(fresh))
        val row = deps.workoutRepository.awaitSession(sessionId) { it.sets.size == 1 }.sets.single()
        assertEquals("with the clock's seconds", 4, row.durationSeconds)
        assertFalse("and the log settles", vm.awaitState { !it.logging && it.session?.sets?.size == 1 }.logging)
    }

    @Test
    fun switchingBackToALiftBringsBackItsStoppedSetClock() = runBlocking {
        val vm = viewModel(handleFor(seed(SQUAT_LIFT, ROW_LIFT)))
        vm.ready()
        vm.startSetStopwatch()
        tick(4_000)
        vm.stopSetStopwatch()

        vm.selectExercise(ROW)
        vm.awaitState { it.selectedExerciseId == ROW && it.canLog && it.draft.weightKg == 80.0 }
        assertEquals(
            "the next lift shows its own clock, never the last lift's seconds",
            SetStopwatchUiState(exerciseId = ROW),
            vm.setStopwatch.value,
        )

        vm.selectExercise(SQUAT)
        vm.awaitState { it.selectedExerciseId == SQUAT && it.canLog }
        val watch = vm.setStopwatch.value
        assertEquals("back on the first lift its clock returns", SQUAT, watch.exerciseId)
        assertTrue("used: $watch", watch.used)
        assertFalse("stopped: $watch", watch.running)
        assertEquals("with its seconds", 4, watch.elapsedSeconds)
    }

    @Test
    fun removingTheLiftWhoseHoldRunsClearsTheHoldAndItsSavedState() = runBlocking {
        val sessionId = seed(HANG_LIFT)
        val handle = handleFor(sessionId)
        val vm = viewModel(handle)
        val seen = cuesOf(vm)
        vm.readyHold()
        vm.startHoldSet()
        tick(5_000)

        vm.removeSelectedLift()
        deps.workoutRepository.awaitSession(sessionId) { it.exercises.isEmpty() }
        vm.undoEntries.awaitFirst { it.lastOrNull()?.offer?.kind == UndoKind.REMOVED_LIFT }
        vm.awaitEntryUnlocked()

        assertEquals("removing the lift ends its hold", HoldTimerUiState(), vm.holdTimer.value)
        assertNull(
            "and forgets its saved copy",
            SavedStateFloorTimer(handle).readHold(null, clock.elapsedRealtimeMillis()),
        )
        tick(60_000)
        assertEquals("no late tick brings it back", HoldTimerUiState(), vm.holdTimer.value)
        assertEquals("and its target never cues", listOf<FloorTimerCue>(FloorTimerCue.HoldStarted), seen.toList())
    }

    @Test
    fun openingASavedSetForCorrectionStopsTheSetClockWithoutLoggingIt() = runBlocking {
        val sessionId = seed(SQUAT_LIFT)
        val handle = handleFor(sessionId)
        val vm = viewModel(handle)
        vm.ready()
        vm.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        val setId = deps.workoutRepository.getSession(sessionId)!!.sets.single().id
        vm.awaitEntryUnlocked()
        vm.startSetStopwatch()
        tick(5_000)
        assertTrue("the clock runs before the correction opens", vm.setStopwatch.value.running)

        vm.editSet(setId)
        vm.awaitState { it.editingSetId == setId && !it.entryLocked }

        assertEquals("the correction clears the clock", SetStopwatchUiState(), vm.setStopwatch.value)
        assertNull(
            "and its saved copy",
            SavedStateFloorTimer(handle).readStopwatch(SQUAT, clock.elapsedRealtimeMillis()),
        )
        val sets = deps.workoutRepository.getSession(sessionId)!!.sets
        assertEquals("and logs nothing", 1, sets.size)
        assertNull("nor gives the saved set its seconds", sets.single().durationSeconds)
    }

    @Test
    fun startingARestEndsARunningSetClockAndForgetsIt() = runBlocking {
        val handle = handleFor(seed(SQUAT_LIFT))
        val vm = viewModel(handle)
        vm.ready()
        vm.startSetStopwatch()
        tick(5_000)

        vm.startSelectedRest()

        assertEquals("a rest clears the set clock", SetStopwatchUiState(), vm.setStopwatch.value)
        assertNull(
            "and its saved copy",
            SavedStateFloorTimer(handle).readStopwatch(SQUAT, clock.elapsedRealtimeMillis()),
        )
        assertTrue("the rest runs", deps.restTimerStore.current().running)
    }

    /**
     * A stopped clock's ticker is gone before the restart's begins. The job is cancelled and the
     * generation moves, so the old ticker ends either way (removing one of the two changes
     * nothing); with both gone it would wake on its own beat and count the restarted clock.
     */
    @Test
    fun aRestartedSetClockTicksOnItsOwnBeatOnly() = runBlocking {
        val vm = viewModel(handleFor(seed(SQUAT_LIFT)))
        vm.ready()
        vm.startSetStopwatch()
        dispatcher.scheduler.advanceTimeBy(100)
        dispatcher.scheduler.runCurrent()
        vm.stopSetStopwatch()
        vm.startSetStopwatch()
        clock.advance(5_000)

        // The first start's beat: 250 ms after it, 150 ms after the restart.
        dispatcher.scheduler.advanceTimeBy(150)
        dispatcher.scheduler.runCurrent()
        assertTrue("the restarted clock runs", vm.setStopwatch.value.running)
        assertEquals(
            "nothing ticks on the stopped clock's beat",
            0,
            vm.setStopwatch.value.elapsedSeconds,
        )

        // The restart's own beat.
        dispatcher.scheduler.advanceTimeBy(100)
        dispatcher.scheduler.runCurrent()
        assertEquals("the restart's ticker counts", 5, vm.setStopwatch.value.elapsedSeconds)
    }

    @Test
    fun aRemovedLiftBroughtBackByUndoKeepsItsStoppedSetClock() = runBlocking {
        val sessionId = seed(SQUAT_LIFT, ROW_LIFT)
        val vm = viewModel(handleFor(sessionId))
        vm.ready()
        vm.selectExercise(ROW)
        vm.awaitState { it.selectedExerciseId == ROW && it.canLog && it.draft.weightKg == 80.0 }
        vm.startSetStopwatch()
        tick(6_000)
        vm.stopSetStopwatch()

        vm.removeSelectedLift()
        deps.workoutRepository.awaitSession(sessionId) { session -> session.exercises.none { it.exercise.id == ROW } }
        vm.undoEntries.awaitFirst { it.lastOrNull()?.offer?.kind == UndoKind.REMOVED_LIFT }
        vm.awaitEntryUnlocked()
        assertFalse("the removed lift's clock leaves the screen", vm.setStopwatch.value.used)

        vm.undoRemoveLift()
        vm.awaitState { it.selectedExerciseId == ROW && !it.entryLocked }

        val watch = vm.setStopwatch.value
        assertEquals("Undo brings the lift back with its clock", ROW, watch.exerciseId)
        assertTrue("used: $watch", watch.used)
        assertFalse("stopped: $watch", watch.running)
        assertEquals("with its seconds", 6, watch.elapsedSeconds)
    }

    /** A clock running when its lift is removed comes back by Undo stopped at the seconds it showed. */
    @Test
    fun aLiftRemovedWhileItsSetClockRanComesBackByUndoStoppedAtItsSeconds() = runBlocking {
        val sessionId = seed(SQUAT_LIFT, ROW_LIFT)
        val vm = viewModel(handleFor(sessionId))
        vm.ready()
        vm.selectExercise(ROW)
        vm.awaitState { it.selectedExerciseId == ROW && it.canLog && it.draft.weightKg == 80.0 }
        vm.startSetStopwatch()
        tick(6_000)
        assertTrue("the clock runs when the lift is removed", vm.setStopwatch.value.running)

        vm.removeSelectedLift()
        deps.workoutRepository.awaitSession(sessionId) { session -> session.exercises.none { it.exercise.id == ROW } }
        vm.undoEntries.awaitFirst { it.lastOrNull()?.offer?.kind == UndoKind.REMOVED_LIFT }
        vm.awaitEntryUnlocked()
        assertFalse("the removed lift's clock leaves the screen", vm.setStopwatch.value.used)
        tick(3_000)

        vm.undoRemoveLift()
        vm.awaitState { it.selectedExerciseId == ROW && !it.entryLocked }

        val watch = vm.setStopwatch.value
        assertEquals("Undo brings the lift back with its clock", ROW, watch.exerciseId)
        assertTrue("used: $watch", watch.used)
        assertFalse("stopped, not running on: $watch", watch.running)
        assertEquals("at the seconds it showed when the lift was removed", 6, watch.elapsedSeconds)
    }

    @Test
    fun aTimedSetLoggedBeforeAProcessDeathStaysForgotten() = runBlocking {
        val sessionId = seed(SQUAT_LIFT)
        val handle = handleFor(sessionId)
        val original = viewModel(handle)
        original.ready()
        original.startSetStopwatch()
        tick(6_000)
        original.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        val row = deps.workoutRepository.getSession(sessionId)!!.sets.single()
        assertEquals("the set logs the clock's seconds", 6, row.durationSeconds)
        assertTrue("the rest after the set runs", deps.restTimerStore.current().running)
        assertNull(
            "the logged clock's saved copy is gone",
            SavedStateFloorTimer(handle).readStopwatch(SQUAT, clock.elapsedRealtimeMillis()),
        )

        val revived = processDeath(original, handle)
        revived.ready()

        assertEquals(
            "the logged clock does not come back after a process death",
            SetStopwatchUiState(exerciseId = SQUAT),
            revived.setStopwatch.value,
        )
        assertNull(
            "nor is any of it saved",
            SavedStateFloorTimer(handle).readStopwatch(SQUAT, clock.elapsedRealtimeMillis()),
        )
        assertTrue("and the rest keeps running", deps.restTimerStore.current().running)
    }

    @Test
    fun aStopKeepsTheCommitDrawnWhileTheClockRan() = runBlocking {
        val sessionId = seed(SQUAT_LIFT)
        val vm = viewModel(handleFor(sessionId))
        vm.ready()
        vm.startSetStopwatch()
        tick(5_000)
        val drawn = vm.primaryAction.awaitFirst {
            it.enabled && it.kind == WorkoutPrimaryKind.LOG_SET && it.durationSeconds == 5
        }

        vm.stopSetStopwatch()

        assertFalse("the clock stopped", vm.setStopwatch.value.running)
        assertTrue("a stop leaves the commit drawn while the clock ran standing", vm.performPrimary(drawn))
        val logged = deps.workoutRepository.awaitSession(sessionId) { it.sets.size == 1 }.sets.single()
        assertEquals("it logs the seconds it showed", 5, logged.durationSeconds)
        assertFalse("and the log settles", vm.awaitState { !it.logging && it.session?.sets?.size == 1 }.logging)
    }

    /** The rest a logged set is waiting to start outlives a lift switch; only a clock start cancels it. */
    @Test
    fun switchingLiftRightAfterALogStillStartsItsRest() = runBlocking {
        val vm = viewModel(handleFor(seed(SQUAT_LIFT, ROW_LIFT)))
        vm.ready()
        vm.logSet()
        vm.awaitState { !it.logging && it.session?.sets?.size == 1 }
        assertFalse("the receipt's rest has not started yet", deps.restTimerStore.current().running)

        vm.selectExercise(ROW)
        vm.awaitState { it.selectedExerciseId == ROW && it.canLog && it.draft.weightKg == 80.0 }
        dispatcher.scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS + 1L)
        dispatcher.scheduler.runCurrent()

        assertTrue("the rest the log scheduled still starts after the switch", deps.restTimerStore.current().running)
    }

    @Test
    fun switchingToALiftWithAPausedClockRightAfterALogStillStartsItsRest() = runBlocking {
        val vm = viewModel(handleFor(seed(SQUAT_LIFT, ROW_LIFT)))
        vm.ready()
        vm.selectExercise(ROW)
        vm.awaitState { it.selectedExerciseId == ROW && it.canLog && it.draft.weightKg == 80.0 }
        vm.startSetStopwatch()
        tick(4_000)
        vm.stopSetStopwatch()
        vm.selectExercise(SQUAT)
        vm.awaitState { it.selectedExerciseId == SQUAT && it.canLog && it.draft.weightKg == 100.0 }
        vm.logSet()
        vm.awaitState { !it.logging && it.session?.sets?.size == 1 }
        assertFalse("the receipt's rest has not started yet", deps.restTimerStore.current().running)

        vm.selectExercise(ROW)
        vm.awaitState { it.selectedExerciseId == ROW && it.canLog && it.draft.weightKg == 80.0 }
        val watch = vm.setStopwatch.value
        assertEquals("the paused clock is back on its lift", ROW, watch.exerciseId)
        assertFalse("paused: $watch", watch.running)
        assertEquals("with its seconds", 4, watch.elapsedSeconds)
        dispatcher.scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS + 1L)
        dispatcher.scheduler.runCurrent()

        assertTrue("bringing back a paused clock does not cancel the waiting rest", deps.restTimerStore.current().running)
    }

    @Test
    fun theFloorSavesItsClocksUnderTheShippedKeys() = runBlocking {
        val handle = handleFor(seed(SQUAT_LIFT, HANG_LIFT))
        val vm = viewModel(handle)
        vm.ready()
        vm.startSetStopwatch()
        tick(3_000)
        vm.stopSetStopwatch()
        vm.selectExercise(HANG)
        vm.awaitState { it.selectedExerciseId == HANG && it.canLog && it.draft.durationSeconds == 30 }
        vm.startHoldSet()
        assertTrue("the hold runs", vm.holdTimer.value.running)

        assertEquals(
            "a hold and one lift's set clock, under the keys the shipped build wrote",
            setOf(
                "timer.hold.exerciseId",
                "timer.hold.running",
                "timer.hold.startMs",
                "timer.hold.deadlineMs",
                "timer.hold.total",
                "timer.hold.targetReached",
                "timer.sw.ids",
                "timer.sw.$SQUAT.running",
                "timer.sw.$SQUAT.startMs",
                "timer.sw.$SQUAT.frozen",
                "timer.sw.$SQUAT.elapsed",
                "timer.sw.$SQUAT.used",
            ),
            handle.keys().filter { it.startsWith("timer.") }.toSet(),
        )
        assertEquals("the hold is the hang's", HANG, handle.get<String>("timer.hold.exerciseId"))
        assertEquals("the set clock is the squat's", arrayListOf(SQUAT), handle.get<ArrayList<String>>("timer.sw.ids"))
    }

    /** Today's eager commit (audit X6 AR-4 keeps it): it follows a hold with nobody watching it. */
    @Test
    fun theCommitFollowsAHoldWithNothingWatching() = runBlocking {
        val vm = viewModel(handleFor(seed(HANG_LIFT)))
        awaitCommit(vm) { it.enabled && it.kind == WorkoutPrimaryKind.START_HOLD }

        vm.startHoldSet()

        awaitCommit(vm) { it.kind == WorkoutPrimaryKind.LOG_HOLD }
        assertTrue("the hold runs", vm.holdTimer.value.running)
    }

    private fun tick(elapsedMs: Long) {
        clock.advance(elapsedMs)
        dispatcher.scheduler.advanceTimeBy(250)
        dispatcher.scheduler.runCurrent()
    }

    /**
     * The squat is on the floor with its prefilled 100 kg and the entry takes taps. The weight is
     * part of the wait: the screen state is combined across Room's threads, and for a moment it
     * can show the entry ready beside the empty draft from before the prefill landed.
     */
    private suspend fun ActiveWorkoutViewModel.ready(): ActiveWorkoutUiState =
        uiState.awaitFirst { it.canLog && it.draft.weightKg == 100.0 }

    /** The hang is on the floor with its 30 s target and the entry takes taps. */
    private suspend fun ActiveWorkoutViewModel.readyHold(): ActiveWorkoutUiState =
        uiState.awaitFirst { it.canLog && it.draft.durationSeconds == 30 }

    /** The ViewModel and its draft cache go with the process; the saved state stays. */
    private suspend fun stopTheProcess(vm: ActiveWorkoutViewModel) {
        vm.clearAndJoinForTest()
        models.remove(vm)
        deps.workoutDraftCache.clearAll()
    }

    private suspend fun processDeath(vm: ActiveWorkoutViewModel, handle: SavedStateHandle): ActiveWorkoutViewModel {
        stopTheProcess(vm)
        return viewModel(handle)
    }

    /** Every cue [vm] sends from now on, in order. */
    private fun cuesOf(vm: ActiveWorkoutViewModel): List<FloorTimerCue> {
        val seen = CopyOnWriteArrayList<FloorTimerCue>()
        collectors.launch { vm.floorTimerCue.collect { seen += it } }
        return seen
    }

    /** The target's cue follows a read of the sound setting, so it is waited for, bounded. */
    private suspend fun awaitCues(seen: List<FloorTimerCue>, count: Int) {
        try {
            withTimeout(TestWaits.FLOW_MS) {
                while (seen.size < count) {
                    dispatcher.scheduler.runCurrent()
                    if (seen.size < count) delay(10)
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("expected $count timer cues, saw $seen", timedOut)
        }
    }

    /** Polls the commit's current value, bounded, with nothing collecting it or the screen state. */
    private suspend fun awaitCommit(vm: ActiveWorkoutViewModel, predicate: (WorkoutPrimaryAction) -> Boolean) {
        try {
            withTimeout(TestWaits.FLOW_MS) {
                while (!predicate(vm.primaryAction.value)) delay(10)
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("the commit never matched; it was ${vm.primaryAction.value}", timedOut)
        }
    }

    private fun handleFor(sessionId: String) = SavedStateHandle(mapOf("sessionId" to sessionId))

    private fun viewModel(handle: SavedStateHandle) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = handle,
        container = deps,
    ).also(models::add)

    /** One live workout of [lifts], in that order; the first is the one the floor opens on. */
    private suspend fun seed(vararg lifts: Lift): String {
        deps.database.exerciseDao().insertAll(
            lifts.map { lift ->
                ExerciseEntity(
                    id = lift.id,
                    name = lift.name,
                    muscleGroup = "Legs",
                    notes = "",
                    isCustom = false,
                    loadType = lift.loadType,
                    nameKey = lift.name.lowercase(),
                )
            },
        )
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(id = ROUTINE, name = "Clocks", notes = "", createdAt = STAMP, updatedAt = STAMP),
        )
        lifts.forEachIndexed { index, lift ->
            deps.database.routineDao().upsertRoutineExercise(
                RoutineExerciseEntity(
                    id = "re-${lift.id}",
                    routineId = ROUTINE,
                    exerciseId = lift.id,
                    sortOrder = index,
                    targetSets = lift.targetSets,
                    targetReps = lift.targetReps,
                    targetWeightKg = lift.targetWeightKg,
                    restSeconds = lift.restSeconds,
                    targetSeconds = lift.targetSeconds,
                ),
            )
        }
        val routine = checkNotNull(deps.routineRepository.getById(ROUTINE))
        return deps.workoutRepository.startRoutine(routine).id
    }

    private data class Lift(
        val id: String,
        val name: String,
        val loadType: String,
        val targetSets: Int,
        val targetReps: Int,
        val targetWeightKg: Double?,
        val targetSeconds: Int?,
        val restSeconds: Int,
    )

    private companion object {
        const val SQUAT = "squat"
        const val ROW = "row"
        const val HANG = "ex-dead-hang"
        const val ROUTINE = "routine-clocks"
        const val STAMP = 1_700_000_000_000L

        val SQUAT_LIFT = Lift(
            id = SQUAT, name = "Squat", loadType = "EXTERNAL", targetSets = 3, targetReps = 5,
            targetWeightKg = 100.0, targetSeconds = null, restSeconds = 90,
        )
        val ROW_LIFT = Lift(
            id = ROW, name = "Row", loadType = "EXTERNAL", targetSets = 3, targetReps = 5,
            targetWeightKg = 80.0, targetSeconds = null, restSeconds = 90,
        )
        val HANG_LIFT = Lift(
            id = HANG, name = "Dead Hang", loadType = "BODYWEIGHT", targetSets = 2, targetReps = 1,
            targetWeightKg = null, targetSeconds = 30, restSeconds = 75,
        )
    }
}
