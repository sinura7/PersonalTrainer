package com.sinura.personaltrainer.ui.plan

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.DayFill
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.WeekBoardCell
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v4 blueprint packet V1a (A-V1-1..6): Plan week rows speak their status, keep minimum heights, and add per row. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class PlanWeekRowsRenderTest {
    @get:Rule val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 3).toEpochDay()

    private fun cell(fill: DayFill, planned: Int) = WeekBoardCell(
        epochDay = day, weekday = Weekday.fromEpochDay(day), fill = fill, caption = "",
        plannedCount = planned, resolvedCount = 0, completedCount = 0, skippedCount = 0, missedCount = 0,
    )

    private fun proposal(rest: Boolean) = SuggestedTrainingDay(
        epochDay = day, dayOfWeek = Weekday.fromEpochDay(day), isRest = rest,
        focusKind = SessionFocusKind.values().first(), focusTitle = if (rest) "Rest" else "Upper",
        routineId = null, routineName = null, reason = "", emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.values().first(),
    )

    private data class Shown(
        val cell: WeekBoardCell,
        val proposal: SuggestedTrainingDay?,
        val fontScale: Float,
        val onAdd: (() -> Unit)?,
    )

    private val shown = mutableStateOf<Shown?>(null)

    private fun show(
        cell: WeekBoardCell,
        proposal: SuggestedTrainingDay? = null,
        fontScale: Float = 1f,
        onAdd: (() -> Unit)? = {},
    ) {
        val first = shown.value == null
        shown.value = Shown(cell, proposal, fontScale, onAdd)
        if (first) compose.setContent {
            val s = shown.value ?: return@setContent
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, s.fontScale)) {
                PersonalTrainerTheme {
                    PlanWeekDayRow(
                        cell = s.cell, items = emptyList(), routines = emptyMap(), proposal = s.proposal,
                        isToday = false, onOpenDay = {}, onAddToDay = s.onAdd,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun spoken(): String = compose.onNodeWithTag(PlanTags.dayHeader(day))
        .fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()

    @Test
    fun suggestedRestDaySpeaksItsStatus() {
        show(cell(DayFill.EMPTY, 0), proposal = proposal(rest = true))
        val words = spoken()
        assertTrue(words, words.contains("Rest") && words.contains("suggested rest day"))
    }

    @Test
    fun trainingRowIsAtLeast72AndRestRowAtLeast56() {
        show(cell(DayFill.NONE, 1))
        val training = compose.onNodeWithTag(PlanTags.day(day)).getBoundsInRoot()
        assertTrue("training ${training.bottom - training.top}", training.bottom - training.top >= 72.dp)
        show(cell(DayFill.EMPTY, 0))
        val rest = compose.onNodeWithTag(PlanTags.day(day)).getBoundsInRoot()
        assertTrue("rest ${rest.bottom - rest.top}", rest.bottom - rest.top >= 56.dp)
    }

    @Test
    fun bareRestRowStaysUnder72() {
        show(cell(DayFill.EMPTY, 0), onAdd = null)
        val rest = compose.onNodeWithTag(PlanTags.day(day)).getBoundsInRoot()
        assertTrue("bare rest ${rest.bottom - rest.top}", rest.bottom - rest.top < 72.dp)
    }

    @Test
    fun rowsGrowAtFontScaleTwo() {
        show(cell(DayFill.EMPTY, 0), proposal = proposal(rest = false))
        val normal = compose.onNodeWithTag(PlanTags.day(day)).getBoundsInRoot().let { it.bottom - it.top }
        show(cell(DayFill.EMPTY, 0), proposal = proposal(rest = false), fontScale = 2f)
        val big = compose.onNodeWithTag(PlanTags.day(day)).getBoundsInRoot().let { it.bottom - it.top }
        assertTrue("normal $normal big $big", big >= normal && big >= 72.dp)
    }

    @Test
    fun rowAddOpensThatDay() {
        var added = 0
        show(cell(DayFill.EMPTY, 0), onAdd = { added++ })
        compose.onNodeWithTag(PlanTags.dayAdd(day)).performClick()
        assertEquals(1, added)
        show(cell(DayFill.EMPTY, 0), onAdd = null)
        compose.onNodeWithTag(PlanTags.dayAdd(day)).assertDoesNotExist()
    }

    @Test
    fun suggestFillsWritesNothing() = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            insights = MutableStateFlow(TrainingInsights(snapshot = emptyHeat())),
            scheduler = dispatcher,
        )
        val vm = PlanViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        try {
            vm.uiState.awaitFirst { !it.isLoading }
            val before = deps.scheduleRepository.slots()
            vm.suggestFills()
            dispatcher.scheduler.advanceUntilIdle()
            vm.uiState.awaitFirst { !it.isLoading }
            assertEquals("Suggest previews; it never writes a slot", before, deps.scheduleRepository.slots())
        } finally {
            vm.clearAndJoinForTest()
            dispatcher.scheduler.advanceUntilIdle()
            deps.close()
            Dispatchers.resetMain()
        }
    }

    private fun emptyHeat() = BodyHeatSnapshot(
        window = HeatWindow.CURRENT_WEEK, windowStartMs = 0L, generatedAtMs = 0L,
        loads = emptyList(), hasAnyWorkingSets = false, hasWindowWorkingSets = false,
    )
}
