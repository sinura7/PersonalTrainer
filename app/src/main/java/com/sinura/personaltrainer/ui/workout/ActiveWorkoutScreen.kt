package com.sinura.personaltrainer.ui.workout


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerMode
import com.sinura.personaltrainer.domain.ExercisePickerState
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.PersonalRecordCopy
import com.sinura.personaltrainer.domain.WorkoutAdvance
import com.sinura.personaltrainer.domain.FloorTimerSurface
import com.sinura.personaltrainer.domain.UndoHostCopy
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.EndWorkoutDialog
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.FloorTimerSlot
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymUndoHost
import com.sinura.personaltrainer.ui.components.NotesBlock
import com.sinura.personaltrainer.ui.components.PersonalRecordBanner
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import kotlinx.coroutines.delay

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
    const val REST_BATTERY = "workout-rest-battery"
    const val HOLD_CLOCK = "workout-hold-clock"
    const val MICRO_REC = "workout-micro-rec"
    const val MICRO_REC_APPLY = "workout-micro-rec-apply"
    const val MICRO_REC_WHY = "workout-micro-rec-why"
    const val PROGRESSION_KICKER = "workout-progression-kicker"
    const val RPE_GLYPH = "workout-rpe-glyph"
    const val WEIGHT_GLYPH = "workout-weight-glyph"
    const val REPS_TIME_GLYPH = "workout-reps-time-glyph"
    const val REST_GLYPH = "workout-rest-glyph"
    const val NEXT = "workout-next"
    const val ANOTHER_SET = "workout-another-set"
    const val RPE_TRACK = "workout-rpe-track"
    const val INSTRUMENT_STRIP = "workout-instrument-strip"
    const val REST_WHEEL = "workout-rest-wheel"
    const val START_SET_CLOCK = "workout-start-set-clock"
    const val STOP_SET_CLOCK = "workout-stop-set-clock"
    const val ADD_SET = "workout-add-set"
    const val LAST_TIME = "workout-last-time"
    const val SELECTED_LIFT = "workout-selected-lift"
    const val START_NEXT = "workout-start-next"
    const val START_REST = "workout-start-rest"
    const val LIFT_OPTIONS = "workout-lift-options"
    const val WEIGHT_WHEEL = "workout-weight-wheel"
    const val REPS_WHEEL = "workout-reps-wheel"
    const val HOLD_WHEEL = "workout-hold-wheel"
    fun liftCard(exerciseId: String) = "workout-lift-card-$exerciseId"
    fun liftSets(exerciseId: String) = "workout-lift-sets-$exerciseId"
    fun liftRest(exerciseId: String) = "workout-lift-rest-$exerciseId"
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
    val holdTimer by viewModel.holdTimer.collectAsStateWithLifecycle()
    val setStopwatch by viewModel.setStopwatch.collectAsStateWithLifecycle()
    val microRec by viewModel.microRec.collectAsStateWithLifecycle()
    val extraSetRequested by viewModel.extraSetRequested.collectAsStateWithLifecycle()
    val windowDp = LocalWindowInfo.current.containerDpSize
    val landscape = LandscapeChrome.isLandscape(
        widthDp = windowDp.width.value.roundToInt(),
        heightDp = windowDp.height.value.roundToInt(),
    )
    val exitRequested by viewModel.exitRequested.collectAsStateWithLifecycle()
    val personalRecord by viewModel.personalRecord.collectAsStateWithLifecycle()
    val deletedSet by viewModel.deletedSet.collectAsStateWithLifecycle()
    val removedLift by viewModel.removedLift.collectAsStateWithLifecycle()
    val pendingAdvance by viewModel.pendingAdvance.collectAsStateWithLifecycle()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    val restNotificationsEnabled = restNotificationsEnabledOverride
        ?: rememberRestNotificationsEnabled()
    val session = state.session
    val selected = session?.exercises?.firstOrNull { it.exercise.id == state.selectedExerciseId }
    val afterWarmup = selected?.let { lift ->
        session?.setsFor(lift.exercise.id)?.maxByOrNull { it.completedAt }?.isWarmup == true
    } == true
    val advance = remember(session, state.selectedExerciseId, extraSetRequested, state.editingSetId) {
        WorkoutAdvance.forSelection(
            session = session,
            selectedExerciseId = state.selectedExerciseId,
            wantAnother = extraSetRequested,
            editing = state.editingSetId != null,
        )
    }
    val workingLogged = advance.workingLogged
    val nextExerciseId = advance.nextExerciseId
    val showNext = advance.showNext
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

    // X and system back go Home with the session still live. Finish is the
    // explicit end (save as is / leave without saving). Both flush the draft.
    fun keepAndExit() {
        viewModel.persistDraftForExit()
        onExit()
    }
    BackHandler(enabled = state.session != null) { keepAndExit() }

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
            if (errorBanner || deletedSet != null || removedLift != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Metrics.gutter),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    if (errorBanner) {
                        GymErrorBanner(message = state.error!!)
                    }
                    // Undo dwell uses Motion.STATUS_DWELL_MS (~6s). Advance is not a
                    // banner: Next lift / Another set stand in the dock until chosen.
                    val undoMessage = removedLift?.let { UndoHostCopy.liftRemoved(it.name) }
                        ?: deletedSet?.let { removed ->
                            UndoHostCopy.setDeleted(
                                SetCopy.setLine(
                                    removed.weightKg,
                                    removed.reps,
                                    LoadClass.of(selected?.exercise?.loadType),
                                    unit,
                                    durationSeconds = removed.durationSeconds,
                                ),
                            )
                        }
                    undoMessage?.let { message ->
                        GymUndoHost(
                            message = message,
                            onUndo = {
                                if (removedLift != null) {
                                    viewModel.undoRemoveLift()
                                } else {
                                    viewModel.undoDeleteSet()
                                }
                            },
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
                canFinish = session != null,
                compact = LandscapeChrome.compactHeader(landscape),
                onExit = { keepAndExit() },
                onFinish = { confirmEnd = true },
                onOpenTimer = { session?.id?.let(onOpenRest) },
                restRunning = rest.running,
                restRemainingSeconds = rest.remainingSeconds,
                plannedRestSeconds = selected?.restSeconds?.takeIf { it > 0 }
                    ?: rest.totalSeconds,
                holdRunning = holdTimer.running,
                holdElapsedSeconds = holdTimer.elapsedSeconds,
                stopwatchRunning = setStopwatch.running,
                stopwatchElapsedSeconds = setStopwatch.elapsedSeconds,
            )
        },
        bottomBar = {
            // G-02 / Packet 2: timer slot, advance choice, and Log set share
            // the LogBar dock so a one-handed thumb reaches every control.
            // Finish stays in the header — it is not a mid-set act.
            val showRest = session != null
            if (showRest || logBarVisible) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (logBarVisible) Modifier
                            else Modifier.navigationBarsPadding(),
                        ),
                ) {
                    if (showRest && selected != null &&
                        !LandscapeChrome.hideSelectedLiftDock(landscape)
                    ) {
                        SelectedLiftDock(
                            lift = selected,
                            workingLogged = workingLogged,
                            unit = unit,
                            restSeconds = selected.restSeconds.takeIf { it > 0 } ?: rest.totalSeconds,
                            restRunning = rest.running,
                            restRemainingSeconds = rest.remainingSeconds,
                        )
                    }
                    if (logBarVisible) {
                        val hold = selected?.exercise?.let { HoldWork.isHold(it) } == true
                        val holdArmed = holdTimer.running || holdTimer.totalSeconds > 0
                        LogBar(
                            editing = state.editingSetId != null,
                            logging = state.logging,
                            error = state.error,
                            draftLabel = SetCopy.setLine(
                                state.draft.weightKg,
                                state.draft.reps,
                                LoadClass.of(selected?.exercise?.loadType),
                                unit,
                                durationSeconds = if (hold) {
                                    if (holdArmed) {
                                        HoldWork.elapsedSeconds(
                                            holdTimer.totalSeconds,
                                            holdTimer.remainingSeconds,
                                        )
                                    } else {
                                        state.draft.durationSeconds ?: selected?.targetSeconds
                                    }
                                } else if (setStopwatch.used) {
                                    FloorTimerSurface.setClockSeconds(setStopwatch.elapsedSeconds)
                                        .coerceAtLeast(1)
                                } else {
                                    null
                                },
                            ),
                            warmup = state.draft.isWarmup,
                            microRec = microRec.takeUnless {
                                showNext || LandscapeChrome.foldMicroRecIntoCard(landscape)
                            },
                            loadClass = LoadClass.of(selected?.exercise?.loadType),
                            unit = unit,
                            showNext = showNext,
                            advanceChoice = pendingAdvance != null,
                            hold = hold,
                            holdRunning = holdArmed,
                            showTimer = showRest,
                            restRemainingSeconds = rest.remainingSeconds,
                            restTotalSeconds = rest.totalSeconds,
                            restRunning = rest.running,
                            restCompletedTimerId = rest.completedTimerId,
                            hideIdleRest = LandscapeChrome.hideIdleRest(landscape),
                            afterWarmup = afterWarmup,
                            restBatteryHint = rest.batteryHint,
                            holdElapsedSeconds = holdTimer.elapsedSeconds,
                            stopwatchRunning = setStopwatch.running,
                            stopwatchElapsedSeconds = setStopwatch.elapsedSeconds,
                            offerSetClock = !hold,
                            onStartSetClock = viewModel::startSetStopwatch,
                            onStopSetClock = viewModel::stopSetStopwatch,
                            onSkipRest = viewModel::skipRest,
                            onStartRest = viewModel::startSelectedRest,
                            onSelectRestDuration = viewModel::selectRestDuration,
                            onDismissRestBatteryHint = viewModel::acknowledgeRestBatteryHint,
                            onStartNextLift = viewModel::startNextLift,
                            onOpenRest = { session?.id?.let(onOpenRest) },
                            onLog = {
                                Haptics.commit(view)
                                if (hold && !holdArmed && state.editingSetId == null) {
                                    viewModel.startHoldSet()
                                } else {
                                    viewModel.logSet()
                                }
                            },
                            onNext = {
                                if (pendingAdvance != null) {
                                    viewModel.advanceNow()
                                } else {
                                    nextExerciseId?.let(viewModel::advanceToNextLift)
                                }
                            },
                            onAnotherSet = viewModel::stayOnCurrentExercise,
                            onCancelEdit = viewModel::cancelEdit,
                            onApplyMicroRec = viewModel::applyMicroRec,
                        )
                    } else if (showRest) {
                        // Empty free workout: timer alone until a lift is selected.
                        FloorTimerSlot(
                            remainingSeconds = rest.remainingSeconds,
                            totalSeconds = rest.totalSeconds,
                            restRunning = rest.running,
                            completedTimerId = rest.completedTimerId,
                            hideWhenIdle = LandscapeChrome.hideIdleRest(landscape),
                            afterWarmup = afterWarmup,
                            batteryHint = rest.batteryHint,
                            holdRunning = holdTimer.running,
                            holdElapsedSeconds = holdTimer.elapsedSeconds,
                            stopwatchRunning = setStopwatch.running,
                            stopwatchElapsedSeconds = setStopwatch.elapsedSeconds,
                            onSkip = viewModel::skipRest,
                            onStart = viewModel::startSelectedRest,
                            onSelectRestDuration = viewModel::selectRestDuration,
                            onDismissBatteryHint = viewModel::acknowledgeRestBatteryHint,
                            onStartNext = viewModel::startNextLift,
                            onOpenRest = { session.id.let(onOpenRest) },
                        )
                    }
                }
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
                    scene = EmptyScene.GONE,
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
                    // Notification recovery stays above the list: it is a
                    // banner, not a gym-floor act. Rest sits in the lower dock.
                    if (!restNotificationsEnabled) {
                        RestNotificationRecoveryRow()
                    }

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
                        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        personalRecord?.let { moment ->
                            item(key = "pr-moment") {
                                PersonalRecordBanner(
                                    headline = PersonalRecordCopy.headline(moment.kinds),
                                    detail = "${moment.exerciseName.ifBlank { "This lift" }} · " +
                                        SetCopy.setLine(moment.weightKg, moment.reps, LoadClass.of(selected?.exercise?.loadType), unit),
                                    onDismiss = viewModel::onPersonalRecordShown,
                                )
                            }
                        }
                        if (!session.hasLifts()) {
                            item(key = "empty-lifts") {
                                EmptyState(
                                    scene = EmptyScene.RACK,
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
                                    card = WorkoutLiftCardState(
                                        lift = lift,
                                        number = index + 1,
                                        selected = isSelected,
                                        loggedSets = logged,
                                        latestSetId = WorkoutAdvance.latestSetId(logged),
                                        editingSetId = state.editingSetId.takeIf { isSelected },
                                        lastPerformance = state.lastPerformance.takeIf { isSelected },
                                        hint = state.hint.takeIf { isSelected },
                                        draftWeightKg = state.draft.weightKg,
                                        draftReps = state.draft.reps,
                                        draftWarmup = state.draft.isWarmup,
                                        draftRpe = state.draft.rpe,
                                        microRec = microRec.takeIf { isSelected },
                                        recommendedRpe = microRec?.nextRpe.takeIf { isSelected },
                                        unit = unit,
                                        canEdit = logged.isEmpty(),
                                        showAddSet = isSelected &&
                                            WorkoutAdvance.cardOffersAnotherSet(logged, lift.targetSets),
                                        restRunning = isSelected && rest.running,
                                        restRemainingSeconds = rest.remainingSeconds,
                                        restSeconds = if (isSelected) {
                                            lift.restSeconds.takeIf { it > 0 } ?: rest.totalSeconds
                                        } else {
                                            lift.restSeconds
                                        },
                                        hold = HoldWork.isHold(lift.exercise),
                                        holdSeconds = if (isSelected) {
                                            state.draft.durationSeconds ?: lift.targetSeconds
                                        } else {
                                            lift.targetSeconds
                                        },
                                        holdRunning = isSelected && holdTimer.running,
                                        holdRemainingSeconds = if (isSelected) {
                                            holdTimer.remainingSeconds
                                        } else {
                                            0
                                        },
                                    ),
                                    events = WorkoutLiftCardEvents(
                                        onSelect = { viewModel.selectExercise(lift.exercise.id) },
                                        onSwap = viewModel::requestSwap,
                                        onRemove = viewModel::removeSelectedLift,
                                        onWeightKgChange = viewModel::setWeight,
                                        onRepsAdjust = viewModel::adjustReps,
                                        onRepsChange = viewModel::setReps,
                                        onSecondsAdjust = viewModel::adjustHoldSeconds,
                                        onSecondsChange = viewModel::setHoldSeconds,
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
                        viewModel.createAndAddExercise(event.name, event.muscleGroup, event.loadType)
                    is ExercisePickerEvent.Toggled -> Unit
                    ExercisePickerEvent.Dismissed -> viewModel.setPickerVisible(false)
                    ExercisePickerEvent.ErrorDismissed -> viewModel.dismissError()
                }
            },
        )
    }

    if (confirmEnd) {
        EndWorkoutDialog(
            loggedSets = session?.sets?.size ?: 0,
            onSave = {
                confirmEnd = false
                Haptics.commit(view)
                viewModel.finishWorkout()
            },
            onDiscardInstead = {
                confirmEnd = false
                confirmDiscard = true
            },
            onDismiss = { confirmEnd = false },
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


private const val PERSONAL_RECORD_DWELL_MS = Motion.STATUS_DWELL_MS
