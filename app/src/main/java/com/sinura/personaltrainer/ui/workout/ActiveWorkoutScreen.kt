package com.sinura.personaltrainer.ui.workout

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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.toKgLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.KgStepper
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.RepsStepper
import com.sinura.personaltrainer.ui.components.RestTimerBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onExit: () -> Unit,
    onFinished: () -> Unit,
    viewModel: ActiveWorkoutViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val session = state.session
    val selected = session?.exercises?.firstOrNull { it.exercise.id == state.selectedExerciseId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.routineName ?: "Workout") },
                navigationIcon = {
                    IconButton(onClick = { confirmDiscard = true }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Exit workout")
                    }
                },
            )
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
            session == null -> {
                EmptyState(
                    title = "Workout missing",
                    body = "This session is no longer available.",
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        RestTimerBar(
                            remainingSeconds = state.restRemainingSeconds,
                            totalSeconds = state.restTotalSeconds,
                            onSkip = viewModel::skipRest,
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(session.exercises, key = { it.id }) { item ->
                                FilterChip(
                                    selected = item.exercise.id == state.selectedExerciseId,
                                    onClick = { viewModel.selectExercise(item.exercise.id) },
                                    label = { Text(item.exercise.name) },
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = { viewModel.setPickerVisible(true) },
                                    label = { Text("Add lift") },
                                )
                            }
                        }
                    }
                    if (selected == null) {
                        item {
                            EmptyState(
                                title = "Add a lift",
                                body = "Start a free workout by adding the first exercise.",
                            )
                        }
                    } else {
                        item {
                            Text(selected.exercise.name, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Target ${selected.targetSets} × ${selected.targetReps}" +
                                    (selected.targetWeightKg?.let { " @ ${it.toKgLabel()}" } ?: ""),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.hint?.let { hint ->
                            item {
                                Card(onClick = viewModel::applySuggestedWeight, modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text("Suggested next weight", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            hint.suggestedWeightKg.toKgLabel(),
                                            style = MaterialTheme.typography.headlineMedium,
                                        )
                                        val reason = when (hint.action) {
                                            ProgressionAction.INCREASE ->
                                                "Hit all ${hint.targetReps} target reps last time. Add 2.5 kg."
                                            ProgressionAction.HOLD ->
                                                "1–2 reps short of ${hint.targetReps}. Keep ${hint.lastWeightKg.toKgLabel()}."
                                            ProgressionAction.DECREASE ->
                                                "3+ reps short of ${hint.targetReps}. Drop 2.5 kg."
                                        }
                                        Text("Last: ${hint.lastWeightKg.toKgLabel()} × ${hint.lastReps}")
                                        Text(reason)
                                    }
                                }
                            }
                        }
                        item {
                            KgStepper(
                                valueKg = state.draft.weightKg,
                                onAdjust = viewModel::adjustWeight,
                            )
                        }
                        item {
                            RepsStepper(
                                value = state.draft.reps,
                                onAdjust = viewModel::adjustReps,
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Warm-up set", style = MaterialTheme.typography.titleMedium)
                                Switch(
                                    checked = state.draft.isWarmup,
                                    onCheckedChange = viewModel::setWarmup,
                                )
                            }
                        }
                        item {
                            Text("RPE (optional)", style = MaterialTheme.typography.titleMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items((6..10).toList()) { value ->
                                    FilterChip(
                                        selected = state.draft.rpe == value,
                                        onClick = {
                                            viewModel.setRpe(if (state.draft.rpe == value) null else value)
                                        },
                                        label = { Text(value.toString()) },
                                    )
                                }
                            }
                        }
                        item {
                            PrimaryGymButton(text = "Log set", onClick = viewModel::logSet)
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        item {
                            Text("This exercise", style = MaterialTheme.typography.titleLarge)
                        }
                        val logged = session.setsFor(selected.exercise.id)
                        if (logged.isEmpty()) {
                            item {
                                EmptyState(
                                    title = "No sets yet",
                                    body = "Log weight in kg and reps. The rest timer starts after working sets.",
                                )
                            }
                        } else {
                            items(logged, key = { it.id }) { set ->
                                SetRow(set = set, onDelete = { viewModel.deleteSet(set.id) })
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = state.notes,
                            onValueChange = viewModel::setNotes,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Session notes") },
                        )
                    }
                    item {
                        PrimaryGymButton(
                            text = "Finish workout",
                            onClick = { viewModel.finishWorkout(onFinished) },
                        )
                    }
                }
            }
        }
    }

    if (state.showExercisePicker) {
        ExercisePickerSheet(
            query = state.searchQuery,
            results = state.searchResults,
            onQueryChange = viewModel::onSearchQuery,
            onSelect = viewModel::addExercise,
            onCreate = viewModel::createAndAddExercise,
            onDismiss = { viewModel.setPickerVisible(false) },
        )
    }

    if (confirmDiscard) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Leave workout?") },
            text = { Text("Discard deletes this in-progress session. Cancel to keep logging.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        viewModel.discardWorkout(onExit)
                    },
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onExit()
                    },
                ) { Text("Keep and exit") }
            },
        )
    }
}

@Composable
private fun SetRow(set: SetLog, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Set ${set.setNumber} · ${set.weightKg.toKgLabel()} × ${set.reps}")
                val extras = buildList {
                    if (set.isWarmup) add("Warm-up")
                    set.rpe?.let { add("RPE $it") }
                }.joinToString(" · ")
                if (extras.isNotEmpty()) {
                    Text(extras, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onDelete) { Text("Undo") }
        }
    }
}
