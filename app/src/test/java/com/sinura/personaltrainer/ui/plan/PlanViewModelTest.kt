package com.sinura.personaltrainer.ui.plan

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearForTest
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.WeeklySchedulePlan
import com.sinura.personaltrainer.domain.WeeklySchedulePlanner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlanViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: PlanViewModel? = null
    private val weekStart = LocalDate.of(2026, 8, 17)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModel?.clearForTest()
        viewModel = null
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun suggestFillsOnlyUnpinnedTrainingDays() = runBlocking {
        val insights = MutableStateFlow(
            TrainingInsights(snapshot = emptyHeat(), weekPlan = emptyWeek()),
        )
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.suggestFills()
        // Suggest re-plans from wall clock and will not propose days already behind
        // today. On a Sunday of a Mon-start 4-day week that set is empty — waiting
        // for a non-empty list hangs. The filter still has to hold.
        val proposals = withTimeout(5_000) {
            if (plannerHasARemainingTrainingDay()) {
                viewModel!!.uiState.first { it.proposals.isNotEmpty() }.proposals
            } else {
                delay(1_000)
                viewModel!!.uiState.value.proposals
            }
        }
        assertTrue(proposals.all { it.slotId == null && !it.isRest })
        if (plannerHasARemainingTrainingDay()) {
            assertTrue(proposals.isNotEmpty())
        }
    }

    @Test
    fun aFullyPinnedWeekYieldsNoProposals() = runBlocking {
        val insights = MutableStateFlow(TrainingInsights(snapshot = emptyHeat()))
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        val upper = deps.routineRepository.create(name = "Upper")
        val lower = deps.routineRepository.create(name = "Lower Body")
        listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
            .forEach { day ->
                deps.scheduleRepository.pin(
                    routineId = if (day == DayOfWeek.WEDNESDAY) lower.id else upper.id,
                    focusKind = null,
                    anchorDay = day,
                )
            }
        insights.value = TrainingInsights(
            snapshot = emptyHeat(),
            routines = listOf(upper, lower),
        )
        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.suggestFills()
        withTimeout(5_000) { viewModel!!.uiState.first { !it.isLoading } }
        assertTrue(viewModel!!.uiState.value.proposals.isEmpty())
        assertEquals(null, viewModel!!.uiState.value.error)
    }

    @Test
    fun replayPinsExistingRoutineIdsAndDoesNotCreate() = runBlocking {
        val insights = MutableStateFlow(TrainingInsights())
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        deps.dbMaintenance.seedCatalog()
        val upper = deps.routineRepository.create(name = "Upper")
        val lower = deps.routineRepository.create(name = "Lower Body")
        val before = deps.routineRepository.count()
        val answers = OnboardingAnswers(
            trainingAge = TrainingAge.RETURNING,
            daysPerWeek = 4,
            preferredDays = emptySet(),
            place = TrainingPlace.FULL_GYM,
        )
        deps.preferencesRepository.setTrainingAge(answers.trainingAge)
        deps.preferencesRepository.setPreferredDays(answers.preferredDays)
        deps.preferencesRepository.setTrainingPlace(answers.place)
        deps.preferencesRepository.setTrainingDaysPerWeek(4)
        insights.value = TrainingInsights(
            routines = listOf(upper, lower),
            weekPlan = emptyWeek(),
        )

        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.replayStoredAnswers()
        val proposals = withTimeout(5_000) {
            viewModel!!.uiState.first { it.proposals.isNotEmpty() || it.error != null }.proposals
        }
        assertEquals(before, deps.routineRepository.count())
        assertTrue(proposals.isNotEmpty())
        assertTrue(proposals.all { it.routineId in setOf(upper.id, lower.id) })
        assertTrue(proposals.all { it.slotId == null })
    }

    @Test
    fun pendingAnswerReplayReplaysWithoutCreating() = runBlocking {
        val insights = MutableStateFlow(TrainingInsights())
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext(), insights)
        deps.dbMaintenance.seedCatalog()
        val upper = deps.routineRepository.create(name = "Upper")
        val lower = deps.routineRepository.create(name = "Lower Body")
        val before = deps.routineRepository.count()
        deps.preferencesRepository.setTrainingAge(TrainingAge.RETURNING)
        deps.preferencesRepository.setPreferredDays(emptySet())
        deps.preferencesRepository.setTrainingPlace(TrainingPlace.FULL_GYM)
        deps.preferencesRepository.setTrainingDaysPerWeek(4)
        insights.value = TrainingInsights(
            routines = listOf(upper, lower),
            weekPlan = emptyWeek(),
        )
        deps.pendingAnswerReplay.value = true

        viewModel = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val proposals = withTimeout(5_000) {
            viewModel!!.uiState.first { it.proposals.isNotEmpty() || it.error != null }.proposals
        }
        assertEquals(before, deps.routineRepository.count())
        assertTrue(proposals.isNotEmpty())
        assertTrue(proposals.all { it.routineId in setOf(upper.id, lower.id) })
        assertFalse(deps.pendingAnswerReplay.value)
    }

    private fun plannerHasARemainingTrainingDay(): Boolean {
        val today = LocalDate.now(ZoneId.systemDefault())
        val prefs = SchedulePreferences.DEFAULT.sanitized()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(prefs.weekStart))
        return WeeklySchedulePlanner.trainingDayIndices(prefs.trainingDaysPerWeek)
            .map { weekStart.plusDays(it.toLong()) }
            .any { !it.isBefore(today) }
    }

    private fun emptyHeat() = BodyHeatSnapshot(
        window = HeatWindow.CURRENT_WEEK,
        windowStartMs = 0L,
        generatedAtMs = 0L,
        loads = emptyList(),
        hasAnyWorkingSets = false,
        hasWindowWorkingSets = false,
    )

    private fun emptyWeek(): WeeklySchedulePlan {
        val start = weekStart.toEpochDay()
        return WeeklySchedulePlan(
            weekStartEpochDay = start,
            generatedAtMs = 0L,
            preferences = SchedulePreferences.DEFAULT,
            resolvedSplit = SplitStyle.UPPER_LOWER,
            days = (0L..6L).map { offset ->
                val date = LocalDate.ofEpochDay(start + offset)
                SuggestedTrainingDay(
                    epochDay = date.toEpochDay(),
                    dayOfWeek = date.dayOfWeek,
                    isRest = true,
                    focusKind = SessionFocusKind.FULL_BODY,
                    focusTitle = "Rest",
                    routineId = null,
                    routineName = null,
                    reason = "",
                    emphasisMuscles = emptyList(),
                    confidence = ScheduleConfidence.HIGH,
                )
            },
            thinHistory = false,
            summary = "No sessions pinned yet.",
        )
    }
}
