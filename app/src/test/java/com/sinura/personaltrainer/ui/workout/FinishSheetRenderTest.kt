package com.sinura.personaltrainer.ui.workout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.sinura.personaltrainer.ui.components.EndWorkoutTags
import com.sinura.personaltrainer.ui.components.NotesSaveState
import com.sinura.personaltrainer.ui.components.NotesSaveStatus
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class FinishSheetRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun finishSheetShowsCountsAndPrimary() {
        compose.setContent {
            PersonalTrainerTheme {
                FinishSheet(
                    loggedSets = 6,
                    plannedWorkingSets = 9,
                    durationMinutes = 42,
                    onFinish = {},
                    onKeepTraining = {},
                    onDiscard = {},
                    onDismiss = {},
                    editOpen = false,
                )
            }
        }
        compose.onNodeWithTag(WorkoutTestTags.FINISH_SHEET).assertIsDisplayed()
        compose.onNodeWithText("Finish workout?").assertIsDisplayed()
        compose.onNodeWithText("Finish and see summary").assertIsDisplayed()
        compose.onNodeWithTag(EndWorkoutTags.SAVE).assertIsDisplayed()
    }

    @Test
    fun finishSheetEmptyShowsDiscard() {
        compose.setContent {
            PersonalTrainerTheme {
                FinishSheet(
                    loggedSets = 0,
                    plannedWorkingSets = 0,
                    durationMinutes = 0,
                    onFinish = {},
                    onKeepTraining = {},
                    onDiscard = {},
                    onDismiss = {},
                    editOpen = false,
                )
            }
        }
        compose.onNodeWithTag(EndWorkoutTags.DISCARD).assertIsDisplayed()
    }

    @Test
    fun finishSheetLoadingAndError() {
        compose.setContent {
            PersonalTrainerTheme {
                FinishSheet(
                    loggedSets = 3,
                    plannedWorkingSets = 3,
                    durationMinutes = 20,
                    onFinish = {},
                    onKeepTraining = {},
                    onDiscard = {},
                    onDismiss = {},
                    editOpen = false,
                    loading = true,
                    error = "Could not finish",
                    onRetry = {},
                )
            }
        }
        compose.onNodeWithText("Could not finish").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun finishSheetBlocksSaveWhileCorrectionOpen() {
        compose.setContent {
            PersonalTrainerTheme {
                FinishSheet(
                    loggedSets = 2,
                    plannedWorkingSets = 3,
                    durationMinutes = 10,
                    onFinish = {},
                    onKeepTraining = {},
                    onDiscard = {},
                    onDismiss = {},
                    editOpen = true,
                )
            }
        }
        compose.onNodeWithTag(EndWorkoutTags.EDIT_OPEN).assertIsDisplayed()
        compose.onNodeWithTag(EndWorkoutTags.BACK_TO_EDIT).assertIsDisplayed()
    }

    @Test
    fun finishSheetBlocksSaveWhileNotesArePending() {
        compose.setContent {
            PersonalTrainerTheme {
                FinishSheet(
                    loggedSets = 2,
                    plannedWorkingSets = 3,
                    durationMinutes = 10,
                    onFinish = {},
                    onKeepTraining = {},
                    onDiscard = {},
                    onDismiss = {},
                    editOpen = false,
                    notesSave = NotesSaveState(status = NotesSaveStatus.SAVING),
                )
            }
        }
        compose.onNodeWithTag(EndWorkoutTags.NOTES_PENDING).assertIsDisplayed()
        compose.onNodeWithTag(EndWorkoutTags.SAVE).assertIsNotEnabled()
    }
}
