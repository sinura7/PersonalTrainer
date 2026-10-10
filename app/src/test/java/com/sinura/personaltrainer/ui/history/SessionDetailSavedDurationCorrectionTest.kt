package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Saved-duration forwarding and refusal controls; actual sheet gestures have separate evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SessionDetailSavedDurationCorrectionTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: SessionDetailViewModel? = null
    private val dao get() = deps.database.workoutDao()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
    }

    @After
    fun tearDown() {
        try {
            runBlocking { viewModel?.clearAndJoinForTest() }
        } finally {
            try { deps.close() } finally { Dispatchers.resetMain() }
        }
    }

    @Test
    fun untouchedShortLongAndLargestDurationsKeepLiteralSavedRows() = runBlocking {
        val originals = seedTimedRows(
            listOf(0, -3).flatMap { reps ->
                listOf(1, 2, 3, 4, 1801, Int.MAX_VALUE).map { seconds -> StoredValues(reps, seconds) }
            },
        )
        val vm = loadedViewModel(originals.first().sessionId)
        val before = inventory()
        for (original in originals) {
            completeMutation(vm) {
                vm.updateSet(
                    original.id, original.weightKg, original.reps, null, false,
                    durationSeconds = original.durationSeconds,
                )
            }
            assertNull(vm.error.value)
            assertEquals("untouched ${original.reps} reps / ${original.durationSeconds} seconds", original, dao.getSet(original.id))
            assertEquals("every saved row and metadata record remain identical", before, inventory())
        }
    }

    @Test
    fun explicitFortyFiveToFiftySecondCorrectionsKeepLiteralZeroAndNegativeReps() = runBlocking {
        val originals = seedTimedRows(listOf(StoredValues(0, 45), StoredValues(-3, 45)), plannedHold = true)
        val vm = loadedViewModel(originals.first().sessionId)
        for (original in originals) {
            val before = inventory()
            completeMutation(vm) {
                vm.updateSet(original.id, original.weightKg, original.reps, null, false, durationSeconds = 50)
            }
            assertNull(vm.error.value)
            assertOnlyOriginalChanged(before, original.copy(durationSeconds = 50))
            assertEquals(original.copy(durationSeconds = 50), dao.getSet(original.id))
        }
    }

    @Test
    fun stopwatchStrengthRetainsRepsAndDurationUnderStrengthMetadata() = runBlocking {
        verifyStopwatchStrength(plannedHold = false)
    }

    @Test
    fun stopwatchStrengthRetainsRepsAndDurationUnderPlannedHoldMetadata() = runBlocking {
        verifyStopwatchStrength(plannedHold = true)
    }

    @Test
    fun zeroRepSubmissionCannotReclassifyPlannedStopwatchStrengthThroughTheViewModel() = runBlocking {
        val original = seedTimedRows(listOf(StoredValues(8, 45)), plannedHold = true).single()
        val vm = loadedViewModel(original.sessionId)
        val before = inventory()
        completeMutation(vm) {
            vm.updateSet(original.id, original.weightKg, 0, 8, false, durationSeconds = 50)
        }
        assertEquals(SetLogRules.INVALID_REPS, vm.error.value)
        assertEquals(before, inventory())
    }

    @Test
    fun knownLoadedAndSavedOnlyUnknownZeroWeightHoldsBothRefuseWithoutInventoryChange() = runBlocking {
        val known = seedTimedRows(listOf(StoredValues(reps = 0, seconds = 45, weightKg = 0.0))).single()
        val savedOnlyExercise = insertTestExercise(
            deps = deps, id = "saved-duration-unknown-load", name = "Saved-only captured exercise", isCustom = true,
        )
        val unknown = known.copy(id = "saved-duration-unknown-original", exerciseId = savedOnlyExercise.id)
        dao.insertSet(unknown)
        val session = checkNotNull(deps.workoutRepository.getSession(known.sessionId))
        assertTrue(session.exercises.any { it.exercise.id == known.exerciseId })
        assertTrue("the saved-only result has no session load metadata", session.exercises.none { it.exercise.id == unknown.exerciseId })
        val vm = loadedViewModel(known.sessionId)
        val before = inventory()
        for (original in listOf(known, unknown)) {
            completeMutation(vm) {
                vm.updateSet(original.id, 0.0, original.reps, null, false, durationSeconds = 50)
            }
            assertEquals(SetLogRules.ZERO_WORKING_WEIGHT, vm.error.value)
            assertEquals("zero-weight refusal for ${original.exerciseId} preserves both results and sentinel", before, inventory())
            vm.onErrorShown()
            assertNull(vm.error.value)
        }
    }

    @Test
    fun savedHoldTypeDoesNotBypassNegativeOrNonfiniteWeightRefusal() = runBlocking {
        val original = seedTimedRows(listOf(StoredValues(-3, 45))).single()
        val vm = loadedViewModel(original.sessionId)
        val before = inventory()
        for (weight in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            completeMutation(vm) {
                vm.updateSet(original.id, weight, original.reps, null, false, durationSeconds = 50)
            }
            assertEquals(SetLogRules.INVALID_WEIGHT, vm.error.value)
            assertEquals("refused weight $weight writes no duration or other record", before, inventory())
            vm.onErrorShown()
            assertNull(vm.error.value)
        }
    }

    @Test
    fun warmupStopwatchStrengthStillAllowsNoEffortWithoutChangingItsType() = runBlocking {
        val original = seedTimedRows(listOf(StoredValues(8, 45))).single()
        val vm = loadedViewModel(original.sessionId)
        val before = inventory()
        completeMutation(vm) {
            vm.updateSet(original.id, 0.0, 8, null, true, durationSeconds = 50)
        }
        assertNull(vm.error.value)
        assertOnlyOriginalChanged(before, original.copy(weightKg = 0.0, rpe = null, isWarmup = true, durationSeconds = 50))
    }

    private suspend fun verifyStopwatchStrength(plannedHold: Boolean) {
        val original = seedTimedRows(listOf(StoredValues(8, 45)), plannedHold = plannedHold).single()
        val vm = loadedViewModel(original.sessionId)
        val before = inventory()
        completeMutation(vm) {
            vm.updateSet(original.id, 15.0, 9, null, false, durationSeconds = 50)
        }
        assertEquals(SetLogRules.EFFORT_MISSING, vm.error.value)
        assertEquals("a missing effort must not partially save the requested duration", before, inventory())
        vm.onErrorShown()

        completeMutation(vm) { vm.updateSet(original.id, 15.0, 9, 9, false) }
        val omittedDuration = original.copy(weightKg = 15.0, reps = 9, rpe = 9)
        assertNull(vm.error.value)
        assertOnlyOriginalChanged(before, omittedDuration)
        assertEquals(omittedDuration, dao.getSet(original.id))

        val afterFirst = inventory()
        completeMutation(vm) { vm.updateSet(original.id, 15.0, 9, 9, false, durationSeconds = 50) }
        val expected = omittedDuration.copy(durationSeconds = 50)
        assertNull(vm.error.value)
        assertOnlyOriginalChanged(afterFirst, expected)
        assertEquals(expected, dao.getSet(original.id))
        assertEquals(135.0, checkNotNull(deps.workoutRepository.getSession(original.sessionId)).work().volumeKg, 0.0)
    }

    private suspend fun loadedViewModel(sessionId: String): SessionDetailViewModel {
        val vm = SessionDetailViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            SavedStateHandle(mapOf("sessionId" to sessionId)), deps,
        ).also { viewModel = it }
        // Keep the state subscribed so every correction classifies the actual saved original.
        vm.viewModelScope.launch { vm.uiState.collect { } }
        vm.uiState.awaitFirst { !it.isLoading && it.session?.id == sessionId }
        return vm
    }

    private suspend fun completeMutation(vm: SessionDetailViewModel, action: () -> Unit) {
        val scope = checkNotNull(vm.viewModelScope.coroutineContext[Job])
        val priorJobs = scope.children.toSet()
        action()
        // A no-op-looking result cannot pass while the requested write is still suspended.
        withTimeout(TestWaits.FLOW_MS) {
            while (scope.children.any { it !in priorJobs && it.isActive }) delay(10)
        }
    }

    private suspend fun seedTimedRows(values: List<StoredValues>, plannedHold: Boolean = false): List<SetLogEntity> {
        val fixture = seedTestWorkout(
            deps = deps,
            exerciseId = if (plannedHold) "hold-original-lift" else "saved-duration-strength",
            exerciseName = if (plannedHold) "Original weighted static hold" else "Original stopwatch strength exercise",
            routineId = "saved-duration-routine", finish = true,
            notes = "Preserve all original captured metadata",
        )
        assertEquals(plannedHold, HoldWork.isHold(fixture.exercise))
        val originals = values.mapIndexed { index, value ->
            SetLogEntity(
                id = "saved-duration-original-$index", sessionId = fixture.session.id,
                exerciseId = fixture.exercise.id, setNumber = index + 1,
                weightKg = value.weightKg, reps = value.reps, rpe = if (value.reps > 0) 8 else null,
                isWarmup = false, completedAt = fixture.session.startedAt, durationSeconds = value.seconds,
            ).also { dao.insertSet(it) }
        }
        seedTestWorkout(
            deps = deps, exerciseId = "saved-duration-sentinel", routineId = "saved-duration-sentinel-routine",
            loggedSets = listOf(TestSetInput(35.0, 5, 8)), finish = true,
            notes = "Preserve this unrelated saved session and result",
        )
        assertEquals(originals.size + 1, dao.getAllSets().size)
        return originals
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun assertOnlyOriginalChanged(before: List<Any?>, expected: SetLogEntity) {
        val wanted = before.toMutableList()
        wanted[0] = (before[0] as List<SetLogEntity>).map { if (it.id == expected.id) expected else it }
        assertEquals("only the requested captured result fields change, with the sentinel intact", wanted, inventory())
    }

    private suspend fun inventory(): List<Any?> = listOf(
        dao.getAllSets().sortedBy { it.id },
        dao.getAllSessions().sortedBy { it.id },
        dao.getAllSessionExercises().sortedBy { it.id },
        deps.database.exerciseDao().getAll().sortedBy { it.id },
        deps.database.routineDao().getAllRoutines().sortedBy { it.id },
        deps.database.routineDao().getAllRoutineExercises().sortedBy { it.id },
    )

    private data class StoredValues(val reps: Int, val seconds: Int, val weightKg: Double = 12.5)
}
