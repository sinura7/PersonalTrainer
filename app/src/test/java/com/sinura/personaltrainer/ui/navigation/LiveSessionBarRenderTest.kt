package com.sinura.personaltrainer.ui.navigation

import android.app.Application
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.sinura.personaltrainer.domain.LiveBarKind
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v4 blueprint packet V1b (A-V1-10): the live bar says Paused for a paused cardio session. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class LiveSessionBarRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pausedLabelShows() {
        val state = LiveSessionBarUiState(
            sessionId = "s1", title = "Easy run", elapsedLabel = "0:42", workingSets = 0, totalSets = 1,
            restRemainingSeconds = 0, restRunning = false, stale = false, staleHours = 0,
            kind = LiveBarKind.ACTIVITY, paused = true,
        )
        compose.setContent {
            PersonalTrainerTheme {
                LiveSessionBar(
                    state = state, applyNavInsets = false, onResume = {}, onFinish = {}, onDiscard = {},
                )
            }
        }
        compose.onNodeWithTag(LiveSessionBarTestTags.ELAPSED, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(LiveSessionBarTestTags.PausedKey, true))
        compose.onNodeWithText("Paused, 0:42", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("0:42 elapsed", useUnmergedTree = true).assertDoesNotExist()
    }
}
