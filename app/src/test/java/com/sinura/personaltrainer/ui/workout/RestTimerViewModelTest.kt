package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestWaits
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
class RestTimerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<RestTimerViewModel>()
    private val workoutViewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
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
            workoutViewModels.forEach { it.clearAndJoinForTest() }
        }
        if (::deps.isInitialized) deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun blankSessionIdResolvesMissing() = runBlocking {
        val vm = createViewModel("")
        val state = vm.awaitState { it.loadState == SessionLoadState.MISSING }
        assertNull(state.floor.exerciseName)
    }

    @Test
    fun liveSessionShowsExerciseAndStartsTheSharedClock() = runBlocking {
        val fixture = seedWorkout(restSeconds = 75)
        val vm = createViewModel(fixture.session.id)
        val state = vm.awaitState {
            it.loadState == SessionLoadState.FOUND && it.floor.exerciseName == "Squat"
        }
        assertEquals("Squat", state.floor.exerciseName)
        assertNull(state.floor.lastSetLine)

        // Wait for the number startSelectedRest will actually read. It takes restTotal.value
        // (RestTimerViewModel:179), which the init coroutine seeds only after a DataStore
        // read (:78-81) — and the barrier above resolves as soon as the session lands, which
        // happens while that read is still in flight. Start the rest in that window and it
        // uses the 90 s default instead of this fixture's 75, so the assertion below fails on
        // the value rather than hanging. uiState.rest.totalSeconds is restTotal while idle.
        vm.awaitState { it.rest.totalSeconds == 75 }
        vm.startSelectedRest()
        deps.restTimerStore.snapshot.first { it.running }
        val rest = deps.restTimerStore.current()
        assertEquals(fixture.session.id, rest.sessionId)
        assertEquals(75, rest.totalSeconds)

        vm.skipRest()
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun lastSetLineAppearsAfterAWorkingSetOnTheSharedStore() = runBlocking {
        val fixture = seedWorkout(targetSets = 3, restSeconds = 90)
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.awaitState { it.loadState == SessionLoadState.FOUND && it.draft.weightKg > 0.0 }
        workout.logSet()
        awaitSession(fixture.session.id) { it.sets.size == 1 }
        deps.restTimerStore.snapshot.first { it.running }

        val floor = createViewModel(fixture.session.id)
        val state = floor.awaitState {
            it.loadState == SessionLoadState.FOUND &&
                it.floor.lastSetLine != null &&
                it.floor.sessionTargetLine != null
        }
        assertEquals("Last set · 100 kg × 5", state.floor.lastSetLine)
        assertEquals("Next: 100 kg × 5 · RPE 8", state.floor.sessionTargetLine)
        assertTrue(state.rest.running)
        assertEquals(fixture.session.id, deps.restTimerStore.current().sessionId)

        floor.skipRest()
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun finishedSessionIsMissingOnTheFloor() = runBlocking {
        val fixture = seedWorkout()
        deps.workoutRepository.logSet(
            sessionId = fixture.session.id,
            exerciseId = SQUAT,
            weightKg = 100.0,
            reps = 5,
            rpe = null,
            isWarmup = false,
        )
        deps.workoutRepository.finishSession(fixture.session.id, notes = "")
        val vm = createViewModel(fixture.session.id)
        val state = vm.awaitState { it.loadState == SessionLoadState.MISSING }
        assertNull(state.floor.lastSetLine)
    }

    @Test
    fun floorAdjustHitsTheSameStoreAsTheLog() = runBlocking {
        val fixture = seedWorkout(restSeconds = 90)
        val vm = createViewModel(fixture.session.id)
        vm.awaitState { it.loadState == SessionLoadState.FOUND }
        vm.startSelectedRest()
        deps.restTimerStore.snapshot.first { it.running }
        vm.adjustRest(15)
        assertTrue(deps.restTimerStore.current().totalSeconds >= 90)
        vm.skipRest()
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun anUnsavedRestReachesTheFloorStateAndClearsWhenTheRowLands() = runBlocking {
        val fixture = seedWorkout(restSeconds = 90)
        val vm = createViewModel(fixture.session.id)
        val healthy = vm.awaitState { it.loadState == SessionLoadState.FOUND }
        assertTrue(healthy.rest.persistenceHealthy)

        deps.setRestPersistenceHealthy(false)
        val unsaved = vm.awaitState { !it.rest.persistenceHealthy }
        assertFalse(unsaved.rest.persistenceHealthy)

        deps.setRestPersistenceHealthy(true)
        val landed = vm.awaitState { it.rest.persistenceHealthy }
        assertTrue(landed.rest.persistenceHealthy)
    }

    @Test
    fun selectingDurationOnTheFloorUpdatesTheLogPlannedRest() = runBlocking {
        val fixture = seedWorkout(restSeconds = 90)
        val workout = createWorkoutViewModel(fixture.session.id)
        workout.awaitState { it.loadState == SessionLoadState.FOUND }
        val floor = createViewModel(fixture.session.id)
        floor.awaitState { it.loadState == SessionLoadState.FOUND }
        workout.restTimerState.first { !it.running && it.totalSeconds > 0 }
        deps.preferencesRepository.restTimerPreferences.first()

        floor.selectRestDuration(105)
        withTimeout(TestWaits.FLOW_MS) {
            workout.restTimerState.first { it.totalSeconds == 105 && !it.running }
        }
        assertEquals(105, floor.uiState.value.rest.totalSeconds)
    }

    private fun createViewModel(sessionId: String): RestTimerViewModel =
        RestTimerViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(viewModels::add)

    private fun createWorkoutViewModel(sessionId: String): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(workoutViewModels::add)

    private suspend fun RestTimerViewModel.awaitState(
        predicate: (RestTimerScreenState) -> Boolean,
    ): RestTimerScreenState = withTimeout(TestWaits.FLOW_MS) {
        uiState.first(predicate)
    }

    private suspend fun ActiveWorkoutViewModel.awaitState(
        predicate: (ActiveWorkoutUiState) -> Boolean,
    ): ActiveWorkoutUiState = withTimeout(TestWaits.FLOW_MS) {
        uiState.first(predicate)
    }

    private suspend fun awaitSession(
        sessionId: String,
        predicate: (WorkoutSession) -> Boolean,
    ): WorkoutSession = withTimeout(TestWaits.FLOW_MS) {
        checkNotNull(
            deps.workoutRepository.observeSession(sessionId).first { session ->
                session != null && predicate(session)
            },
        )
    }

    private suspend fun seedWorkout(
        targetSets: Int = 3,
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
                targetWeightKg = 100.0,
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

    private data class SeededWorkout(val session: WorkoutSession)

    private companion object {
        const val SQUAT = "squat"
        const val ROUTINE = "routine-rest-floor"
        const val STAMP = 1_700_000_000_000L
    }
}
