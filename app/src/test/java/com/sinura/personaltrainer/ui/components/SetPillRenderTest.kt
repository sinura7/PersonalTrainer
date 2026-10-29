package com.sinura.personaltrainer.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class SetPillRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun setPillsShowDoneNowNextAndWarmUp() {
        compose.setContent {
            SetPillRow(
                pills = listOf(
                    SetPillModel(
                        key = "w1",
                        state = SetPillState.WarmUp,
                        label = "W1",
                        value = "20x12",
                        onClick = {},
                        testTag = "pill-w1",
                    ),
                    SetPillModel(
                        key = "d1",
                        state = SetPillState.Done,
                        label = "Set 1",
                        value = "70x10",
                        onClick = {},
                        testTag = "pill-done",
                    ),
                    SetPillModel(
                        key = "n0",
                        state = SetPillState.Now,
                        label = "Set 2",
                        value = null,
                        onClick = {},
                        testTag = "pill-now",
                    ),
                    SetPillModel(
                        key = "n1",
                        state = SetPillState.Next,
                        label = "Set 3",
                        value = null,
                        onClick = {},
                        testTag = "pill-next",
                    ),
                ),
            )
        }
        compose.onNodeWithTag("workout-set-pill-row").assertIsDisplayed()
        compose.onNodeWithContentDescription("W1 20x12").assertIsDisplayed()
        compose.onNodeWithContentDescription("Set 1 70x10").assertIsDisplayed()
        compose.onNodeWithContentDescription("Set 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Set 3").assertIsDisplayed()
    }
}
