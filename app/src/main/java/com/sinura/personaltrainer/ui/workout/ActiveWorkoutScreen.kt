package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
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
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    val session = state.session
    val selected = session?.exercises?.firstOrNull { it.exercise.id == state.selectedExerciseId }
    val lastLoggedSet = session?.sets?.maxByOrNull { it.completedAt }
    val view = LocalView.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

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
                            onAdjust = viewModel::adjustRest,
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
                        val workingLogged = session.setsFor(selected.exercise.id).count { !it.isWarmup }
                        val nextWorkingSet = workingLogged + 1
                        item {
                            Text(selected.exercise.name, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Set $nextWorkingSet of ${selected.targetSets}",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                "Target ${selected.targetSets} × ${selected.targetReps}" +
                                    (selected.targetWeightKg?.let { " @ ${it.toKgLabel()}" } ?: ""),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.hint?.let { hint ->
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            "Suggested: ${hint.suggestedWeightKg.toKgLabel()}",
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
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
                                        PrimaryGymButton(
                                            text = "Use suggested",
                                            onClick = viewModel::applySuggestedWeight,
                                        )
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
                            PrimaryGymButton(
                                text = if (state.editingSetId == null) "Log set" else "Save set",
                                onClick = viewModel::logSet,
                            )
                            if (state.editingSetId != null) {
                                TextButton(onClick = viewModel::cancelEdit) { Text("Cancel edit") }
                            }
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        lastLoggedSet?.let { set ->
                            item {
                                LastSetCard(
                                    set = set,
                                    isEditing = state.editingSetId == set.id,
                                    onEdit = { viewModel.editSet(set.id) },
                                    onDelete = { viewModel.deleteSet(set.id) },
                                )
                            }
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
                                SetRow(
                                    set = set,
                                    isLatest = set.id == lastLoggedSet?.id,
                                    onEdit = { viewModel.editSet(set.id) },
                                    onDelete = { viewModel.deleteSet(set.id) },
                                )
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
                            onClick = { confirmFinish = true },
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

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish workout?") },
            text = { Text("This saves the session to history. You can still leave without finishing if you need more sets.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmFinish = false
                        viewModel.finishWorkout(onFinished)
                    },
                ) { Text("Finish") }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text("Keep logging") }
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
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
private fun LastSetCard(
    set: SetLog,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Last logged set", style = MaterialTheme.typography.labelLarge)
            Text(
                "${set.exerciseName} · ${set.weightKg.toKgLabel()} × ${set.reps}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            val extras = buildList {
                add("Set ${set.setNumber}")
                if (set.isWarmup) add("Warm-up")
                set.rpe?.let { add("RPE $it") }
                if (isEditing) add("Editing")
            }.joinToString(" · ")
            Text(extras, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) { Text("Edit") }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun SetRow(
    set: SetLog,
    isLatest: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Set ${set.setNumber} · ${set.weightKg.toKgLabel()} × ${set.reps}")
                val extras = buildList {
                    if (set.isWarmup) add("Warm-up")
                    set.rpe?.let { add("RPE $it") }
                    if (isLatest) add("Latest")
                }.joinToString(" · ")
                if (extras.isNotEmpty()) {
                    Text(extras, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isLatest) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
