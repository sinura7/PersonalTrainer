package com.sinura.personaltrainer.ui.workout


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.FloorTimerSurface
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * Read-only session instrument strip (Packet 2).
 *
 * Elapsed · sets · volume plus a quiet rest/hold state. The whole strip
 * is one tap target that opens the rest timer page. Start / Skip / rest
 * length / Log set live in [LogBar] — nothing here is a control except
 * Finish / Exit on the session chrome row (not mid-set acts).
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
    onOpenTimer: () -> Unit = {},
    restRunning: Boolean = false,
    restRemainingSeconds: Int = 0,
    plannedRestSeconds: Int = 0,
    holdRunning: Boolean = false,
    holdElapsedSeconds: Int = 0,
    stopwatchRunning: Boolean = false,
    stopwatchElapsedSeconds: Int = 0,
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

    val timerState = FloorTimerSurface.instrumentState(
        holdRunning = holdRunning,
        holdElapsedSeconds = holdElapsedSeconds,
        restRunning = restRunning,
        restRemainingSeconds = restRemainingSeconds,
        plannedRestSeconds = plannedRestSeconds,
        stopwatchRunning = stopwatchRunning,
        stopwatchElapsedSeconds = stopwatchElapsedSeconds,
    )
    val timerLive = holdRunning || restRunning || stopwatchRunning

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.touchMin)
                .padding(start = Metrics.space2)
                .testTag(WorkoutTestTags.INSTRUMENT_STRIP)
                .clickable(role = Role.Button, onClick = onOpenTimer)
                .semantics {
                    contentDescription =
                        "Session instruments. $timerState. Open rest timer."
                },
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (compact) {
                Text(
                    RestTimer.formatClock(elapsedSeconds),
                    style = InstrumentType.numeralSm,
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    timerState,
                    style = InstrumentType.bodyStrong,
                    color = if (timerLive) RestCyan else TextSecondary,
                    maxLines = 1,
                )
            } else {
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
                MetricCluster(
                    value = timerState.substringAfter(' ', timerState),
                    label = when {
                        holdRunning -> FloorTimerSurface.SET_STATE
                        stopwatchRunning -> FloorTimerSurface.SET_STATE
                        else -> FloorTimerSurface.REST_STATE
                    },
                    horizontalAlignment = Alignment.Start,
                    valueColor = if (timerLive) RestCyan else TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
