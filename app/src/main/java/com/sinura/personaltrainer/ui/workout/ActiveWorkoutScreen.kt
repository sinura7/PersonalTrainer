package com.sinura.personaltrainer.ui.workout


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.ExerciseFloorStatsCalculator
import com.sinura.personaltrainer.domain.ExerciseFloorStatsPresentation
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerMode
import com.sinura.personaltrainer.domain.ExercisePickerState
import com.sinura.personaltrainer.domain.FloorCompactChrome
import com.sinura.personaltrainer.domain.FloorTimedModeResolver
import com.sinura.personaltrainer.domain.FloorTimerCue
import com.sinura.personaltrainer.domain.FloorTimerSurface
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LogCommitFeedback
import com.sinura.personaltrainer.domain.PersonalRecordCopy
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetOrdinalCopy
import com.sinura.personaltrainer.domain.SetStopwatchCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WarmupRamp
import com.sinura.personaltrainer.domain.WeightDraftSource
import com.sinura.personaltrainer.domain.WorkoutAdvance
import com.sinura.personaltrainer.domain.WorkoutProgressCalculator
import com.sinura.personaltrainer.timer.RestTimerAlerts
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.EndWorkoutDialog
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymUndoHost
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.NotesBlock
import com.sinura.personaltrainer.ui.components.PersonalRecordBanner
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Stable semantics for the critical device journey; copy remains free to improve. */
object WorkoutTestTags {
    fun weightPreset(source: WeightDraftSource) = "workout-weight-preset-${source.name.lowercase()}"
    /** A set's chip on the floor; the saved-sets sheet's rows keep [setOptions], so both can be open at once. */
    fun setChip(setId: String) = "workout-set-chip-$setId"
    const val CONTENT = "workout-content"
    const val LOG_SET = "workout-log-set"
    const val FINISH = "workout-finish"
    const val NOTIF_RECOVERY = "workout-notif-recovery"
    const val CURRENT_LIFT = "workout-current-lift"
    const val SET_ENTRY = "workout-set-entry"
    const val REST_BAR = "workout-rest-bar"
    const val REST_IDLE = "workout-rest-idle"
    const val REST_MINUS = "workout-rest-minus"
    const val REST_PLUS = "workout-rest-plus"
    const val REST_SKIP = "workout-rest-skip"
    const val HOLD_CLOCK = "workout-hold-clock"
    const val MICRO_REC = "workout-micro-rec"
    const val MICRO_REC_APPLY = "workout-micro-rec-apply"
    const val MICRO_REC_WHY = "workout-micro-rec-why"
    const val COACH_EVIDENCE_CHIP = "workout-coach-evidence-chip"
    const val NEXT_SET = "workout-next-set"
    const val NEXT_SET_COMPACT = "workout-next-set-compact"
    const val NEXT = "workout-next"
    const val DOCK_FINISH = "workout-dock-finish"
    const val ANOTHER_SET = "workout-another-set"
    const val RPE_TRACK = "workout-rpe-track"
    const val RPE_HELPER = "workout-rpe-helper"
    const val RPE_CLEAR = "workout-clear-rpe"
    const val RPE_WARMUP_REASON = "workout-rpe-warmup-reason"
    const val SET_CONTEXT = "workout-set-context"
    const val SET_TYPE = "workout-set-type"
    const val WORKING_CHIP = "workout-working-choice"
    const val WARMUP_CHIP = "workout-warmup-chip"
    const val WARMUP_RAMP = "workout-warmup-ramp"
    const val START_SET_CLOCK = "workout-start-set-clock"
    const val STOP_SET_CLOCK = "workout-stop-set-clock"
    const val SET_HISTORY = "workout-set-history"
    const val CURRENT_SET = "workout-current-set"
    const val VIEW_SETS = "workout-view-sets"
    const val SAVED_SETS_SHEET = "workout-saved-sets-sheet"
    const val SELECTED_LIFT = "workout-selected-lift"
    const val START_REST = "workout-start-rest"
    const val DISCARD = "workout-discard"
    const val DOCK_ADD_LIFT = "workout-dock-add-lift"
    const val LIFT_OPTIONS = "workout-lift-options"
    const val LIFT_SWITCHER = "workout-lift-switcher"
    const val SWITCHER_ADD_LIFT = "workout-switcher-add-lift"
    const val TIMER_ROW = "workout-timer-row"
    const val COMPANION_CLOCK = "workout-companion-clock"
    const val CANCEL_EDIT = "workout-cancel-edit"
    const val ERROR_DETAILS = "workout-error-details"
    const val REST_DURATION_SHEET = "workout-rest-duration-sheet"
    const val SHEET_START_SET_CLOCK = "workout-sheet-start-set-clock"
    const val SESSION_NOTES = "workout-session-notes"
    const val WEIGHT_STEPPER = "workout-weight-stepper"
    const val REPS_STEPPER = "workout-reps-stepper"
    const val HOLD_STEPPER = "workout-hold-stepper"
    const val PROGRESS_LINE = "workout-progress-line"
    const val PROGRESS_BAR = "workout-progress-bar"
    const val DETAILS = "workout-details"
    const val LIFT_SWITCH = "workout-lift-switch"
    const val STATS_ROW = "workout-stats"
    const val STAT_LAST = "workout-stat-last"
    const val STAT_BEST = "workout-stat-best"
    const val STAT_VOLUME = "workout-stat-volume"
    fun liftCard(exerciseId: String) = "workout-lift-card-$exerciseId"
    fun liftSets(exerciseId: String) = "workout-lift-sets-$exerciseId"
    fun liftRest(exerciseId: String) = "workout-lift-rest-$exerciseId"
    fun liftSwitcherRow(exerciseId: String) = "workout-lift-switcher-$exerciseId"
    fun setOptions(setId: String) = "workout-set-options-$setId"
    fun rpeChoice(value: Int) = "workout-rpe-$value"
}

@Composable
fun ActiveWorkoutScreen(
    onExit: () -> Unit,
    onFinished: (String) -> Unit,
    onOpenRest: (String) -> Unit = {},
    onOpenExercise: (String) -> Unit = {},
    viewModel: ActiveWorkoutViewModel = viewModel(),
    restNotificationsEnabledOverride: Boolean? = null,
) {
    // Use the space assigned by our parent, including embedded / constrained
    // windows. The host window can be portrait while this surface is landscape.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        ActiveWorkoutContent(
            onExit = onExit, onFinished = onFinished, onOpenRest = onOpenRest, onOpenExercise = onOpenExercise,
            viewModel = viewModel, restNotificationsEnabledOverride = restNotificationsEnabledOverride,
            landscape = LandscapeChrome.isLandscape(
                widthDp = maxWidth.value.roundToInt(), heightDp = maxHeight.value.roundToInt(),
            ),
        )
    }
}

@Composable
private fun ActiveWorkoutContent(
    onExit: () -> Unit,
    onFinished: (String) -> Unit,
    onOpenRest: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: ActiveWorkoutViewModel,
    restNotificationsEnabledOverride: Boolean?,
    landscape: Boolean,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val restState = viewModel.restTimerState.collectAsStateWithLifecycle()
    val holdState = viewModel.holdTimer.collectAsStateWithLifecycle()
    val stopwatchState = viewModel.setStopwatch.collectAsStateWithLifecycle()
    val microRec by viewModel.microRec.collectAsStateWithLifecycle()
    val exerciseHistory by viewModel.exerciseHistory.collectAsStateWithLifecycle()
    val extraSetRequested by viewModel.extraSetRequested.collectAsStateWithLifecycle()
    val exitRequested by viewModel.exitRequested.collectAsStateWithLifecycle()
    val personalRecord by viewModel.personalRecord.collectAsStateWithLifecycle()
    val undoEntries by viewModel.undoEntries.collectAsStateWithLifecycle()
    val undoDwellMs by viewModel.undoDwellMs.collectAsStateWithLifecycle()
    val primaryState = viewModel.primaryAction.collectAsStateWithLifecycle()
    val logReceipt by viewModel.logReceipt.collectAsStateWithLifecycle()
    val pendingLiftSwitch by viewModel.pendingLiftSwitch.collectAsStateWithLifecycle()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var liftSwitcherOpen by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var sessionSummaryOpen by rememberSaveable { mutableStateOf(false) }
    var finishNotesOpen by rememberSaveable { mutableStateOf(false) }
    var setsOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.entryLocked) {
        if (state.entryLocked) {
            confirmEnd = false
            confirmDiscard = false
            liftSwitcherOpen = false
            setsOpen = false
            viewModel.setPickerVisible(false)
            viewModel.cancelPendingLiftSwitch()
        }
    }
    val restNotificationsEnabled = restNotificationsEnabledOverride
        ?: rememberRestNotificationsEnabled()
    val session = state.session
    val selected = session?.exercises?.firstOrNull { it.exercise.id == state.selectedExerciseId }
    val logged = remember(session, selected) {
        selected?.let { lift -> session?.setsFor(lift.exercise.id) }.orEmpty()
    }
    val afterWarmup = logged.maxByOrNull { it.completedAt }?.isWarmup == true
    val advance = remember(session, state.selectedExerciseId, extraSetRequested, state.editingSetId, state.draft.isWarmup) {
        WorkoutAdvance.forSelection(
            session = session,
            selectedExerciseId = state.selectedExerciseId,
            wantAnother = extraSetRequested || state.draft.isWarmup,
            editing = state.editingSetId != null,
        )
    }
    val workingLogged = advance.workingLogged
    val progress = remember(session, state.selectedExerciseId) {
        WorkoutProgressCalculator.of(session = session, selectedExerciseId = state.selectedExerciseId)
    }
    val sessionWork = remember(session) { session?.work() ?: SetWork.NONE }
    val unit = LocalWeightUnit.current
    val view = LocalView.current
    val receiptDwell = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = Motion.STATUS_DWELL_MS,
        containsIcons = false,
        containsText = true,
        containsControls = false,
    ) ?: Motion.STATUS_DWELL_MS
    LaunchedEffect(logReceipt, receiptDwell) {
        val receipt = logReceipt ?: return@LaunchedEffect
        // The saved chip may be outside the lazy viewport. Announce the durable
        // result once here, never from a timer tick or a chip entering composition.
        @Suppress("DEPRECATION")
        view.announceForAccessibility(receipt.line)
        delay(receiptDwell)
        viewModel.onLogReceiptShown()
    }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(state.loadState, state.selectedExerciseId) {
        if (state.loadState != SessionLoadState.FOUND) return@LaunchedEffect
        if (state.selectedExerciseId != null) {
            listState.scrollToItem(LogLoopBringIntoView.entryListIndex())
        }
    }
    // An edit deliberately reveals the entry; ordinary saves keep the viewport where it is.
    LaunchedEffect(state.editingSetId) {
        if (state.editingSetId != null) listState.animateScrollToItem(LogLoopBringIntoView.editRevealIndex())
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
            is WorkoutExit.Finished -> {
                Haptics.commit(view)
                onFinished(reason.sessionId)
            }
            WorkoutExit.Discarded -> onExit()
        }
    }

    // Back goes Home with the session still live. Finish is the explicit end
    // (save as is / leave without saving). Both flush the draft.
    fun keepAndExit() {
        viewModel.persistDraftForExit()
        onExit()
    }
    BackHandler(enabled = state.session != null) { keepAndExit() }

    // Always composed, unlike the bottom bar. An error raised while no lift is selected —
    // a failed create from the picker in an empty free workout — previously had no reader at
    // all: it was written to state and rendered nowhere.
    val logBarVisible = state.loadState == SessionLoadState.FOUND && session != null &&
        (selected != null || state.save.pending)

    // The record's haptics and its acknowledgement live here rather than inside the banner:
    // the banner is a list item, and logging the set that breaks a record also scrolls the
    // list, so the item is disposed within a second. This screen is always composed, so the
    // beats fire once and the record is always cleared.
    LaunchedEffect(personalRecord) {
        if (personalRecord == null) return@LaunchedEffect
        delay(Motion.PR_ACCENT_DELAY_MS.toLong())
        Haptics.recordAccent(view)
        delay(PERSONAL_RECORD_DWELL_MS)
        viewModel.onPersonalRecordShown()
    }

    // Log's success haptic fires after the durable write, from the commit feedback, never
    // from the tap: a press that does not save must not feel like one that did.
    LaunchedEffect(viewModel) {
        viewModel.logFeedback.collect { feedback ->
            when (feedback) {
                LogCommitFeedback.SUCCESS -> Haptics.commit(view)
                LogCommitFeedback.REJECT -> Haptics.reject(view)
            }
        }
    }

    // Packet G (HA-22/HA-23): a destructive landing warns lightly *after* the write;
    // a landed undo confirms. Failures and expiries stay silent.
    LaunchedEffect(viewModel) {
        viewModel.deleteFeedback.collect { feedback ->
            when (feedback) {
                DeleteFeedback.DELETED, DeleteFeedback.REMOVED -> Haptics.warn(view)
                DeleteFeedback.UNDO -> Haptics.commit(view)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.floorTimerCue.collect { cue ->
            when (cue) {
                FloorTimerCue.HoldStarted -> Haptics.warn(view)
                is FloorTimerCue.HoldTarget -> {
                    Haptics.holdDone(view)
                    RestTimerAlerts.holdTargetTone(context, cue.soundEnabled)
                }
                FloorTimerCue.StopwatchStarted -> Haptics.tickLight(view)
                FloorTimerCue.StopwatchStopped -> Haptics.warn(view)
            }
        }
    }

    val loadClass = LoadClass.of(selected?.exercise?.loadType)
    // Derived and deduplicated: the primary action re-emits every second while a hold or
    // the set clock runs, and only the dock reads it live. The rest of the floor moves
    // only when the plan flips between logging and advancing.
    val plannedComplete by remember(primaryState) {
        derivedStateOf {
            val kind = primaryState.value.kind
            kind == WorkoutPrimaryKind.NEXT_EXERCISE || kind == WorkoutPrimaryKind.FINISH
        }
    }
    val setContext = when {
        state.editingSetId != null -> "Editing saved set"
        plannedComplete -> "Planned sets complete"
        else -> SetOrdinalCopy.draftLine(
            isWarmup = state.draft.isWarmup,
            warmupLogged = logged.count { it.isWarmup },
            workingLogged = workingLogged,
            targetSets = selected?.targetSets ?: 0,
        )
    }

    Scaffold(
        snackbarHost = {
            val errorBanner = state.error != null && !logBarVisible
            val undoTop = undoEntries.lastOrNull()?.offer.takeIf { !logBarVisible }
            if (errorBanner || undoTop != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Metrics.gutter),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    if (errorBanner) {
                        GymErrorBanner(message = state.error!!)
                    }
                    // Undo dwell honours the accessibility timeout. Advance is not a
                    // banner: Next exercise / Add another set stand in the dock until chosen.
                    undoTop?.let { offer ->
                        GymUndoHost(
                            message = offer.message,
                            onUndo = viewModel::undoTopOffer,
                            onDismissed = viewModel::onUndoOfferExpired,
                            offerKey = offer.key,
                            dwellMs = undoDwellMs,
                        )
                    }
                }
            }
        },
        topBar = {
            WorkoutHeader(
                routineName = session?.routineName ?: "Workout",
                progress = progress,
                canFinish = state.canFinish,
                showDiscard = state.showDiscard,
                compact = LandscapeChrome.compactHeader(landscape),
                onExit = { keepAndExit() },
                onFinish = { confirmEnd = true },
                onDiscard = { confirmDiscard = true },
                overflow = selected?.let { lift ->
                    {
                        LiftOverflowMenu(
                            liftId = lift.id,
                            canEdit = logged.isEmpty(),
                            onSwap = viewModel::requestSwap,
                            onRemove = viewModel::removeSelectedLift,
                            onNotes = { notesOpen = true },
                            onSummary = { sessionSummaryOpen = true },
                            onSkip = viewModel::skipForNow,
                            onSwitch = { liftSwitcherOpen = true },
                            onDetails = { onOpenExercise(lift.exercise.id) },
                            enabled = !state.entryLocked,
                        )
                    }
                },
            )
        },
        bottomBar = {
            // Read ticking values inside the dock's composition scope. The screen,
            // exercise identity, and history chips do not subscribe to each tick.
            val rest = restState.value
            val holdTimer = holdState.value
            val setStopwatch = stopwatchState.value
            val primaryAction = primaryState.value
            val holdLift = selected?.exercise?.let { HoldWork.isHold(it) } == true
            val offerSetClock = FloorTimedModeResolver.offerSetClock(
                mode = FloorTimerSurface.mode(
                    holdRunning = holdTimer.running,
                    stopwatchRunning = setStopwatch.running,
                    hasLifts = session?.hasLifts() == true,
                    restRunning = rest.running,
                    restComplete = rest.completedTimerId != null && !rest.running,
                    holdActive = holdTimer.running || holdTimer.targetReached,
                ),
                isHoldLift = holdLift,
            ) && state.offerSetClock
            val showNext = primaryAction.kind == WorkoutPrimaryKind.NEXT_EXERCISE
            val showFinish = primaryAction.kind == WorkoutPrimaryKind.FINISH
            val showAnother = (showNext || showFinish) && !state.entryLocked
            // The timer slot, advance choice, and Log set share one dock so a one-handed
            // thumb reaches every control. Finish stays in the header — it is not a mid-set act.
            val emptySession = state.loadState == SessionLoadState.FOUND && session != null &&
                !session.hasLifts() && !state.save.pending &&
                FloorCompactChrome.emptySessionHidesTimerDock()
            val showRest = state.loadState == SessionLoadState.FOUND && state.showRest
            if (emptySession || showRest || logBarVisible) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (logBarVisible || emptySession) Modifier
                            else Modifier.navigationBarsPadding(),
                        ),
                ) {
                    if (emptySession) {
                        PinnedDock(
                            volt = {
                                PrimaryGymButton(
                                    text = "Add exercise",
                                    onClick = { viewModel.performPrimary(primaryAction) },
                                    enabled = primaryAction.enabled,
                                    modifier = Modifier.testTag(WorkoutTestTags.DOCK_ADD_LIFT),
                                    height = Metrics.commit,
                                )
                            },
                        )
                    } else if (logBarVisible) {
                        WorkoutDock(
                            state = WorkoutDockState(
                                primaryAction = primaryAction,
                                // The verb stays short in every orientation. Portrait names the next
                                // lift on the commit's capped supporting line; a 360 dp landscape has
                                // no room for a second line there, so it only speaks it.
                                verb = primaryAction.verb(includeNextName = false),
                                payload = primaryAction.payload(unit = unit, loadClass = loadClass)
                                    ?: primaryAction.nextName.takeIf { !landscape && primaryAction.kind == WorkoutPrimaryKind.NEXT_EXERCISE },
                                spokenPayload = primaryAction.nextName.takeIf { primaryAction.kind == WorkoutPrimaryKind.NEXT_EXERCISE },
                                editing = state.editingSetId != null,
                                logging = state.logging,
                                canLog = state.canLog,
                                savePending = state.save.pending,
                                error = state.error,
                                suggestionUnavailable = state.suggestionUnavailable,
                                showAnother = showAnother,
                                undoMessage = undoEntries.lastOrNull()?.offer?.message,
                                undoKey = undoEntries.lastOrNull()?.offer?.key,
                                undoDwellMs = undoDwellMs,
                                timer = WorkoutDockTimer(
                                    show = showRest,
                                    restRemainingSeconds = rest.remainingSeconds,
                                    restTotalSeconds = rest.totalSeconds,
                                    restRunning = rest.running,
                                    restCompletedTimerId = rest.completedTimerId,
                                    hideIdleRest = LandscapeChrome.hideIdleRest(landscape),
                                    afterWarmup = afterWarmup,
                                    batteryHint = rest.batteryHint,
                                    holdRunning = holdTimer.running,
                                    holdElapsedSeconds = holdTimer.elapsedSeconds,
                                    holdRemainingSeconds = holdTimer.remainingSeconds,
                                    holdTotalSeconds = holdTimer.totalSeconds,
                                    holdTargetReached = holdTimer.targetReached,
                                    stopwatchRunning = setStopwatch.running,
                                    stopwatchElapsedSeconds = setStopwatch.elapsedSeconds,
                                    offerSetClock = offerSetClock,
                                    persistenceHealthy = rest.persistenceHealthy,
                                    exactBestEffort = rest.exactAlarmBestEffort,
                                    notificationsEnabled = restNotificationsEnabled,
                                ),
                            ),
                            events = WorkoutDockEvents(
                                onPrimary = { action ->
                                    val accepted = viewModel.performPrimary(action)
                                    if (accepted && action.kind == WorkoutPrimaryKind.FINISH) confirmEnd = true
                                    if (accepted && action.kind == WorkoutPrimaryKind.NEXT_EXERCISE) Haptics.warn(view)
                                    accepted
                                },
                                onEditFailedSave = viewModel::editFailedSave,
                                onCancelEdit = viewModel::cancelEdit,
                                onDismissError = viewModel::dismissError,
                                onAnotherSet = {
                                    Haptics.tick(view)
                                    viewModel.requestExtraSet()
                                },
                                onUndo = viewModel::undoTopOffer,
                                onUndoDismissed = viewModel::onUndoOfferExpired,
                                onSkipRest = viewModel::skipRest,
                                onStartRest = viewModel::startSelectedRest,
                                onSelectRestDuration = viewModel::selectRestDuration,
                                onNudgeRest = viewModel::nudgeRest,
                                onCustomRest = viewModel::selectCustomRest,
                                onStartSetClock = viewModel::startSetStopwatch,
                                onStopSetClock = viewModel::stopSetStopwatch,
                                onDismissRestBatteryHint = viewModel::acknowledgeRestBatteryHint,
                                onOpenRest = { session?.id?.let(onOpenRest) },
                                onOpenNotifications = { openRestNotificationSettings(context) },
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loadState == SessionLoadState.FAILED -> {
                EmptyState(
                    scene = EmptyScene.GONE,
                    title = "Workout unavailable",
                    body = "Your workout could not be read. Your draft is kept. Retry, or close this screen and return later.",
                    actionLabel = "Retry",
                    onAction = viewModel::retrySession,
                    compact = true,
                    modifier = Modifier.padding(padding).padding(Metrics.gutter),
                )
            }

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
                        // 12, not 16. The floor's blocks are already separated by hairlines
                        // and by the change of voice between them; the extra four points per
                        // gap bought no clarity and, over five gaps, pushed the commit off
                        // the screen. The bottom padding stays: that is the dock's clearance.
                        verticalArrangement = Arrangement.spacedBy(Metrics.space3),
                    ) {
                        if (!session.hasLifts()) {
                            item(key = "empty-lifts") {
                                EmptyState(
                                    scene = EmptyScene.RACK,
                                    title = "Add a lift",
                                    body = SessionOrderCopy.EMPTY_SESSION_BODY,
                                    compact = true,
                                )
                            }
                        } else {
                            selected?.let { currentLift ->
                                val currentIndex = session.exercises.indexOfFirst {
                                    it.exercise.id == currentLift.exercise.id
                                }.coerceAtLeast(0)
                                val entryEnabled = !state.entryLocked
                                val hold = HoldWork.isHold(currentLift.exercise)
                                item(key = "exercise-header") {
                                    ExerciseHeader(
                                        lift = currentLift,
                                        number = currentIndex + 1,
                                        total = session.exercises.size,
                                        setContext = setContext,
                                        draftWarmup = state.draft.isWarmup,
                                        onWarmup = viewModel::setWarmup,
                                        onOpenSwitcher = { liftSwitcherOpen = true },
                                        onDetails = { onOpenExercise(currentLift.exercise.id) },
                                        enabled = entryEnabled,
                                    )
                                }
                                item(key = "stats") {
                                    val workingSetsToday = remember(session, currentLift.exercise.id) {
                                        ExerciseFloorStatsPresentation.workingSetsLoggedToday(
                                            session = session,
                                            exerciseId = currentLift.exercise.id,
                                        )
                                    }
                                    val statsVisibility = remember(workingSetsToday) {
                                        ExerciseFloorStatsPresentation.rowVisibility(workingSetsToday)
                                    }
                                    val stats = remember(session, currentLift.exercise.id, state.lastPerformance, exerciseHistory, unit) {
                                        ExerciseFloorStatsCalculator.of(
                                            session = session,
                                            exerciseId = currentLift.exercise.id,
                                            lastPerformance = state.lastPerformance,
                                            priorHistory = exerciseHistory,
                                            unit = unit,
                                        )
                                    }
                                    Column {
                                        HairlineDivider(startIndent = 0.dp)
                                        ExerciseStatsRow(
                                            stats = stats,
                                            unit = unit,
                                            visibility = statsVisibility,
                                            onApplyLastSet = if (entryEnabled) viewModel::applyLastTimeSet else null,
                                        )
                                        HairlineDivider(startIndent = 0.dp)
                                    }
                                }
                                item(key = "entry") {
                                    val holdTimer = holdState.value
                                    val lastKg = state.hint?.lastWeightKg ?: state.lastPerformance?.topSet?.weightKg
                                    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                                        WeightRepsEditor(
                                            enabled = entryEnabled,
                                            weightKg = state.draft.weightKg,
                                            reps = state.draft.reps,
                                            unit = unit,
                                            loadClass = loadClass,
                                            loadType = currentLift.exercise.loadType,
                                            equipment = currentLift.exercise.equipment,
                                            movementKey = currentLift.exercise.movementKey,
                                            plated = currentLift.exercise.equipment == EquipmentType.BARBELL,
                                            hold = hold,
                                            holdSeconds = state.draft.durationSeconds ?: currentLift.targetSeconds,
                                            holdRunning = holdTimer.running,
                                            holdRemainingSeconds = holdTimer.remainingSeconds,
                                            plannedKg = currentLift.targetWeightKg,
                                            lastKg = lastKg,
                                            onWeightKgChange = viewModel::setWeight,
                                            onRepsChange = viewModel::setReps,
                                            onSecondsChange = viewModel::setHoldSeconds,
                                        )
                                        if (state.draft.isWarmup) {
                                            val workingKg = WarmupRamp.workingWeightKg(
                                                draftKg = state.draft.weightKg,
                                                draftIsWarmup = true,
                                                workingLogged = workingLogged,
                                                targetKg = currentLift.targetWeightKg,
                                                suggestedKg = state.hint?.suggestedWeightKg,
                                                lastKg = lastKg,
                                                loadType = currentLift.exercise.loadType,
                                                equipment = currentLift.exercise.equipment,
                                                movementKey = currentLift.exercise.movementKey,
                                            )
                                            val ramp = if (workingLogged == 0) {
                                                WarmupRamp.sets(
                                                    workingWeightKg = workingKg,
                                                    loadType = currentLift.exercise.loadType,
                                                    unit = unit,
                                                    equipment = currentLift.exercise.equipment,
                                                )
                                            } else {
                                                emptyList()
                                            }
                                            WarmupRampRow(
                                                enabled = entryEnabled,
                                                ramp = ramp,
                                                emphasisIndex = WarmupRamp.nextUnusedIndex(
                                                    ramp = ramp,
                                                    loggedWarmupKg = logged.filter { it.isWarmup }.map { it.weightKg },
                                                ),
                                                unit = unit,
                                                onApplyRamp = viewModel::applyWarmupRamp,
                                            )
                                        }
                                    }
                                }
                                item(key = "rpe") {
                                    RpeSelector(
                                        enabled = entryEnabled,
                                        warmup = state.draft.isWarmup,
                                        rpe = state.draft.rpe,
                                        recommendedRpe = microRec?.nextRpe,
                                        onRpe = viewModel::setRpe,
                                    )
                                }
                                val rec = microRec?.takeIf { entryEnabled && !state.draft.isWarmup && SetMicroRecCopy.visibleOnEntry(it) }
                                if (rec != null) {
                                    item(key = "next-set") {
                                        val applied = rec.isApplied(
                                            weightKg = state.draft.weightKg,
                                            reps = state.draft.reps,
                                            rpe = state.draft.rpe,
                                            unit = unit,
                                        )
                                        val coachCompact = FloorCompactChrome.coachUsesCompactStrip(
                                            preparePhase = workingLogged == 0,
                                            entryMatchesSuggestion = applied,
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                                            HairlineDivider(startIndent = 0.dp)
                                            NextSetRecommendation(
                                                rec = rec,
                                                loadClass = loadClass,
                                                unit = unit,
                                                applied = applied,
                                                enabled = entryEnabled,
                                                onApply = viewModel::applyMicroRec,
                                                compact = coachCompact,
                                            )
                                        }
                                    }
                                }
                                item(key = "set-history") {
                                    val current = if (state.editingSetId != null || plannedComplete) {
                                        null
                                    } else {
                                        CurrentSetMark(
                                            mark = SetOrdinalCopy.draftMark(state.draft.isWarmup, workingLogged),
                                            label = SetOrdinalCopy.draftChipLabel(
                                                isWarmup = state.draft.isWarmup,
                                                warmupLogged = logged.count { it.isWarmup },
                                                workingLogged = workingLogged,
                                                targetSets = currentLift.targetSets,
                                            ),
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                                        HairlineDivider(startIndent = 0.dp)
                                        SetHistoryStrip(
                                            sets = logged,
                                            targetSets = currentLift.targetSets,
                                            loadClass = loadClass,
                                            unit = unit,
                                            editingSetId = state.editingSetId,
                                            receiptSetId = logReceipt?.setId,
                                            current = current,
                                            enabled = entryEnabled,
                                            onEdit = viewModel::editSet,
                                            onDelete = viewModel::deleteSet,
                                            onOpenAll = { setsOpen = true },
                                        )
                                    }
                                }
                            }
                        }
                        personalRecord?.let { moment ->
                            item(key = "pr-moment") {
                                PersonalRecordBanner(
                                    headline = PersonalRecordCopy.headline(moment.kinds),
                                    detail = "${moment.exerciseName.ifBlank { "This lift" }} · " +
                                        SetCopy.setLine(moment.weightKg, moment.reps, loadClass, unit),
                                    onDismiss = viewModel::onPersonalRecordShown,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (setsOpen && selected != null) {
        WorkoutSetsSheet(
            exerciseName = selected.exercise.name,
            sets = logged,
            latestSetId = WorkoutAdvance.latestSetId(logged),
            editingSetId = state.editingSetId,
            targetSets = selected.targetSets,
            loadClass = loadClass,
            unit = unit,
            showAddSet = WorkoutAdvance.cardOffersAnotherSet(logged, selected.targetSets) && !extraSetRequested,
            onEdit = { setsOpen = false; viewModel.editSet(it) },
            onDelete = { setsOpen = false; viewModel.deleteSet(it) },
            onAddSet = { setsOpen = false; viewModel.requestExtraSet() },
            onDismiss = { setsOpen = false },
        )
    }

    if (liftSwitcherOpen && session != null && session.hasLifts()) {
        WorkoutSwitcherClock(rest = restState) { rest ->
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
                onAddLift = {
                    liftSwitcherOpen = false
                    viewModel.setPickerVisible(true)
                },
            )
        }
    }

    pendingLiftSwitch?.let {
        ConfirmActionDialog(
            title = SetStopwatchCopy.SWITCH_TITLE,
            body = SetStopwatchCopy.SWITCH_BODY,
            confirmLabel = SetStopwatchCopy.SWITCH_CONFIRM,
            onConfirm = viewModel::confirmStopTimingAndSwitch,
            onDismiss = viewModel::cancelPendingLiftSwitch,
        )
    }

    if (sessionSummaryOpen && session != null) {
        WorkoutSessionSummary(
            routineName = session.routineName ?: "Workout",
            startedAt = session.startedAt,
            workingSets = session.sets.count { !it.isWarmup },
            warmups = session.sets.count { it.isWarmup },
            work = sessionWork,
            unit = unit,
            onDismiss = { sessionSummaryOpen = false },
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

/** A suggestion counts as applied once the entry matches it to within a rounding hair. */

/** Keep the optional switcher's clock subscription out of the parent screen. */
@Composable
private fun WorkoutSwitcherClock(
    rest: androidx.compose.runtime.State<RestTimerUiState>,
    content: @Composable (RestTimerUiState) -> Unit,
) {
    content(rest.value)
}
