package com.sinura.personaltrainer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.FloorTimedMode
import com.sinura.personaltrainer.domain.FloorTimedModeResolver
import com.sinura.personaltrainer.domain.FloorTimerSurface
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.RestBatteryCopy
import com.sinura.personaltrainer.domain.RestFinishFlash
import com.sinura.personaltrainer.domain.RestHonestyCopy
import com.sinura.personaltrainer.domain.RestIdleCopy
import com.sinura.personaltrainer.domain.RestNotificationCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.SetStopwatchCopy
import com.sinura.personaltrainer.domain.TalkBackPolicy
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.Warn
import com.sinura.personaltrainer.ui.theme.instrumentTween
import com.sinura.personaltrainer.ui.theme.instrumentLinear
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

/**
 * One clock slot in the lower dock (Packet E).
 *
 * Rest counts down; a running set counts up. Modes never stack. Planned
 * rest is presets / ±15 / Custom. Overlay rest on the live log stays
 * forbidden (ADR-012).
 */
@Composable
fun FloorTimerSlot(
    remainingSeconds: Int,
    totalSeconds: Int,
    restRunning: Boolean,
    onSkip: () -> Unit,
    onStart: () -> Unit,
    onSelectRestDuration: (Int) -> Unit,
    modifier: Modifier = Modifier,
    completedTimerId: String? = null,
    hideWhenIdle: Boolean = false,
    afterWarmup: Boolean = false,
    batteryHint: Boolean = false,
    onDismissBatteryHint: () -> Unit = {},
    onOpenRest: () -> Unit = {},
    holdRunning: Boolean = false,
    holdElapsedSeconds: Int = 0,
    holdRemainingSeconds: Int = 0,
    holdTotalSeconds: Int = 0,
    holdTargetReached: Boolean = false,
    stopwatchRunning: Boolean = false,
    stopwatchElapsedSeconds: Int = 0,
    offerSetClock: Boolean = false,
    onStartSetClock: () -> Unit = {},
    onStopSetClock: () -> Unit = {},
    onNudgeRest: (Int) -> Unit = {},
    onCustomRest: (String) -> Boolean = { false },
    persistenceHealthy: Boolean = true,
    notificationsEnabled: Boolean = true,
    exactAlarmBestEffort: Boolean = false,
    hasLifts: Boolean = true,
    onOpenNotifications: () -> Unit = {},
) {
    val holdActive = holdRunning || holdTargetReached
    val mode = FloorTimerSurface.mode(
        holdRunning = holdRunning,
        stopwatchRunning = stopwatchRunning,
        hasLifts = hasLifts,
        restRunning = restRunning,
        restComplete = !completedTimerId.isNullOrBlank() && !restRunning,
        holdActive = holdActive,
    )
    val showSetClock = offerSetClock &&
        FloorTimedModeResolver.offerSetClock(mode, isHoldLift = holdActive)
    when (mode) {
        FloorTimedMode.NONE -> Unit
        FloorTimedMode.HOLD_RUNNING,
        FloorTimedMode.STOPWATCH_RUNNING,
        -> {
            SetWorkDock(
                elapsedSeconds = if (holdActive) holdElapsedSeconds else stopwatchElapsedSeconds,
                remainingSeconds = if (holdActive) holdRemainingSeconds else 0,
                totalSeconds = if (holdActive) holdTotalSeconds else 0,
                hold = holdActive,
                targetReached = holdTargetReached,
                running = holdRunning || stopwatchRunning,
                onStop = onStopSetClock.takeIf { stopwatchRunning && !holdActive },
                modifier = modifier.fillMaxWidth(),
            )
        }
        FloorTimedMode.REST_IDLE,
        FloorTimedMode.REST_RUNNING,
        FloorTimedMode.REST_COMPLETE,
        -> RestDock(
            remainingSeconds = remainingSeconds,
            totalSeconds = totalSeconds,
            running = restRunning,
            onSkip = onSkip,
            onStart = onStart,
            onSelectRestDuration = onSelectRestDuration,
            modifier = modifier,
            completedTimerId = completedTimerId,
            hideWhenIdle = hideWhenIdle,
            afterWarmup = afterWarmup,
            onOpenRest = onOpenRest,
            offerSetClock = showSetClock,
            onStartSetClock = onStartSetClock,
            onNudgeRest = onNudgeRest,
            onCustomRest = onCustomRest,
        )
    }
}

/**
 * One compact dock instrument: countdown fill behind kicker, time, and
 * mode controls. REST, HOLD, and SET share this geometry. Reserved
 * height is [Metrics.logTimerRow]; font scale may grow the row, extra
 * tracks and honesty captions may not.
 */
@Composable
fun FloorInstrumentBar(
    kicker: String,
    clock: String,
    progress: Float,
    accent: Color,
    spoken: String,
    testTag: String,
    modifier: Modifier = Modifier,
    pulseScale: Float = 1f,
    onClockClick: (() -> Unit)? = null,
    liveRegion: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.logTimerRow)
            .clip(RoundedCornerShape(Radius.sm))
            .background(Surface2)
            .testTag(testTag),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(Radius.sm)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(accent.copy(alpha = 0.28f)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.logTimerRow)
                .padding(start = Metrics.space2, end = Metrics.space1),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Metrics.touchMin)
                    .then(
                        if (onClockClick != null) {
                            Modifier.clickable(role = Role.Button, onClick = onClockClick)
                        } else {
                            Modifier
                        },
                    )
                    .semantics {
                        contentDescription = spoken
                        if (liveRegion) {
                            this.liveRegion = LiveRegionMode.Polite
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Kicker(kicker, color = accent, asHeading = false)
                Text(
                    clock,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        },
                    style = InstrumentType.numeralMd,
                    color = TextPrimary,
                    maxLines = 1,
                )
            }
            trailing()
        }
    }
}

/**
 * In-set work clock. Holds count remaining on the same bar as REST.
 * Stopwatch counts up. Rest UI is hidden while this runs.
 */
@Composable
fun SetWorkDock(
    elapsedSeconds: Int,
    modifier: Modifier = Modifier,
    remainingSeconds: Int = 0,
    totalSeconds: Int = 0,
    hold: Boolean = true,
    targetReached: Boolean = false,
    running: Boolean = true,
    onStop: (() -> Unit)? = null,
) {
    val kicker = when {
        hold && targetReached -> HoldWork.DONE
        hold -> FloorTimerSurface.HOLD_KICKER
        else -> FloorTimerSurface.SET_KICKER
    }
    val displayClock = HoldWork.clock(
        if (hold && !targetReached) {
            HoldWork.liveDockSeconds(
                remainingSeconds = remainingSeconds,
                targetReached = false,
                running = running && hold,
                totalSeconds = totalSeconds,
            )
        } else {
            FloorTimerSurface.setClockSeconds(elapsedSeconds)
        },
    )
    val spoken = if (hold && targetReached) {
        "${HoldWork.DONE}. Log hold with elapsed time."
    } else if (hold) {
        "$kicker $displayClock remaining"
    } else {
        "$kicker $displayClock elapsed"
    }
    val accent = if (hold && targetReached) PrGold else RestCyan
    val progress = if (hold) {
        FloorTimerSurface.holdBarProgress(
            remainingSeconds = remainingSeconds,
            totalSeconds = totalSeconds,
            targetReached = targetReached,
        )
    } else {
        1f
    }
    FloorInstrumentBar(
        kicker = kicker,
        clock = displayClock,
        progress = progress,
        accent = accent,
        spoken = spoken,
        testTag = "workout-hold-clock",
        modifier = modifier,
        trailing = {
            if (onStop != null) {
                RestControl(
                    label = SetStopwatchCopy.STOP,
                    onClick = onStop,
                    modifier = Modifier
                        .widthIn(min = Metrics.touchMin)
                        .testTag("workout-stop-set-clock"),
                )
            }
        },
    )
}

/**
 * The rest clock, pinned in the lower dock above Log set (G-02).
 *
 * Running rest is one [FloorInstrumentBar]: countdown fill, REST + time,
 * and −15 / +15 / Skip. Honesty lives in the context rail so this row
 * cannot collide with the coach line at 360×800. Idle is one matching
 * bar plus optional presets. The rest page still owns the 280 dp ring.
 */
@Composable
fun RestDock(
    remainingSeconds: Int,
    totalSeconds: Int,
    running: Boolean,
    onSkip: () -> Unit,
    onStart: () -> Unit,
    onSelectRestDuration: (Int) -> Unit,
    modifier: Modifier = Modifier,
    completedTimerId: String? = null,
    hideWhenIdle: Boolean = false,
    afterWarmup: Boolean = false,
    onOpenRest: () -> Unit = {},
    offerSetClock: Boolean = false,
    onStartSetClock: () -> Unit = {},
    onNudgeRest: (Int) -> Unit = {},
    onCustomRest: (String) -> Boolean = { false },
) {
    var justFinished by remember { mutableStateOf(false) }
    var flashedTimerId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(completedTimerId) {
        if (RestFinishFlash.shouldFlash(completedTimerId, flashedTimerId)) {
            flashedTimerId = completedTimerId
            justFinished = true
        }
    }
    LaunchedEffect(running) {
        if (running) justFinished = false
    }
    LaunchedEffect(justFinished) {
        if (justFinished) {
            delay(Motion.FINISHED_DWELL_MS)
            justFinished = false
        }
    }

    val safeRemaining = remainingSeconds.coerceAtLeast(0)
    val urgent = running && safeRemaining <= URGENT_SECONDS
    val reduceMotion = LocalReducedMotion.current
    // Composed only while urgent: an infiniteRepeatable never finishes even at
    // target == initial, so the idle dock otherwise requested a frame every
    // vsync for the whole 60-90 minute session.
    val pulseScale: Float by if (urgent && !reduceMotion) {
        rememberInfiniteTransition(label = "rest-pulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.015f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = Motion.PULSE_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "rest-bar-pulse",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    if (!running && !justFinished) {
        if (hideWhenIdle) return
        RestIdleRow(
            totalSeconds = totalSeconds,
            afterWarmup = afterWarmup,
            onStart = onStart,
            onSelectRestDuration = onSelectRestDuration,
            offerSetClock = offerSetClock,
            onStartSetClock = onStartSetClock,
            onNudgeRest = onNudgeRest,
            onCustomRest = onCustomRest,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    // Cyan while the clock runs. Warn in the last ten seconds. Gold only when rest is done —
    // the same gold as a record, for the finished flash, then the dock returns to idle.
    val accent = when {
        justFinished -> PrGold
        urgent -> Warn
        else -> RestCyan
    }
    val clock = RestTimer.formatClock(if (justFinished) 0 else safeRemaining)
    val kicker = TalkBackPolicy.restKicker(justFinished)
    val spoken = buildString {
        append("$kicker $clock remaining.")
        if (urgent) append(" Last ten seconds.")
        append(" Open rest timer.")
    }

    FloorInstrumentBar(
        kicker = kicker,
        clock = clock,
        progress = RestTimer.sweepFraction(
            remainingSeconds = if (justFinished) 0 else safeRemaining,
            totalSeconds = totalSeconds,
        ),
        accent = accent,
        spoken = spoken,
        testTag = "workout-rest-bar",
        modifier = modifier.fillMaxWidth(),
        pulseScale = pulseScale,
        onClockClick = onOpenRest,
        liveRegion = TalkBackPolicy.announceRestKicker(justFinished),
        trailing = {
            if (running) {
                RestControl(
                    label = "−15",
                    spoken = "Minus 15 seconds",
                    onClick = { onNudgeRest(-RestTimer.NUDGE_SECONDS) },
                    modifier = Modifier
                        .widthIn(min = Metrics.touchMin)
                        .testTag("workout-rest-minus"),
                )
                RestControl(
                    label = "+15",
                    spoken = "Plus 15 seconds",
                    onClick = { onNudgeRest(RestTimer.NUDGE_SECONDS) },
                    modifier = Modifier
                        .widthIn(min = Metrics.touchMin)
                        .testTag("workout-rest-plus"),
                )
                RestControl(
                    label = "Skip",
                    onClick = onSkip,
                    confirm = true,
                    modifier = Modifier
                        .widthIn(min = Metrics.touchMin)
                        .testTag("workout-rest-skip"),
                )
            }
        },
    )
}

@Composable
fun RestBatteryHintRow(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "workout-rest-battery",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            RestBatteryCopy.SENTENCE,
            modifier = Modifier.weight(1f),
            style = InstrumentType.caption,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.heightIn(min = Metrics.touchMin),
        ) {
            Text(
                RestBatteryCopy.GOT_IT,
                style = InstrumentType.bodyStrong,
                color = Volt,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun RestHonestyRow(
    honesty: RestHonestyCopy.Honesty?,
    onDismissBatteryHint: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenNotifications: () -> Unit = {},
) {
    if (honesty == null) return
    when (honesty.kind) {
        RestHonestyCopy.Kind.NOTIFICATION -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = Metrics.touchMin)
                    .testTag("workout-notif-recovery")
                    .clickable(role = Role.Button, onClick = onOpenNotifications),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    honesty.sentence,
                    modifier = Modifier.weight(1f),
                    style = InstrumentType.bodyStrong,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    honesty.action ?: RestNotificationCopy.RECOVERY_ACTION,
                    style = InstrumentType.bodyStrong,
                    color = Volt,
                    maxLines = 1,
                )
            }
        }
        RestHonestyCopy.Kind.FIRST_REST -> {
            RestBatteryHintRow(
                onDismiss = onDismissBatteryHint,
                modifier = modifier,
            )
        }
        RestHonestyCopy.Kind.PERSISTENCE,
        RestHonestyCopy.Kind.EXACT,
        -> {
            Text(
                honesty.sentence,
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("workout-rest-honesty"),
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Idle rest on the log: not a countdown. Planned duration is Rest 1:30
 * with quiet −15/+15, presets on tap, and Start. Start next is not
 * composed (Packet A). Log set, pinned under this dock, is the filled
 * act.
 */
@Composable
fun RestIdleRow(
    totalSeconds: Int,
    onStart: () -> Unit,
    onSelectRestDuration: (Int) -> Unit,
    modifier: Modifier = Modifier,
    afterWarmup: Boolean = false,
    offerSetClock: Boolean = false,
    onStartSetClock: () -> Unit = {},
    onNudgeRest: (Int) -> Unit = {},
    onCustomRest: (String) -> Boolean = { false },
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    val safeTotal = totalSeconds.coerceAtLeast(0)
    val clock = RestTimer.formatClock(safeTotal)
    val duration = RestIdleCopy.dockDuration(clock, afterWarmup)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.logTimerRow)
                .clip(RoundedCornerShape(Radius.sm))
                .background(Surface2)
                .padding(horizontal = Metrics.space2),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloorFieldGlyph(icon = TemperIcons.FloorRest)
            Text(
                duration,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Metrics.touchMin)
                    .testTag("workout-rest-idle")
                    .clickable(role = Role.Button) { picking = !picking }
                    .semantics {
                        contentDescription = RestIdleCopy.spoken(clock, afterWarmup) +
                            " Tap for rest presets."
                    },
                style = InstrumentType.bodyStrong,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RestControl(
                label = "−15",
                spoken = "Minus 15 seconds",
                onClick = { onNudgeRest(-RestTimer.NUDGE_SECONDS) },
                modifier = Modifier
                    .widthIn(min = Metrics.touchMin)
                    .testTag("workout-rest-minus"),
            )
            RestControl(
                label = "+15",
                spoken = "Plus 15 seconds",
                onClick = { onNudgeRest(RestTimer.NUDGE_SECONDS) },
                modifier = Modifier
                    .widthIn(min = Metrics.touchMin)
                    .testTag("workout-rest-plus"),
            )
            RestControl(
                label = RestIdleCopy.START,
                onClick = onStart,
                modifier = Modifier
                    .widthIn(min = Metrics.touchMin)
                    .testTag("workout-start-rest"),
            )
        }
        if (picking) {
            RestPresetChips(
                selectedSeconds = safeTotal,
                onSelect = { seconds ->
                    onSelectRestDuration(seconds)
                    picking = false
                },
                onCustom = { showCustom = true },
            )
        }
        if (offerSetClock && !picking) {
            TextButton(
                onClick = onStartSetClock,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Metrics.touchMin)
                    .testTag("workout-start-set-clock"),
            ) {
                Text(
                    SetStopwatchCopy.START,
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
    if (showCustom) {
        CustomRestDialog(
            title = "Custom rest",
            confirmLabel = "Set",
            onConfirm = { input ->
                val ok = onCustomRest(input)
                if (ok) {
                    showCustom = false
                    picking = false
                }
                ok
            },
            onDismiss = { showCustom = false },
        )
    }
}

@Composable
fun RestLinearTrack(
    remainingSeconds: Int,
    totalSeconds: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    finished: Boolean = false,
) {
    val target = RestTimer.sweepFraction(remainingSeconds, totalSeconds)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = instrumentLinear(Motion.TICK_MS),
        label = "rest-track",
    )
    val sweepColor by animateColorAsState(
        targetValue = if (finished) PrGold else accent,
        animationSpec = instrumentTween(Motion.BASE),
        label = "rest-track-accent",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(REST_TRACK_HEIGHT)
            .clip(RoundedCornerShape(Radius.xs))
            .background(HairlineStrong),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(sweepColor),
        )
    }
}

/**
 * The rest-floor clock: a countdown ring around the remaining time.
 *
 * Lives only on the Rest page (280 dp so `numeralHero` still fits a
 * `10:00` clock). The log keeps the condensed bar and linear track.
 * Overlay rest and a 240 dp ring on the log stay won’ts.
 */
@Composable
fun RestSweepRing(
    remainingSeconds: Int,
    totalSeconds: Int,
    accent: Color,
    clock: String,
    kicker: String,
    running: Boolean,
    finished: Boolean,
    modifier: Modifier = Modifier,
    clockTestTag: String? = null,
    ringSize: Dp = REST_RING_SIZE,
    afterWarmup: Boolean = false,
) {
    val target = RestTimer.sweepFraction(remainingSeconds, totalSeconds)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = instrumentLinear(Motion.TICK_MS),
        label = "rest-ring",
    )
    val sweepColor by animateColorAsState(
        targetValue = if (finished) PrGold else accent,
        animationSpec = instrumentTween(Motion.BASE),
        label = "rest-ring-accent",
    )
    val reduceMotion = LocalReducedMotion.current
    val urgent = running && remainingSeconds in 1..10
    val pulseScale: Float by if (urgent && !reduceMotion) {
        rememberInfiniteTransition(label = "rest-ring-pulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.015f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = Motion.PULSE_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "rest-ring-scale",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val spoken = if (running) {
        "Rest $clock remaining"
    } else {
        RestIdleCopy.spoken(clock, afterWarmup)
    }
    Box(
        modifier = modifier
            .size(ringSize)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = REST_RING_STROKE.toPx()
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
            val inset = strokePx / 2f
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val origin = Offset(inset, inset)
            drawArc(
                color = HairlineStrong,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = origin,
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = sweepColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = origin,
                size = arcSize,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (kicker == TalkBackPolicy.REST_RUNNING_KICKER) {
                FloorFieldGlyph(
                    icon = TemperIcons.FloorRest,
                    tint = accent,
                )
            } else {
                Kicker(kicker, color = accent)
            }
            Text(
                clock,
                style = InstrumentType.numeralHero,
                color = TextPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .then(
                        if (clockTestTag != null) Modifier.testTag(clockTestTag) else Modifier,
                    )
                    .clearAndSetSemantics { contentDescription = spoken },
            )
        }
    }
}

/**
 * Dock rest control. ±15 ticks (HA-12). Skip uses [Haptics.commit]
 * (HA-13 medium click). Rest 5–1 stays on the service (HA-14/15/25).
 */
@Composable
fun RestControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    spoken: String? = null,
    confirm: Boolean = false,
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .heightIn(min = Metrics.touchMin)
            .clip(RoundedCornerShape(Radius.sm))
            .background(Surface2)
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.sm))
            .clickable(role = Role.Button) {
                if (confirm) Haptics.commit(view) else Haptics.tick(view)
                onClick()
            }
            .then(
                if (spoken != null) {
                    Modifier.semantics { contentDescription = spoken }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = Metrics.space2, vertical = Metrics.space2),
            style = InstrumentType.bodyStrong,
            color = TextPrimary,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun RestPresetChips(
    selectedSeconds: Int?,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val customSelected = selectedSeconds != null && selectedSeconds !in RestTimer.PRESETS_SECONDS
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        items(RestTimer.PRESETS_SECONDS, key = { it }) { seconds ->
            InstrumentChip(
                label = RestTimer.formatClock(seconds),
                selected = selectedSeconds == seconds,
                onClick = { onSelect(seconds) },
            )
        }
        item {
            InstrumentChip(
                label = if (customSelected) RestTimer.formatClock(selectedSeconds ?: 0) else "Custom",
                selected = customSelected,
                onClick = onCustom,
            )
        }
    }
}

@Composable
fun CustomRestDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = InstrumentType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                Text(
                    "Seconds (90) or mm:ss (1:30). 15 seconds to 30 minutes.",
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
                OutlinedTextField(
                    value = input,
                    // As typed. The confirm button runs RestTimer.parseCustom and says "Use 90
                    // or 1:30." when it cannot read the text; a filter that dropped the dot
                    // from "1.5" used to hand that parser 15 and set a 15-second rest.
                    onValueChange = {
                        input = it
                        invalid = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Rest") },
                    placeholder = { Text("1:30") },
                    singleLine = true,
                    isError = invalid,
                    textStyle = InstrumentType.numeralMd,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = NumericEntry.CUSTOM_REST.imeAction(),
                    ),
                )
                if (invalid) {
                    Text("Use 90 or 1:30.", style = InstrumentType.caption, color = Danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ok = onConfirm(input)
                    invalid = !ok
                    if (!ok) Haptics.reject(view)
                },
            ) { Text(confirmLabel, style = InstrumentType.bodyStrong, color = Volt) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        },
    )
}


private const val URGENT_SECONDS = 10
private val REST_TRACK_HEIGHT = 4.dp
private val REST_RING_SIZE = 280.dp
private val REST_RING_STROKE = 10.dp
