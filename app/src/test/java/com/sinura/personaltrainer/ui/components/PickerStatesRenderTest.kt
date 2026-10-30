package com.sinura.personaltrainer.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerMode
import com.sinura.personaltrainer.domain.ExercisePickerProgress
import com.sinura.personaltrainer.domain.ExercisePickerState
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Blueprint 4.4 (A30): one render check per Add exercise (picker) state. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class PickerStatesRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val bench = Exercise(id = "bench", name = "Bench press", muscleGroup = "Chest", notes = "", isCustom = false)
    private val row = Exercise(id = "row", name = "Barbell row", muscleGroup = "Back", notes = "", isCustom = false)
    private val events = mutableListOf<ExercisePickerEvent>()

    private fun show(state: ExercisePickerState) {
        compose.setContent { PersonalTrainerTheme { ExercisePickerSheet(state = state, onEvent = { events += it }) } }
    }

    private fun multi(
        query: String = "",
        results: List<Exercise> = listOf(bench, row),
        selectedOrder: List<String> = emptyList(),
        suggestion: Exercise? = null,
        progress: ExercisePickerProgress = ExercisePickerProgress(),
    ) = ExercisePickerState(
        query = query, results = results, title = "Add exercise", mode = ExercisePickerMode.MULTI_ADD,
        suggestion = suggestion, selectedOrder = selectedOrder, catalog = listOf(bench, row), progress = progress,
    )

    @Test
    fun emptyResultsSayNoLiftsMatchAndOfferClearFiltersAndCreateLift() {
        show(multi(query = "zercher", results = emptyList()))
        compose.onNodeWithText("No lifts match").assertExists()
        compose.onNodeWithText("Create lift: zercher").assertExists()
        compose.onNodeWithTag(PickerStateTags.CLEAR).performClick()
        assertTrue(events.contains(ExercisePickerEvent.QueryChanged("")))
    }

    @Test
    fun loadingDrawsSkeletonRowsNotNoLiftsMatch() {
        show(multi(results = emptyList(), progress = ExercisePickerProgress(pendingIds = setOf("bench"))))
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(PickerStateTags.LOADING).assertExists()
        compose.onNodeWithText("No lifts match").assertDoesNotExist()
    }

    @Test
    fun errorShowsInlineWithRetry() {
        show(multi(progress = ExercisePickerProgress(error = "Could not add that lift.", canRetry = true)))
        compose.onNodeWithText("Could not add that lift.").assertExists()
        compose.onNodeWithText("Retry").performClick()
        assertTrue(events.contains(ExercisePickerEvent.Retry))
    }

    @Test
    fun firstTimeHidesRecentAndShowsSuggested() {
        show(multi(suggestion = bench))
        compose.onNodeWithText("SUGGESTED").assertExists()
        compose.onNodeWithText("RECENT").assertDoesNotExist()
        compose.onNodeWithText("Recent").assertDoesNotExist()
    }

    @Test
    fun resumeAndProcessDeathKeepThePickedIdsAndCountThem() {
        // The picked ids live in RoutineEditorUiState.pickedIds (SavedStateHandle-backed); the
        // sheet only has to draw what it is handed back.
        show(multi(selectedOrder = listOf("bench", "row")))
        compose.onNodeWithText("Add 2 exercises", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Then set targets", substring = true).assertIsDisplayed()
    }

    @Test
    fun oneLiftUsesTheSingular() {
        show(multi(selectedOrder = listOf("bench")))
        compose.onNodeWithText("Add 1 exercise", substring = true).assertIsDisplayed()
    }
}
