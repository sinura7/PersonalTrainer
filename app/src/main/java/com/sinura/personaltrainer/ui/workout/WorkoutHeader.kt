package com.sinura.personaltrainer.ui.workout


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.EndWorkoutCopy
import com.sinura.personaltrainer.domain.ProgressSegment
import com.sinura.personaltrainer.domain.ProgressSegmentState
import com.sinura.personaltrainer.domain.WorkoutProgress
import com.sinura.personaltrainer.domain.WorkoutProgressCalculator
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim

/**
 * Session chrome: back, the routine's name, where the session stands, and Finish.
 *
 * The second line and the segmented bar are the same numbers said twice — once as
 * words, once as shape — so a lifter walking back to the phone sees how much of the
 * day is left before reading anything. Session telemetry (elapsed time, tonnage) stays
 * behind the overflow's Session summary; the header is about the plan, not the clock.
 */
@Composable
internal fun WorkoutHeader(
    routineName: String,
    progress: WorkoutProgress,
    canFinish: Boolean,
    compact: Boolean,
    onExit: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit = {},
    showDiscard: Boolean = false,
    overflow: (@Composable () -> Unit)? = null,
) {
    // Two lines at display size so a long routine name wraps instead of losing its end;
    // the compact landscape header keeps to one.
    val titleLines = if (compact) 1 else 2
    val headline = WorkoutProgressCalculator.headline(progress)
    val spoken = WorkoutProgressCalculator.spoken(progress)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(bottom = Metrics.space2),
    ) {
        ScreenHeader(
            title = routineName,
            onBack = onExit,
            backIcon = TemperIcons.Back,
            backDescription = "Exit workout",
            paintBackground = true,
            titleStyle = if (compact) InstrumentType.title else InstrumentType.display,
            titleMaxLines = titleLines,
            subtitle = null,
            contentPadding = PaddingValues(start = Metrics.space2, end = Metrics.space2),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.control),
            trailing = {
                if (showDiscard) {
                    TextButton(
                        onClick = onDiscard,
                        modifier = Modifier
                            .widthIn(min = Metrics.headerActMin)
                            .heightIn(min = Metrics.touchMin)
                            .testTag(WorkoutTestTags.DISCARD),
                    ) {
                        Text(
                            EndWorkoutCopy.HEADER_DISCARD,
                            style = InstrumentType.bodyStrong,
                            color = TextPrimary,
                        )
                    }
                } else {
                    TextButton(
                        onClick = onFinish,
                        enabled = canFinish,
                        modifier = Modifier
                            .widthIn(min = Metrics.headerActMin)
                            .heightIn(min = Metrics.touchMin)
                            .testTag(WorkoutTestTags.FINISH)
                            .semantics {
                                if (!canFinish) {
                                    disabled()
                                    contentDescription = EndWorkoutCopy.LOG_FIRST
                                }
                            },
                    ) {
                        Text(
                            EndWorkoutCopy.HEADER_FINISH,
                            style = InstrumentType.bodyStrong,
                            color = if (canFinish) TextPrimary else TextTertiary,
                        )
                    }
                }
                overflow?.invoke()
            },
        )
        if (headline.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Aligned with the title, which sits after the 48 dp back control.
                    .padding(start = Metrics.space2 + Metrics.touchMin, end = Metrics.gutter),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                Text(
                    headline,
                    modifier = Modifier
                        .testTag(WorkoutTestTags.PROGRESS_LINE)
                        .semantics { contentDescription = spoken },
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = titleLines,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact) WorkoutProgressBar(segments = progress.segments)
            }
        }
    }
}

/**
 * One segment per lift: done lifts are Volt, the current lift fills as its sets land
 * on a dim Volt track, and lifts still to come stay graphite. Decorative — the line
 * above already says it in words, and reading it twice would be noise for TalkBack.
 */
@Composable
internal fun WorkoutProgressBar(
    segments: List<ProgressSegment>,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Metrics.progressTrack)
            .testTag(WorkoutTestTags.PROGRESS_BAR)
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        segments.forEach { segment ->
            val track = when (segment.state) {
                ProgressSegmentState.DONE -> Volt
                ProgressSegmentState.CURRENT -> VoltDim
                ProgressSegmentState.UPCOMING -> Surface3
            }
            val fill = when (segment.state) {
                ProgressSegmentState.DONE -> 1f
                ProgressSegmentState.CURRENT -> segment.fraction.coerceIn(0f, 1f)
                ProgressSegmentState.UPCOMING -> 0f
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(Radius.full)
                    .background(track),
            ) {
                if (fill > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fill)
                            .fillMaxHeight()
                            .background(Volt),
                    )
                }
            }
        }
    }
}
