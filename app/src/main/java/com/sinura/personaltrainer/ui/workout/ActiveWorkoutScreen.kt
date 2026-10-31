package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.ui.components.RecoverableExercisePickerSheet


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.ExerciseFloorStatsCalculator
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerMode
import com.sinura.personaltrainer.domain.ExercisePickerState
import com.sinura.personaltrainer.domain.FloorWeightPresets
import com.sinura.personaltrainer.domain.RpeCopy
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
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.SetOrdinalCopy
import com.sinura.personaltrainer.domain.SetStopwatchCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WarmupRamp
import com.sinura.personaltrainer.domain.coach.TempoCoachTip
import com.sinura.personaltrainer.domain.WeightDraftSource
import com.sinura.personaltrainer.domain.WorkoutAdvance
import com.sinura.personaltrainer.domain.WorkoutProgressCalculator
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.timer.RestTimerAlerts
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymDialog
import com.sinura.personaltrainer.ui.components.InstrumentPreset
import com.sinura.personaltrainer.ui.components.QuietButton
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymUndoHost
import com.sinura.personaltrainer.ui.components.FloorSection
import com.sinura.personaltrainer.ui.components.NotesBlock
import com.sinura.personaltrainer.ui.components.NotesLeaveDialog
import com.sinura.personaltrainer.ui.components.PersonalRecordBanner
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.EffortChips
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.TempoLine
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
    const val SET_OPTIONS_SHEET = "workout-set-options-sheet"
    const val SET_OPTIONS_CONTENT = "workout-set-options-content"
    const val SET_OPTIONS_DONE = "workout-set-options-done"
    const val SET_OPTIONS_SETS = "workout-set-options-sets"
    const val SET_OPTIONS_TEMPO = "workout-set-options-tempo"
    const val SET_OPTIONS_DETAILS = "workout-set-options-details"
    const val CONTENT = "workout-content"
    const val LOG_SET = "workout-log-set"
    const val LOG_READINESS = "workout-log-readiness"
    const val FINISH = "workout-finish"
    const val NOTIF_RECOVERY = "workout-notif-recovery"
    const val CURRENT_LIFT = "workout-current-lift"
    const val SET_ENTRY = "workout-set-entry"
    const val REST_BAR = "workout-rest-bar"
    const val REST_CLOCK = "workout-rest-clock"
    const val REST_PROGRESS = "workout-rest-progress"
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
    const val FLOOR_DOCK = "workout-floor-dock"
    const val TEMPO_COACH_CARD = "workout-tempo-coach-card"
    const val TEMPO_LINE = "workout-tempo-line"
    const val UP_NEXT_CARD = "workout-up-next-card"
    const val REST_EDIT_UP_NEXT = "workout-rest-edit-up-next"
    const val WHAT_NEXT_SHEET = "workout-what-next-sheet"
    const val WHAT_NEXT_ADD_SET = "workout-what-next-add-set"
    const val WHAT_NEXT_SWAP = "workout-what-next-swap"
    const val WHAT_NEXT_SKIP = "workout-what-next-skip"
    const val WHAT_NEXT_EDIT = "workout-what-next-edit"
    const val LIFT_TRANSITION = "workout-lift-transition"
    const val BETWEEN_MINI_RING = "workout-between-mini-ring"
    const val BETWEEN_START = "workout-between-start"
    const val BETWEEN_OTHER = "workout-between-other"
    const val BETWEEN_SETS_CHIP = "workout-between-sets-chip"
    const val BETWEEN_VOLUME_CHIP = "workout-between-volume-chip"
    const val BETWEEN_PR_CHIP = "workout-between-pr-chip"
    const val FINISH_SHEET = "workout-finish-sheet"
    const val FINISH_KEEP = "workout-finish-keep"
    const val TEMPO_SHEET = "workout-tempo-sheet"
    const val TEMPO_SHEET_USE = "workout-tempo-sheet-use"
    const val TEMPO_COACH_SHORTCUT = "workout-tempo-coach-shortcut"
    const val TEMPO_SUGGESTION_LABEL = "workout-tempo-suggestion-label"
    const val TEMPO_COACH_DISMISS = "workout-tempo-coach-dismiss"
    const val TEMPO_COACH_DISMISS_MENU = "workout-tempo-coach-dismiss-menu"
    const val TEMPO_WHY_SHEET = "workout-tempo-why-sheet"
    const val TEMPO_WHY_TITLE = "workout-tempo-why-title"
    const val TEMPO_WHY_CLOSE = "workout-tempo-why-close"
    const val TEMPO_WHY_SUMMARY = "workout-tempo-why-summary"
    const val TEMPO_WHY_CALLOUT = "workout-tempo-why-callout"
    const val TEMPO_WHY_USE = "workout-tempo-why-use"
    const val TEMPO_WHY_KEEP = "workout-tempo-why-keep"
    fun tempoWhyEvidence(id: String) = "workout-tempo-why-evidence-$id"
    const val NEXT = "workout-next"
    const val DOCK_FINISH = "workout-dock-finish"
    const val ANOTHER_SET = "workout-another-set"
    const val RPE_TRACK = "workout-rpe-track"
    const val RPE_HELPER = "workout-rpe-helper"
    const val RPE_CLEAR = "workout-clear-rpe"
    const val RPE_WARMUP_REASON = "workout-rpe-warmup-reason"
    const val RPE_MEANING = "workout-rpe-meaning"
    const val RPE_LAST_HINT = "workout-rpe-last-hint"
    const val RPE_WAIT_ENTRY = "workout-rpe-wait-entry"
    const val SET_CONTEXT = "workout-set-context"
    const val SET_TYPE = "workout-set-type"
    const val WORKING_CHIP = "workout-working-choice"
    const val WARMUP_CHIP = "workout-warmup-chip"
    const val WARMUP_RAMP = "workout-warmup-ramp"
    const val START_SET_CLOCK = "workout-start-set-clock"
    const val STOP_SET_CLOCK = "workout-stop-set-clock"
    const val SET_HISTORY = "workout-set-history"
    const val SECTION_STATS = "workout-section-stats"
    const val SECTION_ENTRY = "workout-section-entry"
    const val SECTION_RPE = "workout-section-rpe"
    const val SECTION_NEXT_SET = "workout-section-next-set"
    const val SECTION_SET_HISTORY = "workout-section-set-history"
    const val CURRENT_SET = "workout-current-set"
    const val VIEW_SETS = "workout-view-sets"
    const val SAVED_SETS_SHEET = "workout-saved-sets-sheet"
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
    const val LIFT_SWAP = "workout-lift-swap"
    const val STATS_ROW = "workout-stats"
    const val STAT_LAST = "workout-stat-last"
    const val STAT_BEST = "workout-stat-best"
    const val STAT_VOLUME = "workout-stat-volume"
    fun liftCard(exerciseId: String) = "workout-lift-card-$exerciseId"
    fun liftRest(exerciseId: String) = "workout-lift-rest-$exerciseId"
    fun liftSwitcherRow(exerciseId: String) = "workout-lift-switcher-$exerciseId"
    fun setOptions(setId: String) = "workout-set-options-$setId"
    fun rpeChoice(value: Int) = "workout-rpe-$value"
}

/** v4 blueprint 5.2: "Rest done" stays on the bar this long before it collapses. */
private const val REST_DONE_HOLD_MS = 3_000L

@Composable
fun ActiveWorkoutScreen(
    onExit: () -> Unit,
    onFinished: (String) -> Unit,
    onOpenExercise: (String) -> Unit = {},
    viewModel: ActiveWorkoutViewModel = viewModel(),
    restNotificationsEnabledOverride: Boolean? = null,
) {
    // Use the space assigned by our parent, including embedded / constrained
    // windows. The host window can be portrait while this surface is landscape.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        ActiveWorkoutContent(
            onExit = onExit, onFinished = onFinished, onOpenExercise = onOpenExercise,
            viewModel = viewModel, restNotificationsEnabledOverride = restNotificationsEnabledOverride,
            landscape = LandscapeChrome.isLandscape(
                widthDp = maxWidth.value.roundToInt(), heightDp = maxHeight.value.roundToInt(),
            ),
            viewportHeightDp = maxHeight.value.roundToInt(),
        )
    }
}

@Composable
private fun ActiveWorkoutContent(
    onExit: () -> Unit,
    onFinished: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: ActiveWorkoutViewModel,
    restNotificationsEnabledOverride: Boolean?,
    landscape: Boolean,
    viewportHeightDp: Int,
) {
    val fontScale = LocalDensity.current.fontScale
    val compactTempoDock = landscape || viewportHeightDp <= 640 || fontScale >= 1.6f
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val restState = viewModel.restTimerState.collectAsStateWithLifecycle()
    val holdState = viewModel.holdTimer.collectAsStateWithLifecycle()
    val stopwatchState = viewModel.setStopwatch.collectAsStateWithLifecycle()
    val microRec by viewModel.microRec.collectAsStateWithLifecycle()
    val tempoCoachTip by viewModel.tempoCoachTip.collectAsStateWithLifecycle()
    val tempoCoachDismissed by viewModel.tempoCoachDismissed.collectAsStateWithLifecycle()
    val tempoCoachSheetOpen by viewModel.tempoCoachSheetOpen.collectAsStateWithLifecycle()
    val restFirstCaption by viewModel.restFirstCaptionVisible.collectAsStateWithLifecycle()
    val restImmersionLiftId by viewModel.restImmersionLiftId.collectAsStateWithLifecycle()
    val whatNextVisible by viewModel.whatNextVisible.collectAsStateWithLifecycle()
    val liftTransitionNextId by viewModel.liftTransitionNextId.collectAsStateWithLifecycle()
    val exerciseHistory by viewModel.exerciseHistory.collectAsStateWithLifecycle()
    val extraSetRequested by viewModel.extraSetRequested.collectAsStateWithLifecycle()
    val exitRequested by viewModel.exitRequested.collectAsStateWithLifecycle()
    val notesExitBlocked by viewModel.notesExitBlocked.collectAsStateWithLifecycle()
    val personalRecord by viewModel.personalRecord.collectAsStateWithLifecycle()
    val undoEntries by viewModel.undoEntries.collectAsStateWithLifecycle()
    val undoDwellMs by viewModel.undoDwellMs.collectAsStateWithLifecycle()
    val primaryState = viewModel.primaryAction.collectAsStateWithLifecycle()
    val privateRecovery by viewModel.privateRecovery.collectAsStateWithLifecycle()
    val logReceipt by viewModel.logReceipt.collectAsStateWithLifecycle()
    val pendingLiftSwitch by viewModel.pendingLiftSwitch.collectAsStateWithLifecycle()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmEntryDiscard by rememberSaveable { mutableStateOf(false) }
    var liftSwitcherOpen by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var sessionSummaryOpen by rememberSaveable { mutableStateOf(false) }
    var finishNotesOpen by rememberSaveable { mutableStateOf(false) }
    var setsOpen by rememberSaveable { mutableStateOf(false) }
    var setOptionsOpen by rememberSaveable { mutableStateOf(false) }
    var effortHelpOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.entryLocked) {
        if (state.entryLocked) {
            confirmEnd = false
            confirmDiscard = false
            liftSwitcherOpen = false
            setsOpen = false
            setOptionsOpen = false
            effortHelpOpen = false
            viewModel.setPickerVisible(false)
            viewModel.cancelPendingLiftSwitch()
        }
    }
    val restNotificationsEnabled = restNotificationsEnabledOverride
        ?: rememberRestNotificationsEnabled()
    val rest = restState.value
    val restImmersion = rest.running && state.selectedExerciseId == restImmersionLiftId
    // v4 V4: "Rest done" holds for 3 s on the lift that owned the rest, then the bar collapses.
    var restDoneExpiredId by remember { mutableStateOf<String?>(null) }
    // The ViewModel drops the owning lift when rest stops; the bar remembers it for "Rest done".
    var restBarLiftId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(restImmersionLiftId) { restImmersionLiftId?.let { restBarLiftId = it } }
    LaunchedEffect(rest.completedTimerId) {
        val id = rest.completedTimerId ?: return@LaunchedEffect
        kotlinx.coroutines.delay(REST_DONE_HOLD_MS)
        restDoneExpiredId = id
    }
    val restDoneVisible = !rest.running && rest.completedTimerId != null &&
        rest.completedTimerId != restDoneExpiredId && state.selectedExerciseId == restBarLiftId
    val restBarShown = restImmersion || restDoneVisible
    var restDurationRequest by remember { mutableIntStateOf(0) }
    val restExteriorActive = state.loadState == SessionLoadState.FOUND && (
        state.showRest || rest.running || (rest.completedTimerId != null && !rest.running)
        )
    RestExteriorPermissionGate(active = restExteriorActive)
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
            WorkoutExit.Kept -> onExit()
        }
    }

    // Back goes Home with the session still live. Finish is the explicit end
    // (save as is / leave without saving). Both flush the draft.
    fun keepAndExit() {
        viewModel.requestNotesExit()
    }
    BackHandler(enabled = state.session != null) { keepAndExit() }

    // Always composed, unlike the bottom bar. An error raised while no lift is selected —
    // a failed create from the picker in an empty free workout — previously had no reader at
    // all: it was written to state and rendered nowhere.
    val logBarVisible = privateRecovery.canEnter && state.loadState == SessionLoadState.FOUND && session != null &&
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
                // GET READY (P2b): a light tick each second, a firmer one when the hold begins.
                is FloorTimerCue.LeadInTick -> Haptics.tickLight(view)
                FloorTimerCue.LeadInDone -> Haptics.warn(view)
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
        state.editingSetId != null -> {
            val index = logged.indexOfFirst { it.id == state.editingSetId }
            val ordinal = SetOrdinalCopy.loggedLines(logged.map { it.isWarmup }, selected?.targetSets ?: 0).getOrNull(index)
            "Editing saved set" + (ordinal?.let { " · $it" } ?: "")
        }
        plannedComplete -> "Planned sets complete"
        else -> SetOrdinalCopy.draftLine(
            isWarmup = state.draft.isWarmup,
            warmupLogged = logged.count { it.isWarmup },
            workingLogged = workingLogged,
            targetSets = selected?.targetSets ?: 0,
        )
    }
    val holdLift = selected?.exercise?.let { HoldWork.isHold(it) } == true
    val floorEntryEnabled = !state.entryLocked
    val floorCoachRec = shownNextSet(
        rec = microRec,
        draftIsWarmup = state.draft.isWarmup,
        liftIsHold = holdLift,
    )
    val floorAddASet = tempoCoachTip as? TempoCoachTip.AddASet
    val floorNextTip = tempoCoachTip as? TempoCoachTip.NextSet
    val floorTempoTip = when {
        // A pending write disables the card in place. Removing an inline card here
        // shrinks the lazy list and can move the entry while its set is being saved.
        tempoCoachDismissed || (!floorEntryEnabled && !state.save.pending && !state.logging) ||
            state.draft.isWarmup || holdLift -> null
        floorAddASet != null -> floorAddASet
        floorCoachRec != null && tempoCoachTip != null -> floorNextTip
        else -> null
    }
    val floorTempoApplied = when (val tip = floorTempoTip) {
        is TempoCoachTip.NextSet ->
            tip.rec.isApplied(
                weightKg = state.draft.weightKg,
                reps = state.draft.reps,
                rpe = state.draft.rpe,
                unit = unit,
            )
        is TempoCoachTip.AddASet -> false
        null -> false
    }
    // Consume restored modal state only after the advice loads, as the card did
    // before hoisting; a transient null tip must not reset restored saveable inputs.
    val floorTempoCardState = floorTempoTip?.let { rememberTempoCoachCardState(it.tipShort) }

    CompositionLocalProvider(LocalWorkoutNumbers provides viewModel.numberEntry) {
    Scaffold(
        snackbarHost = {
            // Private recovery owns its message and actions, including while Check again loads.
            val errorBanner = state.error != null && !logBarVisible && privateRecovery.canEnter
            val undoTop = undoEntries.lastOrNull()?.offer.takeIf { !logBarVisible && privateRecovery.canEnter }
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
                compact = LandscapeChrome.compactHeader(landscape),
                focused = true,
                onExit = { keepAndExit() },
                overflow = {
                    when {
                        selected != null -> LiftOverflowMenu(
                            liftId = selected.id,
                            canEdit = logged.isEmpty(),
                            onSwap = viewModel::requestSwap,
                            onRemove = viewModel::removeSelectedLift,
                            onNotes = { notesOpen = true },
                            onSummary = { sessionSummaryOpen = true },
                            onSkip = viewModel::skipForNow,
                            onSwitch = { liftSwitcherOpen = true },
                            onDetails = { onOpenExercise(selected.exercise.id) },
                            enabled = !state.entryLocked,
                            onSetOptions = { setOptionsOpen = true },
                            showDiscardWorkout = state.showDiscard,
                            onDiscardWorkout = { confirmDiscard = true },
                            canFinish = state.canFinish,
                            onFinish = { confirmEnd = true },
                        )
                        // No lift selected: Finish still lives in the session overflow (D3).
                        state.showDiscard || state.canFinish -> SessionOverflowMenu(
                            showDiscardWorkout = state.showDiscard,
                            onDiscardWorkout = { confirmDiscard = true },
                            canFinish = state.canFinish,
                            onFinish = { confirmEnd = true },
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
            val showAnother = advance.showAnother && !state.entryLocked
            // The timer slot, advance choice, and Log set share one dock so a one-handed
            // thumb reaches every control. Finish is overflow or dock-primary, not header text.
            val emptySession = state.loadState == SessionLoadState.FOUND && session != null &&
                !session.hasLifts() && !state.save.pending &&
                FloorCompactChrome.emptySessionHidesTimerDock()
            val showRest = state.loadState == SessionLoadState.FOUND && state.showRest
            if (emptySession || showRest || logBarVisible) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(WorkoutTestTags.FLOOR_DOCK)
                        .then(
                            if (logBarVisible || emptySession) Modifier
                            else Modifier.navigationBarsPadding(),
                        ),
                ) {
                    if (emptySession) {
                        PinnedDock(
                            hairline = false,
                            verticalPadding = Metrics.space2,
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
                                effortMissingForCommit = state.effortMissingForCommit,
                                savePending = state.save.pending,
                                keepEntry = privateRecovery.phase == WorkoutPrivatePhase.PRESERVATION_FAILED && !state.save.pending,
                                keepingEntry = state.mutating || state.logging,
                                error = state.error,
                                suggestionUnavailable = state.suggestionUnavailable,
                                showAnother = showAnother && !whatNextVisible,
                                hideAdvancePrimary = whatNextVisible,
                                undoMessage = undoEntries.lastOrNull()?.offer?.message,
                                undoKey = undoEntries.lastOrNull()?.offer?.key,
                                undoDwellMs = undoDwellMs,
                                restImmersion = restBarShown,
                                restDurationRequest = restDurationRequest,
                                timer = WorkoutDockTimer(
                                    show = showRest,
                                    restRemainingSeconds = rest.remainingSeconds,
                                    restTotalSeconds = rest.totalSeconds,
                                    restSourceTag = rest.restSourceTag,
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
                                    holdGettingReady = holdTimer.gettingReady,
                                    holdLeadInRemainingSeconds = holdTimer.leadInRemainingSeconds,
                                    stopwatchRunning = setStopwatch.running,
                                    stopwatchElapsedSeconds = setStopwatch.elapsedSeconds,
                                    offerSetClock = offerSetClock,
                                    persistenceHealthy = rest.persistenceHealthy,
                                    notificationsEnabled = restNotificationsEnabled,
                                ),
                            ),
                            events = WorkoutDockEvents(
                                onKeepEntry = viewModel::keepPrivateEntry,
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
                                onCancelLeadIn = viewModel::cancelLeadIn,
                                onDismissRestBatteryHint = viewModel::acknowledgeRestBatteryHint,
                                onOpenNotifications = { openRestNotificationSettings(context) },
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            privateRecovery.phase == WorkoutPrivatePhase.READ_FAILED || privateRecovery.phase == WorkoutPrivatePhase.CONFLICT -> {
                Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(Metrics.gutter)) {
                EmptyState(
                    scene = EmptyScene.GONE,
                    title = if (privateRecovery.phase == WorkoutPrivatePhase.CONFLICT) "Entry needs review" else "Entry unavailable",
                    body = checkNotNull(privateRecovery.message),
                    actionLabel = "Check again",
                    onAction = viewModel::retrySession,
                    compact = true,
                )
                TextButton(
                    onClick = { confirmEntryDiscard = true },
                    modifier = Modifier.heightIn(min = Metrics.touchMin),
                ) { Text("Discard unfinished entry", style = InstrumentType.bodyStrong) }
                }
            }
            state.loadState == SessionLoadState.FAILED -> {
                EmptyState(
                    scene = EmptyScene.GONE,
                    title = "Workout unavailable",
                    body = "Your workout could not be read. Your draft is kept. Retry, or close this screen and return later.",
                    actionLabel = "Retry",
                    onAction = viewModel::retrySession,
                    compact = true,
                    modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(Metrics.gutter),
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
                    title = "Workout not live",
                    body = "This workout is not running. If you finished it, look in History.",
                    actionLabel = "Back to home",
                    onAction = onExit,
                    compact = true,
                    modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(Metrics.gutter),
                )
            }

            else -> {
                val transitionNext = liftTransitionNextId?.let { id ->
                    session.exercises.firstOrNull { it.exercise.id == id }
                }
                if (liftTransitionNextId != null && selected != null && transitionNext != null) {
                    BackHandler { viewModel.cancelLiftTransition() }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    if (liftTransitionNextId != null && selected != null && transitionNext != null) {
                        val liftSets = session.setsFor(selected.exercise.id)
                        val working = liftSets.count { !it.isWarmup }
                        val volumeKg = liftSets.filter { !it.isWarmup }.sumOf { it.weightKg * it.reps }
                        val prMoment = personalRecord?.takeIf { it.exerciseName == selected.exercise.name }
                        val prLabel = prMoment?.let {
                            SetCopy.setLine(it.weightKg, it.reps, loadClass, unit)
                        }
                        val nextIndex = session.exercises.indexOfFirst { it.exercise.id == transitionNext.exercise.id } + 1
                        val nextTarget = SetCopy.setLine(
                            transitionNext.targetWeightKg ?: 0.0,
                            transitionNext.targetReps,
                            LoadClass.of(transitionNext.exercise.loadType),
                            unit,
                        )
                        val currentIndex = session.exercises.indexOfFirst { it.exercise.id == selected.exercise.id } + 1
                        LiftTransitionPanel(
                            liftIndex = currentIndex,
                            liftCount = session.exercises.size,
                            completedLiftName = selected.exercise.name,
                            workingSets = working,
                            volumeKgLabel = com.sinura.personaltrainer.util.QuantityFormat.formatGroupedNumber(volumeKg),
                            prLabel = prLabel,
                            nextLiftIndex = nextIndex,
                            nextLiftName = transitionNext.exercise.name,
                            nextTarget = nextTarget,
                            nextTargetLoading = false,
                            nextFirstTimeCaption = state.hint == null && state.lastPerformance == null,
                            restRemainingSeconds = rest.remainingSeconds,
                            restTotalSeconds = rest.totalSeconds,
                            restRunning = rest.running,
                            restCompleted = rest.completedTimerId != null && !rest.running,
                            onStartNext = viewModel::completeLiftTransition,
                            onChooseOther = { liftSwitcherOpen = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else LazyColumn(
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
                        // Compact entry, effort and saved-work regions share a quiet rhythm.
                        // The dock stays separate; secondary context lives in Set options.
                        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
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
                                val effortEntryReady = SetLogRules.validate(
                                    weightKg = state.draft.weightKg,
                                    reps = state.draft.reps,
                                    isWarmup = state.draft.isWarmup,
                                    loadType = currentLift.exercise.loadType,
                                    durationSeconds = state.draft.durationSeconds ?: currentLift.targetSeconds,
                                    isHold = hold,
                                    equipment = currentLift.exercise.equipment,
                                    movementKey = currentLift.exercise.movementKey,
                                ) == null
                                item(key = "exercise-header") {
                                    val orderedIds = session.exercises.map { it.exercise.id }
                                    val liftId = currentLift.exercise.id
                                    val previousId = WorkoutAdvance.previousExerciseId(orderedIds, liftId)
                                    val nextId = WorkoutAdvance.nextExerciseId(orderedIds, liftId)
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
                                        focused = true,
                                        onSwipePrevious = { previousId?.let(viewModel::selectExercise) },
                                        onSwipeNext = { nextId?.let(viewModel::selectExercise) },
                                        canSwipePrevious = previousId != null,
                                        canSwipeNext = nextId != null,
                                        onSwap = viewModel::requestSwap,
                                        canSwap = logged.isEmpty(),
                                    )
                                }
                                if (restBarShown) {
                                    item(key = "rest-bar") {
                                        RestBar(
                                            remainingSeconds = rest.remainingSeconds,
                                            totalSeconds = rest.totalSeconds,
                                            running = rest.running,
                                            overtime = RestBarCopy.overtime(rest.running, rest.remainingSeconds),
                                            completed = !rest.running,
                                            onNudge = viewModel::nudgeRest,
                                            onSkip = viewModel::skipRest,
                                            onEditDuration = { restDurationRequest += 1 },
                                        )
                                    }
                                }
                                item(key = "entry") {
                                    val holdTimer = holdState.value
                                    val lastKg = state.hint?.lastWeightKg ?: state.lastPerformance?.topSet?.weightKg
                                    FloorSection(
                                        modifier = Modifier.testTag(WorkoutTestTags.SECTION_ENTRY),
                                        // The warm-up ramp's three presets need the width for
                                        // `40% · Suggested` to stay on one line at 360 dp.
                                        horizontalPadding = Metrics.space1,
                                        verticalArrangement = Arrangement.spacedBy(Metrics.space3),
                                    ) {
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
                                            focused = true,
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
                                floorTempoTip?.let { tip ->
                                    item(key = "tempo-line") {
                                        TempoLine(
                                            message = tip.tipShort,
                                            applied = floorTempoApplied,
                                            enabled = floorEntryEnabled,
                                            onWhy = { checkNotNull(floorTempoCardState).showWhy = true },
                                            onApply = { viewModel.applyTempoCoachTip(tip) },
                                            onUndo = { viewModel.undoTopOffer() },
                                            onDismiss = { viewModel.dismissTempoCoachTip(tip) },
                                            modifier = Modifier
                                                .padding(horizontal = Metrics.gutter)
                                                .padding(bottom = Metrics.space2)
                                                .testTag(WorkoutTestTags.TEMPO_LINE),
                                        )
                                    }
                                }
                                item(key = "rpe") {
                                    Column(modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.SECTION_RPE),
                                        verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                                        EffortChips(
                                            selected = state.effortChipSelected,
                                            prefilled = state.effortChipPrefilled,
                                            last = state.lastPerformance?.topSet?.rpe,
                                            recommended = microRec?.nextRpe,
                                            enabled = entryEnabled && effortEntryReady,
                                            hiddenForWarmup = state.draft.isWarmup || hold,
                                            warmupCaption = null,
                                            entryReady = effortEntryReady,
                                            showError = state.effortMissingForCommit && state.effortValue == null,
                                            onSelect = { value ->
                                                if (state.effortChipSelected == value) {
                                                    viewModel.setRpe(null)
                                                } else {
                                                    viewModel.setRpe(value)
                                                }
                                            },
                                        )
                                        WorkoutReadiness(
                                            action = primaryState.value,
                                            effortMissing = state.effortMissingForCommit,
                                            resumed = privateRecovery.resumed,
                                            keepEntry = privateRecovery.phase == WorkoutPrivatePhase.PRESERVATION_FAILED && !state.save.pending,
                                        )
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
                                        focused = true,
                                        modifier = Modifier.testTag(WorkoutTestTags.SECTION_SET_HISTORY),
                                        trailing = null,
                                    )
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

    floorTempoTip?.let { tip ->
        TempoCoachDialogs(
            tip = tip,
            loadClass = loadClass,
            unit = unit,
            applied = floorTempoApplied,
            onApply = { viewModel.applyTempoCoachTip(tip) },
            cardState = checkNotNull(floorTempoCardState),
        )
    }
    if (tempoCoachSheetOpen) {
        (floorNextTip ?: floorTempoTip)?.let { tip ->
            if (tip is TempoCoachTip.NextSet) {
                val currentTarget = SetCopy.setLine(
                    state.draft.weightKg,
                    state.draft.reps,
                    loadClass,
                    unit,
                )
                val suggestedTarget = SetMicroRecCopy.numbers(tip.rec, loadClass, unit)
                TempoSheet(
                    tip = tip,
                    currentTarget = currentTarget,
                    suggestedTarget = suggestedTarget,
                    applying = state.logging || state.save.pending,
                    showIntro = false,
                    onUse = { viewModel.applyTempoCoachTip(tip) },
                    onKeep = viewModel::closeTempoCoachSheet,
                    onDismiss = { viewModel.dismissTempoCoachTip(tip) },
                )
            }
        }
    }

    if (setOptionsOpen && selected != null && session != null && !state.entryLocked) {
        WorkoutSetOptionsSheet(lift = selected, onDismiss = { setOptionsOpen = false }) {
            SetTypeToggle(enabled = true, warmup = state.draft.isWarmup, onWarmup = {
                viewModel.setWarmup(it)
                setOptionsOpen = false
            })
            val fills = FloorWeightPresets.quickFills(
                currentKg = state.draft.weightKg,
                plannedKg = selected.targetWeightKg,
                lastKg = state.hint?.lastWeightKg ?: state.lastPerformance?.topSet?.weightKg,
            )
            if (fills.isNotEmpty()) {
                Text("Fill weight", style = InstrumentType.bodyStrong)
                fills.forEach { fill ->
                    InstrumentPreset(
                        label = fill.chipLabel(unit),
                        onClick = { viewModel.setWeight(fill.weightKg); setOptionsOpen = false },
                        modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.weightPreset(fill.source)),
                        enabled = true,
                    )
                }
                Text("Fills change weight only. Reps and effort stay under your control.", style = InstrumentType.caption)
            }
            QuietButton(text = "View all saved sets", enabled = logged.isNotEmpty(), maxLines = Int.MAX_VALUE,
                modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.SET_OPTIONS_SETS),
                onClick = { setOptionsOpen = false; setsOpen = true })
            QuietButton(text = RpeCopy.HELP_TITLE, onClick = { setOptionsOpen = false; effortHelpOpen = true },
                modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.RPE_HELPER), maxLines = Int.MAX_VALUE)
            if (!state.draft.isWarmup && state.draft.rpe != null) {
                QuietButton(text = RpeCopy.CLEAR, onClick = { viewModel.setRpe(null); setOptionsOpen = false },
                    modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.RPE_CLEAR), maxLines = Int.MAX_VALUE)
            }
            floorTempoTip?.let { tip ->
                QuietButton(text = "Tempo suggestion", maxLines = Int.MAX_VALUE,
                    modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.SET_OPTIONS_TEMPO), onClick = {
                    setOptionsOpen = false
                    checkNotNull(floorTempoCardState).showWhy = true
                })
                QuietButton(text = "Dismiss Tempo suggestion", onClick = {
                    viewModel.dismissTempoCoachTip(tip)
                    setOptionsOpen = false
                }, modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.TEMPO_COACH_DISMISS_MENU), maxLines = Int.MAX_VALUE)
            }
            val stats = remember(session, selected.exercise.id, state.lastPerformance, exerciseHistory, unit) {
                ExerciseFloorStatsCalculator.of(session = session, exerciseId = selected.exercise.id,
                    lastPerformance = state.lastPerformance, priorHistory = exerciseHistory, unit = unit)
            }
            FloorSection(modifier = Modifier.testTag(WorkoutTestTags.SECTION_STATS)) {
                ExerciseStatsRow(stats = stats, unit = unit, onApplyLastSet = { kg, reps ->
                    viewModel.applyLastTimeSet(kg, reps)
                    setOptionsOpen = false
                })
            }
            QuietButton(text = CurrentLiftCopy.DETAILS_SPOKEN, maxLines = Int.MAX_VALUE,
                modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.SET_OPTIONS_DETAILS), onClick = {
                setOptionsOpen = false
                onOpenExercise(selected.exercise.id)
            })
        }
    }
    if (effortHelpOpen) {
        GymDialog(title = RpeCopy.HELP_TITLE, body = RpeCopy.helpBody(), confirmLabel = "Done",
            onConfirm = { effortHelpOpen = false }, onDismiss = { effortHelpOpen = false }, dismissLabel = null)
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
                    saveState = state.notesSave,
                    onRetryNotes = viewModel::retryNotesSave,
                    enabled = !state.mutating && !state.finished,
                    modifier = Modifier.testTag(WorkoutTestTags.SESSION_NOTES).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { notesOpen = false }, modifier = Modifier.heightIn(min = Metrics.touchMin)) {
                    Text("Done", style = InstrumentType.bodyStrong)
                }
            },
        )
    }

    if (notesExitBlocked) {
        NotesLeaveDialog(
            saveState = state.notesSave,
            liveDraft = true,
            onRetry = { viewModel.requestNotesExit() },
            onKeepEditing = {
                viewModel.keepEditingNotes()
                notesOpen = true
            },
            onLeave = { viewModel.requestNotesExit(leaveWithDraft = true) },
        )
    }

    if (state.showExercisePicker) {
        RecoverableExercisePickerSheet(
            recovery = viewModel.pickerRecovery,
            canDismiss = viewModel::canDismissPicker,
            state = ExercisePickerState(
                query = state.searchQuery,
                progress = state.pickerProgress,
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
                    ExercisePickerEvent.ErrorDismissed -> viewModel.dismissPickerError()
                    ExercisePickerEvent.Retry -> viewModel.retryPicker()
                }
            },
        )
    }

    if (whatNextVisible && selected != null && session != null) {
        val nextLift = advance.nextLift
        val targetLine = nextLift?.let {
            SetCopy.setLine(it.targetWeightKg ?: 0.0, it.targetReps, LoadClass.of(it.exercise.loadType), unit)
        }
        val liftsLeft = session.exercises.count { !session.isTargetMet(it.exercise.id) }
        WhatNextSheet(
            addSetSubLabel = targetLine?.let { stringResource(R.string.next_add_set_sub, it) },
            swapSubLabel = state.swapSiblings.take(2).joinToString(", ") { it.name }.takeIf { it.isNotBlank() }?.let {
                stringResource(R.string.next_swap_sub, it)
            },
            skipSubLabel = stringResource(R.string.next_skip_sub, liftsLeft.coerceAtLeast(1)),
            primaryLabel = if (advance.showFinish) {
                stringResource(R.string.next_finish_cta)
            } else {
                stringResource(R.string.next_lift_cta, advance.nextName.orEmpty())
            },
            showFinish = advance.showFinish,
            rowsEnabled = !state.entryLocked,
            onAddSet = {
                viewModel.requestExtraSet()
                viewModel.dismissWhatNext()
            },
            onSwap = {
                viewModel.dismissWhatNext()
                viewModel.requestSwap()
            },
            onSkip = {
                viewModel.dismissWhatNext()
                liftSwitcherOpen = true
            },
            onEditSaved = {
                viewModel.dismissWhatNext()
                setsOpen = true
            },
            onPrimary = {
                viewModel.dismissWhatNext()
                if (advance.showFinish) {
                    confirmEnd = true
                } else {
                    advance.nextExerciseId?.let(viewModel::beginLiftTransition)
                }
            },
            onDismiss = viewModel::dismissWhatNext,
        )
    }

    if (confirmEnd) {
        val plannedWorking = session?.exercises?.sumOf { it.targetSets.coerceAtLeast(0) } ?: 0
        FinishSheet(
            loggedSets = session?.sets?.count { !it.isWarmup } ?: 0,
            plannedWorkingSets = plannedWorking,
            durationMinutes = viewModel.sessionDurationMinutes(),
            notesSave = state.notesSave,
            onFinish = {
                confirmEnd = false
                viewModel.finishWorkout()
            },
            onKeepTraining = { confirmEnd = false },
            onDiscard = {
                confirmEnd = false
                confirmDiscard = true
            },
            onDismiss = { confirmEnd = false },
            editOpen = state.editingSetId != null,
            loading = state.mutating,
            error = state.error?.takeIf { state.save.phase == WorkoutSavePhase.FAILED },
            onRetry = viewModel::retrySave,
            onRetryNotes = viewModel::retryNotesSave,
            savePending = state.save.pending,
        )
    }

    if (confirmEntryDiscard) ConfirmActionDialog(
        title = "Discard unfinished entry?",
        body = "This clears the unfinished fields and pending save for this workout. Already saved sets stay saved.",
        confirmLabel = "Discard entry", destructive = true,
        onConfirm = { confirmEntryDiscard = false; viewModel.discardConflictingEntry() },
        onDismiss = { confirmEntryDiscard = false },
    )
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
