package com.sinura.personaltrainer.ui.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RecommendationPriority
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.testutil.insertTestExercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
 * Home already names the ready lifts. The card that only says "some lifts are ready"
 * must not sit next to that list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: HomeViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun dropsProgressionReadyWhenHintsExist() = runBlocking {
        val insights = MutableStateFlow(
            TrainingInsights(
                hints = listOf(hint()),
                recommendations = listOf(rec("progression-ready"), rec("coverage-chest")),
            ),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val state = viewModel!!.uiState.first { !it.isLoading }
        assertTrue(state.recommendations.none { it.id == "progression-ready" })
        assertEquals(listOf("coverage-chest"), state.recommendations.map { it.id })
    }

    @Test
    fun keepsProgressionReadyWhenNothingIsNamed() = runBlocking {
        val insights = MutableStateFlow(
            TrainingInsights(
                hints = emptyList(),
                recommendations = listOf(rec("progression-ready")),
            ),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val state = viewModel!!.uiState.first { !it.isLoading }
        assertEquals(listOf("progression-ready"), state.recommendations.map { it.id })
    }

    @Test
    fun agendaListsIndependentOccurrences() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val weekStart = com.sinura.personaltrainer.domain.CivilDate.fromEpochDay(
            com.sinura.personaltrainer.domain.todayEpochDay(),
        ).previousOrSame(com.sinura.personaltrainer.domain.Weekday.MONDAY)
        val today = com.sinura.personaltrainer.domain.Weekday.fromEpochDay(
            com.sinura.personaltrainer.domain.todayEpochDay(),
        )
        deps.plannerRepository.addTimedRule(
            weekday = today,
            hour = 7,
            minute = 0,
            modality = com.sinura.personaltrainer.domain.ScheduleModality.CARDIO,
        )
        deps.plannerRepository.addTimedRule(
            weekday = today,
            hour = 18,
            minute = 0,
            modality = com.sinura.personaltrainer.domain.ScheduleModality.STRENGTH,
        )
        deps.plannerRepository.ensureWeek(weekStart)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val state = viewModel!!.uiState.first { !it.isLoading && it.agenda.size == 2 }
        assertEquals(listOf(7, 18), state.agenda.map { it.occurrence.hour })
        assertEquals(
            listOf("Cardio", "Strength"),
            state.agenda.map { it.title },
        )
        assertEquals(
            com.sinura.personaltrainer.domain.HomeToday.Surface.AGENDA,
            com.sinura.personaltrainer.domain.HomeToday.surface(state.agenda),
        )
    }

    @Test
    fun lastSessionReadsAllTimeSummariesWhenTheHeatWindowIsEmpty() = runBlocking {
        val today = com.sinura.personaltrainer.domain.todayEpochDay()
        val old = com.sinura.personaltrainer.domain.SessionSummary(
            id = "ancient-pull",
            routineId = "r1",
            routineName = "Pull",
            date = 1_000L,
            finishedAt = 1_100L,
            durationMinutes = 48,
            workingSets = 16,
            volumeKg = 9_000.0,
            localEpochDay = today - 80,
        )
        val insights = MutableStateFlow(
            TrainingInsights(
                history = emptyList(),
                summaries = listOf(old),
            ),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val state = viewModel!!.uiState.first { !it.isLoading }
        assertEquals("ancient-pull", state.lastSession?.id)
        assertEquals(today - 80, state.lastSession?.localEpochDay)
        assertTrue(state.loggedEpochDays.contains(today - 80))
    }

    @Test
    fun plannedStartBindsOccurrenceSoFinishMarksItDone() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val today = todayEpochDay()
        val weekday = Weekday.fromEpochDay(today)
        val weekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        val routine = deps.routineRepository.create("Push")
        val squat = insertTestExercise(deps, "ex-home-squat", "Squat")
        deps.routineRepository.addExercise(routine.id, squat, 3, 5, 100.0, 90)
        deps.scheduleRepository.pin(routine.id, null, weekday)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }

        viewModel!!.startSuggestedDay(plannedDay(today, weekday, routine.id, routine.name))
        val sessionId = viewModel!!.navigateToSession.first { it != null }!!
        PendingOccurrence.complete(deps, sessionId)

        val occurrence = deps.plannerRepository.occurrencesBetween(today, today).single()
        assertEquals(OccurrenceStatus.DONE, occurrence.status)
        assertEquals(sessionId, occurrence.completedActivityId)
    }

    @Test
    fun freeWorkoutOpensAnEmptySessionWithoutMarkingThePlan() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val today = todayEpochDay()
        val weekday = Weekday.fromEpochDay(today)
        val weekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        val routine = deps.routineRepository.create("Push")
        val squat = insertTestExercise(deps, "ex-home-free", "Squat")
        deps.routineRepository.addExercise(routine.id, squat, 3, 5, 100.0, 90)
        deps.scheduleRepository.pin(routine.id, null, weekday)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }

        viewModel!!.startFreeWorkout()
        val sessionId = viewModel!!.navigateToSession.first { it != null }!!
        val session = deps.workoutRepository.getSession(sessionId)!!
        assertEquals("Free workout", session.routineName)
        assertTrue(session.exercises.isEmpty())
        assertNull(deps.pendingOccurrenceId.value)
        assertEquals(
            OccurrenceStatus.PLANNED,
            deps.plannerRepository.occurrencesBetween(today, today).single().status,
        )
    }

    @Test
    fun requestAnswerReplayArmsPlanOnce() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        assertFalse(deps.pendingAnswerReplay.value)
        viewModel!!.requestAnswerReplay()
        assertTrue(deps.pendingAnswerReplay.value)
    }

    @Test
    fun firstInstallIsNotSetupComplete() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val state = viewModel!!.uiState.first { !it.isLoading }
        assertFalse(state.setupComplete)
    }

    @Test
    fun aFinishedSetupHidesTheStarter() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        deps.preferencesRepository.setOnboardingComplete(true)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val state = viewModel!!.uiState.first { !it.isLoading }
        assertTrue(state.setupComplete)
    }

    private fun hint() = ProgressionHint(
        exerciseId = "ex-squat",
        exerciseName = "Squat",
        lastWeightKg = 100.0,
        lastReps = 5,
        targetReps = 5,
        suggestedWeightKg = 102.5,
        action = ProgressionAction.INCREASE,
        loadType = LoadType.EXTERNAL,
    )

    private fun plannedDay(
        today: Long,
        weekday: Weekday,
        routineId: String,
        routineName: String,
    ) = SuggestedTrainingDay(
        epochDay = today,
        dayOfWeek = weekday,
        isRest = false,
        focusKind = SessionFocusKind.PUSH,
        focusTitle = "Push",
        routineId = routineId,
        routineName = routineName,
        reason = "Planned.",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
    )

    private fun rec(id: String) = TrainingRecommendation(
        id = id,
        kicker = "PROGRESSION",
        title = "Ready",
        reason = "named",
        priority = RecommendationPriority.INFO,
        rankScore = 10,
    )
}
