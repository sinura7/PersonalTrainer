package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Saved-state recreation of the shipping dock/overlay, without a live clock. */
@RunWith(AndroidJUnit4::class)
class WorkoutTimerOverlayInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun landscapeCustomDraftSurvivesSavedStateRecreationAndAppliesOnce() {
        val restoration = StateRestorationTester(compose)
        var applied = 0
        var commits = 0
        restoration.setContent {
            PersonalTrainerTheme(reduceMotion = true) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    LogBar(
                        editing = false, logging = false, error = null, draftLabel = "60 kg × 8", warmup = false,
                        showNext = false, onLog = {}, onNext = {}, onCancelEdit = {}, showTimer = true,
                        hideIdleRest = true, restTotalSeconds = 90,
                        onCustomRest = { input ->
                            val seconds = RestTimer.parseCustom(input)
                            if (seconds != null) { applied = seconds; commits++ }
                            seconds != null
                        },
                    )
                }
            }
        }
        compose.onNodeWithTag("workout-companion-clock").performClick()
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("2:15")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Custom rest").assertIsDisplayed()
        compose.onNodeWithTag("workout-rest-duration-sheet").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertTextContains("2:15")
        assertEquals(0, commits)
        compose.onNodeWithText("Set").performScrollTo().performClick()
        assertEquals(135, applied)
        assertEquals(1, commits)
        compose.onNodeWithText("Custom rest").assertDoesNotExist()
        compose.onNodeWithTag("workout-rest-duration-sheet").assertDoesNotExist()
    }
}
