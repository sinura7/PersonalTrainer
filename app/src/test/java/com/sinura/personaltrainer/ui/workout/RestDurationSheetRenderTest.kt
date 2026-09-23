package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentDialog
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
import androidx.compose.ui.test.hasAnyAncestor
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
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.components.RestDurationSheet
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

/**
 * The rest-length sheet the idle rest card opens, composed on its own: the length it names,
 * the presets and Custom, the planned −15 / +15, Time set when the lift offers it, and Start
 * rest when the host hands one in.
 *
 * It was held as a slice of RestTimerUi.kt between `fun RestDurationSheet` and `fun
 * RestSweepRing` (`RestPresetChips(`, `CustomRestDialog(`, `workout-rest-sheet-minus`,
 * `onNudge(-RestTimer.NUDGE_SECONDS)`, `verticalScroll`). W1b shares one ±15 control set
 * between the dock and the full rest screen, which is where this sheet's pair goes, and the
 * slice would have thrown rather than said what moved. What a lifter can pick here, and what
 * each pick sends back, is what these tests hold.
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

    private fun showSheet(
        selectedSeconds: Int = 120,
        offerSetClock: Boolean = false,
        offerStart: Boolean = false,
        fontScale: Float = 1f,
    ) {
        compose.showFloor(fontScale = fontScale) {
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
        // W1b changes this: one ±15 set is shared by the dock and the full rest screen, and
        // this pair is the one that moves; today the sheet carries its own.
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

    private companion object {
        const val SHEET = "workout-rest-duration-sheet"
        const val SHEET_TIME_SET = "workout-sheet-start-set-clock"
    }
}
