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
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.SummaryHeadline
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlin.coroutines.CoroutineContext

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
        val state = vm.uiState.awaitFirst { !it.isLoading }

        assertTrue(state.missing)
        assertFalse(state.isLoading)
        // A row that is not there is not "saved", and it is not a read fault either.
        assertFalse(state.failed)
        assertFalse(state.savedConfirmed)
        assertTrue(state.highlightExercises.isEmpty())
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

        val state = vm.uiState.awaitFirst { !it.isLoading }
        assertFalse(state.missing)
        assertFalse(state.failed)
        assertTrue(state.savedConfirmed)
        assertEquals(fixture.session.id, state.sessionId)
        assertTrue(state.summary.hasWork)
        assertEquals("Summary lower", state.summary.title)
        assertEquals(1, state.summary.workingSets)
        assertEquals(500.0, state.summary.volumeKg, 0.0001)
        assertEquals("summary note", state.summary.notes)
        assertEquals(fixture.exercise, state.highlightExercises[fixture.exercise.id])
    }

    @Test
    fun warmupOnlySessionIsSavedButHasNoSummaryWork() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(20.0, 5, isWarmup = true)),
            finish = true,
        )
        val vm = createViewModel(fixture.session.id)

        val state = vm.uiState.awaitFirst { !it.isLoading }
        assertFalse(state.missing)
        assertFalse(state.failed)
        // The finished row was read: this is the one no-work state allowed to say "saved".
        assertTrue(state.savedConfirmed)
        assertFalse(state.summary.hasWork)
        assertEquals(0, state.summary.workingSets)
        assertTrue(state.highlightExercises.isEmpty())
    }

    @Test
    fun finishedEmptySessionDoesNotPublishUnworkedExerciseArtwork() = runBlocking {
        deps.dbMaintenance.seedCatalog()
        val fixture = seedTestWorkout(
            deps,
            exerciseId = "ex-barbell-back-squat",
            exerciseName = "Barbell Back Squat",
            finish = true,
        )
        val state = createViewModel(fixture.session.id).uiState.awaitFirst { !it.isLoading }

        assertTrue(state.savedConfirmed)
        assertFalse(state.failed)
        assertFalse(state.missing)
        assertFalse(state.summary.hasWork)
        assertTrue(state.highlightExercises.isEmpty())
        assertTrue(deps.database.workoutDao().getAllSets().isEmpty())
        assertEquals(fixture.session.finishedAt, deps.database.workoutDao().getAllSessions().single().finishedAt)
    }

    @Test
    fun blankSessionIdResolvesMissingInsteadOfSpinning() = runBlocking {
        val vm = createViewModel("")
        val state = vm.uiState.awaitFirst { !it.isLoading }

        assertTrue(state.missing)
        assertFalse(state.isLoading)
        assertFalse(state.savedConfirmed)
        assertTrue(state.highlightExercises.isEmpty())
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
        val setsBefore = deps.database.workoutDao().getAllSets()
        val gate = FailureGate(failHistory = true)
        val vm = createViewModel(fixture.session.id, container = faultyReads(gate))

        val failed = withTimeout(5_000) { vm.uiState.first { !it.isLoading } }
        assertTrue(failed.failed)
        assertFalse(failed.missing)
        assertTrue(failed.savedConfirmed)
        assertEquals(fixture.session.id, failed.sessionId)
        assertFalse(failed.summary.hasWork)
        assertTrue(failed.highlightExercises.isEmpty())

        gate.failHistory = false
        vm.retry()
        val recovered = withTimeout(5_000) { vm.uiState.first { !it.isLoading && !it.failed } }
        assertFalse(recovered.missing)
        assertTrue(recovered.savedConfirmed)
        assertTrue(recovered.summary.hasWork)
        assertEquals(500.0, recovered.summary.volumeKg, 0.0001)
        assertEquals(fixture.exercise, recovered.highlightExercises[fixture.exercise.id])

        // Nothing was written by the failure or the retry: same row, same finish stamp,
        // no live session minted.
        val sessions = deps.database.workoutDao().getAllSessions()
        assertEquals(1, sessions.size)
        assertEquals(finishedAt, sessions.single().finishedAt)
        assertEquals(setsBefore, deps.database.workoutDao().getAllSets())
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
        assertTrue(failed.highlightExercises.isEmpty())

        gate.failSession = false
        vm.retry()
        val recovered = withTimeout(5_000) { vm.uiState.first { !it.isLoading && !it.failed } }
        assertTrue(recovered.savedConfirmed)
        assertNotNull(recovered.summary.highlights.singleOrNull())
        assertEquals(fixture.exercise, recovered.highlightExercises[fixture.exercise.id])
    }

    @Test
    fun retryIsANoOpUnlessTheLastReadThrew() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
        )
        val vm = createViewModel(fixture.session.id)
        val loaded = vm.uiState.awaitFirst { !it.isLoading }
        vm.retry()
        assertEquals(loaded, vm.uiState.value)
    }

    /** UX05-AC03: a bodyweight-only session is not a "0 kg" receipt. */
    @Test
    fun aBodyweightOnlySessionHeadlinesReps() = runBlocking {
        // The lift must be BODYWEIGHT before the fixture logs its 0 kg sets: logSet refuses a
        // 0 kg working set on a loaded lift. insertAll ignores conflicts, so the fixture's own
        // insert of the same id leaves this row as it is.
        deps.database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = "test-pushup",
                    name = "Push-up",
                    muscleGroup = "Chest",
                    notes = "",
                    isCustom = false,
                    loadType = LoadType.BODYWEIGHT.name,
                    nameKey = "push-up",
                ),
            ),
        )
        val fixture = seedTestWorkout(
            deps,
            exerciseId = "test-pushup",
            exerciseName = "Push-up",
            loggedSets = listOf(TestSetInput(0.0, 20), TestSetInput(0.0, 15)),
            finish = true,
        )
        val vm = createViewModel(fixture.session.id)
        val state = vm.uiState.awaitFirst { !it.isLoading }
        assertTrue(state.summary.hasWork)
        assertEquals(35, state.summary.bodyweightReps)
        assertEquals(SummaryHeadline.BodyweightReps(35), state.summary.headline)
    }

    @Test
    fun savedExerciseArtworkUsesExactIdsEvenWhenNamesCollide() = runBlocking {
        deps.dbMaintenance.seedCatalog()
        val squatId = "ex-barbell-back-squat"
        val frontSquatId = "ex-front-squat"
        val customId = "custom-squat-with-the-same-name"
        val unknownId = "restored-unknown-squat"
        val duplicateName = "Barbell Back Squat"
        deps.database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = customId,
                    name = duplicateName,
                    muscleGroup = "Back",
                    notes = "Custom cable movement",
                    isCustom = true,
                    equipment = EquipmentType.CABLE.name,
                    movementKey = "row",
                    nameKey = "barbell back squat",
                ),
                ExerciseEntity(
                    id = unknownId,
                    name = duplicateName,
                    muscleGroup = "Shoulders",
                    notes = "Restored identity unknown to this catalog",
                    isCustom = false,
                    equipment = EquipmentType.BAND.name,
                    imageKey = "unknown_restored_artwork",
                    nameKey = "barbell back squat",
                ),
            ),
        )
        val live = deps.workoutRepository.startFreeWorkout()
        val worked = listOf(
            Triple(squatId, 100.0, 5),
            Triple(frontSquatId, 90.0, 4),
            Triple(customId, 20.0, 7),
            Triple(unknownId, 10.0, 8),
        )
        (worked.map { it.first } + "ex-leg-press").forEach { id ->
            deps.workoutRepository.addExerciseToSession(
                sessionId = live.id,
                exercise = checkNotNull(deps.exerciseRepository.getById(id)),
                targetSets = 1,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 90,
            )
        }
        worked.forEach { (id, weight, reps) ->
            deps.workoutRepository.logSet(live.id, id, weight, reps, 8, false)
        }
        deps.workoutRepository.finishSession(live.id, "Exact saved identities")
        val saved = checkNotNull(deps.workoutRepository.getSession(live.id))
        val sessionsBefore = deps.database.workoutDao().getAllSessions()
        val setsBefore = deps.database.workoutDao().getAllSets()
        val liftsBefore = deps.database.workoutDao().getAllSessionExercises()
        val catalogBefore = deps.database.exerciseDao().getAll()

        val state = createViewModel(live.id).uiState.awaitFirst { !it.isLoading }

        assertTrue(state.savedConfirmed)
        assertFalse(state.failed)
        assertEquals(worked.map { it.first }.toSet(), state.highlightExercises.keys)
        assertEquals(state.summary.highlights.map { it.exerciseId }.toSet(), state.highlightExercises.keys)
        assertEquals("ex_barbell_back_squat", checkNotNull(state.highlightExercises[squatId]).imageKey)
        assertEquals("ex_front_squat", checkNotNull(state.highlightExercises[frontSquatId]).imageKey)
        assertEquals(EquipmentType.BARBELL, checkNotNull(state.highlightExercises[squatId]).equipment)
        val custom = checkNotNull(state.highlightExercises[customId])
        assertEquals(customId, custom.id)
        assertTrue(custom.isCustom)
        assertNull(custom.imageKey)
        assertEquals("row", custom.movementKey)
        assertEquals(EquipmentType.CABLE, custom.equipment)
        val unknown = checkNotNull(state.highlightExercises[unknownId])
        assertEquals(unknownId, unknown.id)
        assertFalse(unknown.isCustom)
        assertEquals("unknown_restored_artwork", unknown.imageKey)
        assertEquals(EquipmentType.BAND, unknown.equipment)
        assertEquals(
            setOf(squatId, customId, unknownId),
            state.summary.highlights.filter { it.exerciseName == duplicateName }.map { it.exerciseId }.toSet(),
        )
        assertEquals(4, state.summary.workingSets)
        assertEquals(1_080.0, state.summary.volumeKg, 0.0001)
        state.summary.highlights.forEach { highlight ->
            val original = saved.sets.single { it.exerciseId == highlight.exerciseId }
            val top = checkNotNull(highlight.topSet)
            assertEquals(original.id, top.setId)
            assertEquals(original.sessionId, top.sessionId)
            assertEquals(original.weightKg, top.weightKg, 0.0001)
            assertEquals(original.reps, top.reps)
            assertEquals(original.completedAt, top.completedAt)
        }

        // A successful receipt is only a read: all durable rows and finish stamps stay exact.
        assertEquals(sessionsBefore, deps.database.workoutDao().getAllSessions())
        assertEquals(setsBefore, deps.database.workoutDao().getAllSets())
        assertEquals(liftsBefore, deps.database.workoutDao().getAllSessionExercises())
        assertEquals(catalogBefore, deps.database.exerciseDao().getAll())
        assertNull(deps.workoutRepository.getInProgress())
    }

    @Test
    fun artworkAndNamesUseTheReadSessionWhenCatalogChangesDuringSummaryComputation() = runBlocking {
        deps.dbMaintenance.seedCatalog()
        val fixture = seedTestWorkout(
            deps,
            exerciseId = "ex-barbell-back-squat",
            exerciseName = "Barbell Back Squat",
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
        )
        val historyReached = CompletableDeferred<Unit>()
        val releaseHistory = CompletableDeferred<Unit>()
        val gate = FailureGate(historyReached = historyReached, releaseHistory = releaseHistory)
        val vm = createViewModel(fixture.session.id, faultyReads(gate))
        withTimeout(5_000) { historyReached.await() }
        assertTrue(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.highlightExercises.isEmpty())

        val catalogRow = checkNotNull(deps.database.exerciseDao().getById(fixture.exercise.id))
        deps.database.exerciseDao().update(
            catalogRow.copy(name = "Renamed after receipt read", imageKey = "changed_after_receipt_read"),
        )
        releaseHistory.complete(Unit)
        val state = vm.uiState.awaitFirst { !it.isLoading }

        assertFalse(state.failed)
        assertEquals("Barbell Back Squat", state.summary.highlights.single().exerciseName)
        val identity = checkNotNull(state.highlightExercises[fixture.exercise.id])
        assertEquals("Barbell Back Squat", identity.name)
        assertEquals("ex_barbell_back_squat", identity.imageKey)
        assertEquals(fixture.session.exercises.single().exercise, identity)
        assertEquals(fixture.session.sets, checkNotNull(deps.workoutRepository.getSession(fixture.session.id)).sets.map {
            it.copy(exerciseName = "Barbell Back Squat")
        })
        assertEquals(fixture.session.finishedAt, deps.database.workoutDao().getAllSessions().single().finishedAt)
    }

    @Test
    fun setOnlyLegacyWorkoutKeepsExactIdentityWithoutBorrowingTheNamedCatalogLift() = runBlocking {
        deps.dbMaintenance.seedCatalog()
        val fixture = seedTestWorkout(
            deps,
            exerciseId = "legacy-custom-squat",
            exerciseName = "Barbell Back Squat",
            loggedSets = listOf(TestSetInput(45.0, 6)),
            finish = true,
        )
        deps.database.workoutDao().deleteSessionExercise(fixture.session.exercises.single().id)
        val setsBefore = deps.database.workoutDao().getAllSets()
        val vm = createViewModel(fixture.session.id)

        val state = vm.uiState.awaitFirst { !it.isLoading }

        assertFalse(state.failed)
        assertTrue(state.savedConfirmed)
        val highlight = state.summary.highlights.single()
        assertEquals("legacy-custom-squat", highlight.exerciseId)
        assertEquals("Barbell Back Squat", highlight.exerciseName)
        val identity = state.highlightExercises.values.single()
        assertEquals(highlight.exerciseId, identity.id)
        assertEquals(highlight.exerciseName, identity.name)
        assertTrue(identity.isCustom)
        assertNull(identity.imageKey)
        assertEquals(EquipmentType.OTHER, identity.equipment)
        assertEquals(270.0, state.summary.volumeKg, 0.0001)
        assertEquals(setsBefore, deps.database.workoutDao().getAllSets())
        assertTrue(deps.database.workoutDao().getAllSessionExercises().isEmpty())
        assertEquals(fixture.session.finishedAt, deps.database.workoutDao().getAllSessions().single().finishedAt)

        // A partially populated legacy graph needs the same fallback for its individual
        // missing lift; an unrelated intact plan row must not hide or rename the saved set.
        vm.clearAndJoinForTest()
        deps.database.workoutDao().upsertSessionExercise(
            SessionExerciseEntity(
                id = "intact-unworked-plan-row",
                sessionId = fixture.session.id,
                exerciseId = "ex-front-squat",
                sortOrder = 0,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 90,
            ),
        )
        val partial = createViewModel(fixture.session.id).uiState.awaitFirst { !it.isLoading }
        assertEquals(state.summary, partial.summary)
        assertEquals(state.highlightExercises, partial.highlightExercises)
        assertEquals(setsBefore, deps.database.workoutDao().getAllSets())
        assertEquals("ex-front-squat", deps.database.workoutDao().getAllSessionExercises().single().exerciseId)
    }

    @Test
    fun failedRetryClearsReadIdentityWhileLoadingAndWhenTheSessionHasGone() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
        )
        val releaseHistory = CompletableDeferred<Unit>()
        val gate = FailureGate(failSession = true, releaseHistory = releaseHistory)
        val vm = createViewModel(fixture.session.id, faultyReads(gate))
        vm.uiState.awaitFirst { it.failed }

        gate.failSession = false
        gate.failHistory = true
        vm.retry()
        assertTrue(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.highlightExercises.isEmpty())
        releaseHistory.complete(Unit)
        val failed = vm.uiState.awaitFirst { !it.isLoading && it.failed }
        assertTrue(failed.savedConfirmed)
        assertTrue(failed.highlightExercises.isEmpty())

        // Model a restore/discard between attempts with this test's synthetic row only.
        deps.database.workoutDao().deleteSession(fixture.session.id)
        gate.failHistory = false
        vm.retry()
        val missing = vm.uiState.awaitFirst { !it.isLoading && it.missing }
        assertFalse(missing.failed)
        assertFalse(missing.savedConfirmed)
        assertFalse(missing.summary.hasWork)
        assertTrue(missing.highlightExercises.isEmpty())
        assertTrue(deps.database.workoutDao().getAllSessions().isEmpty())
        assertTrue(deps.database.workoutDao().getAllSets().isEmpty())
    }

    @Test
    fun aComputationFaultDoesNotPublishReadIdentityAndRetryPreservesSavedRows() = runBlocking {
        deps.dbMaintenance.seedCatalog()
        val fixture = seedTestWorkout(
            deps,
            exerciseId = "ex-barbell-back-squat",
            exerciseName = "Barbell Back Squat",
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
        )
        var failCompute = true
        val compute = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                if (failCompute) error("boom: summary computation could not be scheduled")
                Dispatchers.Default.dispatch(context, block)
            }
        }
        val container = object : AppDependencies by deps {
            override val computeDispatcher: CoroutineDispatcher = compute
        }
        val sessionsBefore = deps.database.workoutDao().getAllSessions()
        val setsBefore = deps.database.workoutDao().getAllSets()
        val vm = createViewModel(fixture.session.id, container)

        val failed = vm.uiState.awaitFirst { !it.isLoading }
        assertTrue(failed.failed)
        assertTrue(failed.savedConfirmed)
        assertFalse(failed.missing)
        assertTrue(failed.highlightExercises.isEmpty())

        failCompute = false
        vm.retry()
        val recovered = vm.uiState.awaitFirst { !it.isLoading && !it.failed }
        assertTrue(recovered.savedConfirmed)
        assertEquals("ex_barbell_back_squat", recovered.highlightExercises[fixture.exercise.id]?.imageKey)
        assertEquals(500.0, recovered.summary.volumeKg, 0.0001)
        assertEquals(sessionsBefore, deps.database.workoutDao().getAllSessions())
        assertEquals(setsBefore, deps.database.workoutDao().getAllSets())
        assertNull(deps.workoutRepository.getInProgress())
    }

    @Test
    fun summaryLoadsOnceAndDoesNotChangeAfterHistoricalRepair() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
        )
        val vm = createViewModel(fixture.session.id)
        val before = vm.uiState.awaitFirst { !it.isLoading }.summary

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
        val historyReached: CompletableDeferred<Unit>? = null,
        val releaseHistory: CompletableDeferred<Unit>? = null,
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
            gate.historyReached?.complete(Unit)
            gate.releaseHistory?.await()
            if (gate.failHistory) error("boom: Room could not read the lift history")
            return delegate.finishedWorkingSetsForExercises(exerciseIds)
        }
    }
}
