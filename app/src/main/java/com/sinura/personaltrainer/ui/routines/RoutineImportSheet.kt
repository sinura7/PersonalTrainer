package com.sinura.personaltrainer.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WorkoutPasteCopy
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineImportSheet(state: RoutineImportState, catalog: List<Exercise>,
    onConfirm: () -> Unit, onClose: () -> Unit, onRead: () -> Unit) {
    if (!state.visible) return
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden) { if (!state.applying && !state.keeping) onClose(); false } else true
        })
    ModalBottomSheet(onDismissRequest = { if (!state.applying && !state.keeping) onClose() }, sheetState = sheet, containerColor = Surface3) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = Metrics.gutter).padding(bottom = Metrics.space5).testTag(RoutineEditorTags.IMPORT_PREVIEW),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
            Text("Import preview", style = InstrumentType.title, color = TextPrimary)
            Text("Fills this empty routine and creates the additional routines shown below. Names, notes, targets and lift order come from your text. Saved workout history stays unchanged.",
                style = InstrumentType.caption, color = TextSecondary)
            state.preview?.changes?.forEachIndexed { index, change ->
                val after = change.after ?: return@forEachIndexed
                Kicker(if (index == 0) "This routine" else "Additional routine")
                Text(after.routine.name, style = InstrumentType.bodyStrong, color = TextPrimary)
                if (after.items.isEmpty()) Text("Notes only · no matched lifts", style = InstrumentType.caption, color = TextSecondary)
                after.items.forEach { item ->
                    val exercise = catalog.firstOrNull { it.id == item.exerciseId }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                        if (exercise != null) ExerciseThumb(exercise = exercise)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exercise?.name ?: "Lift unavailable", style = InstrumentType.bodyStrong, color = TextPrimary)
                            val work = item.targetSeconds?.let { HoldWork.formatRange(it, item.targetSecondsMax) } ?: "${item.targetReps} reps"
                            Text("${item.targetSets} sets · $work · rest ${item.restSeconds}s",
                                style = InstrumentType.caption, color = TextSecondary)
                        }
                    }
                }
                Text(after.routine.notes, style = InstrumentType.caption, color = TextSecondary)
                HairlineDivider()
            }
            state.details?.let { details ->
                if (details.duplicates.isNotEmpty()) {
                    Kicker("Duplicate lift lines excluded")
                    Text("The first matched entry keeps its position and targets.", style = InstrumentType.caption, color = TextSecondary)
                    details.duplicates.forEach { Text(it, style = InstrumentType.caption, color = TextSecondary) }
                }
                if (details.unmatched.isNotEmpty()) {
                    Kicker("Unmatched lines")
                    Text("These lines stay in the notes and are not added as lifts. You can resolve them after import.", style = InstrumentType.caption, color = TextSecondary)
                    details.unmatched.forEach { Text(WorkoutPasteCopy.issue(it), style = InstrumentType.caption, color = TextSecondary) }
                }
                Kicker("Training cycle")
                Text(details.scheduleScope, style = InstrumentType.caption, color = TextSecondary)
                state.preview?.pinsAfter.orEmpty().forEach { pin ->
                    val name = details.owners.firstOrNull { it.id == pin.routineId }?.name ?: "Unavailable routine"
                    Text("${Weekday.entries[checkNotNull(pin.anchorDay)].name.lowercase().replaceFirstChar { it.uppercase() }} · $name",
                        style = InstrumentType.caption, color = TextSecondary)
                }
            }
            state.problem?.let { message ->
                Text(message, style = InstrumentType.caption, color = Danger,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = onRead, enabled = !state.applying && !state.keeping,
                    modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(RoutineEditorTags.RECOVERY_READ)) {
                    Text("Read saved result", style = InstrumentType.bodyStrong)
                }
            }
            PrimaryGymButton(text = when {
                state.applying -> "Importing…"
                state.keeping -> "Keeping preview…"
                state.outcome == DraftRecoveryOutcome.UNCOMMITTED -> "Retry import"
                state.outcome == DraftRecoveryOutcome.EXACT_SAVED -> "Complete import receipt"
                else -> "Confirm import"
            }, onClick = onConfirm,
                enabled = !state.applying && !state.keeping && state.preview != null && state.outcome != DraftRecoveryOutcome.CONFLICT && state.outcome != DraftRecoveryOutcome.UNAVAILABLE,
                height = Metrics.touchMin, modifier = Modifier.testTag(RoutineEditorTags.IMPORT_CONFIRM))
            TextButton(onClick = onClose, enabled = !state.applying && !state.keeping,
                modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin).testTag(RoutineEditorTags.IMPORT_CLOSE)) {
                Text("Close · keep preview", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
    }
}
