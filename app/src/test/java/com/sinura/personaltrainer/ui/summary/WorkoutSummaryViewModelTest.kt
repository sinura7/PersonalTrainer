package com.sinura.personaltrainer.ui.summary

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.FinishedWorkingSetRow
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.SummaryHeadline
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Summary reads once. A later historical repair must not rewrite the
 * celebration the user is looking at.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WorkoutSummaryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: WorkoutSummaryViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun missingSessionResolvesMissingInsteadOfSpinning() = runBlocking {
        val vm = createViewModel("missing")
        val state = vm.uiState.first { !it.isLoading }

        assertTrue(state.missing)
        assertFalse(state.isLoading)
        // A row that is not there is not "saved", and it is not a read fault either.
        assertFalse(state.failed)
        assertFalse(state.savedConfirmed)
    }

    @Test
    fun finishedWorkingSessionBuildsVolumeSetsTitleAndNotes() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            routineName = "Summary lower",
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
            notes = "summary note",
        )
        val vm = createViewModel(fixture.session.id)

        val state = vm.uiState.first { !it.isLoading }
        assertFalse(state.missing)
        assertFalse(state.failed)
        assertTrue(state.savedConfirmed)
        assertEquals(fixture.session.id, state.sessionId)
        assertTrue(state.summary.hasWork)
        assertEquals("Summary lower", state.summary.title)
        assertEquals(1, state.summary.workingSets)
        assertEquals(500.0, state.summary.volumeKg, 0.0001)
        assertEquals("summary note", state.summary.notes)
    }

    @Test
    fun warmupOnlySessionIsSavedButHasNoSummaryWork() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(20.0, 5, isWarmup = true)),
            finish = true,
        )
        val vm = createViewModel(fixture.session.id)

        val state = vm.uiState.first { !it.isLoading }
        assertFalse(state.missing)
        assertFalse(state.failed)
        // The finished row was read: this is the one no-work state allowed to say "saved".
        assertTrue(state.savedConfirmed)
        assertFalse(state.summary.hasWork)
        assertEquals(0, state.summary.workingSets)
    }

    @Test
    fun blankSessionIdResolvesMissingInsteadOfSpinning() = runBlocking {
        val vm = createViewModel("")
        val state = vm.uiState.first { !it.isLoading }

        assertTrue(state.missing)
        assertFalse(state.isLoading)
        assertFalse(state.savedConfirmed)
    }

    /**
     * UX05-AC01/AC02: the finished row reads fine but the history read behind the records
     * check throws. That is "saved, summary unavailable" — never "missing" — and Retry re-reads
     * without finishing anything twice.
     */
    @Test
    fun aSummaryThatWillNotComputeIsSavedButUnavailableNotMissing() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
        )
        val finishedAt = fixture.session.finishedAt
        val gate = FailureGate(failHistory = true)
        val vm = createViewModel(fixture.session.id, container = faultyReads(gate))

        val failed = withTimeout(5_000) { vm.uiState.first { !it.isLoading } }
        assertTrue(failed.failed)
        assertFalse(failed.missing)
        assertTrue(failed.savedConfirmed)
        assertEquals(fixture.session.id, failed.sessionId)
        assertFalse(failed.summary.hasWork)

        gate.failHistory = false
        vm.retry()
        val recovered = withTimeout(5_000) { vm.uiState.first { !it.isLoading && !it.failed } }
        assertFalse(recovered.missing)
        assertTrue(recovered.savedConfirmed)
        assertTrue(recovered.summary.hasWork)
        assertEquals(500.0, recovered.summary.volumeKg, 0.0001)

        // Nothing was written by the failure or the retry: same row, same finish stamp,
        // no live session minted.
        val sessions = deps.database.workoutDao().getAllSessions()
        assertEquals(1, sessions.size)
        assertEquals(finishedAt, sessions.single().finishedAt)
        assertNull(deps.workoutRepository.getInProgress())
    }

    /**
     * The row itself could not be read. Nothing is known either way, so the state is failed
     * without the saved claim — and without the missing claim.
     */
    @Test
    fun anUnreadableRowIsUnavailableNotMissingAndNotSaved() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
        )
        val gate = FailureGate(failSession = true)
        val vm = createViewModel(fixture.session.id, container = faultyReads(gate))

        val failed = withTimeout(5_000) { vm.uiState.first { !it.isLoading } }
        assertTrue(failed.failed)
        assertFalse(failed.missing)
        assertFalse(failed.savedConfirmed)

        gate.failSession = false
        vm.retry()
        val recovered = withTimeout(5_000) { vm.uiState.first { !it.isLoading && !it.failed } }
        assertTrue(recovered.savedConfirmed)
        assertNotNull(recovered.summary.highlights.singleOrNull())
    }

    @Test
    fun retryIsANoOpUnlessTheLastReadThrew() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
        )
        val vm = createViewModel(fixture.session.id)
        val loaded = vm.uiState.first { !it.isLoading }
        vm.retry()
        assertEquals(loaded, vm.uiState.value)
    }

    /** UX05-AC03: a bodyweight-only session is not a "0 kg" receipt. */
    @Test
    fun aBodyweightOnlySessionHeadlinesReps() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            exerciseId = "test-pushup",
            exerciseName = "Push-up",
            loggedSets = listOf(TestSetInput(0.0, 20), TestSetInput(0.0, 15)),
            finish = true,
        )
        // The fixture seeds a loaded lift; the summary reads the lift's stored class, so
        // reclassifying the row is what makes this a bodyweight session.
        deps.database.exerciseDao().update(
            ExerciseEntity(
                id = "test-pushup",
                name = "Push-up",
                muscleGroup = "Chest",
                notes = "",
                isCustom = false,
                loadType = LoadType.BODYWEIGHT.name,
                nameKey = "push-up",
            ),
        )
        val vm = createViewModel(fixture.session.id)
        val state = vm.uiState.first { !it.isLoading }
        assertTrue(state.summary.hasWork)
        assertEquals(35, state.summary.bodyweightReps)
        assertEquals(SummaryHeadline.BodyweightReps(35), state.summary.headline)
    }

    @Test
    fun summaryLoadsOnceAndDoesNotChangeAfterHistoricalRepair() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
        )
        val vm = createViewModel(fixture.session.id)
        val before = vm.uiState.first { !it.isLoading }.summary

        val set = fixture.session.sets.single()
        deps.workoutRepository.updateSet(set.id, 110.0, 5, null, false)

        assertEquals(500.0, vm.uiState.value.summary.volumeKg, 0.0001)
        assertEquals(before, vm.uiState.value.summary)
        assertNull(deps.workoutRepository.getInProgress())
    }

    private fun createViewModel(
        sessionId: String,
        container: AppDependencies = deps,
    ): WorkoutSummaryViewModel =
        WorkoutSummaryViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = container,
        ).also { viewModel = it }

    /**
     * The graph with a workout DAO whose two summary reads throw on demand: the session row
     * itself, or the history behind the records check. Everything else runs against the real
     * in-memory database, so the fixture's row is genuinely there when the read is refused.
     */
    private fun faultyReads(gate: FailureGate): AppDependencies {
        val repo = WorkoutRepository(
            database = deps.database,
            workoutDao = FaultyReadDao(deps.database.workoutDao(), gate),
            dbMaintenance = deps.dbMaintenance,
        )
        return object : AppDependencies by deps {
            override val workoutRepository: WorkoutRepository = repo
        }
    }

    private class FailureGate(
        var failSession: Boolean = false,
        var failHistory: Boolean = false,
    )

    private class FaultyReadDao(
        private val delegate: WorkoutDao,
        private val gate: FailureGate,
    ) : WorkoutDao by delegate {
        override suspend fun getSession(id: String): SessionWithDetails? {
            if (gate.failSession) error("boom: Room could not read session $id")
            return delegate.getSession(id)
        }

        override suspend fun finishedWorkingSetsForExercises(
            exerciseIds: List<String>,
        ): List<FinishedWorkingSetRow> {
            if (gate.failHistory) error("boom: Room could not read the lift history")
            return delegate.finishedWorkingSetsForExercises(exerciseIds)
        }
    }
}
