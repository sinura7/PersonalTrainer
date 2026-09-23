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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sinura.personaltrainer.domain.ExerciseFloorStats
import com.sinura.personaltrainer.domain.ExerciseFloorStatsPresentation
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
 *
 * [visibility] picks the cells. The floor shows Last alone before the first working set of
 * the lift, and at large text always (ADR-030); the lift's Details shows the other two.
 */
@Composable
internal fun ExerciseStatsRow(
    stats: ExerciseFloorStats,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
    visibility: ExerciseFloorStatsPresentation.RowVisibility = ExerciseFloorStatsPresentation.RowVisibility.FULL,
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
    val cells = buildList {
        if (visibility.showLast) add(StatCellSpec(stat = stats.lastSet, tag = WorkoutTestTags.STAT_LAST, onClick = onLast))
        if (visibility.showBest) add(StatCellSpec(stat = stats.bestSet, tag = WorkoutTestTags.STAT_BEST, onClick = null))
        if (visibility.showVolume) add(StatCellSpec(stat = volume, tag = WorkoutTestTags.STAT_VOLUME, onClick = null))
    }
    // One line of the value at its full size. Every cell keeps it, whatever size its own value
    // is drawn at, so a value that steps down to fit never shortens the row either.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val valueLine = remember(density) {
        with(density) { measurer.measure("0", style = InstrumentType.numeralSm).size.height.toDp() }
    }
    // Until the first working set of a lift is logged only the Last cell shows (ADR-030), and it
    // shows in the same Row or Column as the full row. Side by side it keeps the full row's two
    // label lines, so Best and Volume arriving with that set do not move the entry.
    val stacked = LogLoopScale.stackEntryWells(density.fontScale)
    if (stacked) {
        // Large text: full-width rows instead of narrow columns.
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag(WorkoutTestTags.STATS_ROW),
        ) {
            cells.forEachIndexed { index, cell ->
                if (index > 0) HairlineDivider(startIndent = Metrics.space2)
                StatCell(cell = cell, modifier = Modifier.fillMaxWidth(), alignAcrossCells = false, valueLine = valueLine)
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .testTag(WorkoutTestTags.STATS_ROW),
        ) {
            cells.forEachIndexed { index, cell ->
                if (index > 0) CellRule()
                StatCell(cell = cell, modifier = Modifier.weight(1f), alignAcrossCells = true, valueLine = valueLine)
            }
        }
    }
}

private class StatCellSpec(
    val stat: FloorStat,
    val tag: String,
    val onClick: (() -> Unit)?,
)

@Composable
private fun StatCell(
    cell: StatCellSpec,
    modifier: Modifier,
    /**
     * Hold the label at two lines so the three numbers share a baseline.
     *
     * Only side-by-side sizes need it. There the Last cell reserves it even while it stands
     * alone in the Row before the first working set, so the row is already its full height and
     * that set does not move the entry (FRONTEND_REDESIGN.md, "Ordinary logging retains entry
     * position"). Stacked, each cell is a full-width row of its own with nothing to line up
     * against, and reserving the second line would leave a blank one above every number at
     * exactly the text size that can least afford it.
     */
    alignAcrossCells: Boolean,
    /** One line of the value at its full size: the height the value keeps, drawn at any size. */
    valueLine: Dp,
) {
    val stat = cell.stat
    val onClick = cell.onClick
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
            .testTag(cell.tag)
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
            // `Best set · Est. 1RM` does not fit a 110 dp cell on one line and
            // `Last set · RPE 9` does, and a label that is sometimes one line and sometimes
            // two drops that cell's number below its neighbours' — three numbers meant to be
            // read across stop being a row at all.
            minLines = if (alignAcrossCells) 2 else 1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // The value stays one line: one too wide for its cell is drawn smaller rather than
        // wrapped (owner decision of 23 September 2026), so the first working set bringing a
        // long Best or Volume — `102.5 × 10` at font 1.3 — no longer adds a line and moves the
        // entry. It keeps the full-size line whatever size it is drawn at, on the full-size
        // baseline so the numbers still read across, and never goes below its own label's
        // size. No ellipsis: this is a number. What TalkBack reads is the cell's, unchanged.
        Box(
            modifier = Modifier.heightIn(min = valueLine),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                stat.value,
                // Laid out at the floor for its intrinsic height: the row reads its height from
                // intrinsics, which never see the autosized size, and a value that must wrap at
                // the floor wraps there too, so both agree on the line count.
                style = LogLoopScale.statValueFloor,
                color = TextPrimary,
                autoSize = StatValueFit,
                maxLines = 2,
            )
        }
    }
}

/**
 * The largest size, from the value's own [InstrumentType.numeralSm] down to
 * [LogLoopScale.statValueFloor], at which a stats value sits on one line. A value too long even
 * at the floor — a four-digit load with a half, `10 reps +45` on a 360 dp phone at font 1.5 — is
 * laid out at the floor and wraps, rather than clip or ellipsise a number.
 */
private data object StatValueFit : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val floor = LogLoopScale.statValueFloor.fontSize.value
        val step = LogLoopScale.STAT_VALUE_STEP.value
        var size = InstrumentType.numeralSm.fontSize.value
        while (size > floor) {
            val layout = performLayout(constraints, text, size.sp)
            if (layout.lineCount == 1 && !layout.didOverflowWidth && !layout.didOverflowHeight) return size.sp
            size -= step
        }
        return floor.sp
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
