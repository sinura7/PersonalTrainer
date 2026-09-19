package com.sinura.personaltrainer.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.LoadTypeChipRow
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary

/**
 * One way to classify a lift, not two.
 *
 * The chip row and a free-text "Or type a muscle group" field used to be mounted at the
 * same time, permanently — a form that could not decide which of its own inputs was the
 * real one, and left the answer to whichever the user touched last. The chips are the
 * answer; "Other" is the escape hatch, and it opens the field it stands for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditorSheet(
    draft: ExerciseEditorDraft,
    muscleOptions: List<String>,
    error: String?,
    onDraftChange: (ExerciseEditorDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = muscleOptions.ifEmpty { MuscleGroups.catalog }
    val chips = groups.filterNot { it.equals(MuscleGroups.OTHER, ignoreCase = true) }
    val chosen = chips.firstOrNull { it.equals(draft.muscleGroup, ignoreCase = true) }
    var typingGroup by rememberSaveable { mutableStateOf(false) }
    // A group no chip can express — imported, or typed before the catalog knew it — has to
    // show its own value, or editing that lift would silently reclassify it.
    val showGroupField = typingGroup ||
        (chosen == null && draft.muscleGroup.isNotBlank() &&
            !draft.muscleGroup.equals(MuscleGroups.OTHER, ignoreCase = true))

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space7),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Text(
                if (draft.id == null) "New lift" else "Edit lift",
                style = InstrumentType.title,
                color = TextPrimary,
            )
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
                isError = error?.contains("name", ignoreCase = true) == true,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                Kicker("Muscle group")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                    items(chips, key = { it }) { group ->
                        InstrumentChip(
                            label = group,
                            selected = group.equals(draft.muscleGroup, ignoreCase = true),
                            onClick = {
                                typingGroup = false
                                onDraftChange(draft.copy(muscleGroup = group))
                            },
                        )
                    }
                    item(key = "other") {
                        InstrumentChip(
                            label = MuscleGroups.OTHER,
                            selected = typingGroup || MuscleGroups.otherSelected(draft.muscleGroup),
                            onClick = {
                                typingGroup = true
                                // Other is an explicit choice, not the empty default. The field
                                // opens so a more specific name can replace it.
                                if (chosen != null || draft.muscleGroup.isBlank() ||
                                    draft.muscleGroup.equals(MuscleGroups.OTHER, ignoreCase = true)
                                ) {
                                    onDraftChange(draft.copy(muscleGroup = MuscleGroups.OTHER))
                                }
                            },
                        )
                    }
                }
                if (showGroupField) {
                    OutlinedTextField(
                        value = draft.muscleGroup,
                        onValueChange = { onDraftChange(draft.copy(muscleGroup = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Muscle group") },
                        singleLine = true,
                    )
                }
            }
            LoadTypeChipRow(
                selected = draft.loadType,
                onSelect = { onDraftChange(draft.copy(loadType = it)) },
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { onDraftChange(draft.copy(notes = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes (optional)") },
                minLines = 2,
            )
            error?.let { Text(it, style = InstrumentType.body, color = Danger) }
            PrimaryGymButton(
                text = if (draft.id == null) "Create lift" else "Save changes",
                onClick = onSave,
            )
        }
    }
}
