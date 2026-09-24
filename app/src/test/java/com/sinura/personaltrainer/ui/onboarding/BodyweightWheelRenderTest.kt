package com.sinura.personaltrainer.ui.onboarding

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
import com.sinura.personaltrainer.domain.BodyweightSteps
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.showFloor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Onboarding's bodyweight is still a wheel: a vertical column of numbers that the lifter flicks
 * to a value, which the big readout then shows and the answer takes. The floor lost its wheels
 * for numerals and plates; this one kept its own on purpose.
 *
 * This was `BodyweightWheel.kt contains "VerticalPager("`, read as text in
 * FloorStepperEntryTest. Typing a bodyweight is TalkBackPolicyTest's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class BodyweightWheelRenderTest {
    @get:Rule val compose = createComposeRule()

    private val chosenKg = mutableListOf<Double>()

    @Test
    fun theBodyweightWheelIsAColumnYouFlick() {
        val onKgChange: (Double) -> Unit = { chosenKg += it }
        compose.showFloor {
            BodyweightWheel(kg = START_KG, unit = WeightUnit.KG, onKgChange = onKgChange, onUnitChange = {})
        }
        val wheel = compose.onNode(hasScrollToIndexAction() and IS_VERTICAL)
        val values = BodyweightSteps.displayValues(WeightUnit.KG)
        wheel.performScrollToIndex(values.indexOf(PICKED))
        compose.awaitThat(what = "the flick chose a bodyweight", now = ::chosenKg) { chosenKg.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(BodyweightSteps.toKg(PICKED, WeightUnit.KG), chosenKg.last(), 1e-6)
        compose.onNode(hasText(PICKED.toString()) and hasClickAction()).assertExists()
    }

    private companion object {
        const val START_KG = 80.0
        const val PICKED = 85

        /** A column: it scrolls up and down. */
        val IS_VERTICAL: SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    }
}
