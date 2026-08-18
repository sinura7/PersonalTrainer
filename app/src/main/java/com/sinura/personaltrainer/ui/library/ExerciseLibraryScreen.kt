package com.sinura.personaltrainer.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    onCreateRoutine: () -> Unit,
    initialMuscle: String? = null,
    viewModel: ExerciseLibraryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(initialMuscle) {
        if (!initialMuscle.isNullOrBlank()) {
            viewModel.applyMuscleFilter(initialMuscle)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Exercise library") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openCreate) {
                Icon(Icons.Outlined.Add, contentDescription = "Create exercise")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "${state.exercises.size} lifts · search, filter, or add your own",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search exercises") },
                            singleLine = true,
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = state.selectedGroup == null,
                                    onClick = { viewModel.onGroupSelected(null) },
                                    label = { Text("All") },
                                )
                            }
                            items(state.muscleFilters) { group ->
                                FilterChip(
                                    selected = state.selectedGroup == group,
                                    onClick = { viewModel.onGroupSelected(group) },
                                    label = { Text(group) },
                                )
                            }
                        }
                    }
                    state.message?.let { note ->
                        item {
                            Text(note, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    state.error?.let { err ->
                        item {
                            Text(err, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (state.visibleExercises.isEmpty()) {
                        item {
                            EmptyState(
                                title = if (state.exercises.isEmpty()) "No exercises yet" else "No matches",
                                body = if (state.exercises.isEmpty()) {
                                    "Add a custom lift to start your library."
                                } else {
                                    "Try another search or muscle group, or create a custom exercise."
                                },
                                actionLabel = "Create exercise",
                                onAction = viewModel::openCreate,
                            )
                        }
                    } else {
                        items(state.visibleExercises, key = { it.id }) { exercise ->
                            ExerciseLibraryCard(
                                exercise = exercise,
                                onAddToRoutine = { viewModel.openAddToRoutine(exercise) },
                                onEdit = { viewModel.openEdit(exercise) },
                                onDelete = { viewModel.requestDelete(exercise) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { draft ->
        ExerciseEditorSheet(
            draft = draft,
            muscleOptions = MuscleGroups.editorOptions(state.exercises),
            error = state.error,
            onDraftChange = viewModel::updateEditor,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::dismissEditor,
        )
    }

    state.pendingDelete?.let { exercise ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete ${exercise.name}?") },
            text = { Text("This removes it from the library. It isn’t used in any routine or workout history.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("Cancel") }
            },
        )
    }

    state.blockedDelete?.let { (exercise, usage) ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Can’t delete ${exercise.name}") },
            text = {
                Text("This lift is used in ${usage.reason()}. Remove it from those routines first, or keep it so your history stays intact.")
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("OK") }
            },
        )
    }

    state.addToRoutine?.let { exercise ->
        AddToRoutineDialog(
            exercise = exercise,
            routines = state.routines,
            onSelect = viewModel::addToRoutine,
            onCreateRoutine = {
                viewModel.dismissAddToRoutine()
                onCreateRoutine()
            },
            onDismiss = viewModel::dismissAddToRoutine,
        )
    }
}

@Composable
private fun ExerciseLibraryCard(
    exercise: Exercise,
    onAddToRoutine: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        exercise.muscleGroup,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (exercise.isCustom) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Text(
                        if (exercise.isCustom) "Custom" else "Built-in",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (exercise.notes.isNotBlank()) {
                Text(
                    exercise.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAddToRoutine) { Text("Add to routine") }
                if (exercise.isCustom) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit ${exercise.name}")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete ${exercise.name}")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddToRoutineDialog(
    exercise: Exercise,
    routines: List<Routine>,
    onSelect: (String) -> Unit,
    onCreateRoutine: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${exercise.name}") },
        text = {
            if (routines.isEmpty()) {
                Text("Create a routine first, then you can add this lift to it.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Choose a routine. Targets default to 3 × 5.")
                    routines.forEach { routine ->
                        TextButton(onClick = { onSelect(routine.id) }) {
                            Text(routine.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (routines.isEmpty()) {
                TextButton(onClick = onCreateRoutine) { Text("Create routine") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (routines.isEmpty()) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
