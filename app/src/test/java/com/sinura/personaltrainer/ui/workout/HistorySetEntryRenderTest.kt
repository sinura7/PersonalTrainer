package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.ui.components.StepperButton
import com.sinura.personaltrainer.ui.history.SetEditSheet
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A saved set's sheet in History, long after the workout: its weight and its reps are the shared
 * entry panel's typing wells. A tap on the weight opens the keypad under the field's name, a
 * typed weight lands, a tap on the reps does the same, and Save hands back what was typed.
 *
 * These were lines of SetEditSheet.kt and SetEntryPanel.kt read as text (`SetEntryPanel(` in the
 * sheet, and `NumeralWell(`, `NumberEntryDialog(` and `"Type a weight"` from `fun WeightStepper`
 * on). The floor draws its own numerals (WeightRepsEditorRenderTest); this is the other place a
 * set's numbers are typed. The panel's step actions for TalkBack live only on its compact path,
 * which nothing composes, so the history sheet offers its plates and the keypad instead.
 *
 * Those plates are StepperButton's own, drawn by its defaults: a 12 dp squared plate with the
 * plate's own numeral type, where the floor's plates ask for round ones in the commit's type.
 * Those defaults were lines of StepperButton.kt read as text (`shape: Shape =
 * RoundedCornerShape(Radius.sm)`, `textStyle: TextStyle? = null`); a plate no caller shapes or
 * styles is now drawn beside one asked for exactly that, and must match it pixel for pixel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class HistorySetEntryRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val saved = mutableListOf<Pair<Double, Int>>()

    @Test
    fun theHistorysSetSheetTypesItsWeightAndRepsWithTheKeypad() {
        val onSave: (Double, Int, Int?, Boolean) -> Unit = { weightKg, reps, _, _ -> saved += weightKg to reps }
        compose.showFloor {
            SetEditSheet(
                exerciseName = "Leg Extension",
                initial = floorSet(number = 2, weightKg = FLOOR_KG70, reps = 10, rpe = 8),
                onSave = onSave,
                onDelete = null,
                onDismiss = {},
            )
        }
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { compose.isDisplayed(hasTestTag(TYPE_WEIGHT)) }
        compose.waitForIdle()
        val weight = compose.onNodeWithTag(TYPE_WEIGHT)
        assertEquals(TYPE_WEIGHT, weight.clickLabel())
        compose.withKeypad(on = weight, value = "82.5") {
            compose.onNodeWithText(WeightMeaning.LIFTED.fieldLabel).assertExists()
        }
        val reps = compose.onNodeWithTag(TYPE_REPS)
        assertEquals(TYPE_REPS, reps.clickLabel())
        compose.withKeypad(on = reps, value = "7")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        val typedKg = checkNotNull(NumericEntry.parseWeightKg("82.5", FLOOR_UNIT))
        assertEquals(1, saved.size)
        assertEquals(typedKg, saved.single().first, 1e-6)
        assertEquals(7, saved.single().second)
    }

    @Test
    fun aPlateNoCallerShapesIsTheSquaredPlateInItsOwnTypeNotTheFloorsRoundOne() {
        compose.showFloor {
            Row(modifier = Modifier.background(Pit)) {
                Box(modifier = Modifier.testTag(DEFAULT_PLATE)) {
                    StepperButton(label = "+", onClick = {}, plateWidth = PLATE_WIDTH, plateHeight = PLATE_HEIGHT)
                }
                Box(modifier = Modifier.testTag(SQUARED_PLATE)) {
                    StepperButton(
                        label = "+",
                        onClick = {},
                        plateWidth = PLATE_WIDTH,
                        plateHeight = PLATE_HEIGHT,
                        shape = RoundedCornerShape(Radius.sm),
                        textStyle = InstrumentType.numeralMd,
                    )
                }
                Box(modifier = Modifier.testTag(ROUND_PLATE)) {
                    StepperButton(label = "+", onClick = {}, plateWidth = PLATE_WIDTH, plateHeight = PLATE_HEIGHT, shape = Radius.full)
                }
            }
        }
        val frame = compose.drawWindow()
        val unshaped = frame.pixelsOf(DEFAULT_PLATE)
        assertEquals("pixels unlike a 12 dp squared plate's in the numeral type", 0, differing(unshaped, frame.pixelsOf(SQUARED_PLATE)))
        // The round plate differs, so the comparison can see a plate's shape at all.
        assertTrue("a round plate draws differently", differing(unshaped, frame.pixelsOf(ROUND_PLATE)) > 0)
    }

    /** The plate under [tag], row by row, as drawn. */
    private fun Bitmap.pixelsOf(tag: String): List<Int> {
        val bounds = compose.onNodeWithTag(tag).windowBounds()
        val left = bounds.left.toInt()
        val top = bounds.top.toInt()
        return (top until bounds.bottom.toInt()).flatMap { y -> (left until bounds.right.toInt()).map { x -> getPixel(x, y) } }
    }

    /** Pixels of two same-sized plates that are not the same colour, give or take anti-aliasing. */
    private fun differing(one: List<Int>, other: List<Int>): Int {
        assertEquals("the plates are the same size", one.size, other.size)
        return one.indices.count { !isNear(one[it], Color(other[it]), PIXEL_TOLERANCE) }
    }

    private companion object {
        const val DEFAULT_PLATE = "plate-default"
        const val SQUARED_PLATE = "plate-squared"
        const val ROUND_PLATE = "plate-round"
        val PLATE_WIDTH = 64.dp
        val PLATE_HEIGHT = 48.dp

        /** A glyph's anti-aliased rim can land a shade apart on two plates; a shape cannot. */
        const val PIXEL_TOLERANCE = 24

        /** Each well is found, and is announced, by what a tap on it does. */
        const val TYPE_WEIGHT = "Type a weight"
        const val TYPE_REPS = "Type a rep count"
    }
}
