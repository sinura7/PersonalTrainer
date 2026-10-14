package com.sinura.personaltrainer.ui.routines

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.sinura.personaltrainer.data.draft.RoutineNumberDraft
import com.sinura.personaltrainer.data.draft.RoutineNumberField
import com.sinura.personaltrainer.data.draft.RoutineTargetDraft
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.LocalNumberEntryRetention
import com.sinura.personaltrainer.ui.components.NumberEntryRetention
import kotlinx.coroutines.flow.StateFlow

internal interface RoutineNumberEntries {
    val itemId: String?
    val exerciseId: String?
    val target: RoutineTargetDraft?
    val entries: StateFlow<List<RoutineNumberDraft>>
    fun put(value: RoutineNumberDraft)
    fun clear(field: RoutineNumberField)
    fun allowsInput(): Boolean
}
internal val LocalRoutineNumbers = staticCompositionLocalOf<RoutineNumberEntries?> { null }

@Composable
internal fun retainedRoutineNumber(field: RoutineNumberField): RoutineNumberDraft? {
    val port = LocalRoutineNumbers.current ?: return null
    val values by port.entries.collectAsState()
    return values.firstOrNull { it.itemId == port.itemId && it.exerciseId == port.exerciseId && it.field == field }
}

@Composable
internal fun RoutineNumberEntry(field: RoutineNumberField, unit: WeightUnit? = null, content: @Composable () -> Unit) {
    val port = LocalRoutineNumbers.current
    val draft = retainedRoutineNumber(field)
    val item = port?.itemId
    val exercise = port?.exerciseId
    val retention = if (port == null || item == null || exercise == null) null else object : NumberEntryRetention {
        override val initial = draft?.let { TextFieldValue(it.text, TextRange(it.selectionStart, it.selectionEnd)) }
        override fun allowsInput(): Boolean = port.itemId == item && port.exerciseId == exercise && port.allowsInput()
        override fun changed(value: TextFieldValue) = port.put(RoutineNumberDraft(
            itemId = item, exerciseId = exercise, field = field, text = value.text,
            selectionStart = value.selection.start, selectionEnd = value.selection.end,
            unit = draft?.unit ?: unit, open = true))
        override fun closed() {
            if (allowsInput()) port.entries.value.firstOrNull { it.itemId == item && it.field == field }?.let { port.put(it.copy(open = false)) }
        }
        override fun discarded() { if (allowsInput()) port.clear(field) }
    }
    key(port, item, exercise, field) { CompositionLocalProvider(LocalNumberEntryRetention provides retention, content = content) }
}
