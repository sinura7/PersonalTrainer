package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentDialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.components.RestDurationSheet
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import kotlin.math.abs

/**
 * The rest-length sheet the idle rest card opens, composed on its own: the length it names,
 * the presets and Custom, the planned −15 / +15, Time set when the lift offers it, Start rest
 * when the host hands one in, and, when the phone asks for reduced motion, no slide (the owner's
 * decision of 24 September 2026; before it the sheet read the setting and slid up anyway).
 *
 * It was held as a slice of RestTimerUi.kt between `fun RestDurationSheet` and `fun
 * RestSweepRing` (`RestPresetChips(`, `CustomRestDialog(`, `workout-rest-sheet-minus`,
 * `onNudge(-RestTimer.NUDGE_SECONDS)`, `verticalScroll`). W1b gave every ±15 in the app one
 * set of words (RestNudgeCopy); this pair keeps its place, because it steps the planned length
 * before rest runs. What a lifter can pick here, and what each pick sends back, is what these
 * tests hold.
 *
 * Native graphics, because where the preset row ends is a matter of real text widths: at
 * 360 dp the five presets fill it, and Custom waits past its edge until the row is scrolled.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class RestDurationSheetRenderTest {
    @get:Rule val compose = createComposeRule()

    private val selected = mutableListOf<Int>()
    private val nudges = mutableListOf<Int>()
    private val typed = mutableListOf<String>()
    private var dismissed = 0
    private var timeSets = 0
    private var startedRest = 0

    /** The phone's reduce-motion setting as the sheet reads it; a test may change it while open. */
    private var phoneReducesMotion by mutableStateOf(false)

    private fun showSheet(
        selectedSeconds: Int = 120,
        offerSetClock: Boolean = false,
        offerStart: Boolean = false,
        fontScale: Float = 1f,
        reducedMotion: Boolean = false,
    ) {
        phoneReducesMotion = reducedMotion
        compose.showFloor(fontScale = fontScale) {
            CompositionLocalProvider(LocalReducedMotion provides phoneReducesMotion) {
                RestDurationSheet(
                    selectedSeconds = selectedSeconds,
                    onSelect = { selected += it },
                    onNudge = { nudges += it },
                    // As the ViewModel does: a length it can read is taken, anything else refused.
                    onCustomRest = { input -> typed += input; RestTimer.parseCustom(input) != null },
                    onDismiss = { dismissed += 1 },
                    offerSetClock = offerSetClock,
                    onTimeSet = { timeSets += 1 },
                    onStartRest = if (offerStart) startRest else null,
                )
            }
        }
    }

    private val startRest: () -> Unit = { startedRest += 1 }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun chip(label: String): SemanticsNodeInteraction = compose.onNode(hasText(label) and hasClickAction())

    /** The row of presets, which scrolls sideways; Custom is its last chip. */
    private fun presetRow(): SemanticsNodeInteraction = compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag(SHEET)))

    @Test
    fun theSheetNamesTheLengthAndOffersThePresetsAndCustom() {
        showSheet()
        compose.onNodeWithTag(SHEET).assertIsDisplayed()
        compose.onNodeWithText("Rest length").assertIsDisplayed()
        // The length is named twice: as the sheet's reading and as the chosen preset.
        compose.onAllNodesWithText("2:00").assertCountEquals(2)
        listOf("0:30", "1:00", "1:30").forEach { chip(it).assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin).assertIsOff() }
        chip("2:00").assertIsDisplayed().assertIsOn()
        // The row scrolls sideways to the last preset and Custom; at 360 dp they start past
        // its edge.
        presetRow().performScrollToNode(hasText("Custom"))
        chip("3:00").assertIsDisplayed().assertIsOff()
        chip("Custom").assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin).assertIsOff()
        // An overlay over the floor, in a window of its own, not a row pushed into the dock.
        assertTrue(compose.onAllNodes(isRoot()).fetchSemanticsNodes().size >= 2)
        chip("1:30").performClick()
        assertEquals(listOf(90), selected)
        assertEquals("the host closes the sheet on a pick", 0, dismissed)
    }

    @Test
    fun aLengthOffThePresetsIsShownOnTheCustomChip() {
        showSheet(selectedSeconds = 105)
        presetRow().performScrollToNode(hasText("1:45"))
        compose.onNode(hasText("1:45") and hasClickAction()).assertIsOn()
        compose.onAllNodesWithText("Custom").assertCountEquals(0)
        listOf("1:30", "2:00", "3:00").forEach { chip(it).assertIsOff() }
    }

    @Test
    fun customOpensADialogAndATypedLengthIsTakenAndClosesTheSheet() {
        showSheet()
        presetRow().performScrollToNode(hasText("Custom"))
        compose.holdingTheClock {
            chip("Custom").performSemanticsAction(SemanticsActions.OnClick)
            compose.settle()
            compose.onNodeWithText("Custom rest").assertExists()
            compose.onNodeWithText("Seconds (90) or mm:ss (1:30). 15 seconds to 30 minutes.").assertExists()
            compose.onNodeWithTag(SHEET).assertDoesNotExist()
            // Set is a thumb's height, not the text button's default 40 dp.
            compose.onNode(hasText("Set") and hasClickAction()).assertHeightIsAtLeast(Metrics.touchMin)
            compose.onNode(hasSetTextAction()).performTextReplacement("1:45")
            compose.settle()
            compose.onNodeWithText("Set").performSemanticsAction(SemanticsActions.OnClick)
            compose.settle()
        }
        assertEquals(listOf("1:45"), typed)
        assertEquals("a taken length closes the sheet", 1, dismissed)
        compose.onAllNodesWithText("Custom rest").assertCountEquals(0)
    }

    @Test
    fun anUnreadableLengthKeepsTheDialogOpenAndSaysHowToWriteIt() {
        showSheet()
        presetRow().performScrollToNode(hasText("Custom"))
        compose.holdingTheClock {
            chip("Custom").performSemanticsAction(SemanticsActions.OnClick)
            compose.settle()
            compose.onNode(hasSetTextAction()).performTextReplacement("1.5")
            compose.settle()
            compose.onNodeWithText("Set").performSemanticsAction(SemanticsActions.OnClick)
            compose.settle()
            compose.onNodeWithText("Use 90 or 1:30.").assertExists()
            compose.onNodeWithText("Custom rest").assertExists()
            assertEquals(0, dismissed)
            // Cancel goes back to the sheet, which still offers the presets.
            compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick)
            compose.settle()
        }
        assertEquals(listOf("1.5"), typed)
        assertEquals(0, dismissed)
        compose.onNodeWithTag(SHEET).assertIsDisplayed()
        chip("2:00").assertIsOn()
    }

    @Test
    fun thePlannedLengthStepsByFifteenSeconds() {
        showSheet()
        // The running rest's −15 and +15, in its words, stepping the length before rest runs.
        val minus = compose.onNodeWithTag("workout-rest-sheet-minus")
            .assert(isButton)
            .assertHeightIsAtLeast(Metrics.touchMin)
            .assertWidthIsAtLeast(Metrics.touchMin)
        val plus = compose.onNodeWithTag("workout-rest-sheet-plus")
            .assert(isButton)
            .assertHeightIsAtLeast(Metrics.touchMin)
            .assertWidthIsAtLeast(Metrics.touchMin)
        assertTrue("−15 sits left of +15", minus.getBoundsInRoot().right <= plus.getBoundsInRoot().left)
        assertEquals(listOf("−15"), minus.mergedTexts())
        assertEquals(listOf("Minus 15 seconds"), minus.spokenDescriptions())
        assertEquals(listOf("+15"), plus.mergedTexts())
        assertEquals(listOf("Plus 15 seconds"), plus.spokenDescriptions())
        minus.performClick()
        plus.performClick()
        assertEquals(listOf(-15, 15), nudges)
        assertEquals("a nudge keeps the sheet open", 0, dismissed)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun backClosesTheSheetWithoutPickingALength() {
        showSheet()
        compose.onNodeWithTag(SHEET).assertIsDisplayed()
        // The sheet is a window of its own, so system Back goes to it, not to the floor beneath.
        val sheetWindow = ShadowDialog.getLatestDialog() as ComponentDialog
        compose.runOnUiThread { sheetWindow.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals("Back asks the host to close the sheet", 1, dismissed)
        assertTrue("Back is not a pick", selected.isEmpty() && nudges.isEmpty())
    }

    @Test
    fun timeSetIsInTheSheetOnlyWhenTheLiftOffersIt() {
        showSheet(offerSetClock = true)
        val timeSet = compose.onNodeWithTag(SHEET_TIME_SET).assertIsDisplayed().assert(isButton).assertHeightIsAtLeast(Metrics.touchMin)
        assertEquals(listOf("Time this set"), timeSet.spokenDescriptions())
        compose.onNodeWithText("Time set", useUnmergedTree = true).assertIsDisplayed()
        timeSet.performClick()
        assertEquals(1, timeSets)
        assertTrue("Time set is not a length", selected.isEmpty() && nudges.isEmpty())
    }

    @Test
    fun withoutTheOfferThereIsNoTimeSetAndNoStartRest() {
        showSheet()
        compose.onNodeWithTag(SHEET_TIME_SET).assertDoesNotExist()
        compose.onAllNodesWithText("Time set", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Start rest").assertCountEquals(0)
    }

    @Test
    fun startRestIsInTheSheetWhenTheHostOffersIt() {
        showSheet(offerStart = true)
        compose.onNode(hasText("Start rest") and hasClickAction()).assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin).performClick()
        assertEquals(1, startedRest)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun atLargeTextTheSheetScrollsToItsLastControl() {
        showSheet(offerSetClock = true, offerStart = true, fontScale = LogLoopScale.STACK_WELLS_FROM)
        compose.onNodeWithTag(SHEET).assert(hasScrollAction())
        compose.onNodeWithTag(SHEET_TIME_SET).performScrollTo().assertIsDisplayed()
        // Every preset is still a chip a thumb can reach, the row scrolled if need be.
        presetRow().performScrollToNode(hasText("Custom"))
        chip("Custom").assertIsDisplayed()
    }

    @Test
    fun underReducedMotionTheSheetIsInPlaceAsSoonAsItIsDrawn() {
        val path = sheetTopWhileItOpens(reducedMotion = true)
        // No slide: from its first frame the sheet already stands where it comes to rest.
        assertEquals("the sheet's top before any frame, against where it settles", path.settled, path.start, EDGE_SLACK_DP)
        assertEquals("the sheet's top a few frames in, against where it settles", path.settled, path.opening, EDGE_SLACK_DP)
    }

    @Test
    fun withMotionTheSheetStillSlidesUpFromTheBottom() {
        val path = sheetTopWhileItOpens(reducedMotion = false)
        // The control: with the phone's motion on, the sheet keeps its slide, so a few frames in
        // it has left where it started and is still on its way up to where it settles.
        assertTrue("it starts below where it settles: $path", path.start > path.settled + EDGE_SLACK_DP)
        assertTrue("a few frames in it is on its way, not there yet: $path", path.opening > path.settled + EDGE_SLACK_DP)
        assertTrue("a few frames in it has left where it started: $path", path.opening < path.start - EDGE_SLACK_DP)
    }

    // Opening in place must not cost the ways out. The state that starts Expanded is built by
    // hand (Motion.rememberFullSheetState), so each way Material closes a sheet is tried on it:
    // the audit W2a review broke that state three ways (refusing Hidden, thresholds no drag
    // could pass, and no Hidden state at all, which throws on Back) and no test noticed.

    @Test
    fun underReducedMotionASwipeDownClosesTheSheetAndAShortDragDoesNot() {
        showSheet(reducedMotion = true)
        val settled = sheetTop()
        compose.onNodeWithTag(SHEET).performTouchInput { swipeDown(startY = top + 20f, endY = top + 60f, durationMillis = 600L) }
        compose.waitForIdle()
        assertEquals("a short drag leaves the sheet open", 0, dismissed)
        assertEquals("and springs back to where it stood", settled, sheetTop(), EDGE_SLACK_DP)
        compose.onNodeWithTag(SHEET).performTouchInput { swipeDown(startY = top + 20f, endY = bottom, durationMillis = 250L) }
        compose.waitForIdle()
        assertEquals("a swipe down asks the host to close the sheet", 1, dismissed)
        assertTrue("a swipe is not a pick", selected.isEmpty() && nudges.isEmpty())
    }

    @Test
    fun underReducedMotionATapOutsideClosesTheSheet() {
        showSheet(reducedMotion = true)
        // The scrim fills the sheet's window above the sheet: a tap near its top is outside.
        compose.onNode(isRoot() and hasAnyDescendant(hasTestTag(SHEET))).performTouchInput { click(Offset(centerX, 40f)) }
        compose.waitForIdle()
        assertEquals("a tap outside asks the host to close the sheet", 1, dismissed)
        assertTrue("a tap outside is not a pick", selected.isEmpty() && nudges.isEmpty())
    }

    @Test
    fun underReducedMotionBackClosesTheSheet() {
        showSheet(reducedMotion = true)
        val sheetWindow = ShadowDialog.getLatestDialog() as ComponentDialog
        compose.runOnUiThread { sheetWindow.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals("Back asks the host to close the sheet", 1, dismissed)
    }

    @Test
    fun turningReducedMotionOffWhileTheSheetIsOpenLeavesItWhereItIs() {
        showSheet(reducedMotion = true)
        val settled = sheetTop()
        // The phone's setting is read again on resume. The open sheet keeps the state it
        // opened with; before the review it swapped to a new one, dropped out of sight and
        // slid back up over some twenty frames.
        val path: List<Float>
        compose.mainClock.autoAdvance = false
        try {
            phoneReducesMotion = false
            Snapshot.sendApplyNotifications()
            path = List(SETTING_CHANGE_FRAMES) {
                compose.mainClock.advanceTimeByFrame()
                sheetTop()
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        assertTrue("the sheet holds its place, frame by frame, at $settled dp: $path", path.all { abs(it - settled) <= EDGE_SLACK_DP })
        assertEquals("and stays open", 0, dismissed)
    }

    /** Where the sheet's top edge is, in dp from the top of its window, as it opens. */
    private data class SheetPath(val start: Float, val opening: Float, val settled: Float)

    /**
     * Opens the sheet with the clock held and reads its top edge in its own window: before any
     * frame runs, after [OPENING_FRAMES] frames, and once it has finished. The sheet is a window
     * of its own, which a capture of the floor's window cannot see, so its place is read from its
     * semantics bounds, not its pixels. On trunk's timing the slide begins on the third frame and
     * takes some seventeen to settle.
     */
    private fun sheetTopWhileItOpens(reducedMotion: Boolean): SheetPath {
        val start: Float
        val opening: Float
        compose.mainClock.autoAdvance = false
        try {
            showSheet(reducedMotion = reducedMotion)
            Snapshot.sendApplyNotifications()
            start = sheetTop()
            repeat(OPENING_FRAMES) { compose.mainClock.advanceTimeByFrame() }
            opening = sheetTop()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        return SheetPath(start = start, opening = opening, settled = sheetTop())
    }

    private fun sheetTop(): Float = compose.onNodeWithTag(SHEET).getBoundsInRoot().top.value

    private companion object {
        const val SHEET = "workout-rest-duration-sheet"
        const val SHEET_TIME_SET = "workout-sheet-start-set-clock"

        /** Two frames for the sheet to measure and start, the animation's first, and one to lay it out. */
        const val OPENING_FRAMES = 4

        /** Longer than the slide a swapped sheet state would make (about twenty frames). */
        const val SETTING_CHANGE_FRAMES = 30

        /** Half a dp: an edge read twice where it settles is the same edge. */
        const val EDGE_SLACK_DP = 0.5f
    }
}
