package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.RestBatteryCopy
import com.sinura.personaltrainer.domain.RestHonestyCopy
import com.sinura.personaltrainer.domain.RestNotificationCopy
import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The dock's clock slot, composed on its own: which clock takes it (the rest card at rest,
 * running or just finished; the hold or set clock while one runs), the rest-length sheet the
 * idle card opens and what each of its picks sends out, the compact clock's words beside a
 * companion, the landscape link, and the timer honesty that outranks the card.
 *
 * These were held as lines of WorkoutDock.kt: `-> SetWorkDock(` before `-> RestTimerCard(`,
 * `onEditDuration = { durationSheet = true }`, `onNudge = events.onNudgeRest`, `onStop =
 * events.onStopSetClock.takeIf { timer.stopwatchRunning && !holdActive }`, the `"Rest …"` /
 * `"Hold …"` / `"Timers"` label lines and the `error / undo / Cancel edit / honesty /
 * caption` order. W1b rewires the rest controls (one ±15 set for the dock and the rest page;
 * "Planned rest · 1:30"), so each wire is held here as a tap and the callback it reaches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class WorkoutDockTimerRenderTest {
    @get:Rule val compose = createComposeRule()

    private var skips = 0
    private var restStarts = 0
    private val durations = mutableListOf<Int>()
    private val nudges = mutableListOf<Int>()
    private val customs = mutableListOf<String>()
    private var clockStarts = 0
    private var clockStops = 0
    private var batteryDismissals = 0
    private var restPages = 0
    private var notificationFixes = 0

    private val logSet = floorPrimaryAction(kind = WorkoutPrimaryKind.LOG_SET, draft = ActiveExerciseDraft(weightKg = FLOOR_KG70, reps = 10))

    private var dockState by mutableStateOf(floorDockState(action = logSet))

    private fun showDock(timer: WorkoutDockTimer, error: String? = null) {
        dockState = floorDockState(action = logSet, error = error, timer = timer)
        val events = floorDockEvents(
            onOpenRest = { restPages += 1 },
            onSkipRest = { skips += 1 },
            onStartRest = { restStarts += 1 },
            onSelectRestDuration = { durations += it },
            onNudgeRest = { nudges += it },
            onCustomRest = { input -> customs += input; true },
            onStartSetClock = { clockStarts += 1 },
            onStopSetClock = { clockStops += 1 },
            onDismissRestBatteryHint = { batteryDismissals += 1 },
            onOpenNotifications = { notificationFixes += 1 },
        )
        compose.showFloor { WorkoutDock(state = dockState, events = events) }
    }

    private fun restAt(total: Int = 120) = WorkoutDockTimer(show = true, restTotalSeconds = total)

    private fun restRunning(remaining: Int = 92) =
        WorkoutDockTimer(show = true, restRemainingSeconds = remaining, restTotalSeconds = 120, restRunning = true)

    private fun cardTile(cardTag: String, clock: String) =
        compose.onNode(hasClickAction() and hasText(clock) and hasAnyAncestor(hasTestTag(cardTag)))

    private fun sheetIsOpen(): Boolean = compose.onAllNodes(hasTestTag(SHEET)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun atRestTheSlotIsTheRestCardAndATapOnItOpensTheLengthSheet() {
        showDock(restAt())
        compose.onNodeWithTag(WorkoutTestTags.TIMER_ROW).assertHeightIsAtLeast(Metrics.logTimerRow)
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.HOLD_CLOCK).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertDoesNotExist()
        assertTrue(!sheetIsOpen())
        cardTile(WorkoutTestTags.REST_IDLE, clock = "2:00").performClick()
        compose.onNodeWithTag(SHEET).assertIsDisplayed()
        // A preset names the next rest and closes the sheet; it does not start the clock.
        compose.onNode(hasText("1:30") and hasClickAction()).performClick()
        assertEquals(listOf(90), durations)
        assertEquals(0, restStarts)
        compose.waitForIdle()
        assertTrue("a pick closes the sheet", !sheetIsOpen())
    }

    @Test
    fun theSheetsStepsCustomLengthAndStartRestReachTheDock() {
        showDock(restAt())
        cardTile(WorkoutTestTags.REST_IDLE, clock = "2:00").performClick()
        // W1b changes this: the sheet's own ±15 pair gives way to the one set shared with the
        // rest page; today it writes the planned length through the dock's nudge.
        compose.onNodeWithTag("workout-rest-sheet-minus").performClick()
        compose.onNodeWithTag("workout-rest-sheet-plus").performClick()
        assertEquals(listOf(-15, 15), nudges)
        assertTrue("a step keeps the sheet open", sheetIsOpen())
        compose.onNode(hasText("Start rest") and hasClickAction() and hasAnyAncestor(hasTestTag(SHEET))).performClick()
        assertEquals(1, restStarts)
        compose.waitForIdle()
        assertTrue("Start rest closes the sheet", !sheetIsOpen())

        cardTile(WorkoutTestTags.REST_IDLE, clock = "2:00").performClick()
        compose.onNodeWithTag(SHEET).assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag(SHEET))).performScrollToNode(hasText("Custom"))
        compose.holdingTheClock {
            compose.onNode(hasText("Custom") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
            compose.settle()
            compose.onNode(hasSetTextAction()).performTextReplacement("1:45")
            compose.settle()
            compose.onNodeWithText("Set").performSemanticsAction(SemanticsActions.OnClick)
            compose.settle()
        }
        assertEquals(listOf("1:45"), customs)
        assertTrue("a taken custom length closes the sheet", !sheetIsOpen())
    }

    @Test
    fun timeSetStartsTheSetClockFromTheCardAndFromTheSheet() {
        showDock(restAt().copy(offerSetClock = true))
        compose.onNodeWithTag(WorkoutTestTags.START_SET_CLOCK).performClick()
        assertEquals(1, clockStarts)
        cardTile(WorkoutTestTags.REST_IDLE, clock = "2:00").performClick()
        compose.onNodeWithTag("workout-sheet-start-set-clock").performClick()
        assertEquals(2, clockStarts)
        assertEquals("Time set is not rest", 0, restStarts)
        compose.waitForIdle()
        assertTrue("Time set closes the sheet", !sheetIsOpen())
    }

    @Test
    fun withoutTheOfferNeitherTheCardNorTheSheetOffersTimeSet() {
        showDock(restAt())
        compose.onNodeWithTag(WorkoutTestTags.START_SET_CLOCK).assertDoesNotExist()
        cardTile(WorkoutTestTags.REST_IDLE, clock = "2:00").performClick()
        compose.onNodeWithTag("workout-sheet-start-set-clock").assertDoesNotExist()
    }

    @Test
    fun atRestStartRestOnTheCardStartsRest() {
        showDock(restAt())
        compose.onNodeWithTag(WorkoutTestTags.START_REST).performClick()
        assertEquals(1, restStarts)
        assertTrue(!sheetIsOpen())
    }

    @Test
    fun runningTheCardsStepsSkipAndTapReachTheDock() {
        showDock(restRunning())
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertDoesNotExist()
        // W1b changes this: the running ±15 become the one set shared with the rest page.
        compose.onNodeWithTag(WorkoutTestTags.REST_MINUS).performClick()
        compose.onNodeWithTag(WorkoutTestTags.REST_PLUS).performClick()
        assertEquals(listOf(-15, 15), nudges)
        compose.onNodeWithTag(WorkoutTestTags.REST_SKIP).performClick()
        assertEquals(1, skips)
        cardTile(WorkoutTestTags.REST_BAR, clock = "1:32").performClick()
        assertEquals(1, restPages)
        assertTrue("a running tap opens the rest page, not the length sheet", !sheetIsOpen())
    }

    @Test
    fun aFinishedRestFlashesOnTheCardInTheSlot() {
        showDock(restAt().copy(restCompletedTimerId = "rest-1"))
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertIsDisplayed()
        compose.onNode(hasText("BACK TO THE BAR"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aRunningHoldTakesTheSlotAndItsClockHasNoStop() {
        // Stop belongs to the set stopwatch, never to a hold, even if both flags were up.
        showDock(
            WorkoutDockTimer(
                show = true,
                restTotalSeconds = 120,
                holdRunning = true,
                holdElapsedSeconds = 5,
                holdRemainingSeconds = 25,
                holdTotalSeconds = 30,
                stopwatchRunning = true,
            ),
        )
        compose.onNodeWithTag(WorkoutTestTags.HOLD_CLOCK).assertIsDisplayed()
        compose.onNode(hasText("HOLD"), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("0:25"), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(STOP).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertDoesNotExist()
    }

    @Test
    fun aRunningHoldNeverCountsFromAboveItsTarget() {
        // The dock hands the hold's target to the clock, which caps the countdown at it: a
        // remaining time past the target (a stale tick after the target changed) reads 0:30.
        showDock(
            WorkoutDockTimer(
                show = true,
                restTotalSeconds = 120,
                holdRunning = true,
                holdElapsedSeconds = 0,
                holdRemainingSeconds = 35,
                holdTotalSeconds = 30,
            ),
        )
        compose.onNode(hasText("0:30"), useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("0:35", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theSetStopwatchTakesTheSlotAndStopsFromIt() {
        showDock(WorkoutDockTimer(show = true, restTotalSeconds = 120, stopwatchRunning = true, stopwatchElapsedSeconds = 12))
        compose.onNodeWithTag(WorkoutTestTags.HOLD_CLOCK).assertIsDisplayed()
        compose.onNode(hasText("SET TIME"), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("0:12"), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertDoesNotExist()
        compose.onNodeWithTag(STOP).performClick()
        assertEquals(1, clockStops)
    }

    @Test
    fun beforeTheFirstLiftTheSlotHoldsNoClock() {
        showDock(WorkoutDockTimer(show = false, restTotalSeconds = 120))
        compose.onNodeWithTag(WorkoutTestTags.TIMER_ROW).assertHeightIsAtLeast(Metrics.logTimerRow)
        listOf(WorkoutTestTags.REST_IDLE, WorkoutTestTags.REST_BAR, WorkoutTestTags.HOLD_CLOCK, WorkoutTestTags.COMPANION_CLOCK).forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
    }

    @Test
    fun besideACompanionTheCompactClockNamesWhatIsRunning() {
        showDock(restRunning(), error = "Could not save")
        val clock = compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK)
        clock.assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin).assertTextEquals("Rest 1:32")
        dockState = dockState.copy(timer = WorkoutDockTimer(show = true, holdRunning = true, holdElapsedSeconds = 12, holdRemainingSeconds = 18, holdTotalSeconds = 30))
        clock.assertTextEquals("Hold 0:12")
        dockState = dockState.copy(timer = WorkoutDockTimer(show = true, stopwatchRunning = true, stopwatchElapsedSeconds = 12))
        clock.assertTextEquals("Set time 0:12")
        dockState = dockState.copy(timer = restAt())
        clock.assertTextEquals("Timers")
    }

    @Test
    fun theCompactClockOpensTheRunningRestOrTheLengthSheet() {
        showDock(restRunning(), error = "Could not save")
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).performClick()
        assertEquals(1, restPages)
        dockState = dockState.copy(timer = restAt())
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).performClick()
        compose.onNodeWithTag(SHEET).assertIsDisplayed()
    }

    @Test
    fun theCompactClockOfTheSetStopwatchOffersToStopTiming() {
        showDock(WorkoutDockTimer(show = true, stopwatchRunning = true, stopwatchElapsedSeconds = 12), error = "Could not save")
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).performClick()
        compose.onNodeWithText("Keep timing").assertIsDisplayed()
        compose.onNodeWithText("Stop timing").performClick()
        assertEquals(1, clockStops)
    }

    @Test
    fun keepTimingClosesTheDialogAndLeavesTheClockRunning() {
        showDock(WorkoutDockTimer(show = true, stopwatchRunning = true, stopwatchElapsedSeconds = 12), error = "Could not save")
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).performClick()
        compose.onNodeWithText("Keep timing").performClick()
        assertEquals("Keep timing does not stop the set clock", 0, clockStops)
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test
    fun theTimingDialogGoesAwayWhenTheStopwatchStops() {
        showDock(WorkoutDockTimer(show = true, stopwatchRunning = true, stopwatchElapsedSeconds = 12), error = "Could not save")
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).performClick()
        compose.onNodeWithText("Stop timing").assertIsDisplayed()
        // Stopped from elsewhere (the bar, the notification): nothing is left timing, so the
        // dialog does not linger as a "Return to workout" with nothing to return from.
        dockState = dockState.copy(timer = restAt())
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onAllNodesWithText("Return to workout").assertCountEquals(0)
        assertEquals(0, clockStops)
    }

    @Test
    fun theLengthSheetClosesWhenRestStartsRunning() {
        showDock(restAt())
        cardTile(WorkoutTestTags.REST_IDLE, clock = "2:00").performClick()
        compose.onNodeWithTag(SHEET).assertIsDisplayed()
        // Rest started from elsewhere (the rest page, the notification): the planned length
        // is no longer the thing to edit, so the sheet stands down.
        dockState = dockState.copy(timer = restRunning())
        compose.waitForIdle()
        assertTrue("a running rest closes the length sheet", !sheetIsOpen())
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertIsDisplayed()
    }

    @Test
    fun theCompactClockOfAHoldOnlyReturnsToTheWorkout() {
        showDock(WorkoutDockTimer(show = true, holdRunning = true, holdElapsedSeconds = 12, holdRemainingSeconds = 18, holdTotalSeconds = 30), error = "Could not save")
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).performClick()
        compose.onAllNodesWithText("Stop timing").assertCountEquals(0)
        compose.onNodeWithText("Return to workout").performClick()
        assertEquals(0, clockStops)
    }

    @Test
    fun inLandscapeIdleRestHidesBehindTimerControls() {
        showDock(restAt().copy(hideIdleRest = true))
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(Metrics.touchMin)
            .assertTextEquals("Timer controls ›")
            .performClick()
        compose.onNodeWithTag(SHEET).assertIsDisplayed()
        // A running rest is shown whatever the orientation.
        dockState = dockState.copy(timer = restRunning().copy(hideIdleRest = true))
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertIsDisplayed()
    }

    @Test
    fun theFirstRestNamesTheBatteryRuleBesideItsClock() {
        showDock(restRunning().copy(batteryHint = true))
        compose.onNodeWithTag("workout-rest-battery").assertIsDisplayed()
        compose.onNodeWithText(RestBatteryCopy.SENTENCE).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertTextEquals("Rest 1:32")
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertDoesNotExist()
        compose.onNode(hasText(RestBatteryCopy.GOT_IT) and hasClickAction()).assertHeightIsAtLeast(Metrics.touchMin).performClick()
        assertEquals(1, batteryDismissals)
    }

    @Test
    fun withRestAlertsOffTheDockSaysSoAndOffersTheFix() {
        showDock(restAt().copy(notificationsEnabled = false))
        compose.onNodeWithTag("workout-notif-recovery").assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin)
        compose.onNodeWithText(RestNotificationCopy.RECOVERY_TITLE, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(RestNotificationCopy.RECOVERY_ACTION, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertTextEquals("Timers")
        compose.onNodeWithTag("workout-notif-recovery").performClick()
        assertEquals(1, notificationFixes)
    }

    @Test
    fun aRestThatMayNotSurviveLeavingTheAppSaysSo() {
        showDock(restRunning().copy(persistenceHealthy = false))
        compose.onNodeWithTag("workout-rest-honesty").assertIsDisplayed()
        compose.onNodeWithText(RestHonestyCopy.PERSISTENCE).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertTextEquals("Rest 1:32")
    }

    @Test
    fun theExactAlarmCaveatStaysOffTheFloor() {
        // The rest page says an alarm may be late; the floor's dock never does.
        showDock(restRunning().copy(exactBestEffort = true))
        compose.onAllNodesWithText(RestHonestyCopy.EXACT_DENIED).assertCountEquals(0)
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertIsDisplayed()
    }

    @Test
    fun theCompanionSlotGoesToTheMostUrgentThing() {
        showDock(restAt().copy(notificationsEnabled = false), error = "Could not save")
        dockState = dockState.copy(undoMessage = "Set deleted", undoKey = "Set deleted", editing = true, suggestionUnavailable = true)
        compose.waitForIdle()
        // An error first, then an undo offer, then Cancel edit, then timer honesty, then the
        // coach's caption; the compact clock stays beside each.
        compose.onNodeWithTag(WorkoutTestTags.ERROR_DETAILS).assertIsDisplayed()
        assertOnly(WorkoutTestTags.ERROR_DETAILS)
        dockState = dockState.copy(error = null)
        compose.onNodeWithText("Set deleted", substring = true).assertIsDisplayed()
        assertOnly(null)
        dockState = dockState.copy(undoMessage = null, undoKey = null)
        compose.onNodeWithTag(WorkoutTestTags.CANCEL_EDIT).assertIsDisplayed()
        assertOnly(WorkoutTestTags.CANCEL_EDIT)
        dockState = dockState.copy(editing = false)
        compose.onNodeWithTag("workout-notif-recovery").assertIsDisplayed()
        assertOnly("workout-notif-recovery")
        dockState = dockState.copy(timer = restAt())
        compose.onNodeWithText(LogCommitCopy.SUGGESTION_UNAVAILABLE).assertIsDisplayed()
        assertOnly(null)
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertIsDisplayed()
    }

    /** Of the companions that each carry a tag, only [tag] is up (none when null). */
    private fun assertOnly(tag: String?) {
        listOf(WorkoutTestTags.ERROR_DETAILS, WorkoutTestTags.CANCEL_EDIT, "workout-notif-recovery")
            .filter { it != tag }
            .forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertDoesNotExist()
    }

    private companion object {
        const val SHEET = "workout-rest-duration-sheet"
        const val STOP = "workout-stop-set-clock"
    }
}
