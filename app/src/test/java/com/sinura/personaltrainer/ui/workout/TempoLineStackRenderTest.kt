package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.sinura.personaltrainer.ui.components.TempoLine
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class TempoLineStackRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun font20At360StacksActionButtons() {
        compose.showFloor(fontScale = 2f) {
            TempoLine(
                message = "Try 70 lbs × 10 on the next set after that solid effort.",
                applied = false,
                onWhy = {},
                onApply = {},
                onUndo = {},
                onDismiss = {},
            )
        }
        val apply = compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dismiss = compose.onNodeWithTag(WorkoutTestTags.TEMPO_COACH_DISMISS).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Apply and Dismiss must stack vertically at font 2.0", apply.top > dismiss.bottom - 1f || dismiss.top > apply.bottom - 1f)
    }
}
