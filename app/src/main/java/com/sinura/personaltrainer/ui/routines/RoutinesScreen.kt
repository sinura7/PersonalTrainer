package com.sinura.personaltrainer.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    viewModel: RoutinesViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("My routines") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateRoutine) {
                Icon(Icons.Outlined.Add, contentDescription = "Create routine")
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
            state.routines.isEmpty() -> {
                EmptyState(
                    title = "No routines yet",
                    body = "Create a program with your lifts, target sets, reps, and optional weight targets.",
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.routines, key = { it.id }) { routine ->
                        Card(
                            onClick = { onOpenRoutine(routine.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(routine.name, style = MaterialTheme.typography.titleLarge)
                                    IconButton(onClick = { pendingDeleteId = routine.id }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete routine")
                                    }
                                }
                                Text(
                                    "${routine.exercises.size} exercises",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (routine.exercises.isNotEmpty()) {
                                    Text(
                                        routine.exercises.take(3).joinToString(" · ") { item ->
                                            val weight = item.targetWeightKg?.toWeightLabel(unit)?.let { " $it" }.orEmpty()
                                            "${item.exercise.name} ${item.targetSets}×${item.targetReps}$weight"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete routine?") },
            text = { Text("This removes the routine. Past workout history stays saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(id)
                        pendingDeleteId = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            },
        )
    }
}
