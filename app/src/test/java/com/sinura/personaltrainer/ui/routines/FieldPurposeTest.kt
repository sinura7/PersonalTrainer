package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import com.sinura.personaltrainer.ui.components.ExerciseSearchField
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Search and the routine name have to say what they are when they are empty and
 * after something has been typed. The placeholder disappears once there is text.
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
        assertPurpose(tag = "exercise-search", purpose = "Search exercises")
        assertPurpose(tag = RoutineEditorTags.NAME, purpose = "Routine name")
        compose.onNodeWithTag(testTag = "exercise-search").performTextReplacement("Back squat")
        compose.onNodeWithTag(testTag = RoutineEditorTags.NAME).performTextReplacement("Upper A")
        assertPurpose(tag = "exercise-search", purpose = "Search exercises")
        assertPurpose(tag = RoutineEditorTags.NAME, purpose = "Routine name")
    }

    private fun assertPurpose(tag: String, purpose: String) {
        val spoken = compose.onNodeWithTag(testTag = tag).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)
            .orEmpty()
        assertTrue("$tag spoke $spoken", spoken.any { it.contains(purpose) })
    }
}
