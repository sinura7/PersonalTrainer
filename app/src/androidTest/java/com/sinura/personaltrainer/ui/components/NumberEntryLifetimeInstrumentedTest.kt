package com.sinura.personaltrainer.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.ui.workout.LocalWorkoutNumbers
import com.sinura.personaltrainer.ui.workout.WorkoutNumberEntries
import com.sinura.personaltrainer.ui.workout.WorkoutNumberEntry
import com.sinura.personaltrainer.workout.WorkoutNumberDraft
import com.sinura.personaltrainer.workout.WorkoutNumberField
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Delivers input from the retired dialog frame, as a queued IME/accessibility action can. */
@RunWith(AndroidJUnit4::class)
class NumberEntryLifetimeInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelRejectsQueuedTextAndConfirmation() = checkRetiredCallbacks(confirm = false)
    @Test fun acceptedValueRejectsQueuedTextAndSecondConfirmation() = checkRetiredCallbacks(confirm = true)

    private fun checkRetiredCallbacks(confirm: Boolean) {
        val showing = mutableStateOf(true)
        val rawEntries = MutableStateFlow<List<WorkoutNumberDraft>>(emptyList())
        val accepted = mutableListOf<Int>()
        val port = object : WorkoutNumberEntries {
            override val exerciseId = "numeric-lifetime-fixture"
            override val entries = rawEntries
            override fun put(value: WorkoutNumberDraft) { entries.value = listOf(value) }
            override fun clear(field: WorkoutNumberField) { entries.value = entries.value.filterNot { it.field == field } }
        }
        compose.setContent {
            CompositionLocalProvider(LocalWorkoutNumbers provides port) {
                if (showing.value) WorkoutNumberEntry(field = WorkoutNumberField.REPS) { NumberEntryDialog(
                    title = "Reps", unitLabel = null, initial = "8", decimal = false,
                    helper = "Enter repetitions", parse = String::toIntOrNull,
                    onConfirm = { accepted += it }, onDismiss = { showing.value = false },
                ) }
            }
        }
        val oldText = checkNotNull(compose.onNodeWithTag(NumberEntryTags.FIELD).fetchSemanticsNode()
            .config[SemanticsActions.SetText].action)
        val oldConfirm = checkNotNull(compose.onNodeWithTag(NumberEntryTags.CONFIRM).fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action)
        compose.onNodeWithText(if (confirm) "Set" else "Cancel").performClick()
        compose.onNodeWithTag(NumberEntryTags.FIELD).assertDoesNotExist()
        compose.runOnIdle { oldText(AnnotatedString("99")) }
        assertTrue("Retired input must not restore a canceled/accepted raw entry", rawEntries.value.isEmpty())
        compose.runOnIdle { oldConfirm() }
        compose.waitForIdle()
        assertTrue(rawEntries.value.isEmpty())
        assertEquals(if (confirm) listOf(8) else emptyList<Int>(), accepted)
        compose.onNodeWithTag(NumberEntryTags.FIELD).assertDoesNotExist()
        compose.runOnIdle { showing.value = true }
        val freshText = checkNotNull(compose.onNodeWithTag(NumberEntryTags.FIELD).fetchSemanticsNode()
            .config[SemanticsActions.SetText].action)
        compose.runOnIdle { freshText(AnnotatedString("11")) }
        compose.onNodeWithText("Set").performClick()
        assertEquals(if (confirm) listOf(8, 11) else listOf(11), accepted)
        assertTrue(rawEntries.value.isEmpty())
    }
}
