package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The current exercise's identity, composed for real: what it says, what a tap does, how
 * big its targets are, and how it reshapes for large text.
 *
 * Since W1a (design audit D09) the header has two visible controls and nothing hidden: the
 * "Lift n of N ⌄" pill opens the switcher, and the picture opens Details. The whole identity
 * used to be an unmarked switch button with Details inside its tap area, read to TalkBack
 * as one long sentence and marked selected. Each fact here is what the lifter or TalkBack
 * gets, so a rebuild that keeps those facts passes and one that loses them fails.
 *
 * Native graphics, as in WorkoutFloorRenderTest: the still's size depends on how many
 * lines the name really takes, and the legacy renderer measures text at almost no width.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class ExerciseHeaderRenderTest {
    @get:Rule val compose = createComposeRule()

    private var switched = 0
    private var details = 0
    private val warmups = mutableListOf<Boolean>()

    private fun showHeader(
        lift: SessionExercise = floorLift(targetSets = 3),
        draftWarmup: Boolean = false,
        enabled: Boolean = true,
        fontScale: Float = 1f,
        number: Int = 1,
        total: Int = 2,
        setContext: String = SET_CONTEXT,
    ) {
        compose.showFloor(fontScale = fontScale) {
            ExerciseHeader(
                lift = lift,
                number = number,
                total = total,
                setContext = setContext,
                draftWarmup = draftWarmup,
                onWarmup = { warmups += it },
                onOpenSwitcher = { switched += 1 },
                onDetails = { details += 1 },
                enabled = enabled,
            )
        }
    }

    private fun identity() = compose.onNodeWithTag(WorkoutTestTags.liftCard("leg-ext"))

    @Test
    fun theVisibleSwitchNamesTheLiftsPlaceAndOpensTheSwitcher() {
        showHeader()
        // D09: the switch is a control a new lifter can see, not an unmarked whole identity.
        val switch = compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH)
            .assertIsDisplayed()
            .assert(hasText(CurrentLiftCopy.switchLabel(1, 2)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(Metrics.touchMin)
        assertEquals("Lift 1 of 2", CurrentLiftCopy.switchLabel(1, 2))
        // "Double-tap to switch exercise": the words name the place, the label names the act.
        assertEquals(CurrentLiftCopy.SWITCH, switch.clickLabel())
        switch.performClick()
        assertEquals(1, switched)
        assertEquals(0, details)
    }

    @Test
    fun theIdentityIsWordsNotAButton() {
        showHeader()
        // Tapping the name or the set context does nothing: only the switch and the picture act.
        identity().assert(!hasClickAction())
        words().assert(!hasClickAction())
        compose.onNodeWithText("Leg Extension").performClick()
        compose.onNodeWithTag(WorkoutTestTags.SET_CONTEXT).performClick()
        assertEquals(0, switched)
        assertEquals(0, details)
        // The lift is one spoken stop, read from its own words, and says nothing twice.
        assertEquals(listOf(EquipmentType.MACHINE.label.uppercase(), "Leg Extension"), words().mergedTexts())
        assertTrue(words().spokenDescriptions().isEmpty())
    }

    @Test
    fun detailsIsThePictureAndSitsOutsideTheSwitch() {
        showHeader()
        val picture = compose.onNodeWithTag(WorkoutTestTags.DETAILS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(Metrics.touchMin)
        assertEquals(listOf(CurrentLiftCopy.DETAILS_SPOKEN), picture.spokenDescriptions())
        assertEquals(CurrentLiftCopy.OPEN_DETAILS, picture.clickLabel())
        // The picture says only where it goes: no text of its own to read twice.
        assertTrue(picture.mergedTexts().isEmpty())
        // Drawn first, read last: the identity row is one reading group, and the picture is
        // placed after the lift's words, its set line and the switch within it.
        assertEquals(true, identity().fetchSemanticsNode().config.getOrNull(SemanticsProperties.IsTraversalGroup))
        assertEquals(1f, picture.fetchSemanticsNode().config.getOrNull(SemanticsProperties.TraversalIndex))
        listOf(words(), compose.onNodeWithTag(WorkoutTestTags.SET_CONTEXT), compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH)).forEach {
            assertEquals(0f, it.fetchSemanticsNode().config.getOrElse(SemanticsProperties.TraversalIndex) { 0f })
        }
        picture.performClick()
        assertEquals(1, details)
        assertEquals("a tap on the picture is not a tap on the switch", 0, switched)
        // Details used to sit inside the switcher's tap area. Now the two share no pixels.
        val detailsBounds = picture.getBoundsInRoot()
        val switchBounds = compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).getBoundsInRoot()
        assertTrue(
            "Details must not overlap the switch, was $detailsBounds and $switchBounds",
            detailsBounds.right <= switchBounds.left || detailsBounds.bottom <= switchBounds.top ||
                switchBounds.right <= detailsBounds.left || switchBounds.bottom <= detailsBounds.top,
        )
    }

    @Test
    fun equipmentAndSetPositionAreWordsBesideThePicture() {
        showHeader()
        compose.onNodeWithText("Leg Extension", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(EquipmentType.MACHINE.label.uppercase(), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.SET_CONTEXT, useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(hasText(SET_CONTEXT))
        // The picture clears its own semantics: it is spoken as "Exercise details", never as
        // the artwork's contents.
        assertTrue(still().fetchSemanticsNode().config.isClearingSemantics)
    }

    @Test
    fun atNormalTextTheSwitchRidesTheSetContextsLine() {
        showHeader()
        val context = assertSetContextIsWhole()
        val switch = compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).getBoundsInRoot()
        // Beside it, not under it: the switch costs the header no height at normal text.
        assertTrue("the switch sits beside the set context, was $switch and $context", switch.left >= context.right && switch.top < context.bottom)
    }

    @Test
    fun atMediumLargeTextTheSetContextStaysWholeAndTheSwitchDropsUnderIt() {
        assertTheSwitchDropsUnderAWholeSetContext(fontScale = LogLoopScale.STACK_WELLS_FROM)
    }

    @Test
    fun atLargestTextTheSetContextStaysWholeAndTheSwitchDropsUnderIt() {
        assertTheSwitchDropsUnderAWholeSetContext(fontScale = 2f)
    }

    /**
     * Large text squeezed "Working set 3 of 3" into "Worki / ng s…" beside the pill. A
     * two-digit lift count is the widest pill a real session makes.
     */
    private fun assertTheSwitchDropsUnderAWholeSetContext(fontScale: Float) {
        showHeader(fontScale = fontScale, number = 10, total = 12)
        val context = assertSetContextIsWhole()
        val switch = compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH)
            .assertIsDisplayed()
            .assert(hasText(CurrentLiftCopy.switchLabel(10, 12)))
            .getBoundsInRoot()
        assertTrue("at font $fontScale the switch drops under the set context, was $switch and $context", switch.top >= context.bottom)
    }

    @Test
    fun aSetContextTooLongToShareItsLineKeepsItsWordsAndTheSwitchMovesUnder() {
        showHeader(setContext = LONG_SET_CONTEXT)
        val context = assertSetContextIsWhole()
        assertTrue(compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).getBoundsInRoot().top >= context.bottom)
    }

    @Test
    fun anAddedWeightLiftSaysWhatItsWeightMeansUnderTheName() {
        showHeader(lift = floorLift(targetSets = 3, equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT_PLUS))
        val line = CurrentLiftCopy.secondaryLine(EquipmentType.BODYWEIGHT.label, WeightMeaning.ADDED)
        compose.onNodeWithText(line.uppercase(), useUnmergedTree = true).assertIsDisplayed()
        assertTrue(line.uppercase() in words().mergedTexts())
    }

    @Test
    fun thePictureIsFullSizeAtNormalText() {
        showHeader()
        still().assertWidthIsEqualTo(Metrics.exerciseHeroImage)
    }

    @Test
    fun aLongNameShrinksThePictureToGiveTheWordsRoom() {
        // More than two lines of title beside the full-size still falls back to the small
        // one, so a long lift name keeps the column instead of running to five lines.
        showHeader(lift = floorLift(targetSets = 3, name = LONG_NAME))
        still().assertWidthIsEqualTo(Metrics.workoutIdentityImage)
    }

    @Test
    fun largeTextShrinksThePictureAndKeepsBothSetTypeChipsOnOneRow() {
        showHeader(fontScale = LogLoopScale.STACK_WELLS_FROM)
        still().assertWidthIsEqualTo(Metrics.workoutIdentityImage)
        val working = compose.onNodeWithTag(WorkoutTestTags.WORKING_CHIP).assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin)
        val warmup = compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin)
        assertEquals(working.getBoundsInRoot().top, warmup.getBoundsInRoot().top)
        // Half a row each is still room for the whole word on one line: neither label wraps
        // or runs past its chip.
        listOf("Working", "Warm-up").forEach { label ->
            val layout = compose.onNodeWithText(label, useUnmergedTree = true).textLayout()
            assertEquals("\"$label\" stays on one line at large text", 1, layout.lineCount)
            assertTrue("\"$label\" is not clipped at large text", layout.fitsItsWidth())
        }
    }

    @Test
    fun setTypeIsATwoWayRadioUnderTheIdentity() {
        showHeader()
        compose.onNodeWithTag(WorkoutTestTags.SET_TYPE)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        val working = compose.onNodeWithTag(WorkoutTestTags.WORKING_CHIP)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
            .assertHeightIsAtLeast(Metrics.touchMin)
        val warmup = compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsNotSelected()
            .assertHeightIsAtLeast(Metrics.touchMin)
        // The words name the set type; the Selected state says "selected" once, on its own.
        assertEquals(listOf("Working set"), working.spokenDescriptions())
        assertEquals(listOf("Warm-up set"), warmup.spokenDescriptions())
        // Working first, Warm-up second, side by side, under the identity rather than in it.
        val workingBounds = working.getBoundsInRoot()
        val warmupBounds = warmup.getBoundsInRoot()
        assertTrue(workingBounds.right <= warmupBounds.left)
        assertEquals(workingBounds.top, warmupBounds.top)
        assertTrue(compose.onNodeWithTag(WorkoutTestTags.SET_TYPE).getBoundsInRoot().top >= identity().getBoundsInRoot().bottom)
        warmup.performClick()
        working.performClick()
        assertEquals(listOf(true, false), warmups)
    }

    @Test
    fun aSetTypeChipIsReadOnce() {
        showHeader(draftWarmup = true)
        val warmup = compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).assertIsSelected()
        val working = compose.onNodeWithTag(WorkoutTestTags.WORKING_CHIP).assertIsNotSelected()
        assertEquals(listOf("Warm-up set"), warmup.spokenDescriptions())
        assertEquals(listOf("Working set"), working.spokenDescriptions())
        // The visible label is not merged in beside the spoken form (the double announcement
        // the audit found), and the state is not said in the words as well as by the chip.
        assertTrue(warmup.mergedTexts().isEmpty())
        assertTrue(working.mergedTexts().isEmpty())
        compose.onAllNodes(hasContentDescription("selected", substring = true)).assertCountEquals(0)
        // Still on screen for a sighted lifter.
        compose.onNodeWithText("Warm-up", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aLockedEntryLocksTheSwitchDetailsAndSetType() {
        showHeader(enabled = false)
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).assertIsNotEnabled().performClick()
        compose.onNodeWithTag(WorkoutTestTags.DETAILS).assertIsNotEnabled().performClick()
        compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).assertIsNotEnabled().performClick()
        compose.onNodeWithTag(WorkoutTestTags.WORKING_CHIP).assertIsNotEnabled()
        assertEquals(0, switched)
        assertEquals(0, details)
        assertTrue(warmups.isEmpty())
    }

    /** The lift's words: equipment and name, merged into one stop beside the picture. */
    private fun words() = compose.onNode(
        hasParent(hasTestTag(WorkoutTestTags.liftCard("leg-ext"))) and
            hasText("Leg Extension", substring = true),
    )

    /**
     * The set context is on screen whole: at most two lines, the last one not cut short, and
     * no word broken across lines. Returns where it sits.
     */
    private fun assertSetContextIsWhole(): DpRect {
        val node = compose.onNodeWithTag(WorkoutTestTags.SET_CONTEXT, useUnmergedTree = true).assertIsDisplayed()
        val layout = node.textLayout()
        val words = layout.layoutInput.text.text
        assertTrue("\"$words\" takes at most two lines, took ${layout.lineCount}", layout.lineCount <= 2)
        assertFalse("\"$words\" is not cut short", layout.isLineEllipsized(layout.lineCount - 1))
        assertTrue(
            "no word of \"$words\" is broken across lines",
            layout.multiParagraph.intrinsics.minIntrinsicWidth <= layout.multiParagraph.width,
        )
        return node.getBoundsInRoot()
    }

    /** The picture, which is also the Details button. */
    private fun still() = compose.onNodeWithTag(WorkoutTestTags.DETAILS)

    private companion object {
        const val SET_CONTEXT = "Working set 3 of 3"

        /**
         * Longer than any set context the app writes today: more than two lines beside the
         * switch at 360 dp, well within two on its own.
         */
        const val LONG_SET_CONTEXT = "Warm-up set 2 · then working set 1 of 3"
        const val LONG_NAME = "Single-arm half-kneeling cable row with a three-second pause at the top of every rep"
    }
}
