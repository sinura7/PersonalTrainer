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
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.RestBatteryCopy
import com.sinura.personaltrainer.domain.RestFinishFlash
import com.sinura.personaltrainer.domain.RestIdleCopy
import com.sinura.personaltrainer.domain.RestTimer
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
import com.sinura.personaltrainer.ui.theme.Surface1
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
 * The rest clock, pinned in the lower dock above Log set (G-02).
 *
 * The log used to hold an 88 dp ring and a −15 / Skip / +15 stack. That is the floor page
 * now. Here the running state is a ~56 dp row: REST, a [InstrumentType.numeralMd] clock, a
 * 4 dp track, and trailing Skip. Idle is Not running + planned duration + Start.
 * Preset chips live on the floor. Tap the bar or the idle line to push it. The
 * hairline sits above the row so the list and the dock stay visually split when
 * rest lives at the bottom.
 */
@Composable
fun RestDock(
    remainingSeconds: Int,
    totalSeconds: Int,
    running: Boolean,
    onSkip: () -> Unit,
    onStart: () -> Unit,
    onOpenRest: () -> Unit,
    modifier: Modifier = Modifier,
    completedTimerId: String? = null,
    hideWhenIdle: Boolean = false,
    afterWarmup: Boolean = false,
    batteryHint: Boolean = false,
    onDismissBatteryHint: () -> Unit = {},
) {
    var justFinished by remember { mutableStateOf(false) }
    var flashedTimerId by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current

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

    // One tick per second through the final stretch, so the end of the rest can be felt with
    // the phone face-down on a bench.
    LaunchedEffect(urgent, safeRemaining) {
        if (urgent && safeRemaining > 0) Haptics.tick(view)
    }

    if (!running && !justFinished) {
        if (hideWhenIdle) return
        HairlineDivider(startIndent = 0.dp)
        RestIdleRow(
            totalSeconds = totalSeconds,
            afterWarmup = afterWarmup,
            onStart = onStart,
            onOpenRest = onOpenRest,
            modifier = modifier
                .fillMaxWidth()
                .background(Surface1)
                .padding(horizontal = Metrics.space4, vertical = Metrics.space2),
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

    HairlineDivider(startIndent = 0.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface1)
            .padding(horizontal = Metrics.space4, vertical = Metrics.space2),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .testTag("workout-rest-bar")
                .clickable(role = Role.Button, onClick = onOpenRest)
                .semantics {
                    contentDescription = "$kicker $clock remaining. Open rest timer."
                    if (TalkBackPolicy.announceRestKicker(justFinished)) {
                        liveRegion = LiveRegionMode.Polite
                    }
                },
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Kicker(kicker, color = accent, asHeading = false)
                Text(
                    clock,
                    modifier = Modifier.graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    },
                    style = InstrumentType.numeralMd,
                    color = TextPrimary,
                    maxLines = 1,
                )
            }
            RestLinearTrack(
                remainingSeconds = if (justFinished) 0 else safeRemaining,
                totalSeconds = totalSeconds,
                accent = accent,
                finished = justFinished,
            )
        }
        if (running) {
            RestControl(
                label = "Skip",
                onClick = onSkip,
                modifier = Modifier.widthIn(min = 72.dp),
            )
        }
    }
        if (running && batteryHint) {
            RestBatteryHintRow(onDismiss = onDismissBatteryHint)
        }
    }
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

/**
 * Idle rest on the log: not a countdown. Planned duration is a label, Start
 * is the only start. Chips live on the floor so a countdown is never shown
 * with a second row of duration controls underneath it.
 */
@Composable
fun RestIdleRow(
    totalSeconds: Int,
    onStart: () -> Unit,
    onOpenRest: () -> Unit,
    modifier: Modifier = Modifier,
    afterWarmup: Boolean = false,
) {
    val clock = RestTimer.formatClock(totalSeconds.coerceAtLeast(0))
    val duration = RestIdleCopy.dockDuration(clock, afterWarmup)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .testTag("workout-rest-idle")
                .clickable(role = Role.Button, onClick = onOpenRest)
                .semantics {
                    contentDescription = RestIdleCopy.spoken(clock, afterWarmup)
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Kicker(RestIdleCopy.KICKER)
            Text(
                duration,
                style = InstrumentType.bodyStrong,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RestControl(
            label = "Start",
            onClick = onStart,
            modifier = Modifier.widthIn(min = 72.dp),
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
            Kicker(kicker, color = accent)
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

@Composable
fun RestControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .heightIn(min = Metrics.control)
            .clip(RoundedCornerShape(Radius.sm))
            .background(Surface2)
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.sm))
            .clickable(role = Role.Button) {
                Haptics.tick(view)
                onClick()
            },
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
