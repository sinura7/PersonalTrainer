package com.sinura.personaltrainer.ui.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.RestFinishFlash
import com.sinura.personaltrainer.domain.RestIdleCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.SetStopwatchCopy
import com.sinura.personaltrainer.domain.TalkBackPolicy
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.RestSegment
import com.sinura.personaltrainer.ui.components.RestSegments
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Warn
import com.sinura.personaltrainer.ui.theme.instrumentLinear
import com.sinura.personaltrainer.ui.theme.instrumentTween
import kotlinx.coroutines.delay

/**
 * The rest clock as its own quiet instrument in the dock: a small countdown ring, the
 * REST kicker, the time, the target, and −15 / +15 / Skip.
 *
 * One card, three moods. Running is cyan and draining; the last ten seconds turn Warn
 * with the words to match; done flashes gold with "Back to the bar" for
 * [Motion.FINISHED_DWELL_MS], then the card is the same instrument at rest: dim, the
 * planned length, Start rest. The clock itself is the timer service's — this reads its
 * state and sends it commands, and never counts on its own (ADR-012).
 */
@Composable
internal fun RestTimerCard(
    remainingSeconds: Int,
    totalSeconds: Int,
    running: Boolean,
    completedTimerId: String?,
    afterWarmup: Boolean,
    offerSetClock: Boolean,
    onSkip: () -> Unit,
    onStart: () -> Unit,
    onNudge: (Int) -> Unit,
    onEditDuration: () -> Unit,
    onStartSetClock: () -> Unit,
    onOpenRest: () -> Unit,
    modifier: Modifier = Modifier,
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
    val safeTotal = totalSeconds.coerceAtLeast(0)
    val urgent = running && safeRemaining <= LAST_SECONDS
    val idle = !running && !justFinished
    val accent = when {
        justFinished -> PrGold
        urgent -> Warn
        running -> RestCyan
        else -> TextSecondary
    }
    val kicker = when {
        justFinished -> TalkBackPolicy.REST_FINISHED_KICKER
        idle && afterWarmup -> RestIdleCopy.WARMUP_KICKER
        else -> TalkBackPolicy.REST_RUNNING_KICKER
    }
    val clockSeconds = when {
        justFinished -> 0
        running -> safeRemaining
        else -> safeTotal
    }
    val clock = RestTimer.formatClock(clockSeconds)
    val targetClock = RestTimer.formatClock(safeTotal)
    val caption = when {
        justFinished -> REST_COMPLETE
        running -> "$TARGET $targetClock"
        afterWarmup -> RestIdleCopy.afterWarmupHint()
        else -> PLANNED
    }
    val spoken = when {
        justFinished -> "$kicker. $REST_COMPLETE."
        running -> buildString {
            append("Rest $clock remaining. $TARGET $targetClock.")
            if (urgent) append(" Last ten seconds.")
            append(" Open rest timer.")
        }
        else -> RestIdleCopy.dockSpoken(clock, afterWarmup)
    }
    val progress = if (justFinished) 0f else RestTimer.sweepFraction(
        remainingSeconds = if (running) safeRemaining else 0,
        totalSeconds = safeTotal,
    )
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val controls = when {
        justFinished -> emptyList()
        running -> listOf(MINUS, PLUS, SKIP)
        offerSetClock -> listOf(SetStopwatchCopy.START, START_REST)
        else -> listOf(START_REST)
    }
    // Measured once per control set and text scale, not on every one-second tick.
    val controlsWidth = remember(controls, density, measurer) {
        controls.sumOf { label ->
            maxOf(
                with(density) { Metrics.touchMin.roundToPx() },
                measurer.measure(label, style = InstrumentType.bodyStrong, softWrap = false).size.width +
                    with(density) { (Metrics.space2 * 2).roundToPx() },
            )
        } + with(density) { (Metrics.space1 * controls.size).roundToPx() }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.commit)
            .clip(RoundedCornerShape(Radius.md))
            // One step lighter than the panels around it, so resting reads as a state the
            // floor is in rather than another card on it. The segments below then recess to
            // Surface1 inside it, which is the same step read the other way.
            .background(Surface2)
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.md))
            .testTag(if (idle) WorkoutTestTags.REST_IDLE else WorkoutTestTags.REST_BAR)
            .padding(horizontal = Metrics.space3, vertical = Metrics.space2),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            itemVerticalAlignment = Alignment.CenterVertically,
            // Large text: the −15 / +15 / Skip row drops under the clock instead of squeezing it.
            maxItemsInEachRow = if (LogLoopScale.stackEntryWells(density.fontScale)) 1 else 2,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = Metrics.touchMin)
                    .heightIn(min = Metrics.touchMin)
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable(role = Role.Button, onClick = if (idle) onEditDuration else onOpenRest)
                    .semantics {
                        contentDescription = spoken
                        if (TalkBackPolicy.announceRestKicker(justFinished)) {
                            liveRegion = LiveRegionMode.Polite
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RestMiniRing(progress = progress, accent = accent, finished = justFinished)
                Column {
                    Kicker(text = kicker, color = accent, asHeading = false)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
                    ) {
                        Text(
                            clock,
                            style = InstrumentType.numeralMd,
                            color = if (idle) TextSecondary else TextPrimary,
                            maxLines = 1,
                        )
                        if (idle) {
                            Icon(
                                TemperIcons.Chevron,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(Metrics.chevron),
                            )
                        }
                    }
                    Text(
                        caption,
                        style = InstrumentType.caption,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (controls.isNotEmpty()) {
                RestSegments(
                    modifier = Modifier.widthIn(min = with(density) { controlsWidth.toDp() }),
                    segments = if (running) {
                        listOf(
                            RestSegment(
                                label = MINUS,
                                spoken = "Minus ${RestTimer.NUDGE_SECONDS} seconds",
                                tag = WorkoutTestTags.REST_MINUS,
                                onClick = { onNudge(-RestTimer.NUDGE_SECONDS) },
                            ),
                            RestSegment(
                                label = PLUS,
                                spoken = "Plus ${RestTimer.NUDGE_SECONDS} seconds",
                                tag = WorkoutTestTags.REST_PLUS,
                                onClick = { onNudge(RestTimer.NUDGE_SECONDS) },
                            ),
                            RestSegment(
                                label = SKIP,
                                spoken = null,
                                tag = WorkoutTestTags.REST_SKIP,
                                onClick = onSkip,
                                confirm = true,
                            ),
                        )
                    } else {
                        listOfNotNull(
                            RestSegment(
                                label = SetStopwatchCopy.START,
                                spoken = SetStopwatchCopy.START_SPOKEN,
                                tag = WorkoutTestTags.START_SET_CLOCK,
                                onClick = onStartSetClock,
                            ).takeIf { offerSetClock },
                            RestSegment(
                                label = START_REST,
                                spoken = RestIdleCopy.startSpoken(safeTotal),
                                tag = WorkoutTestTags.START_REST,
                                onClick = onStart,
                            ),
                        )
                    },
                )
            }
        }
    }
}

/** The card's countdown ring: a track, and the remaining share in the timer's accent. */
@Composable
private fun RestMiniRing(
    progress: Float,
    accent: Color,
    finished: Boolean,
) {
    val sweep by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = instrumentLinear(Motion.TICK_MS),
        label = "rest-mini-ring",
    )
    val sweepColor by animateColorAsState(
        targetValue = if (finished) PrGold else accent,
        animationSpec = instrumentTween(Motion.BASE),
        label = "rest-mini-ring-accent",
    )
    Canvas(modifier = Modifier.size(Metrics.restRingSmall)) {
        val strokePx = Metrics.ringStroke.toPx()
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
        if (sweep > 0f) {
            drawArc(
                color = sweepColor,
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = origin,
                size = arcSize,
                style = stroke,
            )
        }
    }
}

private const val LAST_SECONDS = 10
private val MINUS = "−${RestTimer.NUDGE_SECONDS}"
private val PLUS = "+${RestTimer.NUDGE_SECONDS}"
private const val SKIP = "Skip"
private const val START_REST = "Start rest"
private const val TARGET = "Target"
private const val PLANNED = "Planned"
private const val REST_COMPLETE = "Rest complete"
