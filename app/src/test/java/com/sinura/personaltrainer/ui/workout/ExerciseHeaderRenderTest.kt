package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * These facts used to be pinned as lines of ExerciseHeader.kt — the merged
 * `contentDescription`, `selected = true`, the `onClickLabel`, the image-size tokens, the
 * `val stacked` rule. W1a rebuilds this header on purpose (a visible "Lift n of N" switch,
 * Details out of the switcher's tap area, one announcement per chip), so the pins would
 * all break at once and say nothing about what a lifter lost. Here each fact is what the
 * lifter or TalkBack gets. Where W1a changes that on purpose, the assertion says so.
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
    ) {
        compose.showFloor(fontScale = fontScale) {
            ExerciseHeader(
                lift = lift,
                number = 1,
                total = 2,
                workingLogged = 2,
                setContext = SET_CONTEXT,
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
    fun theIdentitySpeaksTheLiftOnceAndATapOpensTheSwitcher() {
        showHeader()
        val spoken = CurrentLiftCopy.cardSpoken(
            name = "Leg Extension",
            number = 1,
            total = 2,
            workingLogged = 2,
            targetSets = 3,
            equipmentLabel = EquipmentType.MACHINE.label,
            meaning = WeightMeaning.LIFTED,
            includeWorkingProgress = false,
        )
        // The lift's place in the session is spoken; its set position is left to the set
        // context, so "2 of 3 done" is not said twice in different words.
        assertTrue(spoken.contains(CurrentLiftCopy.heroOrdinal(1, 2)))
        assertFalse(spoken.contains(CurrentLiftCopy.heroProgress(2, 3)))
        // W1a changes this: the switch becomes its own visible "Lift n of N" control, so the
        // identity stops being the switcher's button and stops ending in "Switch exercise".
        assertEquals(listOf("$spoken. $SET_CONTEXT. ${CurrentLiftCopy.SWITCH}"), identity().spokenDescriptions())
        assertEquals(CurrentLiftCopy.SWITCH, identity().clickLabel())
        identity()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            // W1a changes this: once the identity is no longer the switcher's button it has
            // nothing to be "selected" among, and W1a may drop the state.
            .assertIsSelected()
            .assertHeightIsAtLeast(Metrics.touchMin)
            .performClick()
        assertEquals(1, switched)
        assertEquals(0, details)
    }

    @Test
    fun detailsIsItsOwnNamedButtonAndNeverOpensTheSwitcher() {
        showHeader()
        val button = compose.onNodeWithTag(WorkoutTestTags.DETAILS)
        assertEquals(listOf("Exercise details"), button.spokenDescriptions())
        button.assertHeightIsAtLeast(Metrics.touchMin).performClick()
        assertEquals(1, details)
        assertEquals("a tap on Details is not a tap on the identity", 0, switched)
        // Details is announced on its own, never folded into the identity's sentence.
        assertFalse(identity().spokenDescriptions().any { it.contains("Exercise details") })
        assertFalse(identity().mergedTexts().contains(CurrentLiftCopy.DETAILS))
        // W1a changes this: Details moves out of the switcher's tap area. Today it is drawn
        // inside the identity's bounds, and only its own click handler keeps a tap on it
        // from opening the switcher.
        val outer = identity().getBoundsInRoot()
        val inner = button.getBoundsInRoot()
        assertTrue(
            "Details sits inside the identity today, was $inner in $outer",
            inner.left >= outer.left && inner.right <= outer.right && inner.top >= outer.top && inner.bottom <= outer.bottom,
        )
    }

    @Test
    fun equipmentAndSetPositionAreWordsBesideAPictureThatSaysNothing() {
        showHeader()
        compose.onNodeWithText("Leg Extension", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(EquipmentType.MACHINE.label.uppercase(), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.SET_CONTEXT, useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(hasText(SET_CONTEXT))
        // The still is decorative: it clears its own semantics and has no words, so the
        // identity's sentence is the one thing TalkBack reads.
        val still = stillOf().fetchSemanticsNode().config
        assertTrue(still.isClearingSemantics)
        assertFalse(still.contains(SemanticsProperties.ContentDescription))
        assertFalse(still.contains(SemanticsProperties.Text))
        assertEquals(1, identity().spokenDescriptions().size)
    }

    @Test
    fun anAddedWeightLiftSaysWhatItsWeightMeansUnderTheName() {
        showHeader(lift = floorLift(targetSets = 3, equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT_PLUS))
        val line = CurrentLiftCopy.secondaryLine(EquipmentType.BODYWEIGHT.label, WeightMeaning.ADDED)
        compose.onNodeWithText(line.uppercase(), useUnmergedTree = true).assertIsDisplayed()
        assertTrue(identity().spokenDescriptions().single().contains(line))
    }

    @Test
    fun thePictureIsFullSizeAtNormalText() {
        showHeader()
        stillOf().assertWidthIsEqualTo(Metrics.exerciseHeroImage)
    }

    @Test
    fun aLongNameShrinksThePictureToGiveTheWordsRoom() {
        // More than two lines of title beside the full-size still falls back to the small
        // one, so a long lift name keeps the column instead of running to five lines.
        showHeader(lift = floorLift(targetSets = 3, name = LONG_NAME))
        stillOf().assertWidthIsEqualTo(Metrics.workoutIdentityImage)
    }

    @Test
    fun largeTextShrinksThePictureAndKeepsBothSetTypeChipsOnOneRow() {
        showHeader(fontScale = LogLoopScale.STACK_WELLS_FROM)
        stillOf().assertWidthIsEqualTo(Metrics.workoutIdentityImage)
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
        assertEquals(listOf("Working set, selected"), working.spokenDescriptions())
        assertEquals(listOf("Warm-up set, not selected"), warmup.spokenDescriptions())
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
    fun aSetTypeChipSaysItsStateAndTodayItsLabelToo() {
        showHeader(draftWarmup = true)
        compose.onAllNodes(hasContentDescription("Warm-up set, selected")).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription("Working set, not selected")).assertCountEquals(1)
        compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).assertIsSelected()
        assertEquals(listOf("Warm-up set, selected"), compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).spokenDescriptions())
        // W1a changes this: the chip also merges its visible label beside the spoken form,
        // which is the double announcement W1a removes.
        assertEquals(listOf("Warm-up"), compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).mergedTexts())
        compose.onAllNodesWithText("Warm-up").assertCountEquals(1)
    }

    @Test
    fun aLockedEntryLocksTheIdentityDetailsAndSetType() {
        showHeader(enabled = false)
        identity().assertIsNotEnabled().performClick()
        compose.onNodeWithTag(WorkoutTestTags.DETAILS).assertIsNotEnabled().performClick()
        compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).assertIsNotEnabled().performClick()
        compose.onNodeWithTag(WorkoutTestTags.WORKING_CHIP).assertIsNotEnabled()
        assertEquals(0, switched)
        assertEquals(0, details)
        assertTrue(warmups.isEmpty())
    }

    /**
     * The identity's picture: the one part of it with no words and no tap of its own. Found
     * by that, not by its place among the identity's children, so W1a can reorder the header
     * without these checks measuring the wrong node.
     */
    private fun stillOf() = compose.onNode(
        hasParent(hasTestTag(WorkoutTestTags.liftCard("leg-ext"))) and
            !SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and
            !SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription) and
            !hasClickAction(),
        useUnmergedTree = true,
    )

    private companion object {
        const val SET_CONTEXT = "Working set 3 of 3"
        const val LONG_NAME = "Single-arm half-kneeling cable row with a three-second pause at the top of every rep"
    }
}
