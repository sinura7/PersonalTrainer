package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.StepperRepeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the entry feels like under the thumb: a tap on a plate is one detent tick; a held plate
 * waits, then steps with a light tick at a capped pace, and letting go adds nothing; and Set on
 * the keypad is a detent, never the commit's confirmation, because typing a number saves nothing.
 *
 * These were lines of StepperButton.kt and NumberEntryDialog.kt read as text
 * (`StepperRepeat.HOLD_BEFORE_REPEAT_MS`, `StepperRepeat.REPEAT_MS`, `Haptics.tick(view)`,
 * `Haptics.tickLight(view)`). The steps a held plate makes, and that its release adds none, are
 * WeightRepsEditorRenderTest's; this is what they feel like. The keypad reads the screen's view
 * where it is opened, so the screen's FeltView hears it too.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class PlateAndKeypadFeelRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var felt: FeltView

    @Test
    fun aTapOnAPlateIsOneDetent() {
        showEditor()
        compose.onNodeWithContentDescription(REPS_UP).performClick()
        compose.waitForIdle()
        assertEquals(listOf(HapticFeedbackConstants.CLOCK_TICK), felt.felt())
    }

    @Test
    fun aHeldPlateWaitsThenRepeatsLightTicksAtItsPace() {
        showEditor()
        val plate = compose.onNodeWithContentDescription(REPS_UP)
        compose.mainClock.autoAdvance = false
        var pressedAt = 0L
        try {
            plate.performTouchInput { down(center) }
            pressedAt = compose.mainClock.currentTime
            compose.mainClock.advanceTimeBy(StepperRepeat.HOLD_BEFORE_REPEAT_MS + StepperRepeat.REPEAT_MS * REPEATS + HALF_A_STEP_MS)
            plate.performTouchInput { up() }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        val ticks = felt.haptics.toList()
        assertTrue("every step of a hold is a light tick, and the release adds none: $ticks", ticks.all { it.first == HapticFeedbackConstants.KEYBOARD_TAP })
        assertEquals("the first step comes once the hold has waited, then one a step: $ticks", REPEATS + 1, ticks.size)
        val first = ticks.first().second - pressedAt
        assertTrue("the first step waits ${StepperRepeat.HOLD_BEFORE_REPEAT_MS} ms, was $first", first in StepperRepeat.HOLD_BEFORE_REPEAT_MS..StepperRepeat.HOLD_BEFORE_REPEAT_MS + FRAME_MS)
        ticks.zipWithNext { a, b -> b.second - a.second }.forEach { gap ->
            assertEquals("then one every ${StepperRepeat.REPEAT_MS} ms, a gap was $gap", StepperRepeat.REPEAT_MS.toFloat(), gap.toFloat(), FRAME_MS.toFloat())
        }
    }

    @Test
    fun setOnTheKeypadIsADetentNeverACommit() {
        showEditor()
        var beforeSet = -1
        compose.withKeypad(on = compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER), value = "7") {
            beforeSet = felt.haptics.size
        }
        val fromSet = felt.felt().drop(beforeSet)
        assertEquals("Set is one detent: $fromSet", listOf(HapticFeedbackConstants.CLOCK_TICK), fromSet)
    }

    /** The floor's two numerals, with the screen's view a FeltView that writes down each haptic. */
    private fun showEditor() {
        felt = compose.attachedFeltView()
        val view = felt
        compose.showFloor {
            CompositionLocalProvider(LocalView provides view) {
                WeightRepsEditor(
                    enabled = true,
                    weightKg = FLOOR_KG70,
                    reps = 10,
                    unit = FLOOR_UNIT,
                    loadClass = LoadClass.of(LoadType.EXTERNAL),
                    loadType = LoadType.EXTERNAL,
                    equipment = EquipmentType.MACHINE,
                    movementKey = null,
                    plated = false,
                    hold = false,
                    holdSeconds = null,
                    holdRunning = false,
                    holdRemainingSeconds = 0,
                    onWeightKgChange = {},
                    onRepsChange = {},
                    onSecondsChange = {},
                )
            }
        }
    }

    private companion object {
        const val REPS_UP = "Increase reps by 1"

        /** Steps after the first, held for. */
        const val REPEATS = 3

        /** Let go midway through the next wait, so no step is on the edge of the release. */
        const val HALF_A_STEP_MS = 100L

        const val FRAME_MS = 16L
    }
}
