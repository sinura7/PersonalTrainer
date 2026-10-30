package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Blueprint 12_between — render states owned by the panel (A38). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class LiftTransitionPanelStatesRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun betweenLiftsShowsUpNextAndMiniRingWhileRestRuns() {
        compose.setContent {
            PersonalTrainerTheme {
                LiftTransitionPanel(
                    liftIndex = 1,
                    liftCount = 3,
                    completedLiftName = "Squat",
                    workingSets = 3,
                    volumeKgLabel = "765",
                    prLabel = "85 kg",
                    nextLiftIndex = 2,
                    nextLiftName = "Bench press",
                    nextTarget = "60 kg × 8",
                    nextTargetLoading = false,
                    nextFirstTimeCaption = false,
                    restRemainingSeconds = 45,
                    restTotalSeconds = 90,
                    restRunning = true,
                    restCompleted = false,
                    onStartNext = {},
                    onChooseOther = {},
                )
            }
        }
        compose.onNodeWithTag(WorkoutTestTags.LIFT_TRANSITION).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.BETWEEN_MINI_RING).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.BETWEEN_START).assertIsDisplayed()
    }

    @Test
    fun firstTimeCaptionAndLoadingHint() {
        compose.setContent {
            PersonalTrainerTheme {
                LiftTransitionPanel(
                    liftIndex = 2,
                    liftCount = 2,
                    completedLiftName = "Deadlift",
                    workingSets = 2,
                    volumeKgLabel = "400",
                    prLabel = null,
                    nextLiftIndex = 2,
                    nextLiftName = "Pull-up",
                    nextTarget = "Start light",
                    nextTargetLoading = true,
                    nextFirstTimeCaption = true,
                    restRemainingSeconds = 0,
                    restTotalSeconds = 60,
                    restRunning = false,
                    restCompleted = false,
                    onStartNext = {},
                    onChooseOther = {},
                )
            }
        }
        compose.onNodeWithText("First time, start light").assertIsDisplayed()
    }

    @Test
    fun restCompletedShowsMiniRingDone() {
        compose.setContent {
            PersonalTrainerTheme {
                LiftTransitionPanel(
                    liftIndex = 1,
                    liftCount = 2,
                    completedLiftName = "Press",
                    workingSets = 1,
                    volumeKgLabel = "120",
                    prLabel = null,
                    nextLiftIndex = 2,
                    nextLiftName = "Row",
                    nextTarget = "40 kg × 10",
                    nextTargetLoading = false,
                    nextFirstTimeCaption = false,
                    restRemainingSeconds = 0,
                    restTotalSeconds = 60,
                    restRunning = false,
                    restCompleted = true,
                    onStartNext = {},
                    onChooseOther = {},
                )
            }
        }
        compose.onNodeWithTag(WorkoutTestTags.BETWEEN_MINI_RING).assertIsDisplayed()
    }
}
