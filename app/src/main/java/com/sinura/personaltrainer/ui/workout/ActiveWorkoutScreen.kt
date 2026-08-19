package com.sinura.personaltrainer.ui.workout

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.ProgressionCalculator
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.GymNumericStyle
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.RepsStepper
import com.sinura.personaltrainer.ui.components.RestTimerBar
import com.sinura.personaltrainer.ui.components.ScreenLoading
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
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteSetId by rememberSaveable { mutableStateOf<String?>(null) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    val restNotificationsEnabled = rememberRestNotificationsEnabled()
    val session = state.session
    val selected = session?.exercises?.firstOrNull { it.exercise.id == state.selectedExerciseId }
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
                title = {
                    Text(
                        session?.routineName ?: "Workout",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { confirmLeave = true }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Exit workout")
                    }
                },
            )
        },
        bottomBar = {
            if (session != null && selected != null) {
                LogBar(
                    editing = state.editingSetId != null,
                    error = state.error,
                    onLog = viewModel::logSet,
                    onCancelEdit = viewModel::cancelEdit,
                )
            }
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
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (!restNotificationsEnabled) {
                        item { RestNotificationsDisabledBanner() }
                    }
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
                        LiftSwitcher(
                            lifts = session.exercises,
                            selectedExerciseId = state.selectedExerciseId,
                            logged = session.sets,
                            onSelect = viewModel::selectExercise,
                            onAdd = { viewModel.setPickerVisible(true) },
                        )
                    }
                    if (!session.hasLifts()) {
                        item {
                            EmptyState(
                                title = "Add a lift",
                                body = "Pick the first exercise, then log weight and reps.",
                                actionLabel = "Add a lift",
                                onAction = { viewModel.setPickerVisible(true) },
                            )
                        }
                    } else if (selected == null) {
                        item {
                            EmptyState(
                                title = "Pick a lift",
                                body = "Choose one above to keep logging.",
                                actionLabel = "Add a lift",
                                onAction = { viewModel.setPickerVisible(true) },
                            )
                        }
                        if (session.sets.isNotEmpty()) {
                            item { SectionLabel("Sets") }
                            items(session.sets, key = { it.id }) { set ->
                                SetRow(
                                    set = set,
                                    isLatest = set.id == session.sets.maxByOrNull { it.completedAt }?.id,
                                    isEditing = state.editingSetId == set.id,
                                    onEdit = { viewModel.editSet(set.id) },
                                    onDelete = { pendingDeleteSetId = set.id },
                                )
                            }
                        }
                    } else {
                        val workingLogged = session.setsFor(selected.exercise.id).count { !it.isWarmup }
                        val logged = session.setsFor(selected.exercise.id)
                        val latestSetId = logged.maxByOrNull { it.completedAt }?.id
                        item {
                            CurrentLiftHeader(
                                lift = selected,
                                nextWorkingSet = workingLogged + 1,
                                unit = unit,
                            )
                        }
                        state.hint?.let { hint ->
                            item {
                                ProgressionStrip(
                                    hint = hint,
                                    incrementLabel = incrementLabel,
                                    unit = unit,
                                    onApply = viewModel::applySuggestedWeight,
                                )
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
                            SecondaryLogOptions(
                                warmup = state.draft.isWarmup,
                                rpe = state.draft.rpe,
                                onWarmup = viewModel::setWarmup,
                                onRpe = viewModel::setRpe,
                            )
                        }
                        item { SectionLabel("Sets") }
                        if (logged.isEmpty()) {
                            item {
                                Text(
                                    "No sets yet. Log the first one below.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        } else {
                            items(logged, key = { it.id }) { set ->
                                SetRow(
                                    set = set,
                                    isLatest = set.id == latestSetId,
                                    isEditing = state.editingSetId == set.id,
                                    onEdit = { viewModel.editSet(set.id) },
                                    onDelete = { pendingDeleteSetId = set.id },
                                )
                            }
                        }
                    }
                    item {
                        NotesBlock(
                            notes = state.notes,
                            expanded = notesOpen,
                            onToggle = { notesOpen = !notesOpen },
                            onChange = viewModel::setNotes,
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                if (session.sets.isEmpty()) {
                                    viewModel.finishWorkout(onFinished)
                                } else {
                                    confirmFinish = true
                                }
                            },
                            enabled = session.sets.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text("Finish workout", style = MaterialTheme.typography.titleMedium)
                        }
                        if (session.sets.isEmpty()) {
                            Text(
                                "Log a set to finish.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
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
            text = { Text("Saves this session to history.") },
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

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave workout?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your sets and rest timer keep running. Pick this session back up from Home.")
                    // Discarding is destructive and deliberately NOT a dialog button: it sits
                    // apart from the two safe actions and routes through its own named confirm,
                    // so it can never be hit by mis-tapping next to "Keep and exit".
                    TextButton(
                        onClick = {
                            confirmLeave = false
                            confirmDiscard = true
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text("Discard this workout instead", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        viewModel.persistDraftForExit()
                        onExit()
                    },
                ) { Text("Keep and exit") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Stay") }
            },
        )
    }

    if (confirmDiscard) {
        val loggedSets = session?.sets?.size ?: 0
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this workout?") },
            text = {
                Text(
                    if (loggedSets > 0) {
                        "This deletes the session and its $loggedSets logged " +
                            (if (loggedSets == 1) "set" else "sets") + ". This cannot be undone."
                    } else {
                        "This deletes the session. This cannot be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        viewModel.discardWorkout(onExit)
                    },
                ) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") }
            },
        )
    }

    pendingDeleteSetId?.let { setId ->
        val set = session?.sets?.firstOrNull { it.id == setId }
        AlertDialog(
            onDismissRequest = { pendingDeleteSetId = null },
            title = { Text("Delete this set?") },
            text = {
                Text(
                    if (set == null) {
                        "Remove it from this session."
                    } else {
                        "Delete ${set.weightKg.toWeightLabel(unit)} × ${set.reps}${if (set.isWarmup) " warm-up" else ""}?"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSet(setId)
                        pendingDeleteSetId = null
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSetId = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun LogBar(
    editing: Boolean,
    error: String?,
    onLog: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (editing) {
                TextButton(onClick = onCancelEdit, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel edit")
                }
            }
            PrimaryGymButton(
                text = if (editing) "Save set" else "Log set",
                onClick = onLog,
                height = 72.dp,
            )
        }
    }
}

@Composable
private fun LiftSwitcher(
    lifts: List<SessionExercise>,
    selectedExerciseId: String?,
    logged: List<SetLog>,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(lifts, key = { it.id }) { item ->
            val working = logged.count { it.exerciseId == item.exercise.id && !it.isWarmup }
            FilterChip(
                selected = item.exercise.id == selectedExerciseId,
                onClick = { onSelect(item.exercise.id) },
                label = {
                    Text(
                        if (item.targetSets > 0) {
                            "${item.exercise.name}  $working/${item.targetSets}"
                        } else {
                            item.exercise.name
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (item.exercise.id == selectedExerciseId) FontWeight.Bold else FontWeight.Medium,
                    )
                },
            )
        }
        item {
            AssistChip(onClick = onAdd, label = { Text("Add lift") })
        }
    }
}

@Composable
private fun CurrentLiftHeader(
    lift: SessionExercise,
    nextWorkingSet: Int,
    unit: WeightUnit,
) {
    val targetSets = lift.targetSets.coerceAtLeast(1)
    val targetReps = lift.targetReps.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            lift.exercise.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Set $nextWorkingSet of $targetSets",
            style = GymNumericStyle.copy(fontSize = 28.sp, lineHeight = 32.sp),
        )
        val targetWeight = lift.targetWeightKg?.takeIf { it > 0.0 }?.toWeightLabel(unit)
        Text(
            buildString {
                append("Target $targetSets × $targetReps")
                if (targetWeight != null) append(" @ $targetWeight")
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ProgressionStrip(
    hint: ProgressionHint,
    incrementLabel: String,
    unit: WeightUnit,
    onApply: () -> Unit,
) {
    val reason = when (hint.action) {
        ProgressionAction.INCREASE -> "Hit target. Add $incrementLabel."
        ProgressionAction.HOLD -> "Close. Keep ${hint.lastWeightKg.toWeightLabel(unit)}."
        ProgressionAction.DECREASE -> "Missed target. Drop $incrementLabel."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    // Labelled "Top set" because that is now literally what these numbers
                    // are: the heaviest working set of the last session, not the last logged.
                    "Top set ${hint.lastWeightKg.toWeightLabel(unit)} × ${hint.lastReps}  →  ${hint.suggestedWeightKg.toWeightLabel(unit)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(reason, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onApply) { Text("Use") }
        }
    }
}

@Composable
private fun SecondaryLogOptions(
    warmup: Boolean,
    rpe: Int?,
    onWarmup: (Boolean) -> Unit,
    onRpe: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = warmup,
            onClick = { onWarmup(!warmup) },
            label = { Text("Warm-up") },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            item {
                Text(
                    "RPE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            items((6..10).toList()) { value ->
                FilterChip(
                    selected = rpe == value,
                    onClick = { onRpe(if (rpe == value) null else value) },
                    label = { Text(value.toString()) },
                )
            }
        }
    }
}

@Composable
private fun NotesBlock(
    notes: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (String) -> Unit,
) {
    Column {
        TextButton(onClick = onToggle) {
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
            )
            Text(if (expanded) "Hide notes" else if (notes.isBlank()) "Session notes" else "Session notes · saved")
        }
        if (expanded) {
            OutlinedTextField(
                value = notes,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
                minLines = 2,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SetRow(
    set: SetLog,
    isLatest: Boolean,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val unit = LocalWeightUnit.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isLatest) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        },
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${set.weightKg.toWeightLabel(unit)}  ×  ${set.reps}",
                    style = GymNumericStyle.copy(fontSize = 20.sp, lineHeight = 24.sp),
                )
                val extras = buildList {
                    add("Set ${set.setNumber}")
                    if (set.isWarmup) add("Warm-up")
                    set.rpe?.let { add("RPE $it") }
                    if (isLatest) add("Latest")
                    if (isEditing) add("Editing")
                }.joinToString(" · ")
                Text(extras, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (isLatest) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

/**
 * Asks for POST_NOTIFICATIONS once, then reports whether rest notifications can actually
 * be shown.
 *
 * The result used to be discarded. On Android 13+ a denial silently removes BOTH off-screen
 * rest surfaces — the countdown and the "Rest done" alert — so a pocketed phone shows nothing
 * at all, with no way back: after two denials the system dialog stops appearing entirely.
 * The returned flag drives an in-workout banner with a deep link to app notification
 * settings, and re-checks on every resume so it disappears the moment the user grants.
 */
@Composable
private fun rememberRestNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

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

    // Returning from system settings is a resume, not a recomposition — re-read there.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return enabled
}

/** Tells the lifter their rest clock is invisible off-screen, and offers the one fix. */
@Composable
private fun RestNotificationsDisabledBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Rest alerts are off",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Notifications are blocked, so you won't see the countdown or hear " +
                    "\"Rest done\" with the phone in your pocket.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) {
                Text("Turn on notifications", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}