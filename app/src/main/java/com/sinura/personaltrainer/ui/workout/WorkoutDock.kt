package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.domain.FloorTimedMode
import com.sinura.personaltrainer.domain.FloorTimerSurface
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.RestHonestyCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.components.GymDialog
import com.sinura.personaltrainer.ui.components.GymUndoHost
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.RestDurationSheet
import com.sinura.personaltrainer.ui.components.RestHonestyRow
import com.sinura.personaltrainer.ui.components.SetWorkDock
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/** The clocks the dock can show. One of them at a time; [FloorTimerSurface.mode] decides. */
internal data class WorkoutDockTimer(
    val show: Boolean,
    val restRemainingSeconds: Int = 0,
    val restTotalSeconds: Int = 0,
    val restRunning: Boolean = false,
    val restCompletedTimerId: String? = null,
    val hideIdleRest: Boolean = false,
    val afterWarmup: Boolean = false,
    val batteryHint: Boolean = false,
    val holdRunning: Boolean = false,
    val holdElapsedSeconds: Int = 0,
    val holdRemainingSeconds: Int = 0,
    val holdTotalSeconds: Int = 0,
    val holdTargetReached: Boolean = false,
    val stopwatchRunning: Boolean = false,
    val stopwatchElapsedSeconds: Int = 0,
    val offerSetClock: Boolean = false,
    val persistenceHealthy: Boolean = true,
    val exactBestEffort: Boolean = false,
    val notificationsEnabled: Boolean = true,
)

internal data class WorkoutDockState(
    val primaryAction: WorkoutPrimaryAction,
    /** First line of the commit: `Log set`, `Next exercise · Leg curl`. */
    val verb: String,
    /** Second line: what will be written. Null when the action carries no set. */
    val payload: String?,
    val editing: Boolean,
    val logging: Boolean,
    val canLog: Boolean,
    val savePending: Boolean,
    val error: String?,
    val suggestionUnavailable: Boolean,
    val showAnother: Boolean,
    val undoMessage: String?,
    val undoKey: String?,
    val undoDwellMs: Long,
    val timer: WorkoutDockTimer,
)

internal data class WorkoutDockEvents(
    val onPrimary: (WorkoutPrimaryAction) -> Boolean,
    val onEditFailedSave: () -> Unit,
    val onCancelEdit: () -> Unit,
    val onDismissError: () -> Unit,
    val onAnotherSet: () -> Unit,
    val onUndo: () -> Unit,
    val onUndoDismissed: () -> Unit,
    val onSkipRest: () -> Unit,
    val onStartRest: () -> Unit,
    val onSelectRestDuration: (Int) -> Unit,
    val onNudgeRest: (Int) -> Unit,
    val onCustomRest: (String) -> Boolean,
    val onStartSetClock: () -> Unit,
    val onStopSetClock: () -> Unit,
    val onDismissRestBatteryHint: () -> Unit,
    val onOpenRest: () -> Unit,
    val onOpenNotifications: () -> Unit,
)

/**
 * The anchored bottom of the floor: one companion slot and the 72 dp commit.
 *
 * The companion is the rest card while resting, the hold or set clock while one runs,
 * and otherwise whichever of error, undo, timer honesty or Cancel edit needs the room —
 * with a compact clock kept reachable beside it. Its minimum height is the commit's, so
 * the Volt's bottom edge does not move as the slot changes hands. Save receipts live
 * in the set history, not here.
 */
@Composable
internal fun WorkoutDock(
    state: WorkoutDockState,
    events: WorkoutDockEvents,
) {
    var saveDetails by rememberSaveable { mutableStateOf(false) }
    var durationSheet by rememberSaveable { mutableStateOf(false) }
    var timingDetails by rememberSaveable { mutableStateOf(false) }
    val timer = state.timer
    val action = state.primaryAction
    LaunchedEffect(timer.show, timer.restRunning) {
        if (!timer.show || timer.restRunning) durationSheet = false
    }
    val nextAct = action.kind == WorkoutPrimaryKind.NEXT_EXERCISE && !state.editing
    val finishAct = action.kind == WorkoutPrimaryKind.FINISH && !state.editing
    val holdActive = timer.holdRunning || timer.holdTargetReached
    val timedActive = timer.restRunning || holdActive || timer.stopwatchRunning
    val completeDock = nextAct || finishAct
    val honesty = if (timer.show) {
        RestHonestyCopy.pick(
            persistenceHealthy = timer.persistenceHealthy,
            restRunning = timer.restRunning,
            notificationsEnabled = timer.notificationsEnabled,
            batteryHint = timer.batteryHint,
            exactBestEffort = timer.exactBestEffort,
            onRestPage = false,
        )
    } else {
        null
    }
    val contextVisible = state.error != null || !state.undoMessage.isNullOrBlank() || state.editing ||
        (!completeDock && (honesty != null || state.suggestionUnavailable))
    val clockLabel = when {
        holdActive -> "Hold ${RestTimer.formatClock(timer.holdElapsedSeconds)}"
        timer.stopwatchRunning -> "Set time ${RestTimer.formatClock(timer.stopwatchElapsedSeconds)}"
        timer.restRunning -> "Rest ${RestTimer.formatClock(timer.restRemainingSeconds)}"
        else -> "Timers"
    }
    LaunchedEffect(timedActive) { if (!timedActive) timingDetails = false }
    val mode = FloorTimerSurface.mode(
        holdRunning = timer.holdRunning,
        stopwatchRunning = timer.stopwatchRunning,
        hasLifts = timer.show,
        restRunning = timer.restRunning,
        restComplete = !timer.restCompletedTimerId.isNullOrBlank() && !timer.restRunning,
        holdActive = holdActive,
    )
    val timerSurface: @Composable () -> Unit = {
        when (mode) {
            FloorTimedMode.NONE -> Unit
            FloorTimedMode.HOLD_RUNNING,
            FloorTimedMode.STOPWATCH_RUNNING,
            -> SetWorkDock(
                elapsedSeconds = if (holdActive) timer.holdElapsedSeconds else timer.stopwatchElapsedSeconds,
                remainingSeconds = if (holdActive) timer.holdRemainingSeconds else 0,
                totalSeconds = if (holdActive) timer.holdTotalSeconds else 0,
                hold = holdActive,
                targetReached = timer.holdTargetReached,
                running = timer.holdRunning || timer.stopwatchRunning,
                onStop = events.onStopSetClock.takeIf { timer.stopwatchRunning && !holdActive },
                modifier = Modifier.fillMaxWidth(),
            )
            FloorTimedMode.REST_IDLE,
            FloorTimedMode.REST_RUNNING,
            FloorTimedMode.REST_COMPLETE,
            -> RestTimerCard(
                remainingSeconds = timer.restRemainingSeconds,
                totalSeconds = timer.restTotalSeconds,
                running = timer.restRunning,
                completedTimerId = timer.restCompletedTimerId,
                afterWarmup = timer.afterWarmup,
                offerSetClock = timer.offerSetClock,
                onSkip = events.onSkipRest,
                onStart = events.onStartRest,
                onNudge = events.onNudgeRest,
                onEditDuration = { durationSheet = true },
                onStartSetClock = events.onStartSetClock,
                onOpenRest = events.onOpenRest,
            )
        }
    }
    val clockButton: @Composable () -> Unit = {
        TextButton(
            onClick = {
                when {
                    holdActive || timer.stopwatchRunning -> timingDetails = true
                    timer.restRunning -> events.onOpenRest()
                    else -> durationSheet = true
                }
            },
            modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(WorkoutTestTags.COMPANION_CLOCK),
        ) { Text(clockLabel, style = InstrumentType.caption, color = TextPrimary) }
    }
    PinnedDock(
        prelude = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Metrics.logTimerRow)
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
                                state.error != null -> TextButton(
                                    onClick = { saveDetails = true },
                                    modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(WorkoutTestTags.ERROR_DETAILS),
                                ) {
                                    Text(
                                        if (state.savePending) "Save needs attention ›" else "Action needs attention ›",
                                        style = InstrumentType.bodyStrong,
                                    )
                                }
                                !state.undoMessage.isNullOrBlank() -> GymUndoHost(
                                    message = state.undoMessage,
                                    onUndo = events.onUndo,
                                    onDismissed = events.onUndoDismissed,
                                    offerKey = state.undoKey ?: state.undoMessage,
                                    dwellMs = state.undoDwellMs,
                                )
                                honesty != null -> RestHonestyRow(
                                    honesty = honesty,
                                    onDismissBatteryHint = events.onDismissRestBatteryHint,
                                    onOpenNotifications = events.onOpenNotifications,
                                )
                                state.editing -> TextButton(
                                    onClick = events.onCancelEdit,
                                    modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(WorkoutTestTags.CANCEL_EDIT),
                                ) {
                                    Text("Cancel edit", style = InstrumentType.bodyStrong, color = TextSecondary)
                                }
                                state.suggestionUnavailable -> Text(
                                    LogCommitCopy.SUGGESTION_UNAVAILABLE,
                                    style = InstrumentType.caption,
                                    color = TextTertiary,
                                )
                            }
                        }
                        if (timer.show) clockButton()
                    }
                    completeDock -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        TextButton(
                            onClick = events.onAnotherSet,
                            enabled = state.showAnother,
                            modifier = Modifier.weight(1f).heightIn(min = Metrics.touchMin).testTag(WorkoutTestTags.ANOTHER_SET),
                        ) { Text("Add another set", style = InstrumentType.bodyStrong, color = TextSecondary) }
                        if (timer.show) clockButton()
                    }
                    timer.show && timer.hideIdleRest && !timedActive -> TextButton(
                        onClick = { durationSheet = true },
                        modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(WorkoutTestTags.COMPANION_CLOCK),
                    ) { Text("Timer controls ›", style = InstrumentType.bodyStrong, color = TextSecondary) }
                    else -> timerSurface()
                }
            }
        },
        volt = {
            key(action.identity) {
                val spokenAction = listOfNotNull(state.verb, state.payload).joinToString(" · ")
                PrimaryGymButton(
                    text = state.verb,
                    supporting = state.payload,
                    textStyle = InstrumentType.commit,
                    onClick = {
                        val accepted = events.onPrimary(action)
                        if (accepted && action.kind == WorkoutPrimaryKind.REVIEW_SAVE) saveDetails = true
                    },
                    enabled = action.enabled,
                    disabledReason = LogCommitCopy.disabledReason(logging = state.logging, liftReady = state.canLog || state.logging),
                    modifier = Modifier
                        .testTag(
                            when {
                                nextAct -> WorkoutTestTags.NEXT
                                finishAct -> WorkoutTestTags.DOCK_FINISH
                                else -> WorkoutTestTags.LOG_SET
                            },
                        )
                        .semantics { contentDescription = spokenAction },
                    height = Metrics.commit,
                    hapticFeedback = false,
                )
            }
        },
    )
    if (saveDetails && state.error != null) {
        GymDialog(
            title = if (state.savePending) "Save needs attention" else "Action needs attention",
            body = state.error,
            confirmLabel = if (state.savePending) "Return to entry" else "Dismiss",
            onConfirm = {
                saveDetails = false
                if (state.savePending) events.onEditFailedSave() else events.onDismissError()
            },
            onDismiss = { saveDetails = false },
            dismissLabel = "Close",
        )
    }
    if (timingDetails) {
        GymDialog(
            title = if (holdActive) "Hold" else "Set time",
            body = clockLabel,
            confirmLabel = if (timer.stopwatchRunning) "Stop timing" else "Return to workout",
            onConfirm = {
                if (timer.stopwatchRunning) events.onStopSetClock()
                timingDetails = false
            },
            onDismiss = { timingDetails = false },
            dismissLabel = if (timer.stopwatchRunning) "Keep timing" else null,
        )
    }
    if (durationSheet) {
        RestDurationSheet(
            selectedSeconds = timer.restTotalSeconds,
            onSelect = { seconds ->
                events.onSelectRestDuration(seconds)
                durationSheet = false
            },
            onNudge = events.onNudgeRest,
            onCustomRest = events.onCustomRest,
            onDismiss = { durationSheet = false },
            onStartRest = {
                durationSheet = false
                events.onStartRest()
            },
            offerSetClock = timer.offerSetClock,
            onTimeSet = {
                durationSheet = false
                events.onStartSetClock()
            },
        )
    }
}
