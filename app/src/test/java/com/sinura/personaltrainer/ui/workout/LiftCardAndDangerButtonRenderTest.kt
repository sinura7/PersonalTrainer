package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.EndWorkoutCopy
import com.sinura.personaltrainer.ui.components.DangerGymButton
import com.sinura.personaltrainer.ui.components.LiftCard
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
 * shows the number in a badge beside its picture, and a danger button (the end-of-workout
 * "Leave without saving") wears Danger as its ink, never as a fill.
 *
 * These were `LiftCard.kt contains "CountBadge("` and `GymButtons.kt contains "fun
 * DangerGymButton"`, read as text in LandscapeChromeTest.
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

        /** "Leave without saving" at 16 sp and xhdpi inks hundreds of pixels; a stray edge does not. */
        const val DANGER_INK_MIN_PIXELS = 60
    }
}
