package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestWaits
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
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
 * No production hook, repository subclass, manufactured session row, private reflection,
 * timer override, or test wait increase is needed. The same real Room DAO and ViewModel
 * are used. An observing DAO decorator records actual rows without holding transactions.
 *
 * Main uses a StandardTestDispatcher. After the initial mutation launch is queued, this
 * test identifies its exact Job and holds that Job's next return-to-Main runnable.
 * There is no prior suspend point outside Room in removeSelectedLift's current path:
 * the undo mutex is uncontended and the repository's withTransaction is the first one.
 * A committed row, actual observer selection, and READY while mutating remains true
 * are mandatory preconditions. If the path gains an earlier suspend, those preconditions
 * fail: the test must not silently call an earlier pause a committed-transaction pause.
 *
 * Unconditional cleanup cleared the observer's READY selection. The conditional
 * clear preserves it and the remaining Remove/Undo/durable-row assertions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RemovedLiftObserverOrderingTest {
    private val scheduler = TestCoroutineScheduler()
    private val mainQueue = StandardTestDispatcher(scheduler, "queued Main")
    private val mainGate = OneJobResumeGate(mainQueue)
    private val ancillary = UnconfinedTestDispatcher(scheduler, "preferences/IO")
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
        // Cancellation must never be held behind a failed precondition/assertion.
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
        vm.removeSelectedLift()
        // No scheduler pumping occurs between the snapshots. Existing observer/combined
        // flow jobs may be queued, but the one new direct scope child is the mutation.
        val newChildren = scopeJob.children.filterNot { it in priorChildren }.toList()
        assertEquals("exactly one removal coroutine was launched", 1, newChildren.size)
        val removalJob = newChildren.single()
        mainGate.arm(removalJob)

        try {
            pumpUntil("Room's removal return is held", { mainGate.isHeld }) { mainGate.isHeld }
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
            assertTrue("the mutation is still paused before cleanup", mainGate.isHeld)
            assertFalse("the held removal has not completed", removalJob.isCompleted)
            assertTrue("the removal still owns the entry lock", vm.uiState.value.mutating)
            assertTrue("the ordinary screen remains locked during the paused mutation", vm.uiState.value.entryLocked)

            mainGate.release()
            pumpUntil("removal cleanup completes and unlocks", { vm.uiState.value }) {
                removalJob.isCompleted && !vm.uiState.value.mutating && !vm.uiState.value.entryLocked
            }
            val after = vm.uiState.value
            assertEquals("the screen names the surviving lift", FLOOR_LIFT_ID, after.selectedExerciseId)
            // This is the decisive red-before/green-after assertion. We deliberately do
            // not wait for READY again: its precondition was already proven before release.
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

    /** Real-time bound for real Room threads; runCurrent never advances virtual timer time. */
    private fun pumpUntil(what: String, last: () -> Any?, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TestWaits.FLOW_MS * 1_000_000L
        do {
            scheduler.runCurrent()
            if (predicate()) return
            Thread.sleep(1)
        } while (System.nanoTime() < deadline)
        throw AssertionError("$what did not arrive within ${TestWaits.FLOW_MS} ms; last=${last()}")
    }

    private inner class ObservedRows(private val real: WorkoutDao) : WorkoutDao by real {
        override fun observeSession(id: String): Flow<SessionWithDetails?> =
            real.observeSession(id).onEach { observedRows.add(it) }
    }

    /** Hold exactly one queued continuation of the captured mutation, without blocking Room. */
    private class OneJobResumeGate(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        private data class Pending(val context: CoroutineContext, val block: Runnable)
        private val lock = Any()
        private var armedJob: Job? = null
        private var held: Pending? = null

        val isHeld: Boolean get() = synchronized(lock) { held != null }

        fun arm(job: Job) = synchronized(lock) {
            check(armedJob == null && held == null)
            armedJob = job
        }

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = delegate.isDispatchNeeded(context)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            val hold = synchronized(lock) {
                val target = armedJob
                if (target != null && context[Job] === target) {
                    check(held == null)
                    held = Pending(context, block)
                    armedJob = null
                    true
                } else false
            }
            if (!hold) delegate.dispatch(context, block)
        }

        fun release() {
            val pending = synchronized(lock) {
                armedJob = null
                held.also { held = null }
            }
            if (pending != null) delegate.dispatch(pending.context, pending.block)
        }
    }

    private companion object { const val EPSILON = 0.0001 }
}
