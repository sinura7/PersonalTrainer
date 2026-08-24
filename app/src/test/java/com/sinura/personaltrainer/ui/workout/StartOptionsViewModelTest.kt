package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.RecommendationPriority
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * Start Options against real Room: blocked start never silently resumes,
 * free/routine/suggested start persist a session, and discard uses the
 * shared lifecycle use case.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StartOptionsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: StartOptionsViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun loadsRoutinesAndClearsLoading() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val vm = createViewModel()

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(listOf(fixture.routine.id), state.routines.map { it.id })
        assertNull(state.inProgress)
    }

    @Test
    fun startRoutinePersistsSessionAndEmitsOneShotNavigation() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val vm = createViewModel()
        vm.uiState.first { !it.isLoading }

        vm.startRoutine(fixture.routine.id)

        val id = eventually { vm.navigateToSession.value }
        val session = checkNotNull(deps.workoutRepository.getSession(id))
        assertEquals(fixture.routine.id, session.routineId)
        assertEquals(1, session.exercises.size)
        vm.onSessionNavigationHandled()
        assertNull(vm.navigateToSession.value)
    }

    @Test
    fun missingAndEmptyRoutineSurfaceErrorsWithoutStarting() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val vm = createViewModel()
        vm.uiState.first { !it.isLoading }

        vm.startRoutine("missing")
        assertEquals(
            "That routine is no longer available.",
            vm.uiState.first { it.error != null }.error,
        )

        val empty = deps.routineRepository.create("Empty")
        vm.startRoutine(empty.id)
        assertEquals(
            "Add at least one exercise before starting this routine.",
            vm.uiState.first { it.error?.startsWith("Add at least") == true }.error,
        )
        assertNull(deps.workoutRepository.getInProgress())
        assertNull(vm.navigateToSession.value)
    }

    @Test
    fun startFreeCreatesFreeWorkoutAndNavigates() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val vm = createViewModel()
        vm.uiState.first { !it.isLoading }

        vm.startFree()

        val id = eventually { vm.navigateToSession.value }
        val session = checkNotNull(deps.workoutRepository.getSession(id))
        assertEquals("Free workout", session.routineName)
        assertTrue(session.exercises.isEmpty())
    }

    @Test
    fun startSuggestedPreAddsNamedLiftWithDefaults() = runBlocking {
        val insights = MutableStateFlow(TrainingInsights())
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        val exercise = insertTestExercise(deps, "suggested-row", "Suggested row")
        insights.value = TrainingInsights(
            recommendations = listOf(
                TrainingRecommendation(
                    id = "rec",
                    kicker = "NEXT",
                    title = "Back is ready",
                    reason = "coverage",
                    priority = RecommendationPriority.INFO,
                    rankScore = 10,
                    actionExerciseId = exercise.id,
                ),
            ),
        )
        val vm = createViewModel()
        vm.uiState.first { it.suggestion?.id == exercise.id }

        vm.startSuggested()

        val id = eventually { vm.navigateToSession.value }
        val session = checkNotNull(deps.workoutRepository.getSession(id))
        assertEquals(exercise.id, session.exercises.single().exercise.id)
        assertTrue(session.exercises.single().targetSets > 0)
    }

    @Test
    fun directStartWhileAnotherSessionIsLiveNeverSilentlyNavigates() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val fixture = seedTestWorkout(deps)
        val vm = createViewModel()
        vm.uiState.first { it.inProgress?.id == fixture.session.id }

        vm.startFree()

        val state = vm.uiState.first { it.error != null }
        assertEquals("A workout is already in progress.", state.error)
        assertNull(vm.navigateToSession.value)
        assertEquals(fixture.session.id, deps.workoutRepository.getInProgress()?.id)
    }

    @Test
    fun startRoutineWhileLiveSurfacesBlockedWithoutNavigating() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val fixture = seedTestWorkout(deps)
        val vm = createViewModel()
        vm.uiState.first { it.inProgress?.id == fixture.session.id }

        vm.startRoutine(fixture.routine.id)

        val state = vm.uiState.first { it.error != null }
        assertEquals("A workout is already in progress.", state.error)
        assertNull(vm.navigateToSession.value)
        assertEquals(fixture.session.id, deps.workoutRepository.getInProgress()?.id)
    }

    @Test
    fun startSuggestedWhileLiveSurfacesBlockedWithoutAddingASecondSession() = runBlocking {
        val insights = MutableStateFlow(TrainingInsights())
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        val fixture = seedTestWorkout(deps)
        val exercise = insertTestExercise(deps, "suggested-row", "Suggested row")
        insights.value = TrainingInsights(
            recommendations = listOf(
                TrainingRecommendation(
                    id = "rec",
                    kicker = "NEXT",
                    title = "Back is ready",
                    reason = "coverage",
                    priority = RecommendationPriority.INFO,
                    rankScore = 10,
                    actionExerciseId = exercise.id,
                ),
            ),
        )
        val vm = createViewModel()
        vm.uiState.first { it.inProgress?.id == fixture.session.id && it.suggestion?.id == exercise.id }

        vm.startSuggested()

        val state = vm.uiState.first { it.error != null }
        assertEquals("A workout is already in progress.", state.error)
        assertNull(vm.navigateToSession.value)
        assertEquals(fixture.session.id, deps.workoutRepository.getInProgress()?.id)
        assertTrue(
            checkNotNull(deps.workoutRepository.getSession(fixture.session.id))
                .exercises
                .none { it.exercise.id == exercise.id },
        )
    }

    @Test
    fun startSuggestedWithNoLiftIsANoOp() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val vm = createViewModel()
        vm.uiState.first { !it.isLoading }
        assertNull(vm.uiState.value.suggestion)

        vm.startSuggested()

        assertNull(vm.navigateToSession.value)
        assertNull(deps.workoutRepository.getInProgress())
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun discardInProgressStopsTimerClearsDraftAndRemovesRow() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val fixture = seedTestWorkout(deps)
        deps.workoutDraftCache.put(
            com.sinura.personaltrainer.workout.WorkoutDraft(
                fixture.session.id,
                fixture.exercise.id,
                100.0,
                5,
                null,
                false,
                "",
            ),
        )
        deps.restTimerController.start(90, fixture.session.id)
        val vm = createViewModel()
        vm.uiState.first { it.inProgress?.id == fixture.session.id }

        vm.discardInProgress()

        eventually {
            true.takeIf {
                deps.workoutRepository.getInProgress() == null &&
                    deps.workoutDraftCache.get(fixture.session.id) == null &&
                    !deps.restTimerStore.current().running
            }
        }
        assertFalse(deps.restTimerStore.current().running)
        assertNull(deps.workoutDraftCache.get(fixture.session.id))
        assertNull(vm.uiState.first { it.inProgress == null }.error)
    }

    private fun createViewModel(): StartOptionsViewModel =
        StartOptionsViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            deps,
        ).also { viewModel = it }

    private suspend fun <T : Any> eventually(block: suspend () -> T?): T =
        withTimeout(5_000) {
            while (true) {
                dispatcher.scheduler.runCurrent()
                block()?.let { return@withTimeout it }
                delay(10)
            }
            error("unreachable")
        }
}
