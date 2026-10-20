package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.UndoKind
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.ControllableTimePort
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.workout.SavedStateFloorTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RemovedLiftUndoRaceTest {
    private val clock = ControllableTimePort()
    private val scheduler = TestCoroutineScheduler()
    private val mainQueue = StandardTestDispatcher(scheduler, "queued Main")
    private val mainGate = MutationJobResumeGate(mainQueue)
    private val ancillary = UnconfinedTestDispatcher(scheduler, "IO/preferences")
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainGate)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = ancillary,
            time = clock,
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        mainGate.release()
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun observerFirst_undoRestoresStoppedSetClockSeconds() = runBlocking {
        val sessionId = seedLegExtension(deps, loggedSets = emptyList(), withNextLift = true)
        val handle = SavedStateHandle(mapOf("sessionId" to sessionId))
        val vm = floorViewModel(deps, sessionId).also(viewModels::add)
        readyOnNext(vm)
        vm.startSetStopwatch()
        tick(7_000)
        vm.stopSetStopwatch()
        armRemovalAndHoldAfterCommit(vm) {
            pumpUntil("observer selects survivor while removal is held") {
                vm.uiState.value.selectedExerciseId == FLOOR_LIFT_ID && vm.uiState.value.canLog
            }
        }
        vm.undoRemoveLift()
        val watch = vm.setStopwatch.awaitFirst {
            it.exerciseId == FLOOR_NEXT_LIFT_ID && it.used && !it.running && it.elapsedSeconds == 7
        }
        assertEquals(7, watch.elapsedSeconds)
    }

    @Test
    fun mutationFirst_undoRestoresStoppedSetClockSeconds() = runBlocking {
        val sessionId = seedLegExtension(deps, loggedSets = emptyList(), withNextLift = true)
        val vm = floorViewModel(deps, sessionId).also(viewModels::add)
        readyOnNext(vm)
        vm.startSetStopwatch()
        tick(7_000)
        vm.stopSetStopwatch()
        vm.removeSelectedLift()
        scheduler.advanceUntilIdle()
        pumpUntil("removal finishes") { !vm.uiState.value.mutating && vm.undoEntries.value.isNotEmpty() }
        vm.undoRemoveLift()
        val watch = vm.setStopwatch.awaitFirst {
            it.exerciseId == FLOOR_NEXT_LIFT_ID && it.used && !it.running && it.elapsedSeconds == 7
        }
        assertEquals(7, watch.elapsedSeconds)
    }

    @Test
    fun afterOfferExpires_checkpointHasNoOrphanStopwatchForRemovedLift() = runBlocking {
        val sessionId = seedLegExtension(deps, loggedSets = emptyList(), withNextLift = true)
        val handle = SavedStateHandle(mapOf("sessionId" to sessionId))
        val vm = floorViewModel(deps, sessionId).also(viewModels::add)
        readyOnNext(vm)
        vm.startSetStopwatch()
        tick(6_000)
        vm.stopSetStopwatch()
        vm.removeSelectedLift()
        scheduler.advanceUntilIdle()
        pumpUntil("undo offer is queued") {
            vm.undoEntries.value.lastOrNull()?.offer?.kind == UndoKind.REMOVED_LIFT
        }
        vm.onUndoOfferExpired()
        assertNull(
            SavedStateFloorTimer(handle).readStopwatch(FLOOR_NEXT_LIFT_ID, clock.elapsedRealtimeMillis()),
        )
    }

    @Test
    fun undoClockRace_stableFiftyRuns() = runBlocking {
        repeat(50) { run ->
            val sessionId = seedLegExtension(deps, loggedSets = emptyList(), withNextLift = true)
            val vm = floorViewModel(deps, sessionId).also(viewModels::add)
            readyOnNext(vm)
            vm.startSetStopwatch()
            tick(5_000)
            vm.stopSetStopwatch()
            vm.removeSelectedLift()
            scheduler.advanceUntilIdle()
            pumpUntil("run $run: removal settled") { !vm.uiState.value.mutating }
            vm.undoRemoveLift()
            val watch = vm.setStopwatch.awaitFirst {
                it.exerciseId == FLOOR_NEXT_LIFT_ID && it.elapsedSeconds == 5
            }
            assertEquals("run $run", 5, watch.elapsedSeconds)
            vm.clearAndJoinForTest()
            viewModels.remove(vm)
        }
    }

    private fun readyOnNext(vm: ActiveWorkoutViewModel) {
        pumpUntil("first lift ready") {
            vm.uiState.value.loadState == SessionLoadState.FOUND && vm.uiState.value.canLog
        }
        vm.selectExercise(FLOOR_NEXT_LIFT_ID)
        pumpUntil("next lift selected") {
            vm.uiState.value.selectedExerciseId == FLOOR_NEXT_LIFT_ID && vm.uiState.value.canLog
        }
    }

    private suspend fun armRemovalAndHoldAfterCommit(vm: ActiveWorkoutViewModel, whileHeld: () -> Unit) {
        val scopeJob = checkNotNull(vm.viewModelScope.coroutineContext[Job])
        val priorChildren = scopeJob.children.toSet()
        vm.removeSelectedLift()
        val removalJob = scopeJob.children.filterNot { it in priorChildren }.single()
        mainGate.arm(removalJob)
        pumpUntil("removal held on Main after Room commit") {
            if (removalJob.isCompleted && !mainGate.isHeld) {
                error("removal finished without capturing Main dispatch")
            }
            mainGate.isHeld
        }
        try {
            whileHeld()
        } finally {
            mainGate.release()
            pumpUntil("removal completes") { removalJob.isCompleted && !vm.uiState.value.mutating }
        }
    }

    private fun tick(elapsedMs: Long) {
        clock.advance(elapsedMs)
        scheduler.advanceTimeBy(250)
        scheduler.runCurrent()
    }

    private fun pumpUntil(what: String, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TestWaits.FLOW_MS * 1_000_000L
        do {
            scheduler.runCurrent()
            if (predicate()) return
        } while (System.nanoTime() < deadline)
        throw AssertionError("$what did not arrive within ${TestWaits.FLOW_MS} ms")
    }
}
