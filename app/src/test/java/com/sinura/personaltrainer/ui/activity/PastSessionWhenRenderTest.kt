package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.DateCopy
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Blueprint 4.16 (A23, A42): one When field, and Save says what it writes or why it cannot. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class PastSessionWhenRenderTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 29).toEpochDay()

    @Test
    fun oneWhenFieldShowsTheDayAndOpensAPickerThatKeepsTheDay() {
        val picked = mutableListOf<Long>()
        compose.setContent {
            PersonalTrainerTheme {
                PastSessionWhenField(epochDay = today - 2, todayEpochDay = today, enabled = true, onPick = { picked += it })
            }
        }
        compose.onNodeWithText("Earlier").assertDoesNotExist()
        compose.onNodeWithText("Later").assertDoesNotExist()
        val label = DateCopy.weekdayShort(LocalDate.ofEpochDay(today - 2))
        compose.onNodeWithTag(ComposerTags.WHEN).assertIsDisplayed()
            .assert(hasContentDescription("When, $label"))
            .performClick()
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithTag(ComposerTags.WHEN_CONFIRM).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(listOf(today - 2), picked)
    }

    @Test
    fun emptyDraftSaveIsDisabledAndSaysWhy() {
        compose.setContent {
            PersonalTrainerTheme {
                ComposerSaveDock(
                    saving = false, canSave = false, onSave = {}, onCancel = {},
                    supporting = composerSaveSupport(ActivityComposerUiState()),
                )
            }
        }
        compose.onNodeWithTag(ComposerTags.SAVE).assertIsNotEnabled()
            .assertTextContains("Add a lift or cardio", substring = true)
    }

    @Test
    fun filledDraftSaveCountsLiftsAndSets() {
        val bench = Exercise(id = "bench", name = "Bench", muscleGroup = "Chest", notes = "", isCustom = false)
        val row = Exercise(id = "row", name = "Row", muscleGroup = "Back", notes = "", isCustom = false)
        val state = ActivityComposerUiState(
            strength = listOf(
                ComposerStrengthLine(bench, 60.0, 8), ComposerStrengthLine(bench, 60.0, 8), ComposerStrengthLine(row, 40.0, 10),
            ),
        )
        compose.setContent {
            PersonalTrainerTheme {
                ComposerSaveDock(saving = false, canSave = true, onSave = {}, onCancel = {}, supporting = composerSaveSupport(state))
            }
        }
        compose.onNodeWithTag(ComposerTags.SAVE).assertTextContains("2 exercises · 3 sets", substring = true)
    }
}
