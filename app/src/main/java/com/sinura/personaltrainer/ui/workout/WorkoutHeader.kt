package com.sinura.personaltrainer.ui.workout


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.EndWorkoutCopy
import com.sinura.personaltrainer.domain.SessionTelemetryCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.util.QuantityFormat
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Packet C top strip: Close / title / Finish, then minute telemetry.
 *
 * Row A is session chrome. Row B is `18 min · 7 sets · 2,340 kg` and
 * updates once per minute. Rest / hold / stopwatch numerals stay in the
 * dock — this strip is not a second clock.
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
    onDiscard: () -> Unit = {},
    showDiscard: Boolean = false,
    onOpenTimer: () -> Unit = {},
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(startedAt) {
        if (startedAt == null) return@LaunchedEffect
        while (true) {
            val elapsed = ((System.currentTimeMillis() - startedAt) / 1_000L)
                .coerceAtLeast(0L)
                .toInt()
            elapsedSeconds = elapsed
            val intoMinute = elapsed % 60
            delay(((60 - intoMinute).coerceAtLeast(1)) * 1_000L)
        }
    }

    val fontScale = LocalDensity.current.fontScale
    val widthDp = LocalWindowInfo.current.containerDpSize.width.value.roundToInt()
    val includeVolume = !compact && SessionTelemetryCopy.includeVolume(fontScale, widthDp)
    val volume = if (work.volumeKg > 0.0) {
        QuantityFormat.formatVolumeLabel(work.volumeKg, unit)
    } else {
        null
    }
    val telemetry = SessionTelemetryCopy.line(
        elapsedSeconds = elapsedSeconds,
        workingSets = workingSets,
        volume = volume,
        includeVolume = includeVolume,
    )
    val spoken = SessionTelemetryCopy.spoken(
        elapsedSeconds = elapsedSeconds,
        workingSets = workingSets,
        volume = volume,
        includeVolume = includeVolume,
    )
    val telemetryMin = if (fontScale >= 2f) Metrics.touchMin else Metrics.space8
    val titleLines = if (fontScale >= 2f) 2 else 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(start = Metrics.space2, end = Metrics.space2, bottom = Metrics.space2),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        ScreenHeader(
            title = routineName,
            onBack = onExit,
            backIcon = Icons.Outlined.Close,
            backDescription = "Exit workout",
            paintBackground = false,
            titleMaxLines = titleLines,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.heightIn(min = Metrics.control),
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
            },
        )
        Text(
            telemetry,
            style = InstrumentType.bodyStrong,
            color = TextSecondary,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = telemetryMin)
                .padding(start = Metrics.space2, end = Metrics.space2)
                .testTag(WorkoutTestTags.INSTRUMENT_STRIP)
                .clickable(role = Role.Button, onClick = onOpenTimer)
                .semantics { contentDescription = spoken },
        )
    }
}
