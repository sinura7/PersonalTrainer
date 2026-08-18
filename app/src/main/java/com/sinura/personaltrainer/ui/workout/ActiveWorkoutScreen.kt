package com.sinura.personaltrainer.ui.workout

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.pm.PackageManager
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.ProgressionCalculator
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.RepsStepper
import com.sinura.personaltrainer.ui.components.RestTimerBar
import com.sinura.personaltrainer.ui.components.WeightStepper
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onExit: () -> Unit,
    onFinished: () -> Unit,
    viewModel: ActiveWorkoutViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rest by viewModel.restTimerState.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteSetId by rememberSaveable { mutableStateOf<String?>(null) }
    RequestRestNotificationPermission()
    val session = state.session
    val selected = session?.exercises?.firstOrNull { it.exercise.id == state.selectedExerciseId }
    val lastLoggedSet = session?.sets
        ?.filter { selected == null || it.exerciseId == selected.exercise.id }
        ?.maxByOrNull { it.completedAt }
    val unit = LocalWeightUnit.current
    val incrementLabel = ProgressionCalculator.INCREMENT_KG.toWeightLabel(unit)
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
                ScreenLoading(modifier = Modifier.padding(padding))
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
                            remainingSeconds = rest.remainingSeconds,
                            totalSeconds = rest.totalSeconds,
                            running = rest.running,
                            onSkip = viewModel::skipRest,
                            onAdjust = viewModel::adjustRest,
                            onPreset = viewModel::startPreset,
                            onCustom = viewModel::startCustom,
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
                                title = "No lifts yet",
                                body = "Add the first exercise, then log weight and reps. You can keep adding lifts as you move around the gym.",
                                actionLabel = "Add a lift",
                                onAction = { viewModel.setPickerVisible(true) },
                            )
                        }
                    } else {
                        val workingLogged = session.setsFor(selected.exercise.id).count { !it.isWarmup }
                        val nextWorkingSet = workingLogged + 1
                        item {
                            val targetSets = selected.targetSets.coerceAtLeast(1)
                            val targetReps = selected.targetReps.coerceAtLeast(1)
                            Text(selected.exercise.name, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Set $nextWorkingSet of $targetSets",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                "Target $targetSets × $targetReps" +
                                    (selected.targetWeightKg?.takeIf { it > 0.0 }?.let { " @ ${it.toWeightLabel(unit)}" } ?: ""),
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
                                            "Suggested: ${hint.suggestedWeightKg.toWeightLabel(unit)}",
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        )
                                        val reason = when (hint.action) {
                                            ProgressionAction.INCREASE ->
                                                "Hit all ${hint.targetReps} target reps last time. Add $incrementLabel."
                                            ProgressionAction.HOLD ->
                                                "1–2 reps short of ${hint.targetReps}. Keep ${hint.lastWeightKg.toWeightLabel(unit)}."
                                            ProgressionAction.DECREASE ->
                                                "3+ reps short of ${hint.targetReps}. Drop $incrementLabel."
                                        }
                                        Text("Last: ${hint.lastWeightKg.toWeightLabel(unit)} × ${hint.lastReps}")
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
                            WeightStepper(
                                valueKg = state.draft.weightKg,
                                onWeightKgChange = viewModel::setWeight,
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
                        }
                        lastLoggedSet?.let { set ->
                            item {
                                LastSetCard(
                                    set = set,
                                    isEditing = state.editingSetId == set.id,
                                    onEdit = { viewModel.editSet(set.id) },
                                    onDelete = { pendingDeleteSetId = set.id },
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
                                    body = "Set the weight and reps above, then tap Log set. Rest starts after working sets.",
                                )
                            }
                        } else {
                            items(logged, key = { it.id }) { set ->
                                SetRow(
                                    set = set,
                                    isLatest = set.id == lastLoggedSet?.id,
                                    onEdit = { viewModel.editSet(set.id) },
                                    onDelete = { pendingDeleteSetId = set.id },
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
                            onClick = {
                                if (session.sets.isEmpty()) {
                                    viewModel.finishWorkout(onFinished)
                                } else {
                                    confirmFinish = true
                                }
                            },
                            enabled = session.sets.isNotEmpty(),
                        )
                        if (session.sets.isEmpty()) {
                            Text(
                                "Log at least one set before finishing.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
        ConfirmActionDialog(
            title = "Finish workout?",
            body = "This saves the session to history. Leave without finishing if you still have sets to log.",
            confirmLabel = "Finish",
            dismissLabel = "Keep logging",
            onConfirm = {
                confirmFinish = false
                viewModel.finishWorkout(onFinished)
            },
            onDismiss = { confirmFinish = false },
        )
    }

    if (confirmDiscard) {
        ConfirmActionDialog(
            title = "Leave workout?",
            body = "Discard deletes this session. Keep and exit saves your draft and leaves the rest timer running.",
            confirmLabel = "Discard",
            dismissLabel = "Keep and exit",
            onConfirm = {
                confirmDiscard = false
                viewModel.discardWorkout(onExit)
            },
            onDismiss = {
                confirmDiscard = false
                viewModel.persistDraftForExit()
                onExit()
            },
        )
    }

    pendingDeleteSetId?.let { setId ->
        val set = session?.sets?.firstOrNull { it.id == setId }
        ConfirmActionDialog(
            title = "Delete this set?",
            body = if (set == null) {
                "Remove this set from the session."
            } else {
                "Delete ${set.weightKg.toWeightLabel(unit)} × ${set.reps}${if (set.isWarmup) " warm-up" else ""}?"
            },
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteSet(setId)
                pendingDeleteSetId = null
            },
            onDismiss = { pendingDeleteSetId = null },
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
            Text("Last set this lift", style = MaterialTheme.typography.labelLarge)
            Text(
                "${set.exerciseName} · ${set.weightKg.toWeightLabel(LocalWeightUnit.current)} × ${set.reps}",
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
                Text("Set ${set.setNumber} · ${set.weightKg.toWeightLabel(LocalWeightUnit.current)} × ${set.reps}")
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

@Composable
private fun RequestRestNotificationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
