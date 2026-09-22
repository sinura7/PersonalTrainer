package com.sinura.personaltrainer.ui.saveposture

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.sinura.personaltrainer.domain.SavePostureCopy
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first-launch chooser: honest about paused sync, and a real gate over the Home drawn
 * beneath it — taps, Back and TalkBack all stop at it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class SavePostureChooserTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var tapsBeneath = 0
    private var backsBeneath = 0
    private var chooserBacks = 0

    private fun show(syncPaused: Boolean = true) {
        compose.setContent {
            PersonalTrainerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Stands in for Home: composed underneath, with its own tap and Back.
                    BackHandler { backsBeneath++ }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .hiddenUnderFirstLaunchOverlay(covered = true)
                            .clickable { tapsBeneath++ }
                            .testTag(BENEATH),
                    ) {
                        Text(BENEATH_TEXT)
                    }
                    SavePostureChooser(
                        onChooseAccount = {},
                        onChooseLocal = {},
                        onSetUpDrive = {},
                        onBack = { chooserBacks++ },
                        syncPaused = syncPaused,
                    )
                }
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

    @Test
    fun aTapOnTheChoosersEmptySpaceNeverReachesTheScreenBeneath() {
        show()
        // Below the last button: the chooser's own background, nothing on it.
        compose.onNodeWithTag(SavePostureTags.ROOT).performTouchInput {
            click(Offset(x = centerX, y = bottom - 40f))
        }
        compose.waitForIdle()
        assertEquals(0, tapsBeneath)
    }

    @Test
    fun backIsTheChoosersAndNeverReachesTheScreenBeneath() {
        show()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, chooserBacks)
        assertEquals(0, backsBeneath)
    }

    @Test
    fun theChooserIsItsOwnPaneAndTheScreenBeneathIsHiddenFromTalkBack() {
        show()
        compose.onNodeWithTag(SavePostureTags.ROOT)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, SavePostureCopy.CHOOSER_HEADLINE))
        compose.onNodeWithText(BENEATH_TEXT).assertDoesNotExist()
        compose.onNodeWithTag(BENEATH).assertDoesNotExist()
    }

    private companion object {
        const val BENEATH = "screen-beneath"
        const val BENEATH_TEXT = "Home beneath the chooser"
    }
}
