package com.sinura.personaltrainer.ui.workout

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.domain.DayLabel
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.ProgressionCalculator
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.GymNoticeBanner
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.PersonalRecordBanner
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.RestDock
import com.sinura.personaltrainer.ui.components.RestIdleRow
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SetEntryPanel
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import kotlinx.coroutines.delay

private const val TAG = "PT/ActiveWorkoutScreen"

@Composable
fun ActiveWorkoutScreen(
    onExit: () -> Unit,
    onFinished: (String) -> Unit,
    viewModel: ActiveWorkoutViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rest by viewModel.restTimerState.collectAsStateWithLifecycle()
    val exitRequested by viewModel.exitRequested.collectAsStateWithLifecycle()
    val personalRecord by viewModel.personalRecord.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteSetId by rememberSaveable { mutableStateOf<String?>(null) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    val restNotificationsEnabled = rememberRestNotificationsEnabled()
    val session = state.session
    val selected = session?.exercises?.firstOrNull { it.exercise.id == state.selectedExerciseId }
    val unit = LocalWeightUnit.current
    val incrementLabel = ProgressionCalculator.INCREMENT_KG.toWeightLabel(unit)
    val view = LocalView.current
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Exit is state, not a callback captured into a coroutine: finishing writes to the database
    // first, and if the Activity is recreated in that window the captured NavController is dead
    // and the user is left staring at a workout that no longer exists. Acks BEFORE navigating —
    // for a pop, a duplicate would eat an extra screen, which is worse than the narrow window
    // where a recomposition between ack and pop drops the request.
    LaunchedEffect(exitRequested) {
        val reason = exitRequested ?: return@LaunchedEffect
        viewModel.onExitHandled()
        when (reason) {
            is WorkoutExit.Finished -> onFinished(reason.sessionId)
            WorkoutExit.Discarded -> onExit()
        }
    }

    // One leave path: system back behaves exactly like the top-bar X. Previously back popped
    // silently, skipping the notes flush in persistDraftForExit, so the two exits from the
    // same screen did different things. Only armed while a session is actually loaded, so
    // back still works normally on the loading and missing states.
    BackHandler(enabled = state.session != null) { confirmLeave = true }

    // Always composed, unlike the bottom bar. An error raised while no lift is selected —
    // a failed create from the picker in an empty free workout — previously had no reader at
    // all: it was written to state and rendered nowhere.
    val snackbarHostState = remember { SnackbarHostState() }
    val logBarVisible = session != null && selected != null
    LaunchedEffect(state.error, logBarVisible) {
        val message = state.error
        if (message != null && !logBarVisible) {
            snackbarHostState.showSnackbar(message)
        }
    }

    // The record's haptics and its acknowledgement live here rather than inside the banner:
    // the banner is a list item, and logging the set that breaks a record also scrolls the
    // list, so the item is disposed within a second. This screen is always composed, so the
    // beats fire once and the record is always cleared.
    LaunchedEffect(personalRecord) {
        if (personalRecord == null) return@LaunchedEffect
        Haptics.celebrate(view)
        delay(PERSONAL_RECORD_DWELL_MS)
        viewModel.onPersonalRecordShown()
    }

    val loggedForSelected = if (session != null && selected != null) {
        session.setsFor(selected.exercise.id)
    } else {
        emptyList()
    }

    // The set that was just logged is the only confirmation the action gives, and it lives
    // below the entry panel — frequently off-screen with the thumb still on the log button.
    // Bringing the list into view is what closes the feedback loop.
    //
    // Keyed on the selected lift so switching lifts resets the baseline, and gated on the
    // count *rising* so neither opening a session that already has sets nor deleting one
    // yanks the list around.
    var previousSetCount by remember(state.selectedExerciseId) { mutableIntStateOf(-1) }
    LaunchedEffect(state.selectedExerciseId, loggedForSelected.size) {
        val count = loggedForSelected.size
        val grew = previousSetCount in 0 until count
        previousSetCount = count
        if (grew) {
            listState.animateScrollToItem(
                index = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            WorkoutHeader(
                routineName = session?.routineName ?: "Workout",
                startedAt = session?.startedAt,
                workingSets = session?.sets?.count { !it.isWarmup } ?: 0,
                volumeKg = session?.workingVolumeKg() ?: 0.0,
                unit = unit,
                canFinish = session != null && session.sets.isNotEmpty(),
                onExit = { confirmLeave = true },
                onFinish = {
                    Haptics.commit(view)
                    viewModel.finishWorkout()
                },
            )
        },
        bottomBar = {
            if (logBarVisible) {
                LogBar(
                    editing = state.editingSetId != null,
                    error = state.error,
                    draftLabel = "${state.draft.weightKg.toWeightLabel(unit)} × ${state.draft.reps}",
                    onLog = {
                        Haptics.commit(view)
                        viewModel.logSet()
                    },
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
                // Terminal state, reached when the session flow has emitted null for a real
                // id: the workout was discarded, restored over, or the id came from a stale
                // notification. It must offer a way out — this used to be an unreachable
                // branch behind a spinner that never resolved.
                EmptyState(
                    title = "Workout missing",
                    body = "This session was finished, discarded, or replaced by a restore. " +
                        "Nothing was lost from your history.",
                    actionLabel = "Back to home",
                    onAction = onExit,
                    modifier = Modifier.padding(padding).padding(Metrics.gutter),
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    // Outside the scroll on purpose. See RestDock.
                    RestDock(
                        remainingSeconds = rest.remainingSeconds,
                        totalSeconds = rest.totalSeconds,
                        running = rest.running,
                        onSkip = viewModel::skipRest,
                        onAdjust = viewModel::adjustRest,
                    )

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Metrics.gutter,
                            end = Metrics.gutter,
                            top = Metrics.space3,
                            bottom = Metrics.space7,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                    ) {
                        if (!restNotificationsEnabled) {
                            item(key = "notif") { RestNotificationsDisabledBanner() }
                        }
                        personalRecord?.let { moment ->
                            item(key = "pr-moment") {
                                PersonalRecordBanner(
                                    headline = personalRecordHeadline(moment),
                                    detail = "${moment.exerciseName.ifBlank { "This lift" }} · " +
                                        "${moment.weightKg.toWeightLabel(unit)} × ${moment.reps}",
                                    onDismiss = viewModel::onPersonalRecordShown,
                                )
                            }
                        }
                        item(key = "switcher") {
                            LiftSwitcher(
                                lifts = session.exercises,
                                selectedExerciseId = state.selectedExerciseId,
                                logged = session.sets,
                                onSelect = viewModel::selectExercise,
                                onAdd = { viewModel.setPickerVisible(true) },
                            )
                        }

                        if (!session.hasLifts()) {
                            item(key = "empty-lifts") {
                                EmptyState(
                                    title = "Add a lift",
                                    body = "Pick the first exercise, then log weight and reps.",
                                    actionLabel = "Add a lift",
                                    onAction = { viewModel.setPickerVisible(true) },
                                )
                            }
                        } else if (selected == null) {
                            item(key = "pick-lift") {
                                EmptyState(
                                    title = "Pick a lift",
                                    body = "Choose one above to keep logging.",
                                    actionLabel = "Add a lift",
                                    onAction = { viewModel.setPickerVisible(true) },
                                )
                            }
                            if (session.sets.isNotEmpty()) {
                                item(key = "sets-label") { Kicker("Sets") }
                                items(session.sets, key = { it.id }) { set ->
                                    SetRow(
                                        set = set,
                                        isLatest = set.id == session.sets.maxByOrNull { it.completedAt }?.id,
                                        isEditing = state.editingSetId == set.id,
                                        onEdit = { viewModel.editSet(set.id) },
                                        onDelete = { pendingDeleteSetId = set.id },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        } else {
                            val workingLogged = loggedForSelected.count { !it.isWarmup }
                            val latestSetId = loggedForSelected.maxByOrNull { it.completedAt }?.id

                            item(key = "lift-header") {
                                CurrentLiftHeader(
                                    lift = selected,
                                    workingLogged = workingLogged,
                                    unit = unit,
                                )
                            }
                            state.lastPerformance?.let { last ->
                                item(key = "last-time") { LastTimeStrip(summary = last, unit = unit) }
                            }
                            state.hint?.let { hint ->
                                item(key = "progression") {
                                    ProgressionStrip(
                                        hint = hint,
                                        incrementLabel = incrementLabel,
                                        unit = unit,
                                        onApply = viewModel::applySuggestedWeight,
                                    )
                                }
                            }
                            item(key = "entry") {
                                SetEntryPanel(
                                    weightKg = state.draft.weightKg,
                                    reps = state.draft.reps,
                                    onWeightKgChange = viewModel::setWeight,
                                    onRepsAdjust = viewModel::adjustReps,
                                    unit = unit,
                                )
                            }
                            item(key = "secondary") {
                                SecondaryLogOptions(
                                    warmup = state.draft.isWarmup,
                                    rpe = state.draft.rpe,
                                    onWarmup = viewModel::setWarmup,
                                    onRpe = viewModel::setRpe,
                                )
                            }
                            if (!rest.running) {
                                item(key = "rest-idle") {
                                    RestIdleRow(
                                        totalSeconds = rest.totalSeconds,
                                        onPreset = viewModel::startPreset,
                                        onCustom = viewModel::startCustom,
                                    )
                                }
                            }
                            item(key = "sets-label") { Kicker("Sets") }
                            items(loggedForSelected, key = { it.id }) { set ->
                                SetRow(
                                    set = set,
                                    isLatest = set.id == latestSetId,
                                    isEditing = state.editingSetId == set.id,
                                    onEdit = { viewModel.editSet(set.id) },
                                    onDelete = { pendingDeleteSetId = set.id },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        item(key = "notes") {
                            NotesBlock(
                                notes = state.notes,
                                expanded = notesOpen,
                                onToggle = { notesOpen = !notesOpen },
                                onChange = viewModel::setNotes,
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

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave workout?", style = InstrumentType.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                    Text(
                        "Your sets and rest timer keep running. Pick this session back up from Home.",
                        style = InstrumentType.body,
                        color = TextSecondary,
                    )
                    // Discarding is destructive and deliberately NOT a dialog button: it sits
                    // apart from the two safe actions and routes through its own named confirm,
                    // so it can never be hit by mis-tapping next to "Keep and exit".
                    TextButton(
                        onClick = {
                            confirmLeave = false
                            confirmDiscard = true
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = Metrics.space1),
                    ) {
                        Text(
                            "Discard this workout instead",
                            style = InstrumentType.bodyStrong,
                            color = Danger,
                        )
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
                ) { Text("Keep and exit", style = InstrumentType.bodyStrong, color = Volt) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text("Stay", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            },
        )
    }

    if (confirmDiscard) {
        val loggedSets = session?.sets?.size ?: 0
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this workout?", style = InstrumentType.title) },
            text = {
                Text(
                    if (loggedSets > 0) {
                        "This deletes the session and its $loggedSets logged " +
                            (if (loggedSets == 1) "set" else "sets") + ". This cannot be undone."
                    } else {
                        "This deletes the session. This cannot be undone."
                    },
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        viewModel.discardWorkout()
                    },
                ) { Text("Discard", style = InstrumentType.bodyStrong, color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text("Cancel", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            },
        )
    }

    pendingDeleteSetId?.let { setId ->
        val set = session?.sets?.firstOrNull { it.id == setId }
        AlertDialog(
            onDismissRequest = { pendingDeleteSetId = null },
            title = { Text("Delete this set?", style = InstrumentType.title) },
            text = {
                Text(
                    if (set == null) {
                        "Remove it from this session."
                    } else {
                        "Delete ${set.weightKg.toWeightLabel(unit)} × ${set.reps}${if (set.isWarmup) " warm-up" else ""}?"
                    },
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSet(setId)
                        pendingDeleteSetId = null
                    },
                ) { Text("Delete", style = InstrumentType.bodyStrong, color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSetId = null }) {
                    Text("Keep", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            },
        )
    }
}

private fun personalRecordHeadline(moment: PersonalRecordMoment): String = when {
    PersonalRecordKind.WEIGHT in moment.kinds -> "Heaviest ever"
    PersonalRecordKind.ESTIMATED_ONE_REP_MAX in moment.kinds -> "Strongest set ever"
    else -> "Most reps at this weight"
}

/**
 * The session's own chrome, carrying the session's own telemetry.
 *
 * This used to be a stock app bar spending its entire width on a routine name and a close
 * icon: the most data-driven screen in the product had less live information in its header
 * than a notes app, and the duration of a workout was computed for the first time only
 * after it had ended.
 *
 * Finish moved up here too. It was the last item of the scrolling content, so ending a
 * session meant scrolling to the bottom of a layout designed for mid-set logging — and then
 * confirming a dialog whose own body text admitted nothing was at stake. Finishing is safe,
 * non-destructive, and followed immediately by a summary that *is* the confirmation, so it
 * now happens on one tap.
 */
@Composable
private fun WorkoutHeader(
    routineName: String,
    startedAt: Long?,
    workingSets: Int,
    volumeKg: Double,
    unit: WeightUnit,
    canFinish: Boolean,
    onExit: () -> Unit,
    onFinish: () -> Unit,
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(startedAt) {
        if (startedAt == null) return@LaunchedEffect
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1_000L)
                .coerceAtLeast(0L)
                .toInt()
            delay(1_000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(start = Metrics.space2, end = Metrics.space4, bottom = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Outlined.Close, contentDescription = "Exit workout", tint = TextSecondary)
            }
            Text(
                routineName,
                modifier = Modifier.weight(1f),
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onFinish, enabled = canFinish) {
                Text(
                    "Finish",
                    style = InstrumentType.bodyStrong,
                    // Real, but secondary to logging: the accent belongs on the log button.
                    color = if (canFinish) TextPrimary else TextTertiary,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Metrics.space2),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space6),
        ) {
            MetricCluster(
                value = RestTimer.formatClock(elapsedSeconds),
                label = "elapsed",
                horizontalAlignment = Alignment.Start,
            )
            MetricCluster(
                value = workingSets.toString(),
                label = "sets",
                horizontalAlignment = Alignment.Start,
            )
            MetricCluster(
                value = WeightConverter.formatGroupedNumber(
                    WeightConverter.toDisplayValue(volumeKg, unit),
                ),
                label = unit.suffix,
                horizontalAlignment = Alignment.Start,
            )
        }
    }
}

/**
 * The one action that matters, and the values it is about to commit.
 *
 * The button is pinned while the entry panel scrolls, so after reviewing the set list a
 * lifter could face a full-width commit button whose payload was nowhere on screen. Echoing
 * the draft in the label means the tap is never blind.
 */
@Composable
private fun LogBar(
    editing: Boolean,
    error: String?,
    draftLabel: String,
    onLog: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        error?.let {
            Text(it, style = InstrumentType.body, color = Danger)
        }
        if (editing) {
            TextButton(onClick = onCancelEdit, modifier = Modifier.align(Alignment.End)) {
                Text("Cancel edit", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
        PrimaryGymButton(
            text = if (editing) "Save $draftLabel" else "Log $draftLabel",
            onClick = onLog,
            height = Metrics.commit,
            hapticFeedback = false,
        )
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
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        items(lifts, key = { it.id }) { item ->
            val working = logged.count { it.exerciseId == item.exercise.id && !it.isWarmup }
            InstrumentChip(
                label = if (item.targetSets > 0) {
                    "${item.exercise.name}  $working/${item.targetSets}"
                } else {
                    item.exercise.name
                },
                selected = item.exercise.id == selectedExerciseId,
                onClick = { onSelect(item.exercise.id) },
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "add-lift") {
            InstrumentChip(label = "+ Add lift", selected = false, onClick = onAdd)
        }
    }
}

@Composable
private fun CurrentLiftHeader(
    lift: SessionExercise,
    workingLogged: Int,
    unit: WeightUnit,
) {
    val targetSets = lift.targetSets.coerceAtLeast(1)
    val targetReps = lift.targetReps.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        Text(lift.exercise.name, style = InstrumentType.display, color = TextPrimary)
        // Progress as a glyph rather than as a second display numeral. "Set 3 of 5" was set
        // at 28sp, competing with the weight it sits above for the eye's attention while
        // saying much less.
        SetDots(completed = workingLogged, target = targetSets)
        val targetWeight = lift.targetWeightKg?.takeIf { it > 0.0 }?.toWeightLabel(unit)
        Text(
            buildString {
                append("Set ${workingLogged + 1} of $targetSets")
                append(" · target $targetSets × $targetReps")
                if (targetWeight != null) append(" @ $targetWeight")
            },
            style = InstrumentType.caption,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SetDots(completed: Int, target: Int) {
    val total = maxOf(target, completed)
    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(Metrics.space2)
                    .clip(CircleShape)
                    // Progress, not an action. Volt on this screen is reserved for the
                    // things that are live or about to be tapped.
                    .background(if (index < completed) TextSecondary else Hairline),
            )
        }
    }
}

/**
 * What this lift looked like last time, in full.
 *
 * Complementary to [ProgressionStrip], not a duplicate of it: the strip states the decision
 * ("top set 100 kg x 5, add 2.5"), this states the evidence — every working set of the last
 * session, so a lifter can see that the top set came after two easy ones or at the end of a
 * grind, which is the difference between adding weight and repeating it.
 */
@Composable
private fun LastTimeStrip(
    summary: ExerciseSessionSummary,
    unit: WeightUnit,
) {
    val relative = remember(summary.performedAtMs) {
        DayLabel.relative(summary.performedAtMs, System.currentTimeMillis())
    }
    val absolute = remember(summary.performedAtMs) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(summary.performedAtMs))
    }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        Kicker("Last time · ${relative ?: absolute}")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            items(summary.sets) { set ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(Surface1)
                        .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.xs))
                        .padding(horizontal = Metrics.space3, vertical = Metrics.space2),
                ) {
                    Text(
                        "${set.weightKg.toWeightLabel(unit)} × ${set.reps}",
                        style = InstrumentType.numeralSm,
                        color = TextSecondary,
                    )
                }
            }
        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Surface2)
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.md))
            .padding(horizontal = Metrics.space4, vertical = Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            // Labelled "Top set" because that is now literally what these numbers
            // are: the heaviest working set of the last session, not the last logged.
            Text(
                "Top set ${hint.lastWeightKg.toWeightLabel(unit)} × ${hint.lastReps}  →  ${hint.suggestedWeightKg.toWeightLabel(unit)}",
                style = InstrumentType.numeralSm,
                color = TextPrimary,
            )
            Text(reason, style = InstrumentType.caption, color = TextSecondary)
        }
        TextButton(onClick = onApply) {
            Text("Use", style = InstrumentType.bodyStrong, color = Volt)
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
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "warmup") {
            InstrumentChip(
                label = "Warm-up",
                selected = warmup,
                onClick = { onWarmup(!warmup) },
            )
        }
        item(key = "rpe-label") {
            Kicker("RPE", modifier = Modifier.padding(horizontal = Metrics.space2))
        }
        items((6..10).toList()) { value ->
            InstrumentChip(
                label = value.toString(),
                selected = rpe == value,
                onClick = { onRpe(if (rpe == value) null else value) },
            )
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
        TextButton(onClick = onToggle, contentPadding = PaddingValues(0.dp)) {
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = TextSecondary,
            )
            Text(
                if (expanded) "Hide notes" else if (notes.isBlank()) "Session notes" else "Session notes · saved",
                style = InstrumentType.bodyStrong,
                color = TextSecondary,
                modifier = Modifier.padding(start = Metrics.space2),
            )
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

/**
 * A logged set.
 *
 * State is carried by the design rather than narrated in the text. The row used to append
 * its own status into a meta string — "Set 2 · RPE 8 · Latest · Editing" — which is the
 * definitive tell of an undesigned surface: something a colour, a rule or a position should
 * say, written out in words instead. The latest set now wears an accent rule on its leading
 * edge, and the one being edited is outlined in the accent, matching the log button that is
 * simultaneously offering to save it.
 */
@Composable
private fun SetRow(
    set: SetLog,
    isLatest: Boolean,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = LocalWeightUnit.current
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isLatest) Surface2 else Surface1)
            .border(
                width = if (isEditing) 2.dp else Metrics.hairline,
                color = if (isEditing) Volt else Hairline,
                shape = shape,
            )
            .padding(end = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = LATEST_RULE_WIDTH, height = LATEST_RULE_HEIGHT)
                .background(if (isLatest) HairlineStrong else Color.Transparent),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Metrics.space3, top = Metrics.space3, bottom = Metrics.space3),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                "${set.weightKg.toWeightLabel(unit)}  ×  ${set.reps}",
                style = InstrumentType.numeralSm,
                color = TextPrimary,
            )
            val extras = buildList {
                add("Set ${set.setNumber}")
                if (set.isWarmup) add("Warm-up")
                set.rpe?.let { add("RPE $it") }
            }.joinToString(" · ")
            Text(extras, style = InstrumentType.caption, color = TextSecondary)
        }
        if (isLatest) {
            TextButton(onClick = onEdit) {
                Text("Edit", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
            TextButton(onClick = onDelete) {
                Text("Delete", style = InstrumentType.bodyStrong, color = Danger)
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
    GymNoticeBanner(
        title = "Rest alerts are off",
        body = "Notifications are blocked, so you won't see the countdown or hear " +
            "\"Rest done\" with the phone in your pocket.",
        actionLabel = "Turn on notifications",
        onAction = {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (thrown: Exception) {
                // Some OEM builds do not expose the per-app notification screen.
                AppLog.w(TAG, "App notification settings unavailable; falling back", thrown)
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        modifier = modifier,
    )
}

private val LATEST_RULE_WIDTH = 3.dp
private val LATEST_RULE_HEIGHT = 44.dp

private const val PERSONAL_RECORD_DWELL_MS = 6_000L
