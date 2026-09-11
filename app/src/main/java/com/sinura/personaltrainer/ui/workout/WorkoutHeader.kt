package com.sinura.personaltrainer.ui.workout


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * The session's own chrome, carrying the session's own telemetry.
 *
 * This used to be a stock app bar spending its entire width on a routine name and a close
 * icon: the most data-driven screen in the product had less live information in its header
 * than a notes app, and the duration of a workout was computed for the first time only
 * after it had ended.
 *
 * Finish moved up here too. It was the last item of the scrolling content, so ending a
 * session meant scrolling to the bottom of a layout designed for mid-set logging — and then
 * confirming a dialog whose own body text admitted nothing was at stake. Finishing is safe,
 * non-destructive, and followed immediately by a summary that *is* the confirmation, so it
 * now happens on one tap.
 */
@Composable
internal fun WorkoutHeader(
    routineName: String,
    startedAt: Long?,
    workingSets: Int,
    work: SetWork,
    unit: WeightUnit,
    canFinish: Boolean,
    compact: Boolean,
    onExit: () -> Unit,
    onFinish: () -> Unit,
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(startedAt) {
        if (startedAt == null) return@LaunchedEffect
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1_000L)
                .coerceAtLeast(0L)
                .toInt()
            delay(1_000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(start = Metrics.space2, end = Metrics.space4, bottom = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        ScreenHeader(
            title = routineName,
            onBack = onExit,
            backIcon = Icons.Outlined.Close,
            backDescription = "Exit workout",
            paintBackground = false,
            contentPadding = PaddingValues(0.dp),
            trailing = {
                TextButton(
                    onClick = onFinish,
                    enabled = canFinish,
                    modifier = Modifier.testTag(WorkoutTestTags.FINISH),
                ) {
                    Text(
                        "Finish",
                        style = InstrumentType.bodyStrong,
                        color = if (canFinish) TextPrimary else TextTertiary,
                    )
                }
            },
        )
        if (!compact && !canFinish) {
            Text(
                "Log a set to finish.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = Metrics.space4),
                style = InstrumentType.caption,
                color = TextTertiary,
                textAlign = TextAlign.End,
            )
        }
        if (!compact) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Metrics.space2),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            MetricCluster(
                value = RestTimer.formatClock(elapsedSeconds),
                label = "elapsed",
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
            )
            MetricCluster(
                value = workingSets.toString(),
                label = "sets",
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
            )
            val column = SetCopy.workColumn(work, unit)
            MetricCluster(
                value = column.value,
                label = column.label,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
            )
        }
        }
    }
}
