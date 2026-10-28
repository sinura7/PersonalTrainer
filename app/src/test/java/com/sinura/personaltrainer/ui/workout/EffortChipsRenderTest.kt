package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.sinura.personaltrainer.ui.components.EffortChips
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class EffortChipsRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun prefilledEffortChipIsShown() {
        compose.showFloor(fontScale = 1f) {
            EffortChips(selected = null, prefilled = 8, onSelect = {}, last = 7)
        }
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(8)).assertIsDisplayed()
    }

    @Test
    fun effortChipsWrapToTwoRowsAtFont20() {
        compose.showFloor(fontScale = 2f) {
            EffortChips(selected = 9, prefilled = null, onSelect = {})
        }
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(6)).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(10)).assertIsDisplayed()
    }
}
