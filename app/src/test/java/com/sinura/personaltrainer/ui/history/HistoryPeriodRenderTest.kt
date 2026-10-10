package com.sinura.personaltrainer.ui.history

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect as PeriodReachRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.dao.RecordSetRow
import com.sinura.personaltrainer.data.local.entity.SessionSummaryRow
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.data.local.relation.ActivitySessionGraph
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilYearMonth
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.HistoryCopy
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.MuscleLoadCalculator
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.FailingPastBlocksDao
import com.sinura.personaltrainer.testutil.ReadGate
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.ui.components.SessionLogTags
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.count
import com.sinura.personaltrainer.ui.workout.settle
import com.sinura.personaltrainer.ui.workout.textLayout
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Actual History/VM/store evidence; no source text guards, fabricated layout or owner data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class HistoryPeriodRenderTest : HistoryPeriodTestHost() {
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun narrowLargestText() = matrix("320x640-font20", 2f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
    fun smallDefaultText() = matrix("360x640-font10", 1f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun smallLargeText() = matrix("360x640-font16", 1.6f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun smallLargestText() = matrix("360x640-font20", 2f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standardDefaultText() = matrix("412x840-font10", 1f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun standardLargeText() = matrix("412x840-font16", 1.6f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun standardLargestText() = matrix("412x840-font20", 2f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1f)
    fun landscapeDefaultText() = matrix("800x360-font10", 1f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscapeLargeText() = matrix("800x360-font16", 1.6f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscapeLargestText() = matrix("800x360-font20", 2f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1f)
    fun tabletDefaultText() = matrix("600x960-font10", 1f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1.6f)
    fun tabletLargeText() = matrix("600x960-font16", 1.6f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun tabletLargestText() = matrix("600x960-font20", 2f)
    @Test @Config(qualifiers = "ldrtl-w360dp-h640dp-xhdpi", fontScale = 2f)
    fun rtlLargestTextAndReducedMotion() = matrix("360x640-rtl-font20", 2f, LayoutDirection.Rtl)

    @Test
    fun scrollabilityDoesNotMeanTheRequiredHistoryReadIsReady() = evidence("required-read-readiness") {
        holdRequired = true
        graph()
        val before = inventory()
        show()
        compose.awaitThat("the real required read is held", { requiredStarted.isCompleted }) {
            requiredStarted.isCompleted && history.uiState.value.isLoading
        }
        // This is the old connected journey predicate. It is already true while
        // the required read is deliberately blocked and no finished row can render.
        compose.onNodeWithTag(HistoryTags.LIST).assert(hasScrollAction())
        compose.onNodeWithTag(HistoryTags.CURRENT).assertDoesNotExist()
        compose.onNodeWithTag(HistoryTags.row(HistoryKind.WORKOUT, HistoryPeriodTestHost.CURRENT_ID)).assertDoesNotExist()
        capture("loading-list-is-scrollable")
        holdRequired = false
        requiredBarrier.complete(Unit)
        awaitLoaded()
        reachTag(HistoryTags.CURRENT).assertIsDisplayed()
        assertRow(HistoryKind.WORKOUT, HistoryPeriodTestHost.CURRENT_ID,
            HistoryPeriodTestHost.WORKOUT_TITLE, day(11), "1 h 20 min")
        reach(wordsInside(HistoryTags.row(HistoryKind.WORKOUT, HistoryPeriodTestHost.CURRENT_ID),
            HistoryPeriodTestHost.WORKOUT_TITLE)).performClick()
        assertEquals(listOf(HistoryKind.WORKOUT to HistoryPeriodTestHost.CURRENT_ID), routes)
        assertEquals("waiting for real readiness cannot change the saved graph", before, inventory())
        capture("ready-exact-row")
    }

    @Test
    fun heldAndFailedProgressNeverLooksLikeSuccessfulZero() = evidence("progress-states") {
        graph(seedFinishedBlock = false)
        holdProgress = true
        show()
        awaitLoaded(success = false)
        compose.awaitThat("held request exposes loading", { history.uiState.value }) {
            history.uiState.value.progressLoading && history.uiState.value.horizonProgress == null
        }
        assertWords(reachTag(HistoryTags.PROGRESS_LOADING), HistoryCopy.PROGRESS_LOADING)
        assertFalse(readoutSpoken().contains("0 PRs"))
        capture("held-progress")
        failProgress = true
        holdProgress = false
        readBarrier.complete(Unit)
        compose.awaitThat("actual full-log failure exposes failed progress", { history.uiState.value }) {
            history.uiState.value.progressFailed
        }
        assertWords(reachTag(HistoryTags.PROGRESS_FAILED), HistoryCopy.PROGRESS_FAILED)
        assertFalse(readoutSpoken().contains("0 PRs"))
        val selected = history.uiState.value.selection
        capture("failed-progress")
        failProgress = false
        reachTag(HistoryTags.PROGRESS_RETRY).performClick()
        awaitLoaded()
        assertEquals(selected, history.uiState.value.selection)
        assertFalse(history.uiState.value.progressFailed)
        assertNotNull(history.uiState.value.horizonProgress)
        capture("recovered-progress")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun manualCalendarExplorationSurvivesCompositionRestoration() = evidence("calendar-restoration") {
        font = 2f
        graph()
        val restore = StateRestorationTester(compose)
        show(restore)
        awaitLoaded()
        reachTag(HistoryTags.day(today))
        val scroll = compose.onNodeWithTag(HistoryTags.CALENDAR_SCROLL)
        val action = scroll.fetchSemanticsNode().config[SemanticsActions.ScrollBy].action!!
        compose.runOnUiThread { action(-10_000f, 0f) }
        drainLayout()
        val before = scroll.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        restore.emulateSavedInstanceStateRestore()
        drainLayout()
        val after = scroll.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertEquals("saved manual exploration is not snapped to selected", before, after, 1f)
        compose.runOnIdle { history.retryHistory() }
        awaitLoaded()
        assertEquals("same selection/geometry after result refresh preserves exploration", before,
            scroll.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value(), 1f)
        capture("restored-exploration")
        compose.runOnIdle { font = 1.6f }
        drainLayout()
        val selected = compose.onNodeWithTag(HistoryTags.day(today)).fetchSemanticsNode()
        assertTrue("geometry change reveals selected column without a test scroll",
            selected.boundsInWindow.width >= selected.size.width - 1f)
        assertVisible(reachTag(HistoryTags.day(today)))
        capture("changed-font-selected-revealed")
    }

    @Test
    fun lifetimeSheetsNeverTurnUnreadOrFreshOwnerLoadingIntoSuccessfulEmpty() = evidence("lifetime-read-health") {
        failRecords = true
        blockGate.shouldFail = true
        graph()
        show()
        awaitLoaded()
        compose.awaitThat("unread lifetime sections are explicitly degraded", { history.uiState.value }) {
            (history.uiState.value.recordsUnavailable || history.uiState.value.recordsStale) &&
                (history.uiState.value.blocksUnavailable || history.uiState.value.blocksStale)
        }
        for (tag in listOf(HistoryTags.LIFETIME_RECORDS, HistoryTags.LIFETIME_BLOCKS)) {
            reachTag(tag).performClick()
            sheetOpen = true
            drainLayout()
            assertTarget(reachTag(HistoryTags.SECONDARY_RETRY))
            assertTrue(compose.onAllNodes(hasText("No lifetime records yet.")).fetchSemanticsNodes().isEmpty())
            assertTrue(compose.onAllNodes(hasText("No completed blocks yet.")).fetchSemanticsNodes().isEmpty())
            capture(if (tag == HistoryTags.LIFETIME_RECORDS) "unread-records" else "unread-blocks")
            reachTag(HistoryTags.SECONDARY_CLOSE).performClick()
            sheetOpen = false
            drainLayout()
        }
        reachTag(HistoryTags.LIFETIME_RECORDS).performClick()
        sheetOpen = true
        failRecords = false
        blockGate.shouldFail = false
        reachTag(HistoryTags.SECONDARY_RETRY).performClick()
        awaitLoaded()
        compose.awaitThat("retry recovers independently successful lifetime records", { history.uiState.value }) {
            !history.uiState.value.recordsUnavailable && !history.uiState.value.recordsStale &&
                history.uiState.value.records.any { it.valueKg == 120.0 }
        }
        capture("lifetime-recovered")

        // A distinct injected VM exercises an already-open local sheet during a new read.
        // This is mounted owner replacement, not a claim of Android process-death recovery.
        val previous = history
        holdRecords = true
        compose.runOnIdle {
            history = HistoryViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
            font = 1.01f
        }
        drainLayout()
        assertWords(reachTag(HistoryTags.SECONDARY_LOADING), "Loading Lifetime records…")
        assertTrue(compose.onAllNodes(hasText("No lifetime records yet.")).fetchSemanticsNodes().isEmpty())
        capture("fresh-owner-loading")
        holdRecords = false
        recordsBarrier.complete(Unit)
        awaitLoaded()
        runBlocking { previous.clearAndJoinForTest() }

        val loaded = history
        failRequired = true
        compose.runOnIdle {
            history = HistoryViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
            font = 1.02f
        }
        drainLayout()
        compose.awaitThat("fresh required summary read is unavailable", { history.uiState.value }) {
            !history.uiState.value.isLoading && history.uiState.value.unavailable
        }
        assertTrue("unread main history must not create a successful lifetime empty",
            compose.onAllNodes(hasText("No lifetime records yet.")).fetchSemanticsNodes().isEmpty())
        assertTrue("lifetime state retains independently read records or offers an honest error",
            history.uiState.value.records.any { it.valueKg == 120.0 } || history.uiState.value.recordsUnavailable)
        capture("fresh-required-failure")
        runBlocking { loaded.clearAndJoinForTest() }
        reachTag(HistoryTags.SECONDARY_CLOSE).performClick()
        sheetOpen = false
        failRequired = false
        compose.onNodeWithText("Retry").performClick()
        awaitLoaded()
        assertTrue(history.uiState.value.records.any { it.valueKg == 120.0 })
        capture("required-recovered")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun populatedLifetimeBlocksStayReadableWhileFailedRefreshOffersRetry() = evidence("lifetime-block-recovery") {
        font = 2f
        graph()
        val stored = inventory()
        show()
        awaitLoaded()
        reachTag(HistoryTags.LIFETIME_BLOCKS).performClick()
        sheetOpen = true
        drainLayout()
        assertFinishedBlock()
        val verified = history.uiState.value.pastBlocks
        val selected = history.uiState.value.selection
        blockGate.shouldFail = true
        compose.runOnIdle { history.retryHistory() }
        compose.awaitThat("failed block read retains its verified review", { history.uiState.value }) {
            history.uiState.value.blocksStale && !history.uiState.value.blocksLoading
        }
        assertEquals(verified, history.uiState.value.pastBlocks)
        assertFalse(history.uiState.value.blocksUnavailable)
        assertWords(reachTag(HistoryTags.SECONDARY_FAILED),
            "Completed blocks · lifetime is behind. Retry to refresh.")
        assertTarget(reachTag(HistoryTags.SECONDARY_RETRY))
        assertFinishedBlock()
        capture("populated-blocks-retained-after-failure")
        blockGate.shouldFail = false
        reachTag(HistoryTags.SECONDARY_RETRY).performClick()
        awaitLoaded()
        compose.awaitThat("block retry finishes loading and clears the section error", { history.uiState.value }) {
            !history.uiState.value.blocksLoading && !history.uiState.value.blocksStale &&
                !history.uiState.value.blocksUnavailable
        }
        assertEquals(selected, history.uiState.value.selection)
        assertEquals(verified, history.uiState.value.pastBlocks)
        assertFinishedBlock()
        assertTarget(reachTag(HistoryTags.SECONDARY_CLOSE))
        reachTag(HistoryTags.SECONDARY_CLOSE).performClick()
        sheetOpen = false
        drainLayout()
        assertEquals("block exploration and retries write no durable data", stored, inventory())
        assertTrue(routes.isEmpty())
        capture("populated-blocks-recovered-and-closed")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun finishedFourWeekBlockRendersItsGenuineOpeningAndClosingMover() = evidence("lifetime-block-mover") {
        font = 2f
        graph(seedBlockMover = true)
        val stored = inventory()
        show()
        awaitLoaded()
        assertScoped(setOf(HistoryKind.WORKOUT to HistoryPeriodTestHost.CURRENT_ID,
            HistoryKind.WORKOUT to HistoryPeriodTestHost.START_ID, HistoryKind.ACTIVITY to HistoryPeriodTestHost.CURRENT_ID))
        reachTag(HistoryTags.LIFETIME_BLOCKS).performClick()
        sheetOpen = true
        drainLayout()
        assertFinishedBlock(hasOpeningSession = true)
        val mover = history.uiState.value.pastBlocks.single().review.movers.single()
        assertEquals("period-lift-1", mover.exerciseId)
        assertEquals("Period lift 1", mover.exerciseName)
        assertEquals("105 kg", mover.fromLabel)
        assertEquals("116.7 kg", mover.toLabel)
        assertTrue("actual opening/closing strength improves beyond the domain threshold", mover.gain > .1)
        assertWords(wordsInside(HistoryTags.SECONDARY_LIST, "Period lift 1"), "Period lift 1")
        assertWords(wordsInside(HistoryTags.SECONDARY_LIST, "105 kg → 116.7 kg"), "105 kg → 116.7 kg")
        capture("four-week-genuine-mover")
        assertTarget(reachTag(HistoryTags.SECONDARY_CLOSE))
        reachTag(HistoryTags.SECONDARY_CLOSE).performClick()
        sheetOpen = false
        drainLayout()
        assertEquals("completed block reading creates no durable changes", stored, inventory())
        assertTrue(routes.isEmpty())
    }

    private fun matrix(name: String, scale: Float, layout: LayoutDirection = LayoutDirection.Ltr) = evidence(name) {
        font = scale
        direction = layout
        graph()
        val stored = inventory()
        show()
        awaitLoaded()
        assertWords(compose.onNodeWithText("History", useUnmergedTree = true), "History")
        assertScoped(setOf(HistoryKind.WORKOUT to HistoryPeriodTestHost.CURRENT_ID, HistoryKind.WORKOUT to HistoryPeriodTestHost.START_ID, HistoryKind.ACTIVITY to HistoryPeriodTestHost.CURRENT_ID))
        capture("month-initial")
        for (horizon in AnalyticsHorizon.entries) {
            val chip = reachTag(HistoryTags.horizon(horizon))
            assertTarget(chip, Role.RadioButton)
            assertWords(wordsInside(HistoryTags.horizon(horizon), horizon.label), horizon.label)
        }
        for ((tag, label) in listOf(HistoryTags.PREVIOUS to "Previous", HistoryTags.NEXT to "Next", HistoryTags.CURRENT to "Current")) {
            assertTarget(reachTag(tag))
            assertWords(wordsInside(tag, label), label)
        }
        assertTrue("next current period is disabled", reachTag(HistoryTags.NEXT).fetchSemanticsNode().config.contains(SemanticsProperties.Disabled))
        assertRange()
        assertLifetimeViews()
        tapDay(day(10))
        assertScoped(setOf(HistoryKind.ACTIVITY to HistoryPeriodTestHost.CURRENT_ID))
        assertEquals("calendar selects Day first", AnalyticsHorizon.DAY, history.uiState.value.horizon)
        assertTrue("day tap never navigates", routes.isEmpty())
        assertRow(HistoryKind.ACTIVITY, HistoryPeriodTestHost.CURRENT_ID, HistoryPeriodTestHost.ACTIVITY_TITLE, day(10), "2 min")
        reach(wordsInside(HistoryTags.row(HistoryKind.ACTIVITY, HistoryPeriodTestHost.CURRENT_ID), HistoryPeriodTestHost.ACTIVITY_TITLE))
            .performTouchInput { click(center) }
        assertEquals(listOf(HistoryKind.ACTIVITY to HistoryPeriodTestHost.CURRENT_ID), routes)
        tapDay(day(11))
        assertScoped(setOf(HistoryKind.WORKOUT to HistoryPeriodTestHost.CURRENT_ID))
        assertRow(HistoryKind.WORKOUT, HistoryPeriodTestHost.CURRENT_ID, HistoryPeriodTestHost.WORKOUT_TITLE, day(11), "1 h 20 min")
        reach(wordsInside(HistoryTags.row(HistoryKind.WORKOUT, HistoryPeriodTestHost.CURRENT_ID), HistoryPeriodTestHost.WORKOUT_TITLE))
            .performTouchInput { click(center) }
        assertEquals("equal bare IDs open their exact distinct route kinds",
            listOf(HistoryKind.ACTIVITY to HistoryPeriodTestHost.CURRENT_ID, HistoryKind.WORKOUT to HistoryPeriodTestHost.CURRENT_ID), routes)
        capture("day-exact-id")
        val week = reachTag(HistoryTags.WEEK)
        assertTarget(week, Role.RadioButton)
        week.performClick()
        awaitLoaded()
        assertEquals(AnalyticsHorizon.WEEK, history.uiState.value.horizon)
        assertEquals(day(5), history.uiState.value.periodRange!!.startEpochDay)
        assertEquals(day(12), history.uiState.value.periodRange!!.endExclusiveEpochDay)
        assertScoped(setOf(HistoryKind.ACTIVITY to HistoryPeriodTestHost.CURRENT_ID,
            HistoryKind.WORKOUT to HistoryPeriodTestHost.CURRENT_ID))
        assertRange()
        assertWords(reachTag(HistoryTags.RANGE_TITLE), "5 October 2026 – 11 October 2026")
        capture("week-readout")
        assertRow(HistoryKind.ACTIVITY, HistoryPeriodTestHost.CURRENT_ID,
            HistoryPeriodTestHost.ACTIVITY_TITLE, day(10), "2 min")
        assertRow(HistoryKind.WORKOUT, HistoryPeriodTestHost.CURRENT_ID,
            HistoryPeriodTestHost.WORKOUT_TITLE, day(11), "1 h 20 min")
        for (number in 5..11) {
            val cell = reachTag(HistoryTags.day(day(number)))
            assertTarget(cell, Role.RadioButton)
            assertWords(reachTag(HistoryTags.date(day(number)), unmerged = true), number.toString())
            assertTrue(cell.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
                .contains(DateCopy.weekdayFullDate(LocalDate.ofEpochDay(day(number)))))
        }
        capture("week-aligned-days")
        reachTag(HistoryTags.YEAR).performClick()
        awaitLoaded()
        val months = listOf("January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December")
        val captureYear = name in setOf("320x640-font20", "800x360-font20",
            "600x960-font20", "360x640-rtl-font20")
        for (number in 1..12) {
            val tag = HistoryTags.month(CivilYearMonth(2026, number))
            val month = reachTag(tag)
            assertTarget(month)
            val count = when (number) { 9 -> 1; 10 -> 3; else -> 0 }
            val label = "${months[number - 1]} 2026 · $count ${if (count == 1) "session" else "sessions"}"
            assertWords(wordsInside(tag, label), label)
            assertEquals("only future month choices are disabled for 15 October 2026",
                number > 10, month.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled))
            if (number <= 10) month.assertIsEnabled()
            if (captureYear && number in listOf(9, 10, 12)) capture("year-month-$number")
        }
        reachTag(HistoryTags.month(CivilYearMonth(2026, 10))).performClick()
        awaitLoaded()
        assertEquals(AnalyticsHorizon.MONTH, history.uiState.value.horizon)
        assertScoped(setOf(HistoryKind.WORKOUT to HistoryPeriodTestHost.CURRENT_ID,
            HistoryKind.WORKOUT to HistoryPeriodTestHost.START_ID, HistoryKind.ACTIVITY to HistoryPeriodTestHost.CURRENT_ID))
        tapDay(day(12))
        assertScoped(emptySet())
        assertWords(wordsInside(HistoryTags.EMPTY, HistoryCopy.EMPTY_PERIOD_TITLE), HistoryCopy.EMPTY_PERIOD_TITLE)
        assertWords(wordsInside(HistoryTags.EMPTY, HistoryCopy.EMPTY_PERIOD), HistoryCopy.EMPTY_PERIOD)
        assertLifetimeViews()
        capture("empty-day")
        reachTag(HistoryTags.ALL).performClick()
        awaitLoaded()
        assertEquals(4, history.uiState.value.summaries.size)
        assertTrue(compose.onAllNodes(hasTestTag(HistoryTags.CALENDAR)).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasTestTag(HistoryTags.PREVIOUS)).fetchSemanticsNodes().isEmpty())
        assertRange()
        assertRow(HistoryKind.WORKOUT, HistoryPeriodTestHost.OLD_ID, "Earlier lifetime strength", LocalDate.of(2026, 9, 30).toEpochDay(), "59 min")
        capture("all-chronology")
        assertEquals("selection, layout and detail callback exploration writes no durable data", stored, inventory())
        assertEquals(2, routes.size)
        assertTrue(openedExercises.isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
open class HistoryPeriodTestHost {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    val dispatcher = UnconfinedTestDispatcher()
    lateinit var deps: FakeAppDependencies
    private var mountedModel by mutableStateOf<HistoryViewModel?>(null)
    var history: HistoryViewModel
        get() = checkNotNull(mountedModel)
        set(value) { mountedModel = value }
    var font by mutableStateOf(1f)
    var direction by mutableStateOf(LayoutDirection.Ltr)
    var profile = "history"
    val screen = "History"
    val runId = UUID.randomUUID().toString()
    val today = LocalDate.of(2026, 10, 15).toEpochDay()
    val routes = mutableListOf<Pair<HistoryKind, String>>()
    val openedExercises = mutableListOf<String>()
    var sheetOpen = false
    var holdProgress = false
    var failProgress = false
    var failRecords = false
    var failRequired = false
    var holdRecords = false
    var holdRequired = false
    val requiredStarted = CompletableDeferred<Unit>()
    val requiredBarrier = CompletableDeferred<Unit>()
    val recordsBarrier = CompletableDeferred<Unit>()
    val blockGate = ReadGate(false)
    val readBarrier = CompletableDeferred<Unit>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        readBarrier.complete(Unit)
        recordsBarrier.complete(Unit)
        requiredBarrier.complete(Unit)
        try { mountedModel?.let { runBlocking { it.clearAndJoinForTest() } } }
        finally {
            if (::deps.isInitialized) {
                deps.restTimerController.stop()
                dispatcher.scheduler.advanceUntilIdle()
                deps.close()
            }
            Dispatchers.resetMain()
        }
    }

    fun day(number: Int) = LocalDate.of(2026, 10, number).toEpochDay()
    fun graph(seed: Boolean = true, seedFinishedBlock: Boolean = true, seedBlockMover: Boolean = false) {
        val frozen = FrozenTime(stamp(today), "UTC")
        val snapshot = MuscleLoadCalculator.snapshot(emptyList(), HeatWindow.CURRENT_WEEK,
            nowMs = frozen.nowMillis(), time = frozen)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher, time = frozen,
            insights = MutableStateFlow(TrainingInsights(snapshot = snapshot)),
            workoutDaoDecorator = { base -> object : WorkoutDao by base {
                override suspend fun sessionSummaries(): List<SessionSummaryRow> {
                    if (holdRequired) {
                        requiredStarted.complete(Unit)
                        requiredBarrier.await()
                    }
                    if (failRequired) throw IllegalStateException("Synthetic required summary read failed")
                    return base.sessionSummaries()
                }
                override suspend fun finishedWorkingSetRecords(): List<RecordSetRow> {
                    if (holdRecords) recordsBarrier.await()
                    if (failRecords) throw IllegalStateException("Synthetic standing record read failed")
                    return base.finishedWorkingSetRecords()
                }
            } },
            trainingBlockDaoDecorator = { base -> FailingPastBlocksDao(base, blockGate) },
            activityDaoDecorator = { base -> object : ActivityDao by base {
                override suspend fun getAllGraphs(): List<ActivitySessionGraph> {
                    if (holdProgress) readBarrier.await()
                    if (failProgress) throw IllegalStateException("Synthetic full-log read failed")
                    return base.getAllGraphs()
                }
            } },
        )
        runBlocking {
            deps.preferencesRepository.setOnboardingComplete(true)
            deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
            if (seed) {
                for (number in 1..3) insertTestExercise(deps, "period-lift-$number", "Period lift $number")
                if (seedBlockMover) workout("block-opening", "Opening week strength",
                    LocalDate.of(2026, 9, 14).toEpochDay(), 90.0, 59, 1)
                workout(OLD_ID, "Earlier lifetime strength", LocalDate.of(2026, 9, 30).toEpochDay(), 120.0, 59, 1)
                workout(START_ID, "First day strength", day(1), 90.0, 59, 1)
                workout(CURRENT_ID, WORKOUT_TITLE, day(11), 100.0, 80, 3)
                val at = CapturedCivilTime(stamp(day(10)), "UTC", 0, day(10))
                val accepted = deps.confirmActivity(
                    ActivityDraft(
                        id = CURRENT_ID, status = ActivityStatus.COMPLETED, origin = ActivityOrigin.BACKDATED,
                        title = ACTIVITY_TITLE, performedStart = at,
                        performedEnd = at.copy(instantMillis = at.instantMillis + 120_000L),
                        blocks = listOf(CardioBlock(
                            id = "period-cardio", sortOrder = 0, type = CardioType.RUN, indoor = false,
                            elapsedSeconds = 120L, movingSeconds = 120L, distanceMeters = 700.0,
                            elevationMeters = null, heartRateBpm = null, energyKj = null,
                            rpe = null, routeRef = null,
                        )),
                    ), CapturedCivilTime(stamp(today), "UTC", 0, today),
                )
                assertTrue("synthetic captured-date activity is accepted: $accepted", accepted is ActivityWrite.Accepted)
                assertEquals(CURRENT_ID, (accepted as ActivityWrite.Accepted).session.id)
                if (seedFinishedBlock) {
                    val start = LocalDate.of(2026, 9, 14).toEpochDay()
                    deps.preferencesRepository.recordBodyweight(kg = 78.0, epochDay = start)
                    deps.preferencesRepository.recordBodyweight(kg = 80.0, epochDay = day(11))
                    deps.preferencesRepository.setTrainingBlock(TrainingBlock(startEpochDay = start, weeks = 4))
                    deps.preferencesRepository.beginBlock(
                        next = TrainingBlock(startEpochDay = today), todayEpochDay = today,
                    )
                }
            }
        }
        history = HistoryViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
    }

    private suspend fun workout(id: String, title: String, epochDay: Long, weight: Double, minutes: Int, lifts: Int) {
        val at = stamp(epochDay)
        val dao = deps.database.workoutDao()
        dao.upsertSession(WorkoutSessionEntity(id, null, title, at, "", minutes, at, at + minutes * 60_000L))
        dao.insertSessionExercises((1..lifts).map { number ->
            SessionExerciseEntity("period-se-$id-$number", id, "period-lift-$number", number - 1, 1, 5, weight, 90)
        })
        for (number in 1..lifts) dao.insertSet(
            SetLogEntity("period-set-$id-$number", id, "period-lift-$number", 1, weight, if (id == OLD_ID) 1 else 5, 8, false, at + number),
        )
    }
    private fun stamp(epochDay: Long) = LocalDate.ofEpochDay(epochDay).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    fun show(restoration: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, font),
                LocalLayoutDirection provides direction,
                LocalTodayEpochDay provides today,
                LocalWeightUnit provides WeightUnit.KG,
            ) {
                PersonalTrainerTheme(reduceMotion = true) {
                    HistoryScreen(
                        onOpenSession = { routes += HistoryKind.WORKOUT to it },
                        onOpenActivity = { routes += HistoryKind.ACTIVITY to it },
                        onOpenExercise = { openedExercises += it },
                        onOpenActiveSession = { throw AssertionError("read-only History must not start/resume") },
                        onOpenStartSheet = { throw AssertionError("read-only History must not start") },
                        viewModel = history,
                    )
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        drainLayout()
    }
    fun awaitLoaded(success: Boolean = true) {
        compose.awaitThat("actual selected History is loaded", { history.uiState.value }) {
            !history.uiState.value.isLoading && history.uiState.value.periodRange != null &&
                (!success || history.uiState.value.horizonProgress != null && !history.uiState.value.progressLoading)
        }
        assertFalse("required History is available", history.uiState.value.unavailable)
        drainLayout()
    }
    fun assertScoped(expected: Set<Pair<HistoryKind, String>>) {
        val state = history.uiState.value
        assertEquals(expected, state.summaries.map { it.kind to it.id }.toSet())
        assertEquals(expected, state.monthGroups.flatMap { it.entries }.map { it.kind to it.id }.toSet())
        assertEquals(expected.size, state.horizonTotals!!.sessionCount)
        assertEquals(expected, state.calendar.weeks.flatten().flatMap { cell ->
            cell.sessionIds.map { HistoryKind.WORKOUT to it } + cell.activityIds.map { HistoryKind.ACTIVITY to it }
        }.toSet())
        assertTrue(state.summaries.all { it.localEpochDay in state.periodRange!! })
    }
    fun assertRange() {
        val expected = DateCopy.periodRange(checkNotNull(history.uiState.value.periodRange))
        assertWords(reachTag(HistoryTags.RANGE_TITLE), expected)
        val spoken = readoutSpoken()
        assertTrue(spoken.contains(expected))
        assertTrue(spoken.contains("days") && spoken.contains("sets"))
        assertTrue(spoken.contains(HistoryCopy.activeDuration(history.uiState.value.horizonTotals!!.activeMinutes)))
    }
    fun readoutSpoken() = findTag(HistoryTags.READOUT).fetchSemanticsNode()
        .config[SemanticsProperties.ContentDescription].joinToString()
    fun tapDay(epochDay: Long) {
        val cell = reachTag(HistoryTags.day(epochDay)).assertIsEnabled()
        assertTarget(cell, Role.RadioButton)
        assertWords(reachTag(HistoryTags.date(epochDay), unmerged = true), LocalDate.ofEpochDay(epochDay).dayOfMonth.toString())
        assertTrue(cell.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
            .single().contains(DateCopy.weekdayFullDate(LocalDate.ofEpochDay(epochDay))))
        cell.performTouchInput { click(center) }
        compose.awaitThat("pointer selects the exact civil day", { history.uiState.value }) {
            history.uiState.value.horizon == AnalyticsHorizon.DAY &&
                history.uiState.value.selection.anchorEpochDay == epochDay
        }
        awaitLoaded()
    }
    fun assertRow(kind: HistoryKind, id: String, title: String, date: Long, duration: String) {
        val tag = HistoryTags.row(kind, id)
        findTag(tag)
        assertWords(wordsInside(tag, title), title)
        assertWords(wordsInside(tag, DateCopy.civilDate(date)), DateCopy.civilDate(date))
        assertWords(wordsInside(tag, duration), duration)
        val row = compose.onNode(hasTestTag(SessionLogTags.ROW) and hasAnyAncestor(hasTestTag(tag)))
        assertTarget(reach(row), Role.Button)
        assertTrue(row.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single().contains(duration))
    }
    fun assertLifetimeViews() {
        for ((tag, title) in listOf(HistoryTags.LIFETIME_RECORDS to HistoryCopy.LIFETIME_RECORDS,
            HistoryTags.LIFETIME_BLOCKS to HistoryCopy.LIFETIME_BLOCKS)) {
            assertWords(wordsInside(tag, title), title)
            assertTarget(reachTag(tag))
            reachTag(tag).performClick()
            sheetOpen = true
            drainLayout()
            assertWords(compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag(HistoryTags.SECONDARY_LIST)), useUnmergedTree = true), title)
            if (tag == HistoryTags.LIFETIME_RECORDS) {
                assertTrue("full log keeps older lifetime best", history.uiState.value.records.any { it.valueKg == 120.0 })
                assertWords(wordsInside(HistoryTags.SECONDARY_LIST, "Period lift 1"), "Period lift 1")
            } else assertFinishedBlock()
            capture(if (tag == HistoryTags.LIFETIME_RECORDS) "lifetime-records" else "lifetime-blocks")
            assertTarget(reachTag(HistoryTags.SECONDARY_CLOSE))
            reachTag(HistoryTags.SECONDARY_CLOSE).performClick()
            sheetOpen = false
            drainLayout()
        }
    }
    fun assertFinishedBlock(hasOpeningSession: Boolean = false) {
        val finished = history.uiState.value.pastBlocks.single()
        assertEquals(LocalDate.of(2026, 9, 14).toEpochDay(), finished.block.startEpochDay)
        assertEquals(day(12), finished.block.endExclusiveEpochDay)
        assertEquals(if (hasOpeningSession) 5 else 4, finished.review.sessions)
        assertEquals(if (hasOpeningSession) 5 else 4, finished.review.daysTrained)
        assertEquals(if (hasOpeningSession) 6 else 5, finished.review.workingSets)
        assertEquals(if (hasOpeningSession) 2520.0 else 2070.0, finished.review.work.volumeKg, .001)
        if (!hasOpeningSession) assertTrue("no comparable opening-week attempt invents a mover", finished.review.movers.isEmpty())
        assertWords(wordsInside(HistoryTags.SECONDARY_LIST,
            "4 weeks · 14 September 2026 – 11 October 2026"),
            "4 weeks · 14 September 2026 – 11 October 2026")
        for ((value, label) in listOf((if (hasOpeningSession) "5" else "4") to "days",
            (if (hasOpeningSession) "6" else "5") to "sets", (if (hasOpeningSession) "2520" else "2070") to "kg",
            finished.review.recordsBroken.toString() to "PRs")) {
            val metric = SemanticsMatcher("actual metric column for $label") { node ->
                node.children.any { child ->
                    child.config.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text == label
                }
            }
            assertWords(compose.onNode(hasText(value) and hasAnyAncestor(metric) and
                hasAnyAncestor(hasTestTag(HistoryTags.SECONDARY_LIST)), useUnmergedTree = true), value)
            assertWords(wordsInside(HistoryTags.SECONDARY_LIST, label), label)
        }
        assertWords(wordsInside(HistoryTags.SECONDARY_LIST,
            "Bodyweight · 78 kg → 80 kg · +2 kg"), "Bodyweight · 78 kg → 80 kg · +2 kg")
        assertTrue("verified populated block does not show successful empty copy",
            compose.onAllNodes(hasText("No completed blocks yet.")).fetchSemanticsNodes().isEmpty())
        assertTarget(reachTag(HistoryTags.SECONDARY_CLOSE))
    }
    fun wordsInside(tag: String, words: String): SemanticsNodeInteraction {
        findTag(tag)
        return findNode(matcher = hasText(words) and hasAnyAncestor(hasTestTag(tag)),
            description = "$tag: $words", unmerged = true)
    }
    fun evidence(name: String, assertion: () -> Unit) {
        profile = name
        try { assertion() } catch (failure: Throwable) {
            runCatching { capture("failed", failure.stackTraceToString()) }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }
    fun activeDecor(): View = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
        ?: compose.activity.window.decorView
    fun activeList() = compose.onNodeWithTag(if (sheetOpen) HistoryTags.SECONDARY_LIST else HistoryTags.LIST)

    companion object {
        const val CURRENT_ID = "same-id"
        const val START_ID = "start-boundary"
        const val OLD_ID = "outside-month"
        const val WORKOUT_TITLE = "Lower strength after a longer day"
        const val ACTIVITY_TITLE = "Captured morning run"
    }

    fun assertTarget(interaction: SemanticsNodeInteraction, role: Role? = null) {
        val node = interaction.fetchSemanticsNode()
        val density = node.layoutInfo.density
        assertEquals("real text scale reaches the screen", font, density.fontScale, .001f)
        assertTrue("real action width is at least 48 dp: ${node.config}", node.size.width / density.density >= 48f - .5f)
        assertTrue("real action height is at least 48 dp: ${node.config}", node.size.height / density.density >= 48f - .5f)
        assertTrue("real action has an offered click", node.config.getOrNull(SemanticsActions.OnClick)?.action != null)
        if (role != null) assertEquals(role, node.config.getOrNull(SemanticsProperties.Role))
        assertVisible(interaction)
    }

    fun assertWords(interaction: SemanticsNodeInteraction, words: String) {
        reach(interaction)
        val node = interaction.fetchSemanticsNode()
        assertTrue("$words has an actual measured text area", node.size.width > 0 && node.size.height > 0)
        val reported = interaction.textLayout()
        val input = reported.layoutInput
        // Compose 1.11 rebuilds simple Text semantics at the offered parent width.
        // Use its exact fonts/input at the actual node width, retaining reported
        // line breaks, and then require native ink within the real clip below.
        val measured = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, cacheSize = 0).measure(
            text = input.text, style = input.style, overflow = input.overflow,
            softWrap = input.softWrap, maxLines = input.maxLines, placeholders = input.placeholders,
            constraints = input.constraints.copy(minWidth = 0, maxWidth = node.size.width),
        )
        assertEquals("measured-width paragraph retains reported line count", reported.lineCount, measured.lineCount)
        repeat(measured.lineCount) { line ->
            assertEquals("reported line start", reported.getLineStart(line), measured.getLineStart(line))
            assertEquals("reported line end", reported.getLineEnd(line), measured.getLineEnd(line))
        }
        assertEquals("actual copy", words, measured.layoutInput.text.text)
        assertEquals("last character is laid out", words.length, measured.getLineEnd(measured.lineCount - 1, visibleEnd = true))
        val bitmap = drawWindow()
        try {
            val visible = node.boundsInWindow.intersect(unoccludedWindow(node,
                PeriodReachRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())))
            val origin = node.positionInWindow
            assertTrue("complete text height fits actual clip: $words", measured.size.height <= visible.height + 1f)
            repeat(measured.lineCount) { line ->
                assertFalse("$words line $line has no ellipsis", measured.isLineEllipsized(line))
                val glyphs = PeriodReachRect(origin.x + measured.getLineLeft(line), origin.y + measured.getLineTop(line),
                    origin.x + measured.getLineRight(line), origin.y + measured.getLineBottom(line))
                assertTrue("full line fits actual clip: $words / $glyphs / $visible",
                    glyphs.left >= visible.left - 1f && glyphs.right <= visible.right + 1f &&
                        glyphs.top >= visible.top - 1f && glyphs.bottom <= visible.bottom + 1f)
                assertTrue("native text ink exists on each line: $words", bitmap.count(glyphs.intersect(visible), input.style.color) > 0)
            }
            val last = measured.getBoundingBox(words.lastIndex)
            val finalGlyph = PeriodReachRect(origin.x + last.left, origin.y + last.top, origin.x + last.right, origin.y + last.bottom)
            assertTrue("native final glyph is visible: $words", bitmap.count(finalGlyph.intersect(visible), input.style.color) > 0)
        } finally { bitmap.recycle() }
    }

    fun reachTag(tag: String, unmerged: Boolean = false): SemanticsNodeInteraction =
        reach(findTag(tag, unmerged))

    /** Lazy content is discovered by bounded scrolling of its real screen list. */
    fun findTag(tag: String, unmerged: Boolean = false): SemanticsNodeInteraction =
        findNode(matcher = hasTestTag(tag), description = tag, unmerged = unmerged)

    fun findNode(matcher: SemanticsMatcher, description: String, unmerged: Boolean): SemanticsNodeInteraction {
        drainLayout()
        val target = compose.onNode(matcher = matcher, useUnmergedTree = unmerged)
        if (compose.onAllNodes(matcher = matcher, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()) return target
        val boards = listOf(activeList().fetchSemanticsNode())
        assertEquals("one real screen lazy content list", 1, boards.size)
        val board = boards.single()
        compose.runOnUiThread {
            checkNotNull(board.config[SemanticsActions.ScrollToIndex].action)(0)
        }
        drainLayout()
        repeat(30) {
            if (compose.onAllNodes(matcher = matcher, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()) return target
            val current = activeList().fetchSemanticsNode()
            val range = current.config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue("$description appears before the real content list end", range.value() < range.maxValue())
            val viewport = current.boundsInWindow.intersect(windowBounds())
            assertTrue("actual pinned chrome leaves a nonzero content viewport for $description: $viewport", viewport.height > 1f)
            compose.runOnUiThread { checkNotNull(current.config[SemanticsActions.ScrollBy].action)(0f, viewport.height * .75f) }
            drainLayout()
        }
        throw AssertionError("$profile $screen cannot compose $description in 30 bounded real scrolls")
    }

    /** Reveal through actual scroll ancestors; pinned chrome cannot be manufactured into a scroll. */
    fun reach(interaction: SemanticsNodeInteraction): SemanticsNodeInteraction {
        repeat(20) {
            val node = interaction.fetchSemanticsNode()
            val window = unoccludedWindow(node, windowBounds())
            val full = fullBounds(node)
            val clipped = node.boundsInWindow.intersect(window)
            if (clipped.width >= node.size.width - 1f && clipped.height >= node.size.height - 1f) return interaction.assertIsDisplayed()
            var ancestor = node.parent
            var moved = false
            while (ancestor != null && !moved) {
                val current = ancestor
                val action = current.config.getOrNull(SemanticsActions.ScrollBy)?.action
                if (action != null) {
                    val viewport = current.boundsInWindow.intersect(window)
                    // An offscreen inner canvas has no visible edge to scroll against.
                    // Reveal it through its outer ancestor before adjusting its own axis.
                    if (viewport.width <= 1f || viewport.height <= 1f) {
                        ancestor = current.parent
                        continue
                    }
                    for ((horizontal, range) in listOf(
                        true to current.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange),
                        false to current.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange),
                    )) {
                        if (range == null) continue
                        val low = if (horizontal) full.left else full.top
                        val high = if (horizontal) full.right else full.bottom
                        val start = if (horizontal) viewport.left else viewport.top
                        val end = if (horizontal) viewport.right else viewport.bottom
                        val delta = when { low < start - 1f -> low - start; high > end + 1f -> high - end; else -> 0f }
                        val reversed = range.reverseScrolling xor (horizontal && node.layoutInfo.layoutDirection == LayoutDirection.Rtl)
                        val amount = if (reversed) -delta else delta
                        if (amount < -1f && range.value() > 0f || amount > 1f && range.value() < range.maxValue()) {
                            moved = compose.runOnUiThread { action(if (horizontal) amount else 0f, if (horizontal) 0f else amount) }
                            if (moved) { drainLayout(); break }
                        }
                    }
                }
                ancestor = current.parent
            }
            if (!moved) throw AssertionError("$profile $screen cannot fully reveal $full clipped to $clipped: ${node.config}")
        }
        throw AssertionError("$profile $screen real scroll ancestors could not fully reveal target")
    }

    fun assertVisible(interaction: SemanticsNodeInteraction) {
        val node = interaction.fetchSemanticsNode()
        val visible = node.boundsInWindow.intersect(unoccludedWindow(node, windowBounds()))
        assertTrue("actual complete control is visible: ${node.config} / $visible / ${node.size}",
            visible.width >= node.size.width - 1f && visible.height >= node.size.height - 1f)
        interaction.assertIsDisplayed()
    }

    fun fullBounds(node: SemanticsNode): PeriodReachRect {
        val origin = node.positionInWindow
        return PeriodReachRect(origin.x, origin.y, origin.x + node.size.width, origin.y + node.size.height)
    }

    /** Opaque sticky headers occupy their measured padded container, beyond the text ink. */
    fun unoccludedWindow(node: SemanticsNode, window: PeriodReachRect): PeriodReachRect {
        var ancestor: SemanticsNode? = node
        var list: SemanticsNode? = null
        while (ancestor != null) {
            val tag = ancestor.config.getOrNull(SemanticsProperties.TestTag)
            if (tag?.startsWith(HistoryTags.MONTH_HEADER_PREFIX) == true) return window
            if (tag == HistoryTags.LIST) {
                list = ancestor
                break
            }
            ancestor = ancestor.parent
        }
        val viewport = list?.boundsInWindow?.intersect(window) ?: return window
        val headers = compose.onAllNodes(
            matcher = SemanticsMatcher(
                description = "rendered padded History month header",
                matcher = { node ->
                    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(HistoryTags.MONTH_HEADER_PREFIX) == true
                },
            ),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        val pinnedBottom = headers.mapNotNull { header ->
            val full = fullBounds(header)
            val visible = full.intersect(viewport)
            visible.bottom.takeIf {
                full.top <= viewport.top + 1f && visible.width > 1f && visible.height > 1f
            }
        }.maxOrNull() ?: return window
        return PeriodReachRect(window.left, maxOf(window.top, pinnedBottom), window.right, window.bottom)
    }

    fun windowBounds(): PeriodReachRect = compose.runOnUiThread {
        val view = activeDecor()
        PeriodReachRect(0f, 0f, view.width.toFloat(), view.height.toFloat())
    }

    /** Drain actual Compose 1.11 RootForTest traversal, with the normal Floor frame bound. */
    fun drainLayout() {
        repeat(3) {
            compose.settle()
            val pending = compose.runOnUiThread {
                val roots = mutableListOf<View>()
                fun visit(view: View) {
                    if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") roots += view
                    if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
                }
                visit(activeDecor())
                check(roots.isNotEmpty())
                roots.forEach { root -> root.javaClass.methods.single { it.name == "measureAndLayoutForTest" && it.parameterCount == 0 }.invoke(root) }
                roots.any { root -> root.javaClass.methods.single { it.name == "getHasPendingMeasureOrLayout" && it.parameterCount == 0 }.invoke(root) as Boolean }
            }
            if (!pending) return
        }
        throw AssertionError("$profile real Compose roots still have pending layout after 60 explicit frames")
    }

    fun drawWindow(): Bitmap = compose.runOnUiThread {
        val view = activeDecor()
        check(view.width > 1 && view.height > 1)
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }



    fun capture(stage: String, failure: String? = null) {
        if (failure == null) drainLayout()
        val bitmap = drawWindow()
        try {
            val directory = File("build/screen-renders/history-period/$runId/$profile")
            check(directory.isDirectory || directory.mkdirs())
            val png = directory.resolve("$stage.png")
            png.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("fresh actual native frame saved", png.length() > 1_000)
            val nodes = compose.onAllNodes(SemanticsMatcher("all actual semantics") { true }, useUnmergedTree = true)
                .fetchSemanticsNodes().joinToString("\n") {
                    "${it.config} size=${it.size} origin=${it.positionInWindow} clip=${it.boundsInWindow}"
                }
            directory.resolve("$stage.txt").writeText(
                "viewport=${bitmap.width}x${bitmap.height}\nfont=$font\ndirection=$direction\n" +
                    "selection=${history.uiState.value.selection}\nrange=${history.uiState.value.periodRange}\n" +
                    "unit=${history.uiState.value.unit}\nprogressLoading=${history.uiState.value.progressLoading}\n" +
                    "progressFailed=${history.uiState.value.progressFailed}\n" +
                    "blocksLoading=${history.uiState.value.blocksLoading}\nblocksUnavailable=${history.uiState.value.blocksUnavailable}\n" +
                    "blocksStale=${history.uiState.value.blocksStale}\npastBlocks=${history.uiState.value.pastBlocks}\nroutes=$routes\n" +
                    (failure?.let { "failure=$it\n" } ?: "") + nodes,
            )
        } finally { bitmap.recycle() }
    }

    fun inventory(): List<Any?> = runBlocking {
        listOf(
            deps.database.workoutDao().getAllSessions(), deps.database.workoutDao().getAllSessionExercises(),
            deps.database.workoutDao().getAllSets(), deps.database.activityDao().getAllGraphs(),
            deps.database.plannerDao().getRules(), deps.database.plannerDao().getAllOccurrences(),
            deps.database.plannerDao().getDecisions(), deps.database.plannerDao().getDeliveries(),
            deps.database.scheduleDao().getAll(), deps.database.routineDao().getAllRoutines(),
            deps.database.routineDao().getAllRoutineExercises(), deps.database.bodyweightDao().getAll(),
            deps.database.trainingBlockDao().getCurrent(),
            deps.database.trainingBlockDao().observePast().first(),
            deps.rawPreferenceValues(), deps.pendingOccurrenceId.value, deps.restTimerStore.current(),
        )
    }
}
