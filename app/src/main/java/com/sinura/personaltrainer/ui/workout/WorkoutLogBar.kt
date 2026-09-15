package com.sinura.personaltrainer.ui.workout


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LogBarCopy
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.FloorTimerSlot
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

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
    microRec: SetMicroRec?,
    loadClass: LoadClass,
    unit: WeightUnit,
    showNext: Boolean,
    onLog: () -> Unit,
    onNext: () -> Unit,
    onCancelEdit: () -> Unit,
    onApplyMicroRec: () -> Unit,
    hold: Boolean = false,
    holdRunning: Boolean = false,
    advanceChoice: Boolean = false,
    onAnotherSet: (() -> Unit)? = null,
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
    onStartNextLift: () -> Unit = {},
    onOpenRest: () -> Unit = {},
    stopwatchRunning: Boolean = false,
    stopwatchElapsedSeconds: Int = 0,
    offerSetClock: Boolean = false,
    onStartSetClock: () -> Unit = {},
    onStopSetClock: () -> Unit = {},
) {
    val nextAct = (showNext || advanceChoice) && !editing
    Column(modifier = Modifier.fillMaxWidth()) {
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
                stopwatchRunning = stopwatchRunning,
                stopwatchElapsedSeconds = stopwatchElapsedSeconds,
                offerSetClock = offerSetClock,
                onStartSetClock = onStartSetClock,
                onStopSetClock = onStopSetClock,
                onSkip = onSkipRest,
                onStart = onStartRest,
                onSelectRestDuration = onSelectRestDuration,
                onDismissBatteryHint = onDismissRestBatteryHint,
                onStartNext = onStartNextLift,
                onOpenRest = onOpenRest,
            )
        }
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
                PrimaryGymButton(
                    text = LogBarCopy.commit(
                        editing = editing,
                        next = nextAct,
                        warmup = warmup,
                        draftLabel = draftLabel,
                        hold = hold,
                        holdRunning = holdRunning,
                    ),
                    onClick = if (nextAct) onNext else onLog,
                    enabled = !logging,
                    modifier = Modifier.testTag(
                        if (nextAct) WorkoutTestTags.NEXT else WorkoutTestTags.LOG_SET,
                    ),
                    height = Metrics.commit,
                    hapticFeedback = nextAct || editing,
                )
            },
            secondary = if (advanceChoice && !editing && onAnotherSet != null) {
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
internal fun MicroRecLine(
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
        SetMicroRecCopy.warmupLine(rec, unit)?.let { warmup ->
            Text(
                warmup,
                style = InstrumentType.caption,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

/**
 * Warm-up is its own chip above the RPE track. RPE 6–10 are equal-weight
 * compact chips in one non-scrolling row so all five stay visible at
 * 360 dp / font scale 2.0 (Warm-up used to share that row and clipped
 * to "Varm-up" while hiding 10).
 */
@Composable
internal fun SecondaryLogOptions(
    warmup: Boolean,
    rpe: Int?,
    onWarmup: (Boolean) -> Unit,
    onRpe: (Int?) -> Unit,
    recommendedRpe: Int? = null,
    showRpe: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        InstrumentChip(
            label = "Warm-up",
            selected = warmup,
            onClick = { onWarmup(!warmup) },
        )
        if (showRpe) {
            Kicker("RPE")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WorkoutTestTags.RPE_TRACK),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (6..10).forEach { value ->
                    InstrumentChip(
                        label = value.toString(),
                        selected = rpe == value,
                        recommended = recommendedRpe == value && rpe != value,
                        onClick = { onRpe(if (rpe == value) null else value) },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
