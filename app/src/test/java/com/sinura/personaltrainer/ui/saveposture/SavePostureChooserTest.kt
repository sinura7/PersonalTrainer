package com.sinura.personaltrainer.ui.saveposture

import android.app.Application
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.sinura.personaltrainer.domain.SavePostureCopy
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The first-launch chooser may not sell cloud keeping while sync keeps nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class SavePostureChooserTest {
    @get:Rule val compose = createComposeRule()

    private fun show(syncPaused: Boolean) {
        compose.setContent {
            PersonalTrainerTheme {
                SavePostureChooser(
                    onChooseAccount = {},
                    onChooseLocal = {},
                    onSetUpDrive = {},
                    syncPaused = syncPaused,
                )
            }
        }
    }

    @Test
    fun whileSyncIsPausedTheAccountChoiceSaysSo() {
        show(syncPaused = true)
        compose.onNodeWithTag(SavePostureTags.ACCOUNT_PAUSED)
            .assertTextEquals(SavePostureCopy.CHOOSE_ACCOUNT_PAUSED)
    }

    @Test
    fun onceSyncRunsTheLineIsGone() {
        show(syncPaused = false)
        compose.onNodeWithTag(SavePostureTags.ACCOUNT_PAUSED).assertDoesNotExist()
    }
}
