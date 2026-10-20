package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.clearForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestWaits
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real Room DAO and ViewModel. A post-commit hold on [WorkoutRepository.afterRemoveExerciseCommitForTest]
 * pauses removal cleanup after the delete commits so the observer can win deterministically.
 * [MutationJobResumeGate] (test-only Main dispatcher) may still queue descendant Main work; it does
 * not ship in production.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RemovedLiftObserverOrderingTest {
    private val scheduler = TestCoroutineScheduler()
    private val mainQueue = StandardTestDispatcher(scheduler, "queued Main")
    private val mainGate = MutationJobResumeGate(mainQueue)
    private val ancillary = UnconfinedTestDispatcher(scheduler, "IO/preferences")
    private val observedRows = CopyOnWriteArrayList<SessionWithDetails?>()
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private lateinit var deps: FakeAppDependencies
    private lateinit var unheld: WorkoutRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(mainGate)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = ancillary,
            workoutDaoDecorator = { ObservedRows(it) },
        )
        unheld = WorkoutRepository(deps.database, deps.database.workoutDao())
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        deps.workoutRepository.afterRemoveExerciseCommitForTest = null
        mainGate.release()
        try {
            runBlocking {
                val clearing = viewModels.map { model ->
                    launch(Dispatchers.Unconfined) { model.clearAndJoinForTest() }
                }
                pumpUntil("ViewModels cancel completely", { clearing.map { it.isCompleted } }) {
                    clearing.all { it.isCompleted }
                }
            }
            viewModels.clear()
        } finally {
            if (::deps.isInitialized) {
                deps.restTimerController.stop()
                deps.close()
            }
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aCommittedRemovalMustNotClearTheSurvivorAlreadySelectedByItsObserver() {
        val sessionId = runBlocking {
            seedLegExtension(deps, loggedSets = emptyList(), withNextLift = true)
        }
        val vm = floorViewModel(deps, sessionId).also(viewModels::add)
        readyOn(vm, FLOOR_LIFT_ID, FLOOR_KG70, 10)
        vm.selectExercise(FLOOR_NEXT_LIFT_ID)
        readyOn(vm, FLOOR_NEXT_LIFT_ID, 40.0, 8)
        assertEquals(FLOOR_NEXT_LIFT_ID, deps.workoutDraftCache.selectedExerciseId(sessionId))
        assertFalse("no rest timer is running before this selection race", deps.restTimerStore.current().running)

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
        val newChildren = scopeJob.children.filterNot { it in priorChildren }.toList()
        assertEquals("exactly one removal coroutine was launched", 1, newChildren.size)
        val removalJob = newChildren.single()

        try {
            pumpUntil(
                "post-commit hold engaged after Room committed the removal",
                { "postCommit=${postCommitReached.isCompleted} removalComplete=${removalJob.isCompleted}" },
            ) {
                postCommitReached.isCompleted
            }
            val committed = checkNotNull(runBlocking { unheld.getSession(sessionId) })
            assertEquals(
                "the actual committed Room row contains only the survivor",
                listOf(FLOOR_LIFT_ID),
                committed.exercises.map { it.exercise.id },
            )
            assertTrue("removal never creates a saved set", committed.sets.isEmpty())

            pumpUntil("the ordinary observer selects and prefills the surviving lift", { vm.uiState.value }) {
                val state = vm.uiState.value
                deps.workoutDraftCache.selectedExerciseId(sessionId) == FLOOR_LIFT_ID &&
                    state.session?.exercises?.map { it.exercise.id } == listOf(FLOOR_LIFT_ID) &&
                    state.selectedExerciseId == FLOOR_LIFT_ID &&
                    state.liftReadiness == LiftEntryReadiness.READY &&
                    state.draft.weightKg == FLOOR_KG70 && state.draft.reps == 10
            }
            assertTrue(
                "a real DAO observation delivered the committed survivor row",
                observedRows.drop(rowMark).filterNotNull().any { row ->
                    row.session.id == sessionId &&
                        row.exercises.map { it.exercise.id } == listOf(FLOOR_LIFT_ID)
                },
            )
            assertFalse("the held removal has not completed", removalJob.isCompleted)
            assertTrue("the removal still owns the entry lock", vm.uiState.value.mutating)
            assertTrue("the ordinary screen remains locked during the paused mutation", vm.uiState.value.entryLocked)

            postCommitRelease.complete(Unit)
            pumpUntil("removal cleanup completes and unlocks", { vm.uiState.value }) {
                removalJob.isCompleted && !vm.uiState.value.mutating && !vm.uiState.value.entryLocked
            }
            val after = vm.uiState.value
            assertEquals("the screen names the surviving lift", FLOOR_LIFT_ID, after.selectedExerciseId)
            assertEquals(
                "removal cleanup must preserve the observer's READY selection; state=$after",
                LiftEntryReadiness.READY,
                after.liftReadiness,
            )
            assertEquals(FLOOR_KG70, after.draft.weightKg, EPSILON)
            assertEquals(10, after.draft.reps)
            assertTrue("removal is still available to Undo", vm.undoEntries.value.isNotEmpty())
            assertFalse("the ordering check did not start or stop rest", deps.restTimerStore.current().running)

            vm.undoRemoveLift()
            readyOn(vm, FLOOR_NEXT_LIFT_ID, 40.0, 8)
            val restored = checkNotNull(runBlocking { unheld.getSession(sessionId) })
            assertEquals(listOf(FLOOR_LIFT_ID, FLOOR_NEXT_LIFT_ID), restored.exercises.map { it.exercise.id })
            assertTrue("Undo restores the lift without inventing a saved set", restored.sets.isEmpty())
            assertFalse("Undo also leaves the timer idle", deps.restTimerStore.current().running)
        } finally {
            postCommitRelease.complete(Unit)
            deps.workoutRepository.afterRemoveExerciseCommitForTest = null
            mainGate.release()
        }
    }

    private fun readyOn(vm: ActiveWorkoutViewModel, exerciseId: String, weightKg: Double, reps: Int) {
        pumpUntil("$exerciseId is ready with its own numbers", { vm.uiState.value }) {
            val state = vm.uiState.value
            state.loadState == SessionLoadState.FOUND && state.selectedExerciseId == exerciseId &&
                state.liftReadiness == LiftEntryReadiness.READY && !state.entryLocked &&
                state.draft.weightKg == weightKg && state.draft.reps == reps
        }
    }

    private fun pumpUntil(what: String, last: () -> Any?, predicate: () -> Boolean) {
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

    @Test
    fun postCommitHoldCapturesEveryRemovalAcross100Runs() {
        repeat(100) { run ->
            val postCommitReached = CompletableDeferred<Unit>()
            val postCommitRelease = CompletableDeferred<Unit>()
            deps.workoutRepository.afterRemoveExerciseCommitForTest = {
                postCommitReached.complete(Unit)
                postCommitRelease.await()
            }
            val sessionId = runBlocking {
                seedLegExtension(deps, loggedSets = emptyList(), withNextLift = true)
            }
            val vm = floorViewModel(deps, sessionId)
            readyOn(vm, FLOOR_LIFT_ID, FLOOR_KG70, 10)
            vm.selectExercise(FLOOR_NEXT_LIFT_ID)
            readyOn(vm, FLOOR_NEXT_LIFT_ID, 40.0, 8)
            val scopeJob = checkNotNull(vm.viewModelScope.coroutineContext[Job])
            val priorChildren = scopeJob.children.toSet()
            vm.removeSelectedLift()
            val removalJob = scopeJob.children.filterNot { it in priorChildren }.single()
            try {
                pumpUntil(
                    "run $run: post-commit hold after removal commit",
                    { "postCommit=${postCommitReached.isCompleted}" },
                ) {
                    postCommitReached.isCompleted
                }
                assertTrue("run $run: mutation still owns the entry lock", vm.uiState.value.mutating)
                assertFalse("run $run: removal coroutine still paused in hook", removalJob.isCompleted)
            } finally {
                postCommitRelease.complete(Unit)
                pumpUntil("run $run: removal cleanup completes", { vm.uiState.value }) {
                    removalJob.isCompleted && !vm.uiState.value.mutating
                }
                deps.workoutRepository.afterRemoveExerciseCommitForTest = null
                scheduler.advanceUntilIdle()
                vm.clearForTest()
                scheduler.advanceUntilIdle()
            }
        }
    }

    private companion object { const val EPSILON = 0.0001 }
}
