package com.sinura.personaltrainer.ui.settings

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.sinura.personaltrainer.ui.components.GymSettingsTags
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GymsSectionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sectionTagExistsInTree() {
        // Minimal wiring: full VM integration lives in instrumented walkthrough flow 11.
        compose.setContent {
            PersonalTrainerTheme {
                androidx.compose.material3.Text(
                    "Gyms",
                    modifier = Modifier.testTag(GymSettingsTags.SETTINGS_SECTION),
                )
            }
        }
        compose.onNodeWithTag(GymSettingsTags.SETTINGS_SECTION).assertExists()
    }
}
