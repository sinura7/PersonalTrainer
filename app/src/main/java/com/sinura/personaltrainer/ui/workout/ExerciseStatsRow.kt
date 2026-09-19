package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalDensity
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
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
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
    // The measure and its label are the domain's (the same column History shows); only the
    // thousands grouping is this platform's.
    val column = stats.volumeColumn(unit)
    val nothingYet = column.value == SetCopy.NOTHING_YET
    val number = if (stats.work.volumeKg > 0.0) QuantityFormat.formatVolumeNumber(stats.work.volumeKg, unit) else column.value
    val volumeValue = if (nothingYet) SetCopy.NOTHING_YET else "$number ${column.label}"
    val volumeSpoken = if (nothingYet) "Volume this exercise, nothing yet" else "Volume this exercise, $volumeValue"
    val lastTime = stats.lastSet.applies
    val onLast: (() -> Unit)? = if (lastTime != null && onApplyLastSet != null) {
        { onApplyLastSet(lastTime.weightKg, lastTime.reps) }
    } else {
        null
    }
    val volume = FloorStat(
        label = FloorStatCopy.VOLUME,
        value = volumeValue,
        detail = FloorStatCopy.VOLUME_DETAIL,
        spoken = volumeSpoken,
    )
    if (LogLoopScale.stackEntryWells(LocalDensity.current.fontScale)) {
        // Large text: three full-width rows instead of three narrow columns.
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag(WorkoutTestTags.STATS_ROW),
        ) {
            StatCell(stat = stats.lastSet, tag = WorkoutTestTags.STAT_LAST, modifier = Modifier.fillMaxWidth(), onClick = onLast)
            HairlineDivider(startIndent = Metrics.space2)
            StatCell(stat = stats.bestSet, tag = WorkoutTestTags.STAT_BEST, modifier = Modifier.fillMaxWidth())
            HairlineDivider(startIndent = Metrics.space2)
            StatCell(stat = volume, tag = WorkoutTestTags.STAT_VOLUME, modifier = Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .testTag(WorkoutTestTags.STATS_ROW),
        ) {
            StatCell(stat = stats.lastSet, tag = WorkoutTestTags.STAT_LAST, modifier = Modifier.weight(1f), onClick = onLast)
            CellRule()
            StatCell(stat = stats.bestSet, tag = WorkoutTestTags.STAT_BEST, modifier = Modifier.weight(1f))
            CellRule()
            StatCell(stat = volume, tag = WorkoutTestTags.STAT_VOLUME, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(
    stat: FloorStat,
    tag: String,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
) {
    val view = LocalView.current
    Column(
        modifier = modifier
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
        // The qualifier rides the label rather than a third line of its own. `Warm-up`,
        // `RPE 9`, `Last time` and `Today` all still appear, and under the same rules; they
        // simply sit next to the word they qualify instead of under the number. A 110 dp
        // cell could not hold three stacked lines without the labels folding back anyway.
        Text(
            stat.detail?.let { detail -> stat.label + FloorStatCopy.DETAIL_JOIN + detail } ?: stat.label,
            style = InstrumentType.caption,
            color = TextSecondary,
            // Two lines, always. `Best set · Est. 1RM` does not fit a 110 dp cell on one
            // line and `Last set · RPE 9` does, and a label that is sometimes one line and
            // sometimes two drops that cell's number below its neighbours' — three numbers
            // meant to be read across stop being a row at all.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // The value may wrap once rather than lose its reps or effort to an ellipsis.
        Text(
            stat.value,
            style = InstrumentType.numeralSm,
            color = TextPrimary,
            maxLines = 2,
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
