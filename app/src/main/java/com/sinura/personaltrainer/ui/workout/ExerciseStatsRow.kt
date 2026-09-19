package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.ExerciseFloorStats
import com.sinura.personaltrainer.domain.FloorStat
import com.sinura.personaltrainer.domain.FloorStatCopy
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.util.QuantityFormat

/**
 * Last set · Best set · Volume, in three equal cells split by hairlines.
 *
 * Everything here is read from the same saved rows the history screens read, so the
 * three numbers cannot disagree with the sets under them. No card around it: the
 * hairlines and the alignment are the grouping.
 */
@Composable
internal fun ExerciseStatsRow(
    stats: ExerciseFloorStats,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
    /** Copies last time's set into the entry when the Last set cell is showing one. Never logs. */
    onApplyLastSet: ((weightKg: Double, reps: Int) -> Unit)? = null,
) {
    val volumeValue: String
    val volumeSpoken: String
    when {
        stats.work.volumeKg > 0.0 -> {
            volumeValue = "${QuantityFormat.formatVolumeNumber(stats.work.volumeKg, unit)} ${unit.suffix}"
            volumeSpoken = "Volume this exercise, $volumeValue"
        }
        stats.work.bodyweightReps > 0 -> {
            volumeValue = "${stats.work.bodyweightReps} reps"
            volumeSpoken = "Volume this exercise, $volumeValue"
        }
        else -> {
            volumeValue = SetCopy.NOTHING_YET
            volumeSpoken = "Volume this exercise, nothing yet"
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag(WorkoutTestTags.STATS_ROW),
    ) {
        val lastTime = stats.lastSet.applies
        StatCell(
            stat = stats.lastSet,
            tag = WorkoutTestTags.STAT_LAST,
            onClick = if (lastTime != null && onApplyLastSet != null) {
                { onApplyLastSet(lastTime.weightKg, lastTime.reps) }
            } else {
                null
            },
        )
        CellRule()
        StatCell(stat = stats.bestSet, tag = WorkoutTestTags.STAT_BEST)
        CellRule()
        StatCell(
            stat = FloorStat(
                label = FloorStatCopy.VOLUME,
                value = volumeValue,
                detail = FloorStatCopy.VOLUME_DETAIL,
                spoken = volumeSpoken,
            ),
            tag = WorkoutTestTags.STAT_VOLUME,
        )
    }
}

@Composable
private fun RowScope.StatCell(stat: FloorStat, tag: String, onClick: (() -> Unit)? = null) {
    val view = LocalView.current
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(Radius.sm))
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = USE_LAST_TIME) {
                        Haptics.tick(view)
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(vertical = Metrics.space3, horizontal = Metrics.space2)
            .testTag(tag)
            .semantics(mergeDescendants = true) { contentDescription = stat.spoken },
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Text(stat.label, style = InstrumentType.caption, color = TextSecondary, maxLines = 1)
            stat.detail?.let { detail ->
                Text(
                    detail,
                    style = InstrumentType.caption,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            stat.value,
            style = InstrumentType.numeralSm,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CellRule() {
    Box(
        modifier = Modifier
            .width(Metrics.hairline)
            .fillMaxHeight()
            .padding(vertical = Metrics.space2)
            .background(Hairline),
    )
}

private const val USE_LAST_TIME = "Use last time"
