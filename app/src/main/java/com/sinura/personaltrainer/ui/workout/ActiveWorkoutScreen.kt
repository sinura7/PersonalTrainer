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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.sinura.personaltrainer.domain.ProgressionCopy
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RestNotificationCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RpeCopy
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerMode
import com.sinura.personaltrainer.domain.ExercisePickerState
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutCopy
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.EquipmentGlyphIcon
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.LeaveWorkoutDialog
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.NotesBlock
import com.sinura.personaltrainer.ui.components.PersonalRecordBanner
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.RestDock
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SetEntryPanel
import com.sinura.personaltrainer.ui.components.glyphFor
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import kotlinx.coroutines.delay

private const val TAG = "PT/ActiveWorkoutScreen"

/** Stable semantics for the critical device journey; copy remains free to improve. */
object WorkoutTestTags {
    const val CONTENT = "workout-content"
    const val LOG_SET = "workout-log-set"
    const val FINISH = "workout-finish"
    const val NOTIF_RECOVERY = "workout-notif-recovery"
    const val CURRENT_LIFT = "workout-current-lift"
    const val SET_ENTRY = "workout-set-entry"
    const val REST_BAR = "workout-rest-bar"
    const val REST_IDLE = "workout-rest-idle"
    const val MICRO_REC = "workout-micro-rec"
    const val MICRO_REC_APPLY = "workout-micro-rec-apply"
    const val MICRO_REC_WHY = "workout-micro-rec-why"
}

@Composable
fun ActiveWorkoutScreen(
    onExit: () -> Unit,
    onFinished: (String) -> Unit,
    onOpenRest: (String) -> Unit = {},
    viewModel: ActiveWorkoutViewModel = viewModel(),
    restNotificationsEnabledOverride: Boolean? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rest by viewModel.restTimerState.collectAsStateWithLifecycle()
    val microRec by viewModel.microRec.collectAsStateWithLifecycle()
    val exitRequested by viewModel.exitRequested.collectAsStateWithLifecycle()
    val personalRecord by viewModel.personalRecord.collectAsStateWithLifecycle()
    val deletedSet by viewModel.deletedSet.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var confirmRemoveLift by rememberSaveable { mutableStateOf(false) }
    val restNotificationsEnabled = restNotificationsEnabledOverride
        ?: rememberRestNotificationsEnabled()
    val session = state.session
    val selected = session?.exercises?.firstOrNull { it.exercise.id == state.selectedExerciseId }
    // Keyed on the session, not recomputed per frame: the header below it redraws every second
    // as the elapsed clock ticks, and this walks every set of the workout.
    val sessionWork = remember(session) { session?.work() ?: SetWork.NONE }
    val unit = LocalWeightUnit.current
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

    // Deleting a set is immediate now, so the reversal has to be: the snackbar IS the confirm,
    // moved to after the act instead of in front of every one of them. It carries the set's own
    // numbers because "Set deleted" alone cannot tell you which set you just lost.
    LaunchedEffect(deletedSet) {
        val removed = deletedSet ?: return@LaunchedEffect
        val outcome = snackbarHostState.showSnackbar(
            message = "Set deleted · " + SetCopy.setLine(removed.weightKg, removed.reps, LoadClass.of(selected?.exercise?.loadType), unit),
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (outcome == SnackbarResult.ActionPerformed) {
            viewModel.undoDeleteSet()
        } else {
            viewModel.onUndoOfferHandled()
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
                work = sessionWork,
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
                    draftLabel = SetCopy.setLine(state.draft.weightKg, state.draft.reps, LoadClass.of(selected?.exercise?.loadType), unit),
                    microRec = microRec,
                    loadClass = LoadClass.of(selected?.exercise?.loadType),
                    unit = unit,
                    onLog = {
                        Haptics.commit(view)
                        viewModel.logSet()
                    },
                    onCancelEdit = viewModel::cancelEdit,
                    onApplyMicroRec = viewModel::applyMicroRec,
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
                    compact = true,
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
                    if (!restNotificationsEnabled) {
                        RestNotificationRecoveryRow()
                    }
                    RestDock(
                        remainingSeconds = rest.remainingSeconds,
                        totalSeconds = rest.totalSeconds,
                        running = rest.running,
                        onSkip = viewModel::skipRest,
                        onStart = viewModel::startSelectedRest,
                        onOpenRest = { session.id.let(onOpenRest) },
                    )

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(WorkoutTestTags.CONTENT),
                        contentPadding = PaddingValues(
                            start = Metrics.gutter,
                            end = Metrics.gutter,
                            top = Metrics.space3,
                            bottom = Metrics.space7,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                    ) {
                        personalRecord?.let { moment ->
                            item(key = "pr-moment") {
                                PersonalRecordBanner(
                                    headline = personalRecordHeadline(moment),
                                    detail = "${moment.exerciseName.ifBlank { "This lift" }} · " +
                                        SetCopy.setLine(moment.weightKg, moment.reps, LoadClass.of(selected?.exercise?.loadType), unit),
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
                                    body = SessionOrderCopy.EMPTY_SESSION_BODY,
                                    actionLabel = "Add a lift",
                                    onAction = { viewModel.setPickerVisible(true) },
                                    compact = true,
                                )
                            }
                        } else if (selected == null) {
                            item(key = "pick-lift") {
                                EmptyState(
                                    title = "Pick a lift",
                                    body = "Choose one above to keep logging.",
                                    actionLabel = "Add a lift",
                                    onAction = { viewModel.setPickerVisible(true) },
                                    compact = true,
                                )
                            }
                            if (session.sets.isNotEmpty()) {
                                item(key = "sets-label") { Kicker("Sets") }
                                item(key = "sets") {
                                    LoggedSetsPanel(
                                        sets = session.sets,
                                        latestSetId = session.sets.maxByOrNull { it.completedAt }?.id,
                                        editingSetId = state.editingSetId,
                                        loadClassOf = { set -> session.loadClassOf(set.exerciseId) },
                                        onEdit = { viewModel.editSet(it) },
                                        onDelete = { viewModel.deleteSet(it) },
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
                                    canEdit = loggedForSelected.isEmpty(),
                                    onSwap = viewModel::requestSwap,
                                    onRemove = { confirmRemoveLift = true },
                                    modifier = Modifier.testTag(WorkoutTestTags.CURRENT_LIFT),
                                )
                            }
                            state.lastPerformance?.let { last ->
                                                item(key = "last-time") {
                                    LastTimeStrip(
                                        summary = last,
                                        unit = unit,
                                        loadClass = LoadClass.of(selected?.exercise?.loadType),
                                    )
                                }
                            }
                            state.hint?.let { hint ->
                                item(key = "progression") {
                                    ProgressionStrip(
                                        hint = hint,
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
                                    // A push-up gets one well and no weight box; a weighted
                                    // pull-up gets a box labelled "added"; an assisted machine
                                    // one labelled "assist".
                                    loadClass = LoadClass.of(selected?.exercise?.loadType),
                                    plated = selected?.exercise?.equipment == EquipmentType.BARBELL,
                                    modifier = Modifier.testTag(WorkoutTestTags.SET_ENTRY),
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
                            item(key = "sets-label") { Kicker("Sets") }
                            item(key = "sets") {
                                LoggedSetsPanel(
                                    sets = loggedForSelected,
                                    latestSetId = latestSetId,
                                    editingSetId = state.editingSetId,
                                    loadClassOf = { LoadClass.of(selected?.exercise?.loadType) },
                                    onEdit = { viewModel.editSet(it) },
                                    onDelete = { viewModel.deleteSet(it) },
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
            state = ExercisePickerState(
                query = state.searchQuery,
                results = state.searchResults,
                title = if (state.swapping) "Swap lift" else "Add a lift",
                mode = if (state.swapping) ExercisePickerMode.SWAP else ExercisePickerMode.SINGLE_ADD,
                suggestion = state.suggestion,
                suggestionReason = state.suggestionReason,
                siblings = state.swapSiblings,
            ),
            onEvent = { event ->
                when (event) {
                    is ExercisePickerEvent.QueryChanged -> viewModel.onSearchQuery(event.query)
                    is ExercisePickerEvent.Selected -> viewModel.addExercise(event.exercise)
                    is ExercisePickerEvent.Created ->
                        viewModel.createAndAddExercise(event.name, event.muscleGroup)
                    is ExercisePickerEvent.Toggled -> Unit
                    ExercisePickerEvent.Confirmed -> Unit
                    ExercisePickerEvent.Dismissed -> viewModel.setPickerVisible(false)
                }
            },
        )
    }

    if (confirmLeave) {
        // X, system back, and Finish-disabled empty sessions all land here. Keep is the
        // gym-floor leave; Discard still opens the named confirm below.
        LeaveWorkoutDialog(
            onKeepAndExit = {
                confirmLeave = false
                viewModel.persistDraftForExit()
                onExit()
            },
            onStay = { confirmLeave = false },
            onDiscardInstead = {
                confirmLeave = false
                confirmDiscard = true
            },
            onDismiss = { confirmLeave = false },
        )
    }

    if (confirmRemoveLift) {
        val name = selected?.exercise?.name ?: "this lift"
        ConfirmActionDialog(
            title = "Remove $name?",
            body = "It comes out of this session's plan. Nothing logged is affected — this lift " +
                "has no sets yet.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                confirmRemoveLift = false
                viewModel.removeSelectedLift()
            },
            onDismiss = { confirmRemoveLift = false },
        )
    }

    if (confirmDiscard) {
        val loggedSets = session?.sets?.size ?: 0
        ConfirmActionDialog(
            title = "Discard this workout?",
            body = if (loggedSets > 0) {
                "This deletes the session and its $loggedSets logged " +
                    (if (loggedSets == 1) "set" else "sets") + ". This cannot be undone."
            } else {
                "This deletes the session. This cannot be undone."
            },
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                viewModel.discardWorkout()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

private fun personalRecordHeadline(moment: PersonalRecordMoment): String = when {
    // REPS first: it is the only record a bodyweight lift can break, so anything that outranks
    // it here would leave the banner saying "most reps at this weight" about a push-up, whose
    // weight is nothing.
    PersonalRecordKind.REPS in moment.kinds -> "Most reps ever"
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
    work: SetWork,
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
            TextButton(
                onClick = onFinish,
                enabled = canFinish,
                modifier = Modifier.testTag(WorkoutTestTags.FINISH),
            ) {
                Text(
                    "Finish",
                    style = InstrumentType.bodyStrong,
                    // Real, but secondary to logging: the accent belongs on the log button.
                    color = if (canFinish) TextPrimary else TextTertiary,
                )
            }
        }
        if (!canFinish) {
            Text(
                "Log a set to finish.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = Metrics.space4),
                style = InstrumentType.caption,
                color = TextTertiary,
                textAlign = TextAlign.End,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Metrics.space2),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            MetricCluster(
                value = RestTimer.formatClock(elapsedSeconds),
                label = "elapsed",
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
            )
            MetricCluster(
                value = workingSets.toString(),
                label = "sets",
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
            )
            val column = SetCopy.workColumn(work, unit)
            MetricCluster(
                value = column.value,
                label = column.label,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
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
 *
 * Scaffold's bottomBar draws edge-to-edge. The tab bar is gone on this route, so this
 * dock owns the system-nav inset the same way the tab bar and live bar already do —
 * otherwise Log sits under the three-button nav / gesture pill.
 */
@Composable
private fun LogBar(
    editing: Boolean,
    error: String?,
    draftLabel: String,
    microRec: SetMicroRec?,
    loadClass: LoadClass,
    unit: WeightUnit,
    onLog: () -> Unit,
    onCancelEdit: () -> Unit,
    onApplyMicroRec: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .navigationBarsPadding()
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
        microRec?.let { rec ->
            MicroRecLine(
                rec = rec,
                loadClass = loadClass,
                unit = unit,
                onApply = onApplyMicroRec,
            )
        }
        PrimaryGymButton(
            text = if (editing) "Save $draftLabel" else "Log $draftLabel",
            onClick = onLog,
            modifier = Modifier.testTag(WorkoutTestTags.LOG_SET),
            height = Metrics.commit,
            hapticFeedback = false,
        )
    }
}

@Composable
private fun MicroRecLine(
    rec: SetMicroRec,
    loadClass: LoadClass,
    unit: WeightUnit,
    onApply: () -> Unit,
) {
    var showWhy by rememberSaveable(rec.reasonCode, rec.nextWeightKg, rec.nextReps, rec.nextRpe) {
        mutableStateOf(false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        SetMicroRecCopy.caption(rec)?.let { caption ->
            Text(caption, style = InstrumentType.caption, color = TextTertiary)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Text(
                SetMicroRecCopy.line(rec, loadClass, unit),
                modifier = Modifier
                    .weight(1f)
                    .testTag(WorkoutTestTags.MICRO_REC),
                style = InstrumentType.bodyStrong,
                color = TextPrimary,
            )
            TextButton(
                onClick = { showWhy = !showWhy },
                modifier = Modifier.testTag(WorkoutTestTags.MICRO_REC_WHY),
            ) {
                Text(
                    if (showWhy) "Hide why" else "Why",
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
            if (rec.showApply && !rec.previewOnly) {
                TextButton(
                    onClick = onApply,
                    modifier = Modifier.testTag(WorkoutTestTags.MICRO_REC_APPLY),
                ) {
                    Text("Use", style = InstrumentType.bodyStrong, color = Volt)
                }
            }
        }
        if (showWhy) {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                SetMicroRecCopy.whyLines(rec).forEach { line ->
                    Text(line, style = InstrumentType.caption, color = TextSecondary)
                }
            }
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
                // The glyph only — a body silhouette is unreadable at chip height. TextSecondary
                // in both states, selected or not: the equipment is metadata you glance at, and
                // tinting it with the selection would make it compete with the label that
                // actually says which lift you are on.
                leading = { EquipmentGlyphIcon(glyphFor(item.exercise.equipment)) },
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
    canEdit: Boolean,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetSets = lift.targetSets.coerceAtLeast(1)
    val targetReps = lift.targetReps.coerceAtLeast(1)
    var menuOpen by rememberSaveable(lift.id) { mutableStateOf(false) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                lift.exercise.name,
                modifier = Modifier.weight(1f),
                style = InstrumentType.display,
                color = TextPrimary,
                maxLines = 3,
            )
            // Only while nothing has been logged against this lift. Once a set exists, the
            // lift is part of what happened: removing it would delete real work and swapping
            // it would silently reattribute those sets to a different exercise.
            if (canEdit) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "Lift options",
                            tint = TextSecondary,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text("Swap lift…", style = InstrumentType.bodyStrong, color = TextPrimary)
                            },
                            onClick = {
                                menuOpen = false
                                onSwap()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Remove lift", style = InstrumentType.bodyStrong, color = Danger)
                            },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        }
        // Progress as a glyph rather than as a second display numeral. "Set 3 of 5" was set
        // at 28sp, competing with the weight it sits above for the eye's attention while
        // saying much less.
        SetDots(completed = workingLogged, target = targetSets)
        Text(
            WorkoutCopy.setProgress(
                workingLogged = workingLogged,
                targetSets = targetSets,
                targetReps = targetReps,
                targetWeightLabel = lift.targetWeightKg?.takeIf { it > 0.0 }?.toWeightLabel(unit),
            ),
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
    loadClass: LoadClass,
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
            items(summary.sets, key = { it.setId }) { set ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(Surface1)
                        .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.xs))
                        .padding(horizontal = Metrics.space3, vertical = Metrics.space2),
                ) {
                    Text(
                        SetCopy.setLine(set.weightKg, set.reps, loadClass, unit),
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
    unit: WeightUnit,
    onApply: () -> Unit,
) {
    // The sentence lives in the domain so this strip and the coach card cannot disagree about
    // the same lift, and so a bodyweight lift is told to add a rep rather than a kilogram.
    val reason = ProgressionCopy.stripReason(hint, unit)
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
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
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
        Text(
            RpeCopy.BLURB,
            style = InstrumentType.caption,
            color = TextTertiary,
        )
    }
}

@Composable
private fun LoggedSetsPanel(
    sets: List<SetLog>,
    latestSetId: String?,
    editingSetId: String?,
    loadClassOf: (SetLog) -> LoadClass,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (sets.isEmpty()) return
    GroupedList {
        sets.forEachIndexed { index, set ->
            if (index > 0) HairlineDivider()
            SetRow(
                set = set,
                isLatest = set.id == latestSetId,
                isEditing = editingSetId == set.id,
                loadClass = loadClassOf(set),
                onEdit = { onEdit(set.id) },
                onDelete = { onDelete(set.id) },
            )
        }
    }
}

/**
 * A logged set.
 *
 * State is carried by the design rather than narrated in the text. Latest wears a Volt rail.
 * Warm-up wears a cyan tick. Editing is outlined in Volt, matching the log button.
 */
@Composable
private fun SetRow(
    set: SetLog,
    isLatest: Boolean,
    isEditing: Boolean,
    loadClass: LoadClass,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = LocalWeightUnit.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isEditing) {
                    Modifier.border(Metrics.emphasisBorder, Volt)
                } else {
                    Modifier
                },
            )
            .padding(end = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = LATEST_RULE_WIDTH, height = LATEST_RULE_HEIGHT)
                .background(if (isLatest) Volt else Color.Transparent),
        )
        if (set.isWarmup) {
            Box(
                modifier = Modifier
                    .padding(start = Metrics.space2)
                    .size(WARMUP_TICK)
                    .clip(CircleShape)
                    .background(RestCyan)
                    .semantics { contentDescription = "Warm-up" },
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Metrics.space3, top = Metrics.space3, bottom = Metrics.space3),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                SetCopy.setLine(set.weightKg, set.reps, loadClass, unit),
                style = InstrumentType.numeralSm,
                color = TextPrimary,
            )
            val extras = buildList {
                add("Set ${set.setNumber}")
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
 * The system dialog has no gym why, so an in-app sentence runs first. Continue launches
 * the permission prompt; Not now leaves the compact recovery row as the way back.
 * The returned flag drives that row (deep link to app notification settings) and
 * re-checks on every resume so it disappears the moment the user grants.
 */
@Composable
private fun rememberRestNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var showWhy by rememberSaveable { mutableStateOf(false) }
    var decided by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !decided) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                showWhy = true
            }
        }
    }

    if (showWhy) {
        ConfirmActionDialog(
            title = RestNotificationCopy.TITLE,
            body = RestNotificationCopy.SENTENCE,
            confirmLabel = RestNotificationCopy.CONTINUE,
            dismissLabel = RestNotificationCopy.NOT_NOW,
            onConfirm = {
                decided = true
                showWhy = false
                if (Build.VERSION.SDK_INT >= 33) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = {
                decided = true
                showWhy = false
            },
        )
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

/**
 * Persistent recovery after the one explanation. One identity line and one
 * act — not a viewport-dominating banner (FND-015).
 */
@Composable
private fun RestNotificationRecoveryRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    InstrumentRow(
        title = RestNotificationCopy.RECOVERY_TITLE,
        modifier = modifier.testTag(WorkoutTestTags.NOTIF_RECOVERY),
        onClick = {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (thrown: Exception) {
                AppLog.w(TAG, "App notification settings unavailable; falling back", thrown)
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        trailing = {
            Text(
                RestNotificationCopy.RECOVERY_ACTION,
                style = InstrumentType.bodyStrong,
                color = Volt,
                maxLines = 1,
            )
        },
    )
}

private val LATEST_RULE_WIDTH = 3.dp
private val LATEST_RULE_HEIGHT = 44.dp
private val WARMUP_TICK = 6.dp

private const val PERSONAL_RECORD_DWELL_MS = 6_000L
