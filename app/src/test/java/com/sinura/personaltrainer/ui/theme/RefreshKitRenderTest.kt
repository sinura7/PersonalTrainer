package com.sinura.personaltrainer.ui.theme

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.sinura.personaltrainer.ui.components.GymTags
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h1200dp-xhdpi")
class RefreshKitRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun refreshKitGalleryRenders() {
        compose.setContent { PersonalTrainerTheme { RefreshKitGallery() } }
        compose.onNodeWithText("Refresh kit").assertExists()
        compose.onNodeWithTag(GymTags.plate("weston")).assertExists()
    }

    @Test
    fun primaryDisabledReasonUsesTertiaryInk() {
        compose.setContent {
            PersonalTrainerTheme {
                PrimaryGymButton(
                    text = "Disabled",
                    supporting = "Enter a weight first",
                    enabled = false,
                    onClick = {},
                )
            }
        }
        compose.onNodeWithText("Enter a weight first").assertExists()
    }

    @Test
    fun gymPlateRowMeetsTouchFloor() {
        compose.setContent { PersonalTrainerTheme { RefreshKitGallery() } }
        compose.onNodeWithTag(GymTags.plate("home")).assertHeightIsAtLeast(Metrics.touchMin)
    }
}
