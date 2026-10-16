package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import com.sinura.personaltrainer.ui.components.ExerciseSearchField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The purpose sits beside the box. The box keeps the typed words and does not
 * carry a description that a screen reader would read instead of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp", fontScale = 2f)
class FieldPurposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun searchAndRoutineNameAnnounceTheirPurposeEmptyAndFilled() {
        compose.setContent {
            val search = remember { mutableStateOf("") }
            val name = remember { mutableStateOf("") }
            Column {
                ExerciseSearchField(
                    value = search.value,
                    onValueChange = { search.value = it },
                    placeholder = "Search",
                )
                RoutineTitleField(name = name.value, onNameChange = { name.value = it })
            }
        }
        assertPurposes(searchTyped = "", nameTyped = "")
        compose.onNodeWithTag(testTag = "exercise-search").performTextReplacement("Back squat")
        compose.onNodeWithTag(testTag = RoutineEditorTags.NAME).performTextReplacement("Upper A")
        assertPurposes(searchTyped = "Back squat", nameTyped = "Upper A")
    }

    private fun assertPurposes(searchTyped: String, nameTyped: String) {
        compose.onNodeWithContentDescription(label = "Search exercises").assertIsDisplayed()
        compose.onNodeWithText(text = "Routine name").assertIsDisplayed()
        compose.onNodeWithTag(testTag = RoutineEditorTags.NAME_PURPOSE).assertIsDisplayed()
        assertField(tag = "exercise-search", typed = searchTyped)
        assertField(tag = RoutineEditorTags.NAME, typed = nameTyped)
    }

    private fun assertField(tag: String, typed: String) {
        val config = compose.onNodeWithTag(testTag = tag).fetchSemanticsNode().config
        assertNull(config.getOrNull(SemanticsProperties.ContentDescription))
        assertNull(config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(typed, config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty())
    }
}
