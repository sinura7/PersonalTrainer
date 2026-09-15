package com.sinura.personaltrainer.ui.workout


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.FloorCompactChrome
import com.sinura.personaltrainer.domain.LogBarCopy
import com.sinura.personaltrainer.domain.LogCommitFeedback
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
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymUndoHost
import com.sinura.personaltrainer.ui.components.NotesBlock
import com.sinura.personaltrainer.ui.components.PersonalRecordBanner
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
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
    const val RPE_HELPER = "workout-rpe-helper"
    const val RPE_WARMUP_REASON = "workout-rpe-warmup-reason"
    const val SET_CONTEXT = "workout-set-context"
    const val WARMUP_CHIP = "workout-warmup-chip"
    const val WARMUP_RAMP = "workout-warmup-ramp"
    const val INSTRUMENT_STRIP = "workout-instrument-strip"
    const val REST_WHEEL = "workout-rest-wheel"
    const val START_SET_CLOCK = "workout-start-set-clock"
    const val STOP_SET_CLOCK = "workout-stop-set-clock"
    const val ADD_SET = "workout-add-set"
    const val LAST_TIME = "workout-last-time"
    const val SELECTED_LIFT = "workout-selected-lift"
    const val START_NEXT = "workout-start-next"
    const val START_REST = "workout-start-rest"
    const val DISCARD = "workout-discard"
    const val DOCK_ADD_LIFT = "workout-dock-add-lift"
    const val LIFT_OPTIONS = "workout-lift-options"
    const val LIFT_SWITCHER = "workout-lift-switcher"
    const val SESSION_NOTES = "workout-session-notes"
    const val WEIGHT_STEPPER = "workout-weight-stepper"
    const val REPS_STEPPER = "workout-reps-stepper"
    const val HOLD_STEPPER = "workout-hold-stepper"
    fun liftCard(exerciseId: String) = "workout-lift-card-$exerciseId"
    fun liftSets(exerciseId: String) = "workout-lift-sets-$exerciseId"
    fun liftRest(exerciseId: String) = "workout-lift-rest-$exerciseId"
    fun liftSwitcherRow(exerciseId: String) = "workout-lift-switcher-$exerciseId"
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
    val rpeHelperVisible by viewModel.rpeHelperVisible.collectAsStateWithLifecycle()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var liftSwitcherOpen by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var finishNotesOpen by rememberSaveable { mutableStateOf(false) }
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
    val sessionWork = remember(session) { session?.work() ?: SetWork.NONE }
    val unit = LocalWeightUnit.current
    val view = LocalView.current
    val listState = rememberLazyListState()
    LaunchedEffect(state.loadState, state.selectedExerciseId) {
        if (state.loadState != SessionLoadState.FOUND) return@LaunchedEffect
        if (state.selectedExerciseId != null) {
            listState.scrollToItem(LogLoopBringIntoView.entryListIndex())
        }
    }

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

    LaunchedEffect(viewModel) {
        viewModel.logFeedback.collect { feedback ->
            when (feedback) {
                LogCommitFeedback.SUCCESS -> Haptics.commit(view)
                LogCommitFeedback.REJECT -> Haptics.reject(view)
            }
        }
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
                canFinish = state.canFinish,
                showDiscard = state.showDiscard,
                compact = LandscapeChrome.compactHeader(landscape),
                onExit = { keepAndExit() },
                onFinish = { confirmEnd = true },
                onDiscard = { confirmDiscard = true },
                onOpenTimer = { session?.id?.let(onOpenRest) },
            )
        },
        bottomBar = {
            // G-02 / Packet 2: timer slot, advance choice, and Log set share
            // the LogBar dock so a one-handed thumb reaches every control.
            // Finish stays in the header — it is not a mid-set act.
            val emptySession = session != null &&
                !session.hasLifts() &&
                FloorCompactChrome.emptySessionHidesTimerDock()
            val showRest = state.showRest
            if (emptySession || showRest || logBarVisible) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (logBarVisible || emptySession) Modifier
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
                    if (emptySession) {
                        PinnedDock(
                            volt = {
                                PrimaryGymButton(
                                    text = LogBarCopy.ADD_LIFT,
                                    onClick = { viewModel.setPickerVisible(true) },
                                    modifier = Modifier.testTag(WorkoutTestTags.DOCK_ADD_LIFT),
                                    height = Metrics.commit,
                                )
                            },
                        )
                    } else if (logBarVisible) {
                        val hold = selected?.exercise?.let { HoldWork.isHold(it) } == true
                        val holdArmed = holdTimer.running || holdTimer.totalSeconds > 0
                        LogBar(
                            editing = state.editingSetId != null,
                            logging = state.logging,
                            canLog = state.canLog,
                            suggestionUnavailable = state.suggestionUnavailable,
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
                            offerSetClock = state.offerSetClock,
                            onStartSetClock = viewModel::startSetStopwatch,
                            onStopSetClock = viewModel::stopSetStopwatch,
                            onSkipRest = viewModel::skipRest,
                            onStartRest = viewModel::startSelectedRest,
                            onSelectRestDuration = viewModel::selectRestDuration,
                            onDismissRestBatteryHint = viewModel::acknowledgeRestBatteryHint,
                            onOpenRest = { session?.id?.let(onOpenRest) },
                            onLog = {
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
                            selected?.let { currentLift ->
                                val currentIndex = session.exercises.indexOfFirst {
                                    it.exercise.id == currentLift.exercise.id
                                }.coerceAtLeast(0)
                                val logged = session.setsFor(currentLift.exercise.id)
                                item(key = "current-lift") {
                                    CurrentLiftCard(
                                        lift = currentLift,
                                        number = currentIndex + 1,
                                        total = session.exercises.size,
                                        workingLogged = workingLogged,
                                        canEdit = logged.isEmpty(),
                                        onOpenSwitcher = { liftSwitcherOpen = true },
                                        onSwap = viewModel::requestSwap,
                                        onRemove = viewModel::removeSelectedLift,
                                        onNotes = { notesOpen = true },
                                    )
                                }
                                item(key = "current-entry") {
                                    WorkoutLiftCard(
                                        card = WorkoutLiftCardState(
                                            lift = currentLift,
                                            number = currentIndex + 1,
                                            selected = true,
                                            loggedSets = logged,
                                            latestSetId = WorkoutAdvance.latestSetId(logged),
                                            editingSetId = state.editingSetId,
                                            lastPerformance = state.lastPerformance,
                                            hint = state.hint,
                                            draftWeightKg = state.draft.weightKg,
                                            draftReps = state.draft.reps,
                                            draftWarmup = state.draft.isWarmup,
                                            draftRpe = state.draft.rpe,
                                            microRec = microRec,
                                            recommendedRpe = microRec?.nextRpe,
                                            unit = unit,
                                            canEdit = logged.isEmpty(),
                                            showAddSet = WorkoutAdvance.cardOffersAnotherSet(
                                                logged,
                                                currentLift.targetSets,
                                            ),
                                            restRunning = rest.running,
                                            restRemainingSeconds = rest.remainingSeconds,
                                            restSeconds = currentLift.restSeconds.takeIf { it > 0 }
                                                ?: rest.totalSeconds,
                                            hold = HoldWork.isHold(currentLift.exercise),
                                            holdSeconds = state.draft.durationSeconds
                                                ?: currentLift.targetSeconds,
                                            holdRunning = holdTimer.running,
                                            holdRemainingSeconds = holdTimer.remainingSeconds,
                                            rpeHelperVisible = rpeHelperVisible,
                                        ),
                                        events = WorkoutLiftCardEvents(
                                            onWeightKgChange = viewModel::setWeight,
                                            onRepsAdjust = viewModel::adjustReps,
                                            onRepsChange = viewModel::setReps,
                                            onSecondsAdjust = viewModel::adjustHoldSeconds,
                                            onSecondsChange = viewModel::setHoldSeconds,
                                            onApplyLastTime = viewModel::applyLastTimeSet,
                                            onWarmup = viewModel::setWarmup,
                                            onRpe = viewModel::setRpe,
                                            onApplyWarmupRamp = viewModel::applyWarmupRamp,
                                            onDismissRpeHelper = viewModel::dismissRpeHelper,
                                            onApplySuggested = viewModel::applySuggestedWeight,
                                            onEditSet = viewModel::editSet,
                                            onDeleteSet = viewModel::deleteSet,
                                            onAddSet = viewModel::requestExtraSet,
                                        ),
                                    )
                                }
                            }
                            item(key = "add-lift") {
                                SecondaryGymButton(
                                    text = "Add a lift",
                                    onClick = { viewModel.setPickerVisible(true) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (liftSwitcherOpen && session != null && session.hasLifts()) {
        LiftSwitcherSheet(
            lifts = session.exercises.mapIndexed { index, lift ->
                val isCurrent = lift.exercise.id == state.selectedExerciseId
                LiftSwitcherRow(
                    lift = lift,
                    number = index + 1,
                    workingLogged = session.setsFor(lift.exercise.id).count { !it.isWarmup },
                    restSeconds = if (isCurrent) {
                        lift.restSeconds.takeIf { it > 0 } ?: rest.totalSeconds
                    } else {
                        lift.restSeconds
                    },
                    restRunning = isCurrent && rest.running,
                    restRemainingSeconds = rest.remainingSeconds,
                    current = isCurrent,
                )
            },
            onSelect = { exerciseId ->
                liftSwitcherOpen = false
                viewModel.selectExercise(exerciseId)
            },
            onDismiss = { liftSwitcherOpen = false },
        )
    }

    if (notesOpen) {
        AlertDialog(
            onDismissRequest = { notesOpen = false },
            title = { Text(CurrentLiftCopy.SESSION_NOTES, style = InstrumentType.title) },
            text = {
                NotesBlock(
                    notes = state.notes,
                    expanded = true,
                    onToggle = { notesOpen = false },
                    onChange = viewModel::setNotes,
                    modifier = Modifier.testTag(WorkoutTestTags.SESSION_NOTES),
                )
            },
            confirmButton = {
                TextButton(onClick = { notesOpen = false }) {
                    Text("Done", style = InstrumentType.bodyStrong)
                }
            },
        )
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
            notes = state.notes,
            notesExpanded = finishNotesOpen,
            onToggleNotes = { finishNotesOpen = !finishNotesOpen },
            onNotesChange = viewModel::setNotes,
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
