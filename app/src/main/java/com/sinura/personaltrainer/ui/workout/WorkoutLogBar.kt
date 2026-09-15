package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LogBarCopy
import com.sinura.personaltrainer.domain.LogCommitCopy
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
import com.sinura.personaltrainer.ui.components.GymReceiptBanner
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import androidx.compose.ui.platform.LocalView

/**
 * Every gym-floor control in one dock (Packet 2).
 *
 * Owns the timer slot (rest countdown or set count-up), the Packet 1
 * advance choice (Next lift / Another set), and the single Volt
 * [LogBarCopy.commit]. The header strip is read-only instruments.
 *
 * Scaffold's bottomBar draws edge-to-edge. The tab bar is gone on this
 * route, so this dock owns the system-nav inset the same way the tab
 * bar and live bar already do — otherwise Log sits under the three-
 * button nav / gesture pill.
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
) {
    val nextAct = showNext && !editing
    val finishAct = showFinish && !editing
    val timedActive = restRunning || holdRunning || stopwatchRunning
    val completeDock = (nextAct || finishAct) && !editing && !timedActive
    val logEnabled = if (nextAct || finishAct) !logging else canLog
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showTimer && !completeDock) {
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
                holdTargetReached = holdTargetReached,
                stopwatchRunning = stopwatchRunning,
                stopwatchElapsedSeconds = stopwatchElapsedSeconds,
                offerSetClock = offerSetClock,
                onStartSetClock = onStartSetClock,
                onStopSetClock = onStopSetClock,
                onSkip = onSkipRest,
                onStart = onStartRest,
                onSelectRestDuration = onSelectRestDuration,
                onNudgeRest = onNudgeRest,
                onCustomRest = onCustomRest,
                onDismissBatteryHint = onDismissRestBatteryHint,
                onOpenRest = onOpenRest,
                persistenceHealthy = restPersistenceHealthy,
                notificationsEnabled = notificationsEnabled,
                exactAlarmBestEffort = restExactBestEffort,
                onOpenNotifications = onOpenNotifications,
            )
        }
        PinnedDock(
            prelude = {
                error?.let {
                    Text(it, style = InstrumentType.body, color = Danger)
                }
                if (suggestionUnavailable && error == null) {
                    Text(
                        LogCommitCopy.SUGGESTION_UNAVAILABLE,
                        style = InstrumentType.caption,
                        color = TextTertiary,
                    )
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
                receiptLine?.let { line ->
                    GymReceiptBanner(
                        message = line,
                        onDismissed = onReceiptDismissed,
                        modifier = Modifier.testTag(WorkoutTestTags.LOG_RECEIPT),
                    )
                }
                if (completeDock) {
                    NextLiftPreview(
                        lift = nextLift,
                        nextName = nextName,
                    )
                }
            },
            volt = {
                PrimaryGymButton(
                    text = LogBarCopy.commit(
                        editing = editing,
                        next = nextAct,
                        finish = finishAct,
                        nextName = nextName,
                        warmup = warmup,
                        draftLabel = draftLabel,
                        hold = hold,
                        holdRunning = holdRunning,
                        logging = logging && !nextAct && !finishAct,
                    ),
                    onClick = when {
                        finishAct -> onFinish
                        nextAct -> onNext
                        else -> onLog
                    },
                    enabled = logEnabled,
                    disabledReason = LogCommitCopy.disabledReason(
                        logging = logging && !nextAct && !finishAct,
                        liftReady = nextAct || finishAct || canLog || logging,
                    ),
                    modifier = Modifier.testTag(
                        when {
                            finishAct -> WorkoutTestTags.DOCK_FINISH
                            nextAct -> WorkoutTestTags.NEXT
                            else -> WorkoutTestTags.LOG_SET
                        },
                    ),
                    height = Metrics.commit,
                    hapticFeedback = true,
                )
            },
            secondary = if (showAnother && !editing && onAnotherSet != null) {
                {
                    TextButton(
                        onClick = onAnotherSet,
                        modifier = Modifier
                            .fillMaxWidth()
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
            } else {
                null
            },
        )
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
            .heightIn(min = Metrics.commit)
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
                style = InstrumentType.title,
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
            Kicker(
                text = RpeCopy.LABEL,
                asHeading = false,
            )
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
                        spoken = RpeCopy.spoken(value, selected),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
