package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.RpeCopy
import com.sinura.personaltrainer.ui.theme.Metrics
import kotlin.math.abs
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
 * The effort track under the numerals, composed on its own: five equal radio choices, 6 to
 * 10, with the ends named; a coach recommendation that is spoken but never selected; one
 * tap to the help; and, for a warm-up, the reason in place of the track.
 *
 * It was held as lines of RpeSelector.kt (`Kicker("RPE")`, `FlowRow(`, `.weight(1f)`,
 * `role = Role.RadioButton`, `val recommended = recommendedRpe == value && !selected`,
 * `rememberTextMeasurer`, "Warm-ups leave RPE blank"). W1b replaces the "RPE" kicker with
 * "Effort · optional" and shows each value's meaning inline, which rewrites those lines; the
 * heading check below says so where it stands. Native graphics, because whether the five
 * fit one row is decided by measuring the widest label.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class RpeSelectorRenderTest {
    @get:Rule val compose = createComposeRule()

    private val picks = mutableListOf<Int?>()
    private var rpe by mutableStateOf<Int?>(null)

    /** Composed at [screenWidth] inside the floor's side gutters, as the list lays it out. */
    private fun showTrack(
        recommended: Int? = null,
        warmup: Boolean = false,
        enabled: Boolean = true,
        fontScale: Float = 1f,
        screenWidth: Dp = 360.dp,
    ) {
        compose.showFloor(fontScale = fontScale) {
            Box(modifier = Modifier.width(screenWidth).padding(horizontal = Metrics.gutter)) {
                RpeSelector(
                    enabled = enabled,
                    warmup = warmup,
                    rpe = rpe,
                    recommendedRpe = recommended,
                    onRpe = { picks += it },
                )
            }
        }
    }

    private fun choice(value: Int): SemanticsNodeInteraction = compose.onNodeWithTag(WorkoutTestTags.rpeChoice(value))

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun effortIsFiveEqualRadioChoicesWithTheirMeaningSpoken() {
        showTrack()
        // W1b changes this: "Effort · optional" replaces the "RPE" kicker, with each value's
        // meaning shown inline rather than only spoken.
        compose.onNode(hasText("RPE"), useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithTag(WorkoutTestTags.RPE_TRACK)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        val spoken = mapOf(
            6 to "RPE 6, about four reps left",
            7 to "RPE 7, about three reps left",
            8 to "RPE 8, about two reps left",
            9 to "RPE 9, about one rep left",
            10 to "RPE 10, max",
        )
        RpeCopy.VALUES.forEach { value ->
            choice(value).assertIsDisplayed().assert(radio).assertIsNotSelected().assertHeightIsAtLeast(Metrics.touchMin)
            assertEquals(listOf(spoken.getValue(value)), choice(value).spokenDescriptions())
            compose.onNode(hasText(value.toString()), useUnmergedTree = true).assertIsDisplayed()
        }
        compose.onAllNodesWithTag(WorkoutTestTags.RPE_CLEAR).assertCountEquals(0)
        choice(7).performClick()
        assertEquals(listOf<Int?>(7), picks)
    }

    @Test
    fun onA360DpPhoneTheFiveShareOneRowInEqualWidthsWithTheEndsUnderneath() {
        // Even at the largest text.
        showTrack(fontScale = 2f)
        val bounds = RpeCopy.VALUES.map { choice(it).getBoundsInRoot() }
        assertEquals("one row", 1, bounds.map { it.top }.toSet().size)
        assertEquals("left to right, 6 to 10", bounds.map { it.left }.sorted(), bounds.map { it.left })
        val widths = bounds.map { (it.right - it.left).value }
        assertTrue("equal widths, were $widths", widths.all { abs(it - widths.first()) <= 1f })
        val track = compose.onNodeWithTag(WorkoutTestTags.RPE_TRACK).getBoundsInRoot()
        val easy = compose.onNodeWithText("Easy").assertIsDisplayed().getBoundsInRoot()
        val max = compose.onNodeWithText("Max effort").assertIsDisplayed().getBoundsInRoot()
        assertTrue("the ends sit under the track", easy.top >= track.bottom && max.top >= track.bottom)
        assertTrue("Easy at the 6 end, Max effort at the 10 end", easy.left < max.left)
    }

    @Test
    fun atLargeTextOnASmallPhoneTheTrackWrapsInsteadOfScrolling() {
        // At 360 dp the five still fit one row at the largest text; a 320 dp phone is where
        // they run out of room, and they must wrap rather than scroll or clip.
        showTrack(fontScale = 2f, screenWidth = 320.dp)
        val track = compose.onNodeWithTag(WorkoutTestTags.RPE_TRACK).assert(hasScrollAction().not())
        val trackBounds = track.getBoundsInRoot()
        RpeCopy.VALUES.forEach { value ->
            val chip = choice(value).assertIsDisplayed().assertWidthIsAtLeast(Metrics.touchMin).getBoundsInRoot()
            assertTrue("chip $value stays inside the track", chip.left >= trackBounds.left && chip.right <= trackBounds.right + 1.dp)
            assertTrue(
                "the label $value is laid out whole",
                compose.onNode(hasText(value.toString()), useUnmergedTree = true).textLayout().fitsItsWidth(),
            )
        }
        assertTrue("the five wrap onto a second row", choice(9).getBoundsInRoot().top > choice(6).getBoundsInRoot().top)
        compose.onNodeWithText("Max effort").assertIsDisplayed()
    }

    @Test
    fun aRecommendationIsSpokenButNeverSelected() {
        showTrack(recommended = 8)
        choice(8).assertIsNotSelected()
        assertEquals(listOf("RPE 8, about two reps left, recommended"), choice(8).spokenDescriptions())
        RpeCopy.VALUES.filter { it != 8 }.forEach {
            assertFalse(choice(it).spokenDescriptions().single().endsWith("recommended"))
        }
        compose.onAllNodesWithTag(WorkoutTestTags.RPE_CLEAR).assertCountEquals(0)
        assertTrue("a recommendation picks nothing on its own", picks.isEmpty())
        // Chosen, the same value is selected and no longer called a recommendation.
        rpe = 8
        choice(8).assertIsSelected()
        assertEquals(listOf("RPE 8, about two reps left"), choice(8).spokenDescriptions())
    }

    @Test
    fun theChosenEffortClearsFromClearOrASecondTap() {
        rpe = 9
        showTrack()
        choice(9).assertIsSelected()
        compose.onNodeWithTag(WorkoutTestTags.RPE_CLEAR).assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin).performClick()
        choice(9).performClick()
        assertEquals(listOf<Int?>(null, null), picks)
    }

    @Test
    fun helpIsOneTapAwayAndExplainsEachValue() {
        showTrack()
        val help = compose.onNodeWithTag(WorkoutTestTags.RPE_HELPER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(Metrics.touchMin)
            .assertWidthIsAtLeast(Metrics.touchMin)
        assertEquals(listOf("RPE help"), help.spokenDescriptions())
        help.performClick()
        compose.onNodeWithText("Effort (RPE)").assertIsDisplayed()
        compose.onNodeWithText("6 · four reps left", substring = true).assertIsDisplayed()
        compose.onNodeWithText("10 · max", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onAllNodesWithText("Effort (RPE)").assertCountEquals(0)
    }

    @Test
    fun aWarmupShowsTheReasonInsteadOfTheTrack() {
        rpe = 8
        showTrack(warmup = true, recommended = 8)
        compose.onNodeWithTag(WorkoutTestTags.RPE_WARMUP_REASON)
            .assertIsDisplayed()
            .assert(hasText("Effort is recorded for working sets. Warm-ups leave RPE blank."))
        compose.onNodeWithTag(WorkoutTestTags.RPE_TRACK).assertDoesNotExist()
        RpeCopy.VALUES.forEach { choice(it).assertDoesNotExist() }
        compose.onNodeWithTag(WorkoutTestTags.RPE_CLEAR).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.RPE_HELPER).assertIsDisplayed()
    }

    @Test
    fun aLockedEntryTakesNoEffort() {
        rpe = 9
        showTrack(enabled = false)
        RpeCopy.VALUES.forEach { choice(it).assertIsNotEnabled() }
        choice(7).performClick()
        compose.onNodeWithTag(WorkoutTestTags.RPE_CLEAR).assertIsNotEnabled().performClick()
        assertTrue("was $picks", picks.isEmpty())
        compose.onNodeWithTag(WorkoutTestTags.RPE_HELPER).assertIsEnabled()
    }
}
