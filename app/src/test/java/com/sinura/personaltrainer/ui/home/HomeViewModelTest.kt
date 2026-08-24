package com.sinura.personaltrainer.ui.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RecommendationPriority
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.TrainingRecommendation
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
    }

    @Test
    fun requestAnswerReplayArmsPlanOnce() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        assertFalse(deps.pendingAnswerReplay.value)
        viewModel!!.requestAnswerReplay()
        assertTrue(deps.pendingAnswerReplay.value)
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

    private fun rec(id: String) = TrainingRecommendation(
        id = id,
        kicker = "PROGRESSION",
        title = "Ready",
        reason = "named",
        priority = RecommendationPriority.INFO,
        rankScore = 10,
    )
}
