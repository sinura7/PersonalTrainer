package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.workout.WorkoutNumberField

/** The owning recovery surface remains reachable even when the saved row/sheet no longer exists. */
@Composable
internal fun HistoricalSetRecovery(
    formState: FinishedSetFormState,
    enabled: Boolean,
    onRead: () -> Unit,
    onDiscard: () -> Unit,
    onResume: () -> Unit,
) {
    val unit = LocalWeightUnit.current
    Column(modifier = Modifier.fillMaxWidth().heightIn(max = Metrics.recoveryPreviewMax).verticalScroll(rememberScrollState())
        .padding(horizontal = Metrics.gutter, vertical = Metrics.space2).testTag("historical-set-recovery"),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        Text(if (formState.draft == null) "Set editing" else "Unfinished set edit", style = InstrumentType.bodyStrong, color = TextPrimary)
        formState.message?.let { Text(it, style = InstrumentType.body, color = TextSecondary) }
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                formState.draft?.let { draft ->
                    val values = draft.values
                    val load = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(values.weightKg, unit))
                    val timeOnly = values.reps < 1 && (values.durationSeconds ?: 0) > 0
                    Text((if (values.weightKg > 0) "Draft load $load ${unit.suffix} · " else "Draft · ") +
                        (if (timeOnly) "${values.durationSeconds} seconds" else "${values.reps} reps" +
                            (values.durationSeconds?.let { " · $it seconds" } ?: "")) +
                        (values.rpe?.let { " · RPE $it" } ?: "") + (if (values.isWarmup) " · Warm-up" else ""),
                        style = InstrumentType.body, color = TextPrimary)
                    draft.numbers.forEach { entry ->
                        val label = when (entry.field) {
                            WorkoutNumberField.WEIGHT -> "Typed load ${entry.weightUnit?.suffix.orEmpty()}"
                            WorkoutNumberField.REPS -> "Typed reps"
                            WorkoutNumberField.HOLD_SECONDS, WorkoutNumberField.REST_SECONDS -> "Typed seconds"
                        }
                        Text("$label: ${entry.text}", style = InstrumentType.body, color = TextPrimary)
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            if (formState.draft != null) TextButton(onClick = onResume, enabled = enabled,
                modifier = Modifier.heightIn(min = Metrics.touchMin)) { Text("Resume edit") }
            TextButton(onClick = onRead, enabled = enabled, modifier = Modifier.heightIn(min = Metrics.touchMin)
                .testTag("historical-set-read-again")) { Text("Read again") }
            TextButton(onClick = onDiscard, enabled = enabled, modifier = Modifier.heightIn(min = Metrics.touchMin)
                .testTag("historical-set-discard")) { Text("Discard unfinished edit") }
        }
    }
}
