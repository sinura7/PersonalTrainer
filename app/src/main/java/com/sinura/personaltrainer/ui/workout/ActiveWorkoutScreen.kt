package com.sinura.personaltrainer.ui.workout

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
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
import com.sinura.personaltrainer.domain.WorkoutAdvance
import com.sinura.personaltrainer.domain.WorkoutCopy
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.CountBadge
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymStatusBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.LeaveWorkoutDialog
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.NotesBlock
import com.sinura.personaltrainer.ui.components.PersonalRecordBanner
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.RestDock
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.components.SetEntryPanel
import com.sinura.personaltrainer.ui.components.ThumbSize
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
import com.sinura.personaltrainer.ui.theme.VoltDim
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
    const val NEXT = "workout-next"
    const val ADD_SET = "workout-add-set"
    const val LAST_TIME = "workout-last-time"
    fun liftCard(exerciseId: String) = "workout-lift-card-$exerciseId"
    fun lastTimeChip(setId: String) = "workout-last-time-$setId"
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
    val extraSetRequested by viewModel.extraSetRequested.collectAsStateWithLifecycle()
    val config = LocalConfiguration.current
    val landscape = LandscapeChrome.isLandscape(config.screenWidthDp, config.screenHeightDp)
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
    val workingLogged = selected?.let { lift ->
        session.setsFor(lift.exercise.id).count { !it.isWarmup }
    } ?: 0
    val liftComplete = WorkoutAdvance.liftComplete(
        workingLogged = workingLogged,
        targetSets = selected?.targetSets ?: 0,
        wantAnother = extraSetRequested,
    )
    val nextExerciseId = WorkoutAdvance.nextExerciseId(
        session?.exercises.orEmpty().map { it.exercise.id },
        state.selectedExerciseId,
    )
    val showNext = liftComplete && nextExerciseId != null && state.editingSetId == null
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
    val logBarVisible = session != null && selected != null

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

    Scaffold(
        snackbarHost = {
            val errorBanner = state.error != null && !logBarVisible
            if (errorBanner || deletedSet != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Metrics.gutter),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    if (errorBanner) {
                        GymErrorBanner(message = state.error!!)
                    }
                    deletedSet?.let { removed ->
                        GymStatusBanner(
                            message = "Set deleted · " + SetCopy.setLine(
                                removed.weightKg,
                                removed.reps,
                                LoadClass.of(selected?.exercise?.loadType),
                                unit,
                            ),
                            actionLabel = "Undo",
                            onAction = { viewModel.undoDeleteSet() },
                            onDismissed = { viewModel.onUndoOfferHandled() },
                        )
                    }
                }
            }
        },
        topBar = {
            WorkoutHeader(
                routineName = session?.routineName ?: "Workout",
                startedAt = session?.startedAt,
                workingSets = session?.sets?.count { !it.isWarmup } ?: 0,
                work = sessionWork,
                unit = unit,
                canFinish = session != null && session.sets.isNotEmpty(),
                compact = LandscapeChrome.compactHeader(landscape),
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
                    logging = state.logging,
                    error = state.error,
                    draftLabel = SetCopy.setLine(state.draft.weightKg, state.draft.reps, LoadClass.of(selected?.exercise?.loadType), unit),
                    microRec = microRec.takeUnless {
                        showNext || LandscapeChrome.foldMicroRecIntoCard(landscape)
                    },
                    loadClass = LoadClass.of(selected?.exercise?.loadType),
                    unit = unit,
                    showNext = showNext,
                    onLog = {
                        Haptics.commit(view)
                        viewModel.logSet()
                    },
                    onNext = {
                        nextExerciseId?.let(viewModel::advanceToNextLift)
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
                        completedTimerId = rest.completedTimerId,
                        hideWhenIdle = LandscapeChrome.hideIdleRest(landscape),
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
                        } else {
                            itemsIndexed(
                                items = session.exercises,
                                key = { _, lift -> "lift-${lift.id}" },
                            ) { index, lift ->
                                val isSelected = lift.exercise.id == state.selectedExerciseId
                                val logged = session.setsFor(lift.exercise.id)
                                WorkoutLiftCard(
                                    state = WorkoutLiftCardState(
                                        lift = lift,
                                        number = index + 1,
                                        selected = isSelected,
                                        loggedSets = logged,
                                        latestSetId = logged.maxByOrNull { it.completedAt }?.id,
                                        editingSetId = state.editingSetId.takeIf { isSelected },
                                        lastPerformance = state.lastPerformance.takeIf { isSelected },
                                        hint = state.hint.takeIf { isSelected },
                                        draftWeightKg = state.draft.weightKg,
                                        draftReps = state.draft.reps,
                                        draftWarmup = state.draft.isWarmup,
                                        draftRpe = state.draft.rpe,
                                        microRec = microRec.takeIf { isSelected },
                                        unit = unit,
                                        canEdit = logged.isEmpty(),
                                        showAddSet = isSelected &&
                                            WorkoutAdvance.liftComplete(
                                                workingLogged = logged.count { !it.isWarmup },
                                                targetSets = lift.targetSets,
                                                wantAnother = false,
                                            ),
                                    ),
                                    events = WorkoutLiftCardEvents(
                                        onSelect = { viewModel.selectExercise(lift.exercise.id) },
                                        onSwap = viewModel::requestSwap,
                                        onRemove = { confirmRemoveLift = true },
                                        onWeightKgChange = viewModel::setWeight,
                                        onRepsAdjust = viewModel::adjustReps,
                                        onApplyLastTime = viewModel::applyLastTimeSet,
                                        onWarmup = viewModel::setWarmup,
                                        onRpe = viewModel::setRpe,
                                        onApplySuggested = viewModel::applySuggestedWeight,
                                        onEditSet = viewModel::editSet,
                                        onDeleteSet = viewModel::deleteSet,
                                        onAddSet = viewModel::requestExtraSet,
                                    ),
                                )
                            }
                            item(key = "add-lift") {
                                SecondaryGymButton(
                                    text = "Add a lift",
                                    onClick = { viewModel.setPickerVisible(true) },
                                    modifier = Modifier.fillMaxWidth(),
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
                    ExercisePickerEvent.ErrorDismissed -> viewModel.dismissError()
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
    compact: Boolean,
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
        ScreenHeader(
            title = routineName,
            onBack = onExit,
            backIcon = Icons.Outlined.Close,
            backDescription = "Exit workout",
            paintBackground = false,
            contentPadding = PaddingValues(0.dp),
            trailing = {
                TextButton(
                    onClick = onFinish,
                    enabled = canFinish,
                    modifier = Modifier.testTag(WorkoutTestTags.FINISH),
                ) {
                    Text(
                        "Finish",
                        style = InstrumentType.bodyStrong,
                        color = if (canFinish) TextPrimary else TextTertiary,
                    )
                }
            },
        )
        if (!compact && !canFinish) {
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
        if (!compact) {
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
    logging: Boolean,
    error: String?,
    draftLabel: String,
    microRec: SetMicroRec?,
    loadClass: LoadClass,
    unit: WeightUnit,
    showNext: Boolean,
    onLog: () -> Unit,
    onNext: () -> Unit,
    onCancelEdit: () -> Unit,
    onApplyMicroRec: () -> Unit,
) {
    PinnedDock(
        prelude = {
            error?.let {
                Text(it, style = InstrumentType.body, color = Danger)
            }
            if (editing) {
                TextButton(
                    onClick = onCancelEdit,
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = Metrics.touchMin),
                ) {
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
        },
        volt = {
            val nextAct = showNext && !editing
            PrimaryGymButton(
                text = when {
                    editing -> "Save $draftLabel"
                    nextAct -> "Next"
                    else -> "Log $draftLabel"
                },
                onClick = if (nextAct) onNext else onLog,
                enabled = !logging,
                modifier = Modifier.testTag(
                    if (nextAct) WorkoutTestTags.NEXT else WorkoutTestTags.LOG_SET,
                ),
                height = Metrics.commit,
                hapticFeedback = nextAct || editing,
            )
        },
    )
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
            Text(
                caption,
                style = InstrumentType.caption,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = { showWhy = true },
                modifier = Modifier
                    .heightIn(min = Metrics.touchMin)
                    .testTag(WorkoutTestTags.MICRO_REC_WHY),
            ) {
                Text(
                    "Why",
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
            if (rec.showApply && !rec.previewOnly) {
                TextButton(
                    onClick = onApply,
                    modifier = Modifier
                        .heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutTestTags.MICRO_REC_APPLY),
                ) {
                    Text("Use", style = InstrumentType.bodyStrong, color = Volt)
                }
            }
        }
    }
    if (showWhy) {
        ConfirmActionDialog(
            title = "Why",
            body = SetMicroRecCopy.whyLines(rec).joinToString("\n"),
            confirmLabel = "OK",
            onConfirm = { showWhy = false },
            onDismiss = { showWhy = false },
            dismissLabel = null,
        )
    }
}

private data class WorkoutLiftCardState(
    val lift: SessionExercise,
    val number: Int,
    val selected: Boolean,
    val loggedSets: List<SetLog>,
    val latestSetId: String?,
    val editingSetId: String?,
    val lastPerformance: ExerciseSessionSummary?,
    val hint: ProgressionHint?,
    val draftWeightKg: Double,
    val draftReps: Int,
    val draftWarmup: Boolean,
    val draftRpe: Int?,
    val microRec: SetMicroRec?,
    val unit: WeightUnit,
    val canEdit: Boolean,
    val showAddSet: Boolean,
)

private data class WorkoutLiftCardEvents(
    val onSelect: () -> Unit,
    val onSwap: () -> Unit,
    val onRemove: () -> Unit,
    val onWeightKgChange: (Double) -> Unit,
    val onRepsAdjust: (Int) -> Unit,
    val onApplyLastTime: (Double, Int) -> Unit,
    val onWarmup: (Boolean) -> Unit,
    val onRpe: (Int?) -> Unit,
    val onApplySuggested: () -> Unit,
    val onEditSet: (String) -> Unit,
    val onDeleteSet: (String) -> Unit,
    val onAddSet: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutLiftCard(
    state: WorkoutLiftCardState,
    events: WorkoutLiftCardEvents,
) {
    val lift = state.lift
    val number = state.number
    val selected = state.selected
    val loggedSets = state.loggedSets
    val latestSetId = state.latestSetId
    val editingSetId = state.editingSetId
    val lastPerformance = state.lastPerformance
    val hint = state.hint
    val draftWeightKg = state.draftWeightKg
    val draftReps = state.draftReps
    val draftWarmup = state.draftWarmup
    val draftRpe = state.draftRpe
    val microRec = state.microRec
    val unit = state.unit
    val canEdit = state.canEdit
    val showAddSet = state.showAddSet
    val onSelect = events.onSelect
    val onSwap = events.onSwap
    val onRemove = events.onRemove
    val onWeightKgChange = events.onWeightKgChange
    val onRepsAdjust = events.onRepsAdjust
    val onApplyLastTime = events.onApplyLastTime
    val onWarmup = events.onWarmup
    val onRpe = events.onRpe
    val onApplySuggested = events.onApplySuggested
    val onEditSet = events.onEditSet
    val onDeleteSet = events.onDeleteSet
    val onAddSet = events.onAddSet
    val workingLogged = loggedSets.count { !it.isWarmup }
    val targetSets = lift.targetSets
    val entryRequester = remember { BringIntoViewRequester() }
    var previousSetCount by remember(lift.id) { mutableIntStateOf(-1) }
    LaunchedEffect(lift.id, loggedSets.size) {
        val count = loggedSets.size
        val grew = LogLoopBringIntoView.shouldBringIntoView(previousSetCount, count)
        previousSetCount = count
        if (grew) {
            entryRequester.bringIntoView()
        }
    }
    val shape = RoundedCornerShape(Radius.sm)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .clip(shape)
            .background(if (selected) VoltDim else Surface2)
            .border(
                if (selected) Metrics.emphasisBorder else Metrics.hairline,
                if (selected) Volt else Hairline,
                shape,
            ),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .testTag(WorkoutTestTags.liftCard(lift.exercise.id))
                .semantics(mergeDescendants = true) {
                    this.selected = selected
                }
                .padding(Metrics.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            CountBadge(number = number, selected = selected)
            ExerciseThumb(
                exercise = lift.exercise,
                size = ThumbSize.header,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lift.exercise.name,
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lift.exercise.muscleGroup.isNotBlank()) {
                    Text(
                        lift.exercise.muscleGroup,
                        style = InstrumentType.caption,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                if (targetSets > 0) "$workingLogged/$targetSets" else workingLogged.toString(),
                style = InstrumentType.numeralSm,
                color = TextPrimary,
                maxLines = 1,
            )
        }
        if (selected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Metrics.space3,
                        end = Metrics.space3,
                        bottom = Metrics.space3,
                    ),
                verticalArrangement = Arrangement.spacedBy(Metrics.space4),
            ) {
                CurrentLiftHeader(
                    lift = lift,
                    workingLogged = workingLogged,
                    unit = unit,
                    canEdit = canEdit,
                    rec = microRec,
                    onSwap = onSwap,
                    onRemove = onRemove,
                    showName = false,
                    modifier = Modifier.testTag(WorkoutTestTags.CURRENT_LIFT),
                )
                lastPerformance?.let { last ->
                    LastTimeStrip(
                        summary = last,
                        unit = unit,
                        loadClass = LoadClass.of(lift.exercise.loadType),
                        onApplySet = onApplyLastTime,
                    )
                }
                hint?.let { next ->
                    ProgressionStrip(
                        hint = next,
                        unit = unit,
                        onApply = onApplySuggested,
                    )
                }
                SetEntryPanel(
                    weightKg = draftWeightKg,
                    reps = draftReps,
                    onWeightKgChange = onWeightKgChange,
                    onRepsAdjust = onRepsAdjust,
                    unit = unit,
                    loadClass = LoadClass.of(lift.exercise.loadType),
                    plated = lift.exercise.equipment == EquipmentType.BARBELL,
                    modifier = Modifier
                        .testTag(LogLoopBringIntoView.ANCHOR_TAG)
                        .bringIntoViewRequester(entryRequester),
                )
                SecondaryLogOptions(
                    warmup = draftWarmup,
                    rpe = draftRpe,
                    onWarmup = onWarmup,
                    onRpe = onRpe,
                )
                if (loggedSets.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        Kicker("Sets")
                        LoggedSetsPanel(
                            sets = loggedSets,
                            latestSetId = latestSetId,
                            editingSetId = editingSetId,
                            loadClassOf = { LoadClass.of(lift.exercise.loadType) },
                            showAddSet = showAddSet,
                            onEdit = onEditSet,
                            onDelete = onDeleteSet,
                            onAddSet = onAddSet,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentLiftHeader(
    lift: SessionExercise,
    workingLogged: Int,
    unit: WeightUnit,
    canEdit: Boolean,
    rec: SetMicroRec?,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
) {
    val targetSets = lift.targetSets.coerceAtLeast(1)
    val targetReps = lift.targetReps.coerceAtLeast(1)
    var menuOpen by rememberSaveable(lift.id) { mutableStateOf(false) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        if (showName || canEdit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showName) {
                    Text(
                        lift.exercise.name,
                        modifier = Modifier.weight(1f),
                        style = InstrumentType.display,
                        color = TextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (canEdit) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "Lift options",
                                tint = TextSecondary,
                            )
                        }
                        InstrumentMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
        }
        SetDots(completed = workingLogged, target = targetSets)
        val liveRec = rec?.takeIf { it.reasonCode != SetMicroRecCalculator.LIFT_DONE }
        val loadClass = LoadClass.of(lift.exercise.loadType)
        val liveWeightLabel = liveRec?.nextWeightKg
            ?.takeIf { it > 0.0 && loadClass.weightMeaning != com.sinura.personaltrainer.domain.WeightMeaning.NONE }
            ?.toWeightLabel(unit)
        Text(
            WorkoutCopy.setProgress(
                workingLogged = workingLogged,
                targetSets = targetSets,
                targetReps = targetReps,
                targetWeightLabel = lift.targetWeightKg?.takeIf { it > 0.0 }?.toWeightLabel(unit),
                liveReps = liveRec?.nextReps,
                liveWeightLabel = liveWeightLabel,
            ),
            style = InstrumentType.caption,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
    onApplySet: (weightKg: Double, reps: Int) -> Unit,
) {
    val view = LocalView.current
    val relative = remember(summary.performedAtMs) {
        DayLabel.relative(summary.performedAtMs, System.currentTimeMillis())
    }
    val absolute = remember(summary.performedAtMs) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(summary.performedAtMs))
    }
    Column(
        modifier = Modifier.testTag(WorkoutTestTags.LAST_TIME),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Kicker("Last time · ${relative ?: absolute}")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            items(summary.sets, key = { it.setId }) { set ->
                val line = SetCopy.setLine(set.weightKg, set.reps, loadClass, unit)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(Surface1)
                        .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.xs))
                        .clickable(role = Role.Button, onClick = {
                            Haptics.tick(view)
                            onApplySet(set.weightKg, set.reps)
                        })
                        .testTag(WorkoutTestTags.lastTimeChip(set.setId))
                        .semantics {
                            contentDescription = "Use last time $line"
                        }
                        .padding(horizontal = Metrics.space3, vertical = Metrics.space2),
                ) {
                    Text(
                        line,
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                reason,
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onApply,
            modifier = Modifier.heightIn(min = Metrics.touchMin),
        ) {
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
    showAddSet: Boolean,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddSet: () -> Unit,
) {
    if (sets.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
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
        if (showAddSet) {
            TextButton(
                onClick = onAddSet,
                modifier = Modifier
                    .heightIn(min = Metrics.touchMin)
                    .testTag(WorkoutTestTags.ADD_SET),
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(Metrics.space4),
                )
                Text(
                    "Add set",
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val extras = buildList {
                add("Set ${set.setNumber}")
                set.rpe?.let { add("RPE $it") }
            }.joinToString(" · ")
            Text(
                extras,
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
