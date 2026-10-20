package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearForTest
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.UndoKind
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.ControllableTimePort
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.workout.SavedStateFloorTimer
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val mainQueue = StandardTestDispatcher(scheduler, "Main")
    private val ancillary = UnconfinedTestDispatcher(scheduler, "IO/preferences")
    private val observedRows = CopyOnWriteArrayList<SessionWithDetails?>()
    private lateinit var deps: FakeAppDependencies
    private lateinit var unheld: WorkoutRepository
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainQueue)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = ancillary,
            time = clock,
            workoutDaoDecorator = { ObservedRows(it) },
        )
        unheld = WorkoutRepository(deps.database, deps.database.workoutDao())
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        deps.workoutRepository.afterRemoveExerciseCommitForTest = null
        viewModels.forEach { vm ->
            scheduler.advanceUntilIdle()
            vm.clearForTest()
        }
        scheduler.advanceUntilIdle()
        viewModels.clear()
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun observerFirst_undoRestoresStoppedSetClockSeconds() {
        val sessionId = runBlocking {
            seedLegExtension(deps, loggedSets = emptyList(), withNextLift = true)
        }
        val vm = floorViewModel(deps, sessionId).also(viewModels::add)
        readyOn(vm, FLOOR_LIFT_ID, FLOOR_KG70, 10)
        vm.selectExercise(FLOOR_NEXT_LIFT_ID)
        readyOn(vm, FLOOR_NEXT_LIFT_ID, 40.0, 8)
        vm.startSetStopwatch()
        tick(7_000)
        vm.stopSetStopwatch()

        val scopeJob = checkNotNull(vm.viewModelScope.coroutineContext[Job])
        val priorChildren = scopeJob.children.toSet()
        val rowMark = observedRows.size
        val postCommitReached = CompletableDeferred<Unit>()
        val postCommitRelease = CompletableDeferred<Unit>()
        deps.workoutRepository.afterRemoveExerciseCommitForTest = {
            postCommitReached.complete(Unit)
            postCommitRelease.await()
        }
        vm.removeSelectedLift()
        val removalJob = scopeJob.children.filterNot { it in priorChildren }.single()
        try {
            pumpUntil("post-commit hold after removal commit") { postCommitReached.isCompleted }
            pumpUntil("observer selects survivor while removal is held", { vm.uiState.value }) {
                val state = vm.uiState.value
                deps.workoutDraftCache.selectedExerciseId(sessionId) == FLOOR_LIFT_ID &&
                    state.session?.exercises?.map { it.exercise.id } == listOf(FLOOR_LIFT_ID) &&
                    state.selectedExerciseId == FLOOR_LIFT_ID &&
                    state.liftReadiness == LiftEntryReadiness.READY &&
                    state.draft.weightKg == FLOOR_KG70 && state.draft.reps == 10
            }
            assertTrue(
                "DAO observation delivered the survivor row",
                observedRows.drop(rowMark).filterNotNull().any { row ->
                    row.session.id == sessionId &&
                        row.exercises.map { it.exercise.id } == listOf(FLOOR_LIFT_ID)
                },
            )
            assertFalse("removal coroutine still paused in hook", removalJob.isCompleted)
            postCommitRelease.complete(Unit)
            pumpUntil("removal cleanup completes", { vm.uiState.value }) {
                removalJob.isCompleted && !vm.uiState.value.mutating
            }
        } finally {
            postCommitRelease.complete(Unit)
            deps.workoutRepository.afterRemoveExerciseCommitForTest = null
        }

        vm.undoRemoveLift()
        pumpUntil("undo restores stopwatch") {
            val watch = vm.setStopwatch.value
            watch.exerciseId == FLOOR_NEXT_LIFT_ID && watch.used && !watch.running && watch.elapsedSeconds == 7
        }
        assertEquals(7, vm.setStopwatch.value.elapsedSeconds)
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
        pumpUntil("removal finishes") { !vm.uiState.value.mutating && vm.undoEntries.value.isNotEmpty() }
        vm.undoRemoveLift()
        pumpUntil("undo restores stopwatch") {
            val watch = vm.setStopwatch.value
            watch.exerciseId == FLOOR_NEXT_LIFT_ID && watch.used && !watch.running && watch.elapsedSeconds == 7
        }
        val watch = vm.setStopwatch.value
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
            pumpUntil("run $run: restored lift is on the session", { vm.uiState.value.session?.exercises?.map { it.exercise.id } }) {
                vm.uiState.value.session?.exercises?.map { it.exercise.id }?.contains(FLOOR_NEXT_LIFT_ID) == true
            }
            vm.selectExercise(FLOOR_NEXT_LIFT_ID)
            pumpUntil("run $run: undo restores stopwatch", { vm.setStopwatch.value }) {
                val watch = vm.setStopwatch.value
                watch.exerciseId == FLOOR_NEXT_LIFT_ID && watch.elapsedSeconds == 5
            }
            assertEquals("run $run", 5, vm.setStopwatch.value.elapsedSeconds)
            scheduler.advanceUntilIdle()
            vm.clearForTest()
            scheduler.advanceUntilIdle()
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

    private fun readyOn(vm: ActiveWorkoutViewModel, exerciseId: String, weightKg: Double, reps: Int) {
        pumpUntil("$exerciseId ready", { vm.uiState.value }) {
            val state = vm.uiState.value
            state.loadState == SessionLoadState.FOUND && state.selectedExerciseId == exerciseId &&
                state.liftReadiness == LiftEntryReadiness.READY && !state.entryLocked &&
                state.draft.weightKg == weightKg && state.draft.reps == reps
        }
    }

    private fun tick(elapsedMs: Long) {
        clock.advance(elapsedMs)
        scheduler.advanceTimeBy(250)
        scheduler.runCurrent()
    }

    private fun pumpUntil(what: String, last: () -> Any? = { null }, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TestWaits.FLOW_MS * 1_000_000L
        do {
            scheduler.runCurrent()
            if (predicate()) return
        } while (System.nanoTime() < deadline)
        throw AssertionError("$what did not arrive within ${TestWaits.FLOW_MS} ms; last=${last()}")
    }

    private inner class ObservedRows(private val real: WorkoutDao) : WorkoutDao by real {
        override fun observeSession(id: String): Flow<SessionWithDetails?> =
            real.observeSession(id).onEach { observedRows.add(it) }
    }

    private companion object { const val EPSILON = 0.0001 }
}
