package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.EndWorkoutCopy
import com.sinura.personaltrainer.ui.components.DangerGymButton
import com.sinura.personaltrainer.ui.components.LiftCard
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.Danger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Two shared pieces the floor's chrome leans on, as drawn: a lift card numbered in its list
 * shows the number in a badge beside its picture, pictures its lift in a 56 dp still, and
 * keeps its trailing (History's "3/3") on its identity row, inside its one tap target and
 * before whatever the card holds under it; and a danger button (the end-of-workout "Leave
 * without saving") wears Danger as its ink, never as a fill.
 *
 * These were `LiftCard.kt contains "CountBadge("` and `GymButtons.kt contains "fun
 * DangerGymButton"`, read as text in LandscapeChromeTest, and, since audit T1c-2,
 * `trailing()` inside the identity row and `ExerciseThumb(` at `ThumbSize.header`, read as text
 * in FloorCompactPresentationTest and WorkoutLiftChipTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class LiftCardAndDangerButtonRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var discards = 0

    @Test
    fun aNumberedLiftCardShowsItsNumberInABadge() {
        val lift = floorLift(targetSets = 3).exercise
        compose.showFloor {
            Column(modifier = Modifier.width(360.dp)) {
                LiftCard(exercise = lift, number = 3, cardTag = NUMBERED)
                LiftCard(exercise = lift, cardTag = PLAIN)
            }
        }
        compose.onNode(hasText("3") and hasAnyAncestor(hasTestTag(NUMBERED)), useUnmergedTree = true).assertIsDisplayed()
        // Only the numbered card carries one.
        compose.onAllNodesWithText("3", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun aLiftCardsTrailingSitsOnItsIdentityRowBeforeItsContent() {
        val lift = floorLift(targetSets = 3).exercise
        val trailing: @Composable () -> Unit = { Text(COUNT) }
        val underneath: @Composable () -> Unit = { Text(CONTENT) }
        compose.showFloor {
            Column(modifier = Modifier.width(360.dp)) {
                LiftCard(exercise = lift, cardTag = WITH_TRAILING, trailing = trailing, content = underneath)
            }
        }
        val name = compose.onNode(hasText(lift.name) and hasAnyAncestor(hasTestTag(WITH_TRAILING)), useUnmergedTree = true).getBoundsInRoot()
        val count = compose.onNodeWithText(COUNT, useUnmergedTree = true).assertIsDisplayed().getBoundsInRoot()
        val content = compose.onNodeWithText(CONTENT, useUnmergedTree = true).assertIsDisplayed().getBoundsInRoot()
        assertTrue("beside the lift's words, was $count after $name", count.left >= name.right)
        assertTrue("on the identity row, above what the card holds, was $count over $content", count.bottom <= content.top)
        // Part of the card's one tap target, read with it.
        assertTrue(COUNT in compose.onNodeWithTag(WITH_TRAILING).mergedTexts())
    }

    @Test
    fun aLiftCardPicturesItsLiftInAHeaderSizedStill() {
        val lift = floorLift(targetSets = 3).exercise
        compose.showFloor {
            Column(modifier = Modifier.width(360.dp)) {
                LiftCard(exercise = lift, cardTag = PLAIN)
            }
        }
        assertEquals(1, compose.stillsUnder(hasTestTag(PLAIN), ThumbSize.header).size)
    }

    @Test
    fun aDangerButtonWearsDangerAsItsInkNotAsAFill() {
        val discard: () -> Unit = { discards += 1 }
        compose.showFloor {
            Column(modifier = Modifier.width(360.dp).padding(16.dp)) {
                DangerGymButton(text = EndWorkoutCopy.DISCARD, onClick = discard)
            }
        }
        val words = compose.onNodeWithText(EndWorkoutCopy.DISCARD, useUnmergedTree = true).assertIsDisplayed().windowBounds()
        val frame = compose.drawWindow()
        assertTrue("the words are drawn in Danger", frame.count(words, Danger, tolerance = 40) >= DANGER_INK_MIN_PIXELS)
        // Beside the words, inside the button: the button's own surface, not a red slab.
        val px = compose.density.density
        val beside = box(words.right + 8f * px, words.top, words.right + 24f * px, words.bottom)
        assertEquals(0, frame.count(beside, Danger, tolerance = 40))
        compose.onNodeWithText(EndWorkoutCopy.DISCARD).performClick()
        assertEquals(1, discards)
    }

    private companion object {
        const val NUMBERED = "numbered-card"
        const val PLAIN = "plain-card"
        const val WITH_TRAILING = "trailing-card"
        const val COUNT = "2/3"
        const val CONTENT = "Work 3 × 10"

        /** "Leave without saving" at 16 sp and xhdpi inks hundreds of pixels; a stray edge does not. */
        const val DANGER_INK_MIN_PIXELS = 60
    }
}
