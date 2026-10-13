package com.sinura.personaltrainer.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.LocalNumberEntryRetention
import com.sinura.personaltrainer.ui.components.NumberEntryRetention
import com.sinura.personaltrainer.workout.WorkoutNumberDraft
import com.sinura.personaltrainer.workout.WorkoutNumberField
import kotlinx.coroutines.flow.StateFlow

internal interface WorkoutNumberEntries {
    val entries: StateFlow<List<WorkoutNumberDraft>>
    val exerciseId: String?
    fun put(value: WorkoutNumberDraft)
    fun clear(field: WorkoutNumberField)
    fun allowsInput(): Boolean
}
internal val LocalWorkoutNumbers = staticCompositionLocalOf<WorkoutNumberEntries?> { null }

@Composable
internal fun retainedNumber(field: WorkoutNumberField): WorkoutNumberDraft? {
    val port = LocalWorkoutNumbers.current ?: return null
    val values by port.entries.collectAsState()
    return values.firstOrNull { it.exerciseId == port.exerciseId && it.field == field }
}

@Composable
internal fun WorkoutNumberEntry(field: WorkoutNumberField, unit: WeightUnit? = null, content: @Composable () -> Unit) {
    val port = LocalWorkoutNumbers.current
    val draft = retainedNumber(field)
    val owner = port?.exerciseId
    val retention = if (port == null) null else object : NumberEntryRetention {
        override val initial = draft?.let { TextFieldValue(it.text, TextRange(it.selectionStart, it.selectionEnd)) }
        override fun allowsInput(): Boolean = port.exerciseId == owner && port.allowsInput()
        override fun changed(value: TextFieldValue) = port.put(WorkoutNumberDraft(
            exerciseId = owner, field = field, text = value.text, weightUnit = draft?.weightUnit ?: unit,
            selectionStart = value.selection.start, selectionEnd = value.selection.end, open = true,
        ))
        override fun closed() { if (allowsInput()) port.entries.value.firstOrNull { it.exerciseId == owner && it.field == field }?.let { port.put(it.copy(open = false)) } }
        override fun discarded() { if (allowsInput()) port.clear(field) }
    }
    key(port, owner, field) { CompositionLocalProvider(LocalNumberEntryRetention provides retention, content = content) }
}
