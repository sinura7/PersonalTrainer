package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LogBarCopy
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.RestHonestyCopy
import com.sinura.personaltrainer.domain.RpeCopy
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutAdvance
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.FloorFieldGlyph
import com.sinura.personaltrainer.ui.components.FloorTimerSlot
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymReceiptBanner
import com.sinura.personaltrainer.ui.components.GymUndoHost
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.RestDurationSheet
import com.sinura.personaltrainer.ui.components.RestHonestyRow
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * Gym-floor dock: one reserved timer row, one context rail, one 72 dp
 * filled Volt Log. REST / HOLD / SET share the timer row. Receipt,
 * error, undo, and Next / Finish / Another replace each other in the
 * rail so Log never moves.
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
    receiptLine: String? = null,
    onReceiptDismissed: () -> Unit = {},
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
) {
    var durationSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showTimer, restRunning, hideIdleRest) {
        if (!showTimer || restRunning || hideIdleRest) durationSheet = false
    }
    val nextAct = showNext && !editing
    val finishAct = showFinish && !editing
    val timedActive = restRunning || holdRunning || stopwatchRunning
    val completeDock = (nextAct || finishAct) && !editing && !timedActive
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.logTimerRow)
                .testTag(WorkoutTestTags.TIMER_ROW),
        ) {
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
        PinnedDock(
            prelude = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Metrics.logContextRail)
                        .testTag(WorkoutTestTags.CONTEXT_RAIL),
                ) {
                    when {
                        error != null -> GymErrorBanner(
                            message = error,
                            onRetry = onLog,
                            onDismiss = onDismissError,
                        )
                        !undoMessage.isNullOrBlank() -> GymUndoHost(
                            message = undoMessage,
                            onUndo = onUndo,
                            onDismissed = onUndoDismissed,
                            offerKey = undoKey ?: undoMessage,
                            dwellMs = undoDwellMs,
                        )
                        receiptLine != null -> GymReceiptBanner(
                            message = receiptLine,
                            onDismissed = onReceiptDismissed,
                            modifier = Modifier.testTag(WorkoutTestTags.LOG_RECEIPT),
                        )
                        completeDock -> CompletionRail(
                            nextAct = nextAct,
                            finishAct = finishAct,
                            showAnother = showAnother && onAnotherSet != null,
                            nextName = nextName,
                            nextLift = nextLift,
                            onNext = onNext,
                            onFinish = onFinish,
                            onAnotherSet = onAnotherSet ?: {},
                        )
                        honesty != null -> RestHonestyRow(
                            honesty = honesty,
                            onDismissBatteryHint = onDismissRestBatteryHint,
                            onOpenNotifications = onOpenNotifications,
                        )
                        editing -> TextButton(
                            onClick = onCancelEdit,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .heightIn(min = Metrics.touchMin),
                        ) {
                            Text("Cancel edit", style = InstrumentType.bodyStrong, color = TextSecondary)
                        }
                        suggestionUnavailable -> Text(
                            LogCommitCopy.SUGGESTION_UNAVAILABLE,
                            style = InstrumentType.caption,
                            color = TextTertiary,
                        )
                    }
                }
            },
            volt = {
                PrimaryGymButton(
                    text = LogBarCopy.commit(
                        editing = editing,
                        next = false,
                        finish = false,
                        nextName = nextName,
                        warmup = warmup,
                        draftLabel = draftLabel,
                        hold = hold,
                        holdRunning = holdRunning,
                        logging = logging,
                    ),
                    onClick = onLog,
                    enabled = canLog,
                    disabledReason = LogCommitCopy.disabledReason(
                        logging = logging,
                        liftReady = canLog || logging,
                    ),
                    modifier = Modifier.testTag(WorkoutTestTags.LOG_SET),
                    height = Metrics.commit,
                    hapticFeedback = true,
                )
            },
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
            offerSetClock = offerSetClock,
            onTimeSet = {
                durationSheet = false
                onStartSetClock()
            },
        )
    }
}

@Composable
private fun CompletionRail(
    nextAct: Boolean,
    finishAct: Boolean,
    showAnother: Boolean,
    nextName: String?,
    nextLift: SessionExercise?,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onAnotherSet: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        NextLiftPreview(lift = nextLift, nextName = nextName)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (nextAct) {
                TextButton(
                    onClick = onNext,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutTestTags.NEXT),
                ) {
                    Text(
                        LogBarCopy.nextLift(nextName),
                        style = InstrumentType.bodyStrong,
                        color = Volt,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (finishAct) {
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutTestTags.DOCK_FINISH),
                ) {
                    Text(
                        LogBarCopy.FINISH_WORKOUT,
                        style = InstrumentType.bodyStrong,
                        color = Volt,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showAnother) {
                TextButton(
                    onClick = onAnotherSet,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutTestTags.ANOTHER_SET),
                ) {
                    Text(
                        LogBarCopy.ANOTHER_SET,
                        style = InstrumentType.bodyStrong,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NextLiftPreview(
    lift: SessionExercise?,
    nextName: String?,
) {
    if (lift == null && nextName.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.touchMin)
            .testTag(WorkoutTestTags.NEXT_PREVIEW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        lift?.let {
            ExerciseThumb(exercise = it.exercise, size = ThumbSize.row)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                nextName?.takeIf { it.isNotBlank() } ?: lift?.exercise?.name.orEmpty(),
                style = InstrumentType.bodyStrong,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            lift?.let { sessionLift ->
                val planned = WorkoutAdvance.plannedWork(sessionLift.targetSets, sessionLift.targetReps)
                if (planned.isNotBlank()) {
                    Text(
                        planned,
                        style = InstrumentType.caption,
                        color = TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
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
                maxLines = 2,
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
 * Packet D: RPE for a working-set draft. Warm-up lives above the weight
 * well, outside this track. Five equal chips, one non-scrolling row.
 */
@Composable
internal fun SecondaryLogOptions(
    warmup: Boolean,
    rpe: Int?,
    onRpe: (Int?) -> Unit,
    recommendedRpe: Int? = null,
    showRpe: Boolean = true,
    showHelper: Boolean = false,
    onDismissHelper: () -> Unit = {},
) {
    val fontScale = LocalDensity.current.fontScale
    val largeType = fontScale >= 2f
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            FloorFieldGlyph(
                icon = TemperIcons.FloorRpe,
                spoken = null,
                modifier = Modifier.testTag(WorkoutTestTags.RPE_GLYPH),
            )
            if (largeType) {
                Text(
                    RpeCopy.LABEL,
                    modifier = Modifier.weight(1f),
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Kicker(
                    text = RpeCopy.LABEL,
                    asHeading = false,
                )
            }
        }
        if (warmup && !showRpe) {
            Text(
                RpeCopy.WARMUP_REASON,
                modifier = Modifier
                    .testTag(WorkoutTestTags.RPE_WARMUP_REASON)
                    .semantics { contentDescription = RpeCopy.WARMUP_REASON },
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
        if (showRpe) {
            if (showHelper) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutTestTags.RPE_HELPER)
                        .clickable(role = Role.Button, onClick = onDismissHelper)
                        .semantics { contentDescription = RpeCopy.helperSpoken() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    Text(
                        RpeCopy.HELPER,
                        modifier = Modifier.weight(1f),
                        style = InstrumentType.caption,
                        color = TextSecondary,
                    )
                    Text(
                        RpeCopy.HELPER_DISMISS,
                        style = InstrumentType.bodyStrong,
                        color = Volt,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Metrics.touchMin)
                    .selectableGroup()
                    .testTag(WorkoutTestTags.RPE_TRACK),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RpeCopy.VALUES.forEach { value ->
                    val selected = rpe == value
                    InstrumentChip(
                        label = value.toString(),
                        selected = selected,
                        recommended = recommendedRpe == value && !selected,
                        onClick = { onRpe(if (selected) null else value) },
                        compact = true,
                        role = Role.RadioButton,
                        spoken = RpeCopy.spoken(
                            value,
                            selected,
                            recommended = recommendedRpe == value,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
