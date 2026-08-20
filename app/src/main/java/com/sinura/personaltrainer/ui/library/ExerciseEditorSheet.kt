package com.sinura.personaltrainer.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.ui.components.PrimaryGymButton

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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (draft.id == null) "New exercise" else "Edit exercise",
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
                isError = error?.contains("name", ignoreCase = true) == true,
            )
            Text("Muscle group", style = MaterialTheme.typography.titleMedium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp),
            ) {
                items(groups) { group ->
                    FilterChip(
                        selected = draft.muscleGroup.equals(group, ignoreCase = true),
                        onClick = { onDraftChange(draft.copy(muscleGroup = group)) },
                        label = { Text(group) },
                    )
                }
            }
            OutlinedTextField(
                value = draft.muscleGroup,
                onValueChange = { onDraftChange(draft.copy(muscleGroup = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Or type a muscle group") },
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { onDraftChange(draft.copy(notes = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes (optional)") },
                minLines = 2,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryGymButton(
                text = if (draft.id == null) "Create exercise" else "Save changes",
                onClick = onSave,
            )
        }
    }
}
