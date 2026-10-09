package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilYearMonth
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.IdPort
import com.sinura.personaltrainer.domain.MuscleLoadCalculator
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.util.JvmTime
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Confirmed captured dates remain real, reachable History actions after crossing the date line. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class HistoryCapturedDateTravelRenderTest : HistoryPeriodTestHost() {
    private var currentCivilDay by mutableStateOf(0L)

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun narrowLargestTextKeepsTheTokyoDay() = travel("travel-oct-320-font20", 2f, false)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscapeLargestTextKeepsTheTokyoDay() = travel("travel-oct-land-font20", 2f, false)
    @Test @Config(qualifiers = "ldrtl-w360dp-h640dp-xhdpi", fontScale = 2f)
    fun rtlLargestTextKeepsTheTokyoDay() = travel("travel-oct-rtl-font20", 2f, false, LayoutDirection.Rtl)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standardTextKeepsTheTokyoDay() = travel("travel-oct-412-font10", 1f, false)

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun narrowLargestTextReachesTheKnownNextYear() = travel("travel-jan-320-font20", 2f, true)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscapeLargestTextReachesTheKnownNextYear() = travel("travel-jan-land-font20", 2f, true)
    @Test @Config(qualifiers = "ldrtl-w360dp-h640dp-xhdpi", fontScale = 2f)
    fun rtlLargestTextReachesTheKnownNextYear() = travel("travel-jan-rtl-font20", 2f, true, LayoutDirection.Rtl)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standardTextReachesTheKnownNextYear() = travel("travel-jan-412-font10", 1f, true)

    private fun travel(name: String, scale: Float, yearBoundary: Boolean,
        layout: LayoutDirection = LayoutDirection.Ltr) = evidence(name) {
        font = scale
        direction = layout
        val clock = HistoryTravelRenderClock(
            nowMs = Instant.parse(if (yearBoundary) "2026-12-31T20:20:00Z" else "2026-10-08T20:20:00Z").toEpochMilli(),
            zone = "Asia/Tokyo",
        )
        val capturedDay = clock.captureNow().localEpochDay
        val capturedDate = LocalDate.ofEpochDay(capturedDay)
        val snapshot = MuscleLoadCalculator.snapshot(sessions = emptyList(), window = HeatWindow.CURRENT_WEEK,
            nowMs = clock.nowMillis(), time = clock)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher, time = clock,
            insights = MutableStateFlow(TrainingInsights(snapshot = snapshot)),
        )
        runBlocking {
            deps.preferencesRepository.setOnboardingComplete(true)
            deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
            val accepted = deps.activityRepository.confirm(
                draft = ActivityDraft(
                    id = ACTIVITY_ID, status = ActivityStatus.COMPLETED, origin = ActivityOrigin.BACKDATED,
                    title = TITLE, performedStart = clock.capture(clock.nowMillis() - 600_000L),
                    performedEnd = clock.captureNow(),
                    blocks = listOf(CardioBlock(
                        id = "travel-render-walk", sortOrder = 0, type = CardioType.WALK, indoor = false,
                        elapsedSeconds = 600, movingSeconds = null, distanceMeters = 1000.0,
                        elevationMeters = null, heartRateBpm = null, energyKj = null,
                        rpe = null, routeRef = null,
                    )),
                ),
                now = clock.captureNow(), ids = IdPort { ACTIVITY_ID }, clock = clock,
            )
            assertTrue("real Tokyo authoring succeeds: $accepted", accepted is ActivityWrite.Accepted)
            assertEquals(capturedDay, (accepted as ActivityWrite.Accepted).session.localEpochDay)
            assertEquals(ACTIVITY_ID, accepted.session.id)
        }
        val durableGraphs = runBlocking { deps.database.activityDao().getAllGraphs() }
        val savedSummary = runBlocking { deps.database.activityDao().observeCompletedSummaries().first().single() }
        val stored = inventory()
        currentCivilDay = capturedDay
        history = HistoryViewModel(application = ApplicationProvider.getApplicationContext<Application>(),
            savedStateHandle = SavedStateHandle(), container = deps)
        mountTravel()
        ready(AnalyticsHorizon.MONTH)
        assertContents(1, capturedDay)
        capture("tokyo-before-travel")

        clock.nowMs += 9L * 60L * 60L * 1000L
        clock.zone = "Pacific/Honolulu"
        val deviceDay = clock.captureNow().localEpochDay
        assertEquals("later instant crosses back one civil day", capturedDay - 1, deviceDay)
        compose.runOnIdle { currentCivilDay = deviceDay }
        drainLayout()
        ready(AnalyticsHorizon.MONTH)
        assertEquals(setOf(capturedDay), history.uiState.value.completedEpochDays)

        reachTag(HistoryTags.ALL).performClick()
        ready(AnalyticsHorizon.ALL_TIME)
        assertContents(1, capturedDay)
        assertRange()
        assertRow(HistoryKind.ACTIVITY, ACTIVITY_ID, TITLE, capturedDay, "10 min")
        reach(wordsInside(HistoryTags.row(HistoryKind.ACTIVITY, ACTIVITY_ID), TITLE))
            .performTouchInput { click(center) }
        assertEquals(listOf(HistoryKind.ACTIVITY to ACTIVITY_ID), routes)
        capture("honolulu-all-saved-date")

        // The current Day's scoped calendar has no training on the known later day.
        // Eligibility therefore must come from the catalog, not the scoped trained bit.
        reachTag(HistoryTags.DAY).performClick()
        ready(AnalyticsHorizon.DAY)
        assertContents(0, capturedDay)
        val known = reachTag(HistoryTags.day(capturedDay)).assertIsEnabled()
        assertTarget(known, Role.RadioButton)
        assertFalse(known.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single().contains("trained"))
        assertWords(reachTag(HistoryTags.date(capturedDay), unmerged = true), capturedDate.dayOfMonth.toString())
        known.performTouchInput { click(center) }
        ready(horizon = AnalyticsHorizon.DAY, anchor = capturedDay, followToday = false)
        assertEquals(capturedDay, history.uiState.value.selection.anchorEpochDay)
        assertContents(1, capturedDay)
        val selected = reachTag(HistoryTags.day(capturedDay)).fetchSemanticsNode()
        assertTrue(selected.config[SemanticsProperties.Selected])
        assertFalse(selected.config[SemanticsProperties.ContentDescription].single().contains("today"))
        val actualToday = reachTag(HistoryTags.day(deviceDay)).fetchSemanticsNode()
        assertFalse(actualToday.config[SemanticsProperties.Selected])
        assertTrue(actualToday.config[SemanticsProperties.ContentDescription].single().contains("today"))
        val unknown = reachTag(HistoryTags.day(capturedDay + 1))
        assertTarget(unknown, Role.RadioButton)
        assertTrue("unlogged later day stays disabled", unknown.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled))
        capture("honolulu-today-and-known-selection")
        nextEnabled(false)
        reachTag(HistoryTags.PREVIOUS).performClick()
        ready(horizon = AnalyticsHorizon.DAY, anchor = deviceDay, followToday = false)
        assertContents(0, capturedDay)
        nextEnabled(true)
        reachTag(HistoryTags.NEXT).performClick()
        ready(horizon = AnalyticsHorizon.DAY, anchor = capturedDay, followToday = false)
        assertEquals(capturedDay, history.uiState.value.selection.anchorEpochDay)
        assertContents(1, capturedDay)

        reachTag(HistoryTags.WEEK).performClick()
        ready(AnalyticsHorizon.WEEK)
        assertContents(1, capturedDay)
        assertRange()
        capture("honolulu-known-week")
        reachTag(HistoryTags.MONTH).performClick()
        ready(AnalyticsHorizon.MONTH)
        assertContents(1, capturedDay)
        reachTag(HistoryTags.CURRENT).performClick()
        ready(horizon = AnalyticsHorizon.MONTH, followToday = true)
        assertContents(if (yearBoundary) 0 else 1, capturedDay)
        nextEnabled(yearBoundary)
        if (yearBoundary) {
            reachTag(HistoryTags.NEXT).performClick()
            ready(horizon = AnalyticsHorizon.MONTH, followToday = false)
            assertContents(1, capturedDay)
            assertEquals(CivilYearMonth(2027, 1), history.uiState.value.calendar.month)
            nextEnabled(false)
        }

        reachTag(HistoryTags.YEAR).performClick()
        ready(AnalyticsHorizon.YEAR)
        reachTag(HistoryTags.CURRENT).performClick()
        ready(horizon = AnalyticsHorizon.YEAR, followToday = true)
        assertContents(if (yearBoundary) 0 else 1, capturedDay)
        nextEnabled(yearBoundary)
        if (yearBoundary) {
            reachTag(HistoryTags.NEXT).performClick()
            ready(horizon = AnalyticsHorizon.YEAR, followToday = false)
            assertContents(1, capturedDay)
            nextEnabled(false)
        }
        val names = listOf("January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December")
        for (number in 1..12) {
            val tag = HistoryTags.month(CivilYearMonth(capturedDate.year, number))
            val button = reachTag(tag)
            assertTarget(button)
            val count = if (number == capturedDate.monthValue) 1 else 0
            val label = "${names[number - 1]} ${capturedDate.year} · $count ${if (count == 1) "session" else "sessions"}"
            assertWords(wordsInside(tag, label), label)
            val enabled = number == capturedDate.monthValue || !yearBoundary && number <= 10
            assertEquals("only known completed content permits a later month", !enabled,
                button.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled))
            if (enabled) button.assertIsEnabled()
            if (number == capturedDate.monthValue || number == capturedDate.monthValue + 1)
                capture("honolulu-year-month-$number")
        }
        reachTag(HistoryTags.month(CivilYearMonth(capturedDate.year, capturedDate.monthValue))).performClick()
        ready(AnalyticsHorizon.MONTH)
        assertContents(1, capturedDay)
        assertRow(HistoryKind.ACTIVITY, ACTIVITY_ID, TITLE, capturedDay, "10 min")
        capture("honolulu-known-month-exact-row")
        assertEquals(listOf(HistoryKind.ACTIVITY to ACTIVITY_ID), routes)
        assertTrue(openedExercises.isEmpty())
        assertEquals("date selection and exact detail callback write no durable data", stored, inventory())
        runBlocking {
            assertEquals(durableGraphs, deps.database.activityDao().getAllGraphs())
            assertEquals(savedSummary, deps.database.activityDao().observeCompletedSummaries().first().single())
        }
    }

    private fun mountTravel() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, font), LocalLayoutDirection provides direction,
                LocalTodayEpochDay provides currentCivilDay, LocalWeightUnit provides WeightUnit.KG,
            ) {
                PersonalTrainerTheme(reduceMotion = true) {
                    HistoryScreen(
                        onOpenSession = { routes += HistoryKind.WORKOUT to it },
                        onOpenActivity = { routes += HistoryKind.ACTIVITY to it },
                        onOpenExercise = { openedExercises += it },
                        onOpenActiveSession = { throw AssertionError("travel review cannot resume training") },
                        onOpenStartSheet = { throw AssertionError("travel review cannot author training") },
                        viewModel = history,
                    )
                }
            }
        }
        drainLayout()
    }

    private fun ready(horizon: AnalyticsHorizon, anchor: Long? = null, followToday: Boolean? = null) {
        compose.awaitThat("requested travel period reaches READY for the actual current day", { history.uiState.value }) {
            val state = history.uiState.value
            state.today.epochDay == currentCivilDay && state.horizon == horizon &&
                !state.isLoading && !state.unavailable && !state.progressLoading && !state.progressFailed &&
                state.horizonProgress != null && state.periodRange != null &&
                (anchor == null || state.selection.anchorEpochDay == anchor) &&
                (followToday == null || state.selection.followToday == followToday)
        }
        drainLayout()
    }

    private fun assertContents(count: Int, capturedDay: Long) {
        val state = history.uiState.value
        val expected = if (count == 1) setOf(HistoryKind.ACTIVITY to ACTIVITY_ID) else emptySet()
        assertEquals(expected, state.summaries.map { it.kind to it.id }.toSet())
        assertEquals(expected, state.monthGroups.flatMap { it.entries }.map { it.kind to it.id }.toSet())
        assertEquals(count, state.horizonTotals!!.sessionCount)
        assertEquals(count, state.horizonTotals!!.trainedDays)
        assertEquals(0, state.horizonTotals!!.workingSets)
        assertEquals(if (count == 1) 10 else 0, state.horizonTotals!!.activeMinutes)
        assertEquals(0, state.horizonProgress!!.recordsBroken)
        assertEquals(count == 1, capturedDay in state.periodRange!!)
        assertTrue(state.summaries.all { it.localEpochDay in state.periodRange!! })
        if (state.horizon in listOf(AnalyticsHorizon.DAY, AnalyticsHorizon.WEEK, AnalyticsHorizon.MONTH)) {
            val calendar = state.calendar.weeks.flatten().flatMap { cell ->
                cell.sessionIds.map { HistoryKind.WORKOUT to it } + cell.activityIds.map { HistoryKind.ACTIVITY to it }
            }.toSet()
            assertEquals(expected, calendar)
        }
    }

    private fun nextEnabled(expected: Boolean) {
        val next = reachTag(HistoryTags.NEXT)
        assertTarget(next)
        assertWords(wordsInside(HistoryTags.NEXT, "Next"), "Next")
        assertEquals(!expected, next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled))
        assertEquals(expected, history.uiState.value.canGoNext)
        if (expected) next.assertIsEnabled()
    }

    private companion object {
        const val ACTIVITY_ID = "travel-render-tokyo-walk"
        const val TITLE = "Morning walk recorded in Tokyo"
    }
}

private class HistoryTravelRenderClock(@Volatile var nowMs: Long, @Volatile var zone: String) : TimePort by JvmTime {
    override fun nowMillis(): Long = nowMs
    override fun defaultZoneId(): String = zone
    override fun captureNow(zoneId: String): CapturedCivilTime = capture(nowMs, zoneId)
}
