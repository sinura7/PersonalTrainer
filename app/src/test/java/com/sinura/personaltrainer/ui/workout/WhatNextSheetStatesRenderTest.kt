package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Blueprint 11_set_options — six-state coverage where the sheet owns the UI (A37). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class WhatNextSheetStatesRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyLastLiftPrimaryIsFinish() {
        compose.setContent {
            PersonalTrainerTheme {
                WhatNextSheet(
                    addSetSubLabel = "85 kg × 3",
                    swapSubLabel = "Same muscles: Front squat",
                    skipSubLabel = "Pick from the 2 left",
                    primaryLabel = "Finish workout",
                    showFinish = true,
                    rowsEnabled = true,
                    onAddSet = {},
                    onSwap = {},
                    onSkip = {},
                    onEditSaved = {},
                    onPrimary = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithTag(WorkoutTestTags.WHAT_NEXT_SHEET).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.DOCK_FINISH).assertIsDisplayed()
        compose.onNodeWithText("Finish workout").assertIsDisplayed()
    }

    @Test
    fun loadingDisablesActionRows() {
        compose.setContent {
            PersonalTrainerTheme {
                WhatNextSheet(
                    addSetSubLabel = "85 kg × 3",
                    swapSubLabel = null,
                    skipSubLabel = "Pick from the 3 left",
                    primaryLabel = "Next: Bench press",
                    showFinish = false,
                    rowsEnabled = false,
                    onAddSet = {},
                    onSwap = {},
                    onSkip = {},
                    onEditSaved = {},
                    onPrimary = {},
                    onDismiss = {},
                )
            }
        }
        val addSet = compose.onNodeWithTag(WorkoutTestTags.WHAT_NEXT_ADD_SET)
        assertNull(addSet.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))
        compose.onNodeWithTag(WorkoutTestTags.NEXT).assertIsDisplayed()
    }

    @Test
    fun defaultNextLiftPrimaryAndRows() {
        compose.setContent {
            PersonalTrainerTheme {
                WhatNextSheet(
                    addSetSubLabel = "60 kg × 5",
                    swapSubLabel = "Same muscles: Goblet squat",
                    skipSubLabel = "Pick from the 4 left",
                    primaryLabel = "Next: Romanian deadlift",
                    showFinish = false,
                    rowsEnabled = true,
                    onAddSet = {},
                    onSwap = {},
                    onSkip = {},
                    onEditSaved = {},
                    onPrimary = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("What next?").assertIsDisplayed()
        compose.onNodeWithText("Planned sets complete").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.WHAT_NEXT_EDIT).assertIsDisplayed()
    }
}
