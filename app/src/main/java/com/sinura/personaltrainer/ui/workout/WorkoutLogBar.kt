package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LogBarCopy
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.RestHonestyCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RpeCopy
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightUnit
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.FloorTimerSlot
import com.sinura.personaltrainer.ui.components.GymUndoHost
import com.sinura.personaltrainer.ui.components.InstrumentChoiceChip
import com.sinura.personaltrainer.ui.components.InstrumentSuggestion
import com.sinura.personaltrainer.ui.components.GymDialog
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.RestDurationSheet
import com.sinura.personaltrainer.ui.components.RestHonestyRow
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * One companion row and a 72 dp primary action. Errors and undo replace
 * timer detail while retaining an active clock control. Save receipts live
 * beside the latest saved set, outside this anchored action area.
 */
@Composable
internal fun LogBar(
    editing: Boolean,
    logging: Boolean,
    error: String?,
    draftLabel: String,
    warmup: Boolean,
    showNext: Boolean,
    onLog: () -> Unit,
    onNext: () -> Unit,
    onCancelEdit: () -> Unit,
    hold: Boolean = false,
    holdRunning: Boolean = false,
    showFinish: Boolean = false,
    showAnother: Boolean = false,
    nextName: String? = null,
    nextLift: SessionExercise? = null,
    onAnotherSet: (() -> Unit)? = null,
    onFinish: () -> Unit = {},
    showTimer: Boolean = false,
    restRemainingSeconds: Int = 0,
    restTotalSeconds: Int = 0,
    restRunning: Boolean = false,
    restCompletedTimerId: String? = null,
    hideIdleRest: Boolean = false,
    afterWarmup: Boolean = false,
    restBatteryHint: Boolean = false,
    holdElapsedSeconds: Int = 0,
    holdRemainingSeconds: Int = 0,
    holdTotalSeconds: Int = 0,
    onSkipRest: () -> Unit = {},
    onStartRest: () -> Unit = {},
    onSelectRestDuration: (Int) -> Unit = {},
    onDismissRestBatteryHint: () -> Unit = {},
    onOpenRest: () -> Unit = {},
    stopwatchRunning: Boolean = false,
    stopwatchElapsedSeconds: Int = 0,
    offerSetClock: Boolean = false,
    onStartSetClock: () -> Unit = {},
    onStopSetClock: () -> Unit = {},
    onNudgeRest: (Int) -> Unit = {},
    onCustomRest: (String) -> Boolean = { false },
    restPersistenceHealthy: Boolean = true,
    restExactBestEffort: Boolean = false,
    notificationsEnabled: Boolean = true,
    holdTargetReached: Boolean = false,
    onOpenNotifications: () -> Unit = {},
    canLog: Boolean = true,
    suggestionUnavailable: Boolean = false,
    onDismissError: () -> Unit = {},
    undoMessage: String? = null,
    undoKey: String? = null,
    undoDwellMs: Long = Motion.STATUS_DWELL_MS,
    onUndo: () -> Unit = {},
    onUndoDismissed: () -> Unit = {},
    primaryAction: WorkoutPrimaryAction? = null,
    primaryLabel: String? = null,
    onPrimary: ((WorkoutPrimaryAction) -> Boolean)? = null,
    savePending: Boolean = false,
    onEditFailedSave: () -> Unit = {},
) {
    var saveDetails by rememberSaveable { mutableStateOf(false) }
    var durationSheet by rememberSaveable { mutableStateOf(false) }
    var timingDetails by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showTimer, restRunning) {
        if (!showTimer || restRunning) durationSheet = false
    }
    val nextAct = showNext && !editing && !warmup
    val finishAct = showFinish && !editing && !warmup
    val holdActive = holdRunning || holdTargetReached
    val timedActive = restRunning || holdActive || stopwatchRunning
    val completeDock = (nextAct || finishAct) && !editing
    val honesty = if (showTimer) {
        RestHonestyCopy.pick(
            persistenceHealthy = restPersistenceHealthy,
            restRunning = restRunning,
            notificationsEnabled = notificationsEnabled,
            batteryHint = restBatteryHint,
            exactBestEffort = restExactBestEffort,
            onRestPage = false,
        )
    } else {
        null
    }
    val timerSurface: @Composable () -> Unit = {
        if (showTimer) {
            FloorTimerSlot(
                remainingSeconds = restRemainingSeconds,
                totalSeconds = restTotalSeconds,
                restRunning = restRunning,
                completedTimerId = restCompletedTimerId,
                hideWhenIdle = hideIdleRest,
                afterWarmup = afterWarmup,
                batteryHint = restBatteryHint,
                holdRunning = holdRunning,
                holdElapsedSeconds = holdElapsedSeconds,
                holdRemainingSeconds = holdRemainingSeconds,
                holdTotalSeconds = holdTotalSeconds,
                holdTargetReached = holdTargetReached,
                stopwatchRunning = stopwatchRunning,
                stopwatchElapsedSeconds = stopwatchElapsedSeconds,
                offerSetClock = offerSetClock,
                onStartSetClock = onStartSetClock,
                onStopSetClock = onStopSetClock,
                onSkip = onSkipRest,
                onStart = onStartRest,
                onNudgeRest = onNudgeRest,
                onDismissBatteryHint = onDismissRestBatteryHint,
                onOpenRest = onOpenRest,
                onEditRestDuration = { durationSheet = true },
                persistenceHealthy = restPersistenceHealthy,
                notificationsEnabled = notificationsEnabled,
                exactAlarmBestEffort = restExactBestEffort,
                onOpenNotifications = onOpenNotifications,
            )
        }
    }
    val contextVisible = error != null || !undoMessage.isNullOrBlank() || editing ||
        (!completeDock && (honesty != null || suggestionUnavailable))
    val clockLabel = when {
        holdActive -> "Hold ${RestTimer.formatClock(holdElapsedSeconds)}"
        stopwatchRunning -> "Set time ${RestTimer.formatClock(stopwatchElapsedSeconds)}"
        restRunning -> "Rest ${RestTimer.formatClock(restRemainingSeconds)}"
        else -> "Timers"
    }
    LaunchedEffect(timedActive) { if (!timedActive) timingDetails = false }
    PinnedDock(
        prelude = {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.logTimerRow)
                    .testTag(WorkoutTestTags.TIMER_ROW),
            ) {
                when {
                    contextVisible -> FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = if (LocalDensity.current.fontScale >= 1.6f) 1 else 2,
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            when {
                                error != null -> TextButton(
                                    onClick = { saveDetails = true },
                                    modifier = Modifier.heightIn(min = Metrics.touchMin).testTag("workout-error-details"),
                                ) { Text(if (savePending) "Save needs attention ›" else "Action needs attention ›", style = InstrumentType.bodyStrong) }
                                !undoMessage.isNullOrBlank() -> GymUndoHost(
                                    message = undoMessage,
                                    onUndo = onUndo,
                                    onDismissed = onUndoDismissed,
                                    offerKey = undoKey ?: undoMessage,
                                    dwellMs = undoDwellMs,
                                )
                                honesty != null -> RestHonestyRow(
                                    honesty = honesty,
                                    onDismissBatteryHint = onDismissRestBatteryHint,
                                    onOpenNotifications = onOpenNotifications,
                                )
                                editing -> TextButton(onClick = onCancelEdit, modifier = Modifier.heightIn(min = Metrics.touchMin)) {
                                    Text("Cancel edit", style = InstrumentType.bodyStrong, color = TextSecondary)
                                }
                                suggestionUnavailable -> Text(LogCommitCopy.SUGGESTION_UNAVAILABLE, style = InstrumentType.caption, color = TextTertiary)
                            }
                        }
                        if (showTimer) {
                            TextButton(
                                onClick = {
                                    when {
                                        holdActive || stopwatchRunning -> timingDetails = true
                                        restRunning -> onOpenRest()
                                        else -> durationSheet = true
                                    }
                                },
                                modifier = Modifier.heightIn(min = Metrics.touchMin).testTag("workout-companion-clock"),
                            ) { Text(clockLabel, style = InstrumentType.caption, color = TextPrimary) }
                        }
                    }
                    completeDock -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        TextButton(
                            onClick = onAnotherSet ?: {},
                            enabled = showAnother && onAnotherSet != null,
                            modifier = Modifier.weight(1f).heightIn(min = Metrics.touchMin).testTag(WorkoutTestTags.ANOTHER_SET),
                        ) { Text("Add another set", style = InstrumentType.bodyStrong, color = TextSecondary) }
                        if (showTimer) TextButton(
                            onClick = {
                                if (restRunning) onOpenRest() else durationSheet = true
                            },
                            modifier = Modifier.heightIn(min = Metrics.touchMin).testTag("workout-companion-clock"),
                        ) { Text(clockLabel, style = InstrumentType.caption, color = TextPrimary) }
                    }
                    showTimer && hideIdleRest && !timedActive -> TextButton(
                        onClick = { durationSheet = true },
                        modifier = Modifier.heightIn(min = Metrics.touchMin).testTag("workout-companion-clock"),
                    ) { Text("Timer controls ›", style = InstrumentType.bodyStrong, color = TextSecondary) }
                    else -> timerSurface()
                }
            }
        },
        volt = {
            key(primaryAction?.identity ?: Triple(editing, nextAct, finishAct)) {
                PrimaryGymButton(
                    text = primaryLabel ?: LogBarCopy.commit(
                        editing = editing, next = nextAct, finish = finishAct, nextName = nextName,
                        warmup = warmup, draftLabel = draftLabel, hold = hold,
                        holdRunning = holdRunning, logging = logging,
                    ),
                    onClick = {
                        if (primaryAction != null && onPrimary != null) {
                            val accepted = onPrimary(primaryAction)
                            if (accepted && primaryAction.kind == WorkoutPrimaryKind.REVIEW_SAVE) saveDetails = true
                        } else if (nextAct) onNext() else if (finishAct) onFinish() else onLog()
                    },
                    enabled = primaryAction?.enabled ?: (canLog || nextAct || finishAct),
                    disabledReason = LogCommitCopy.disabledReason(logging = logging, liftReady = canLog || logging),
                    modifier = Modifier.testTag(when {
                        nextAct -> WorkoutTestTags.NEXT
                        finishAct -> WorkoutTestTags.DOCK_FINISH
                        else -> WorkoutTestTags.LOG_SET
                    }),
                    height = Metrics.commit,
                    hapticFeedback = false,
                )
            }
        },
    )
    if (saveDetails && error != null) {
        GymDialog(
            title = if (savePending) "Save needs attention" else "Action needs attention",
            body = error,
            confirmLabel = if (savePending) "Return to entry" else "Dismiss",
            onConfirm = {
                saveDetails = false
                if (savePending) onEditFailedSave() else onDismissError()
            },
            onDismiss = { saveDetails = false },
            dismissLabel = "Close",
        )
    }
    if (timingDetails) {
        GymDialog(
            title = if (holdActive) "Hold" else "Set time",
            body = clockLabel,
            confirmLabel = if (stopwatchRunning) "Stop timing" else "Return to workout",
            onConfirm = { if (stopwatchRunning) onStopSetClock(); timingDetails = false },
            onDismiss = { timingDetails = false },
            dismissLabel = if (stopwatchRunning) "Keep timing" else null,
        )
    }
    if (durationSheet) {
        RestDurationSheet(
            selectedSeconds = restTotalSeconds,
            onSelect = { seconds ->
                onSelectRestDuration(seconds)
                durationSheet = false
            },
            onNudge = onNudgeRest,
            onCustomRest = onCustomRest,
            onDismiss = { durationSheet = false },
            onStartRest = { durationSheet = false; onStartRest() },
            offerSetClock = offerSetClock,
            onTimeSet = {
                durationSheet = false
                onStartSetClock()
            },
        )
    }
}

@Composable
internal fun MicroRecLine(
    rec: SetMicroRec,
    loadClass: LoadClass,
    unit: WeightUnit,
    onApply: () -> Unit,
) {
    if (!SetMicroRecCopy.visibleOnEntry(rec)) return
    val view = LocalView.current
    var showWhy by rememberSaveable(rec.reasonCode, rec.nextWeightKg, rec.nextReps, rec.nextRpe) {
        mutableStateOf(false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        Text("Suggested", style = InstrumentType.caption, color = TextTertiary)
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
                SetMicroRecCopy.collapsed(rec, loadClass, unit),
                modifier = Modifier
                    .weight(1f)
                    .testTag(WorkoutTestTags.MICRO_REC),
                style = InstrumentType.bodyStrong,
                color = TextPrimary,
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
                    onClick = {
                        Haptics.tick(view)
                        onApply()
                    },
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
        val canUse = rec.showApply && !rec.previewOnly
        ConfirmActionDialog(
            title = "Why",
            body = SetMicroRecCopy.whyLines(rec).joinToString("\n"),
            confirmLabel = if (canUse) SetMicroRecCopy.USE_SUGGESTION else SetMicroRecCopy.KEEP_MY_NUMBERS,
            dismissLabel = if (canUse) SetMicroRecCopy.KEEP_MY_NUMBERS else null,
            onConfirm = {
                if (canUse) {
                    Haptics.tick(view)
                    onApply()
                }
                showWhy = false
            },
            onDismiss = { showWhy = false },
        )
    }
}

/**
 * Optional working-set effort, with persistent help and explicit clearing.
 * Choices reflow at larger text sizes; a suggestion never looks selected.
 */
@Composable
internal fun SecondaryLogOptions(
    enabled: Boolean = true,
    warmup: Boolean,
    rpe: Int?,
    onRpe: (Int?) -> Unit,
    recommendedRpe: Int? = null,
    showRpe: Boolean = true,
) {
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text("Effort · Optional", modifier = Modifier.weight(1f), style = InstrumentType.caption, color = TextSecondary)
            if (showRpe && rpe != null) {
                TextButton(enabled = enabled, onClick = { onRpe(null) }, modifier = Modifier.heightIn(min = Metrics.touchMin).testTag("workout-clear-rpe")) {
                    Text("Clear", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            }
            TextButton(onClick = { helpOpen = true }, modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(WorkoutTestTags.RPE_HELPER)) {
                Text("RPE help", style = InstrumentType.bodyStrong, color = TextPrimary)
            }
        }
        if (warmup && !showRpe) {
            Text(
                "Effort is recorded for working sets. Warm-ups leave RPE blank.",
                modifier = Modifier.testTag(WorkoutTestTags.RPE_WARMUP_REASON),
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
        if (showRpe) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val optionWidth = measurer.measure("10", style = InstrumentType.bodyStrong).size.width +
                    with(density) { (Metrics.chevron + Metrics.space2 * 3).roundToPx() }
                val gaps = with(density) { (Metrics.space1 * 4).roundToPx() }
                val columns = if (optionWidth * 5 + gaps <= with(density) { maxWidth.roundToPx() }) 5 else 3
                FlowRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup().testTag(WorkoutTestTags.RPE_TRACK),
                    maxItemsInEachRow = columns,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                ) {
                    RpeCopy.VALUES.forEach { value ->
                        InstrumentChoiceChip(
                            enabled = enabled,
                            label = value.toString(),
                            selected = rpe == value,
                            onClick = { onRpe(if (rpe == value) null else value) },
                            compact = true,
                            modifier = Modifier.weight(1f).semantics {
                                contentDescription = "RPE $value, ${RpeCopy.meaning(value)}"
                            },
                        )
                    }
                }
            }
            if (recommendedRpe != null) InstrumentSuggestion(text = "RPE $recommendedRpe")
        }
    }
    if (helpOpen) {
        GymDialog(
            title = "Effort (RPE)",
            body = "Optional. Choose how hard your working set felt. Tap the selected value again or Clear to remove it.\n\n" +
                RpeCopy.VALUES.joinToString("\n") { "$it · ${RpeCopy.meaning(it)}" },
            confirmLabel = "Done",
            onConfirm = { helpOpen = false },
            onDismiss = { helpOpen = false },
            dismissLabel = null,
        )
    }
}
