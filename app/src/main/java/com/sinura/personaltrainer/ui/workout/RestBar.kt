package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Warn

/** Pure presentation for [RestBar], kept apart so the overtime and spoken rules are unit-testable. */
internal object RestBarCopy {
    /** Spoken text changes once per 15 s bucket (and at 0:00), so a polite live region is not chatty. */
    fun spokenBucketSeconds(remainingSeconds: Int): Int {
        val left = remainingSeconds.coerceAtLeast(0)
        return if (left == 0) 0 else ((left + 14) / 15) * 15
    }

    fun overtime(running: Boolean, remainingSeconds: Int): Boolean = running && remainingSeconds <= 0

    fun fraction(remainingSeconds: Int, totalSeconds: Int, overtime: Boolean): Float = when {
        overtime -> 1f
        totalSeconds <= 0 -> 0f
        else -> (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
    }
}

/**
 * v4 blueprint 3.4: the inline rest bar. Lives in the live LazyColumn directly above the next-set
 * entry, so the next set stays visible and editable while rest counts down (D5).
 *
 * [completed] shows "Rest done"; the caller collapses the bar after 3 s. Overtime counts up in Warn.
 * Tapping the clock opens the existing rest duration sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RestBar(
    remainingSeconds: Int,
    totalSeconds: Int,
    running: Boolean,
    overtime: Boolean,
    onNudge: (deltaSeconds: Int) -> Unit,
    onSkip: () -> Unit,
    onEditDuration: () -> Unit,
    modifier: Modifier = Modifier,
    completed: Boolean = false,
) {
    val accent = if (overtime) Warn else RestCyan
    val clock = when {
        completed -> stringResource(R.string.rest_done)
        overtime -> stringResource(R.string.rest_overtime, RestTimer.formatClock(-remainingSeconds))
        else -> RestTimer.formatClock(remainingSeconds.coerceAtLeast(0))
    }
    val bucket = RestBarCopy.spokenBucketSeconds(remainingSeconds)
    val spoken = if (completed) {
        stringResource(R.string.rest_done)
    } else {
        stringResource(R.string.rest_spoken, bucket / 60, bucket % 60)
    }
    val fraction = if (completed) 0f else RestBarCopy.fraction(remainingSeconds, totalSeconds, overtime)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.restBar)
            .background(Surface2)
            .drawBehind {
                drawRect(color = accent, size = Size(Metrics.emphasisBorder.toPx(), size.height))
            }
            .testTag(WorkoutTestTags.REST_BAR)
            .padding(horizontal = Metrics.space4, vertical = Metrics.space2),
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.rest_kicker),
                style = InstrumentType.kicker,
                color = accent,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            Box(
                modifier = Modifier
                    .heightIn(min = Metrics.touchMin)
                    .clickable(role = Role.Button, onClick = onEditDuration)
                    .testTag(WorkoutTestTags.REST_CLOCK)
                    .semantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = clock,
                    style = InstrumentType.numeralMd,
                    color = if (overtime) Warn else TextPrimary,
                    softWrap = false,
                )
            }
            Box(modifier = Modifier.weight(1f))
            if (running) {
                RestBarButton(
                    label = stringResource(R.string.rest_minus),
                    tag = WorkoutTestTags.REST_MINUS,
                    onClick = { onNudge(-15) },
                )
                RestBarButton(
                    label = stringResource(R.string.rest_plus),
                    tag = WorkoutTestTags.REST_PLUS,
                    onClick = { onNudge(15) },
                )
                RestBarButton(
                    label = stringResource(R.string.rest_skip),
                    tag = WorkoutTestTags.REST_SKIP,
                    onClick = onSkip,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Metrics.restBarProgress)
                .background(Surface3)
                .testTag(WorkoutTestTags.REST_PROGRESS)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(current = fraction, range = 0f..1f) },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(Metrics.restBarProgress)
                    .background(accent),
            )
        }
    }
}

@Composable
private fun RestBarButton(label: String, tag: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = Metrics.touchMin)
            .widthIn(min = Metrics.touchMin)
            .testTag(tag),
    ) {
        Text(text = label, style = InstrumentType.bodyStrong, color = TextSecondary)
    }
}
