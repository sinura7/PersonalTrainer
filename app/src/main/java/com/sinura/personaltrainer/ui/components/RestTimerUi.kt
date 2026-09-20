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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sinura.personaltrainer.domain.FloorTimerSurface
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.RestBatteryCopy
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
import com.sinura.personaltrainer.ui.theme.RestCyanDim
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.instrumentTween
import com.sinura.personaltrainer.ui.theme.instrumentLinear
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion

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
    showRestControls: Boolean = false,
    onNudgeRest: (Int) -> Unit = {},
    onSkip: () -> Unit = {},
    onStop: (() -> Unit)? = null,
    clockColor: Color = TextPrimary,
    showChevron: Boolean = false,
    leadingGlyph: ImageVector? = null,
    glyphTint: Color = TextSecondary,
    showIdleStart: Boolean = false,
    onStart: () -> Unit = {},
    startSpoken: String? = null,
    offerSetClock: Boolean = false,
    onStartSetClock: () -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.logTimerRow)
            .clip(RoundedCornerShape(Radius.sm))
            .background(Surface2)
            .testTag(testTag),
    ) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val clockWidth = measurer.measure(clock, style = InstrumentType.numeralMd, softWrap = false).size.width
        val labels = when {
            showRestControls -> listOf("−15", "+15", "Skip")
            showIdleStart -> if (offerSetClock) listOf(SetStopwatchCopy.START, "Start rest") else listOf("Start rest")
            onStop != null -> listOf(SetStopwatchCopy.STOP)
            else -> emptyList()
        }
        val controlWidth = labels.sumOf { label ->
            maxOf(with(density) { Metrics.touchMin.roundToPx() },
                measurer.measure(label, style = InstrumentType.bodyStrong, softWrap = false).size.width +
                    with(density) { (Metrics.space2 * 2).roundToPx() })
        }
        val wrapControls = clockWidth + controlWidth + with(density) { (Metrics.space4 * 2).roundToPx() } > constraints.maxWidth
        val clockSpace = if (wrapControls) constraints.maxWidth else
            constraints.maxWidth - controlWidth - with(density) { Metrics.space4.roundToPx() }
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(Radius.sm)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(RestCyanDim),
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.logTimerRow)
                .padding(start = Metrics.space2, end = Metrics.space1),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
            itemVerticalAlignment = Alignment.CenterVertically,
            maxItemsInEachRow = if (wrapControls) 1 else 2,
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
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
            ) {
                val density = LocalDensity.current
                val measurer = rememberTextMeasurer()
                val clockWidth = measurer.measure(clock, style = InstrumentType.numeralMd, softWrap = false).size.width
                val labelWidth = measurer.measure(kicker.uppercase(), style = InstrumentType.kicker, softWrap = false).size.width
                val decorations = Metrics.space2 * 2 +
                    (if (leadingGlyph != null) Metrics.icon + Metrics.space2 else 0.dp) +
                    (if (showChevron) Metrics.chevron else 0.dp)
                val inline = clockWidth + labelWidth + with(density) { decorations.roundToPx() } <= clockSpace
                val clockText: @Composable () -> Unit = {
                    Text(
                        clock,
                        modifier = Modifier.graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        },
                        style = InstrumentType.numeralMd,
                        color = clockColor,
                    )
                }
                if (inline) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2), verticalAlignment = Alignment.CenterVertically) {
                        leadingGlyph?.let { FloorFieldGlyph(icon = it, tint = glyphTint) }
                        Kicker(kicker, color = accent, asHeading = false)
                        clockText()
                        if (showChevron) Icon(TemperIcons.Chevron, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(Metrics.chevron))
                    }
                } else {
                    Column(modifier = Modifier.padding(vertical = Metrics.space1), verticalArrangement = Arrangement.Center) {
                        Kicker(kicker, color = accent, asHeading = false)
                        clockText()
                    }
                }
            }
            Row(
                modifier = if (wrapControls) Modifier.fillMaxWidth().padding(bottom = Metrics.space1) else Modifier,
                horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val controlModifier = if (wrapControls) Modifier.weight(1f) else Modifier
                if (showRestControls) {
                    RestControl(
                        label = "−15",
                        spoken = "Minus 15 seconds",
                        onClick = { onNudgeRest(-RestTimer.NUDGE_SECONDS) },
                        modifier = controlModifier
                            .widthIn(min = Metrics.touchMin)
                            .testTag("workout-rest-minus"),
                    )
                    RestControl(
                        label = "+15",
                        spoken = "Plus 15 seconds",
                        onClick = { onNudgeRest(RestTimer.NUDGE_SECONDS) },
                        modifier = controlModifier
                            .widthIn(min = Metrics.touchMin)
                            .testTag("workout-rest-plus"),
                    )
                    RestControl(
                        label = "Skip",
                        onClick = onSkip,
                        confirm = true,
                        modifier = controlModifier
                            .widthIn(min = Metrics.touchMin)
                            .testTag("workout-rest-skip"),
                    )
                }
                if (showIdleStart) {
                    if (offerSetClock) {
                        RestControl(
                            label = SetStopwatchCopy.START,
                            spoken = SetStopwatchCopy.START_SPOKEN,
                            onClick = onStartSetClock,
                            modifier = controlModifier.widthIn(min = Metrics.touchMin).testTag("workout-start-set-clock"),
                        )
                    }
                    RestControl(
                        label = "Start rest",
                        spoken = startSpoken,
                        onClick = onStart,
                        modifier = controlModifier
                            .widthIn(min = Metrics.touchMin)
                            .testTag("workout-start-rest"),
                    )
                }
                if (onStop != null) {
                    RestControl(
                        label = SetStopwatchCopy.STOP,
                        onClick = onStop,
                        modifier = controlModifier
                            .widthIn(min = Metrics.touchMin)
                            .testTag("workout-stop-set-clock"),
                    )
                }
            }
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
        onStop = onStop,
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
 * Quick duration editor. Overlay: the dock stays 56 dp. Presets apply and
 * dismiss. Custom opens [CustomRestDialog]. Planned ±15 ticks here; running
 * ±15 stay on the live bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestDurationSheet(
    selectedSeconds: Int,
    onSelect: (Int) -> Unit,
    onNudge: (Int) -> Unit,
    onCustomRest: (String) -> Boolean,
    onDismiss: () -> Unit,
    offerSetClock: Boolean = false,
    onTimeSet: () -> Unit = {},
    onStartRest: (() -> Unit)? = null,
) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    val reduceMotion = LocalReducedMotion.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clock = RestTimer.formatClock(selectedSeconds.coerceAtLeast(0))
    val sheetSnap = Motion.durationMs(reduceMotion, Motion.BASE) == 0
    if (!showCustom) ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface3,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("workout-rest-duration-sheet")
                .then(if (sheetSnap) Modifier else Modifier)
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space4),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    RestIdleCopy.SHEET_TITLE,
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    clock,
                    style = InstrumentType.numeralMd,
                    color = TextPrimary,
                    maxLines = 1,
                )
            }
            if (onStartRest != null) {
                SecondaryGymButton(text = "Start rest", onClick = onStartRest)
            }
            RestPresetChips(
                selectedSeconds = selectedSeconds,
                onSelect = onSelect,
                onCustom = { showCustom = true },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RestControl(
                    label = "−15",
                    spoken = "Minus 15 seconds",
                    onClick = { onNudge(-RestTimer.NUDGE_SECONDS) },
                    modifier = Modifier
                        .widthIn(min = Metrics.touchMin)
                        .testTag("workout-rest-sheet-minus"),
                )
                RestControl(
                    label = "+15",
                    spoken = "Plus 15 seconds",
                    onClick = { onNudge(RestTimer.NUDGE_SECONDS) },
                    modifier = Modifier
                        .widthIn(min = Metrics.touchMin)
                        .testTag("workout-rest-sheet-plus"),
                )
                if (offerSetClock) {
                    TextButton(
                        onClick = onTimeSet,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Metrics.touchMin)
                            .testTag("workout-sheet-start-set-clock")
                            .semantics {
                                contentDescription = SetStopwatchCopy.START_SPOKEN
                            },
                    ) {
                        Text(
                            SetStopwatchCopy.START,
                            style = InstrumentType.bodyStrong,
                            color = TextSecondary,
                        )
                    }
                }
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
                    onDismiss()
                }
                ok
            },
            onDismiss = { showCustom = false },
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

/** One control in a [RestSegments] row. */
data class RestSegment(
    val label: String,
    val spoken: String?,
    val tag: String,
    val onClick: () -> Unit,
    val confirm: Boolean = false,
)

/**
 * − / + / Skip as one instrument rather than three loose [RestControl] pills.
 *
 * On the dock's rest card those three were separate bordered cards with gaps between them:
 * a card of cards, read at exactly the moment a lifter is glancing rather than reading. One
 * recessed track with hairline dividers says the same thing with a quarter of the edges,
 * and each segment still keeps its own 48 dp target, test tag, spoken form and haptic — the
 * same `confirm` commit for Skip that [RestControl] gives it.
 *
 * It lives here beside [RestControl], not in the card that uses it, because the rest card
 * is not allowed to name `Haptics` at all: the card reads the timer service's clock and
 * must never be able to pulse on its own.
 */
@Composable
fun RestSegments(
    segments: List<RestSegment>,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) return
    val view = LocalView.current
    Row(
        modifier = modifier
            .heightIn(min = Metrics.touchMin)
            .clip(RoundedCornerShape(Radius.sm))
            .background(Surface1)
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.sm)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, segment ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(Metrics.hairline)
                        .height(Metrics.touchMin)
                        .background(Hairline),
                )
            }
            Box(
                modifier = Modifier
                    .widthIn(min = Metrics.touchMin)
                    .heightIn(min = Metrics.touchMin)
                    .clickable(role = Role.Button) {
                        if (segment.confirm) Haptics.commit(view) else Haptics.tick(view)
                        segment.onClick()
                    }
                    .testTag(segment.tag)
                    .then(
                        if (segment.spoken == null) {
                            Modifier
                        } else {
                            Modifier.semantics { contentDescription = segment.spoken }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    segment.label,
                    modifier = Modifier.padding(horizontal = Metrics.space2, vertical = Metrics.space2),
                    style = InstrumentType.bodyStrong,
                    color = TextPrimary,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
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
    val submit = {
        val ok = onConfirm(input)
        invalid = !ok
        if (!ok) Haptics.reject(view)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(Metrics.gutter),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = Metrics.inputDialogMaxWidth).fillMaxWidth().heightIn(max = maxHeight)
                    .semantics { paneTitle = title },
                shape = RoundedCornerShape(Radius.lg), color = Surface2,
            ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(Metrics.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                Text(title, style = InstrumentType.title, color = TextPrimary)
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
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Text("Seconds (90) or mm:ss (1:30). 15 seconds to 30 minutes.",
                    style = InstrumentType.body, color = TextSecondary)
                if (invalid) {
                    Text("Use 90 or 1:30.", style = InstrumentType.caption, color = Danger)
                }
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Metrics.touchMin)) {
                        Text("Cancel", style = InstrumentType.bodyStrong, color = TextSecondary)
                    }
                    TextButton(onClick = submit, modifier = Modifier.heightIn(min = Metrics.touchMin)) {
                        Text(confirmLabel, style = InstrumentType.bodyStrong, color = Volt)
                    }
                }
            }
            }
        }
    }
}

private val REST_RING_SIZE = 280.dp
private val REST_RING_STROKE = 10.dp
