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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.ProgressSegment
import com.sinura.personaltrainer.domain.ProgressSegmentState
import com.sinura.personaltrainer.domain.WorkoutProgress
import com.sinura.personaltrainer.domain.WorkoutProgressCalculator
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim

/**
 * Session chrome: back, the routine's name, where the session stands, and overflow.
 *
 * Finish lives in [LiftOverflowMenu] (D3); the dock owns Finish as the primary after the last
 * planned set. Landscape gets the one-row compact form: the plan's words are the title.
 */
@Composable
internal fun WorkoutHeader(
    routineName: String,
    progress: WorkoutProgress,
    compact: Boolean,
    onExit: () -> Unit,
    overflow: (@Composable () -> Unit)? = null,
    focused: Boolean = false,
) {
    val largeText = LogLoopScale.stackEntryWells(LocalDensity.current.fontScale)
    val titleLines = if (compact || largeText) 1 else 2
    val headline = WorkoutProgressCalculator.headline(progress)
    val spoken = WorkoutProgressCalculator.spoken(progress)
    val planAsTitle = compact && headline.isNotBlank()
    fun progressLine(spokenForm: String): Modifier = Modifier
        .testTag(WorkoutTestTags.PROGRESS_LINE)
        .semantics { contentDescription = spokenForm }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .then(if (compact) Modifier else Modifier.padding(bottom = Metrics.space2)),
    ) {
        ScreenHeader(
            title = if (planAsTitle) headline else routineName,
            onBack = onExit,
            backIcon = TemperIcons.Back,
            backDescription = "Close",
            paintBackground = true,
            titleStyle = when {
                compact -> InstrumentType.title
                focused -> InstrumentType.workoutTitle
                else -> InstrumentType.display
            },
            titleMaxLines = titleLines,
            titleModifier = if (planAsTitle) progressLine("$routineName. $spoken") else Modifier,
            subtitle = headline.takeIf { focused && !compact && !largeText && it.isNotBlank() },
            subtitleModifier = progressLine(spoken),
            contentPadding = PaddingValues(start = Metrics.space2, end = Metrics.space2),
            modifier = Modifier
                .fillMaxWidth()
                .height(Metrics.control),
            trailing = { overflow?.invoke() },
        )
        if (headline.isNotBlank() && !planAsTitle) {
            if (focused && !largeText) {
                WorkoutProgressBar(segments = progress.segments,
                    modifier = Modifier.padding(start = Metrics.space2 + Metrics.touchMin, end = Metrics.gutter))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (focused) Metrics.gutter else Metrics.space2 + Metrics.touchMin, end = Metrics.gutter),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    Text(
                        if (focused) headline else headline.uppercase(),
                        modifier = progressLine(spoken),
                        style = if (focused) InstrumentType.caption else InstrumentType.kicker,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    WorkoutProgressBar(segments = progress.segments)
                }
            }
        }
    }
}

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
