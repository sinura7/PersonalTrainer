package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.RecommendationPriority
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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

    private fun graph(
        insights: MutableStateFlow<TrainingInsights> = MutableStateFlow(TrainingInsights()),
    ): FakeAppDependencies = FakeAppDependencies(
        ApplicationProvider.getApplicationContext(),
        insights,
        scheduler = dispatcher,
    )

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun loadsRoutinesAndClearsLoading() = runBlocking {
        deps = graph()
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val vm = createViewModel()

        val state = vm.uiState.awaitFirst { !it.isLoading }
        assertEquals(listOf(fixture.routine.id), state.routines.map { it.id })
        assertNull(state.inProgress)
    }

    @Test
    fun emptyWeekHasNoTodayStart() = runBlocking {
        deps = graph()
        val vm = createViewModel()
        val state = vm.uiState.awaitFirst { !it.isLoading }
        assertNull(state.todayStart)
    }

    @Test
    fun startRoutinePersistsSessionAndEmitsOneShotNavigation() = runBlocking {
        deps = graph()
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val vm = createViewModel()
        vm.uiState.awaitFirst { !it.isLoading }

        vm.startRoutine(fixture.routine.id)

        val id = checkNotNull(vm.navigateToSession.awaitFirst { it != null })
        val session = checkNotNull(deps.workoutRepository.getSession(id))
        assertEquals(fixture.routine.id, session.routineId)
        assertEquals(1, session.exercises.size)
        vm.onSessionNavigationHandled()
        assertNull(vm.navigateToSession.value)
    }

    @Test
    fun missingAndEmptyRoutineSurfaceErrorsWithoutStarting() = runBlocking {
        deps = graph()
        val vm = createViewModel()
        vm.uiState.awaitFirst { !it.isLoading }

        vm.startRoutine("missing")
        assertEquals(
            "That routine is no longer available.",
            vm.uiState.awaitFirst { it.error != null }.error,
        )

        val empty = deps.routineRepository.create("Empty")
        vm.startRoutine(empty.id)
        assertEquals(
            SessionOrderCopy.NEED_A_LIFT,
            vm.uiState.awaitFirst { it.error?.startsWith("Add at least") == true }.error,
        )
        assertNull(deps.workoutRepository.getInProgress())
        assertNull(vm.navigateToSession.value)
    }

    @Test
    fun startFreeCreatesFreeWorkoutAndNavigates() = runBlocking {
        deps = graph()
        val vm = createViewModel()
        vm.uiState.awaitFirst { !it.isLoading }

        vm.startFree()

        val id = checkNotNull(vm.navigateToSession.awaitFirst { it != null })
        val session = checkNotNull(deps.workoutRepository.getSession(id))
        assertEquals("Free workout", session.routineName)
        assertTrue(session.exercises.isEmpty())
    }

    @Test
    fun startSuggestedPreAddsNamedLiftWithDefaults() = runBlocking {
        val insights = MutableStateFlow(TrainingInsights())
        deps = graph(insights)
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
        vm.uiState.awaitFirst { it.suggestion?.id == exercise.id }

        vm.startSuggested()

        val id = checkNotNull(vm.navigateToSession.awaitFirst { it != null })
        val session = checkNotNull(deps.workoutRepository.getSession(id))
        assertEquals(exercise.id, session.exercises.single().exercise.id)
        assertTrue(session.exercises.single().targetSets > 0)
    }

    @Test
    fun directStartWhileAnotherSessionIsLiveNeverSilentlyNavigates() = runBlocking {
        deps = graph()
        val fixture = seedTestWorkout(deps)
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.inProgress?.id == fixture.session.id }

        vm.startFree()

        val state = vm.uiState.awaitFirst { it.error != null }
        assertEquals("A workout is already in progress.", state.error)
        assertNull(vm.navigateToSession.value)
        assertEquals(fixture.session.id, deps.workoutRepository.getInProgress()?.id)
    }

    @Test
    fun startRoutineWhileLiveSurfacesBlockedWithoutNavigating() = runBlocking {
        deps = graph()
        val fixture = seedTestWorkout(deps)
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.inProgress?.id == fixture.session.id }

        vm.startRoutine(fixture.routine.id)

        val state = vm.uiState.awaitFirst { it.error != null }
        assertEquals("A workout is already in progress.", state.error)
        assertNull(vm.navigateToSession.value)
        assertEquals(fixture.session.id, deps.workoutRepository.getInProgress()?.id)
    }

    @Test
    fun startSuggestedWhileLiveSurfacesBlockedWithoutAddingASecondSession() = runBlocking {
        val insights = MutableStateFlow(TrainingInsights())
        deps = graph(insights)
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
        vm.uiState.awaitFirst { it.inProgress?.id == fixture.session.id && it.suggestion?.id == exercise.id }

        vm.startSuggested()

        val state = vm.uiState.awaitFirst { it.error != null }
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
        deps = graph()
        val vm = createViewModel()
        vm.uiState.awaitFirst { !it.isLoading }
        assertNull(vm.uiState.value.suggestion)

        vm.startSuggested()

        assertNull(vm.navigateToSession.value)
        assertNull(deps.workoutRepository.getInProgress())
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun discardInProgressStopsTimerClearsDraftAndRemovesRow() = runBlocking {
        deps = graph()
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
        vm.uiState.awaitFirst { it.inProgress?.id == fixture.session.id }

        vm.discardInProgress()

        deps.workoutRepository.observeInProgress().first { it == null }
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(deps.restTimerStore.current().running)
        assertNull(deps.workoutDraftCache.get(fixture.session.id))
        assertNull(vm.uiState.awaitFirst { it.inProgress == null }.error)
    }

    @Test
    fun startCardioFromEmptyWeekOpensLiveCardio() = runBlocking {
        deps = graph()
        val vm = createViewModel()
        vm.uiState.awaitFirst { !it.isLoading }
        assertNull(vm.uiState.value.todayStart)
        assertTrue(deps.plannerRepository.observeOccurrences().first().isEmpty())

        vm.startCardio()

        val id = checkNotNull(vm.navigateToCardio.awaitFirst { it != null })
        val live = checkNotNull(deps.activityRepository.getLive())
        assertEquals(id, live.id)
        assertEquals("Cardio", live.title)
        assertEquals(id, deps.cardioTimerPersistence.load()?.sessionId)
    }

    @Test
    fun discardLiveCardioClearsTimerAndRemovesRow() = runBlocking {
        deps = graph()
        val vm = createViewModel()
        vm.uiState.awaitFirst { !it.isLoading }
        vm.startCardio()
        val id = checkNotNull(vm.navigateToCardio.awaitFirst { it != null })
        vm.uiState.awaitFirst { it.liveActivity?.id == id }
        assertEquals(id, deps.cardioTimerPersistence.load()?.sessionId)

        vm.discardInProgress()

        vm.uiState.awaitFirst { it.liveActivity == null }
        // discardActivity suspends on Room; liveActivity can go null before the
        // persistence clear that follows it. Wait for that write, not only idle.
        withTimeout(TestWaits.FLOW_MS) {
            while (deps.cardioTimerPersistence.load() != null) {
                dispatcher.scheduler.advanceUntilIdle()
                yield()
            }
        }
        assertNull(deps.activityRepository.getLive())
        assertNull(deps.cardioTimerPersistence.load())
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun discardLiveCardioFailureLeavesTimerAndRow() = runBlocking {
        deps = graph()
        val vm = createViewModel()
        vm.uiState.awaitFirst { !it.isLoading }
        vm.startCardio()
        val id = checkNotNull(vm.navigateToCardio.awaitFirst { it != null })
        vm.uiState.awaitFirst { it.liveActivity?.id == id }
        checkNotNull(deps.cardioTimerPersistence.load())
        deps.discardActivity = object : com.sinura.personaltrainer.activity.DiscardActivity(
            deps.activityRepository,
        ) {
            override suspend fun invoke(sessionId: String) {
                error("forced discard failure")
            }
        }

        vm.discardInProgress()

        val state = vm.uiState.awaitFirst { it.error != null }
        assertEquals("Could not discard this session. Try again.", state.error)
        assertEquals(id, deps.activityRepository.getLive()?.id)
        assertEquals(id, deps.cardioTimerPersistence.load()?.sessionId)
    }

    /**
     * Nothing the sheet staged may outlive the sheet.
     *
     * This view model is scoped to the Activity rather than to the sheet, so a value left in one
     * of the three navigation flows survives a close and fires on the NEXT open — before the user
     * has chosen anything, and including while a session is live, which is the one state the
     * sheet promises will start nothing. That is not hypothetical: the "Mixed session" row used to
     * call onDismiss() and then openComposer("mixed"), and the only reader of that flow is a
     * LaunchedEffect inside the sheet the dismiss had just removed. The tap opened nothing, and
     * the flow kept "mixed" until the next open spent it.
     */
    @Test
    fun aPendingNavigationDoesNotSurviveTheSheetClosing() = runBlocking {
        deps = graph()
        val vm = createViewModel()
        vm.uiState.awaitFirst { !it.isLoading }

        vm.openComposer("mixed")
        assertEquals("mixed", vm.navigateToComposer.value)

        // What the sheet's onDispose does.
        vm.clearPendingNavigation()

        assertNull(vm.navigateToComposer.value)
        assertNull(vm.navigateToSession.value)
        assertNull(vm.navigateToCardio.value)
    }

    /**
     * The flow itself is not the defect and must keep working: StartOccurrenceOutcome.OpenComposer
     * sets it while the sheet is still composed, which is what it is for.
     */
    @Test
    fun aComposerNavigationRaisedWhileTheSheetIsOpenIsStillDelivered() = runBlocking {
        deps = graph()
        val vm = createViewModel()
        vm.uiState.awaitFirst { !it.isLoading }

        vm.openComposer("mixed")

        assertEquals("mixed", checkNotNull(vm.navigateToComposer.awaitFirst { it != null }))
        vm.onComposerNavigationHandled()
        assertNull(vm.navigateToComposer.value)
    }

    private fun createViewModel(): StartOptionsViewModel =
        StartOptionsViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            deps,
        ).also { viewModel = it }
}
