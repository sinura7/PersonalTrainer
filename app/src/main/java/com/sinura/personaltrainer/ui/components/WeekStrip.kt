package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.DayFill
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.WeekBoard
import com.sinura.personaltrainer.domain.WeekBoardCell
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * Shared day selector. Cells retain their full touch area and scroll on narrow displays.
 *
 * Shared by Plan and Home. Captions come from occurrence [WeekBoardCell]s,
 * never leftover slot-week routine names. Today is the 3 dp Volt bar.
 * Selection has an outline and pressed fill; status is an explicit word.
 */
@Composable
fun WeekStrip(
    cells: List<WeekBoardCell>,
    today: Long,
    selected: Long,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
    proposals: Map<Long, SuggestedTrainingDay> = emptyMap(),
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cellWidth = maxOf(
            Metrics.touchMin * density.fontScale.coerceAtLeast(1f),
            (maxWidth - Metrics.space1 * (cells.size - 1).coerceAtLeast(0)) /
                cells.size.coerceAtLeast(1),
        )
        val stepPx = with(density) { (cellWidth + Metrics.space1).roundToPx() }
        val viewportPx = with(density) { maxWidth.roundToPx() }
        val index = cells.indexOfFirst { it.epochDay == selected }
        LaunchedEffect(selected, index, stepPx, viewportPx, scroll.maxValue) {
            if (index >= 0) {
                val start = index * stepPx
                val end = start + with(density) { cellWidth.roundToPx() }
                when {
                    start < scroll.value -> scroll.scrollTo(start)
                    end > scroll.value + viewportPx -> scroll.scrollTo(end - viewportPx)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WeekStripTags.STRIP)
                .horizontalScroll(scroll)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            cells.forEach { cell ->
                WeekCell(
                    cell = cell,
                    proposal = proposals[cell.epochDay],
                    isToday = cell.epochDay == today,
                    selected = cell.epochDay == selected,
                    spoken = WeekBoard.spoken(cell, today, selected, proposals[cell.epochDay]),
                    onClick = { onSelectDay(cell.epochDay) },
                    modifier = Modifier.width(cellWidth),
                )
            }
        }
    }
}

@Composable
private fun WeekCell(
    cell: WeekBoardCell,
    proposal: SuggestedTrainingDay?,
    isToday: Boolean,
    selected: Boolean,
    spoken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayOfMonth = remember(cell.epochDay) {
        CivilDate.fromEpochDay(cell.epochDay).dayOfMonth
    }
    val preview = cell.fill == DayFill.EMPTY && proposal != null && !proposal.isRest
    val label = when {
        preview && cell.recordedCount == 0 -> "Draft"
        else -> WeekBoard.statusLabel(cell)
    }
    val labelColor = when {
        selected -> TextPrimary
        preview -> TextTertiary
        cell.missedCount > 0 -> Danger
        else -> TextSecondary
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (selected) SurfacePressed else Surface2)
            .border(
                Metrics.hairline,
                if (selected) HairlineStrong else Color.Transparent,
                RoundedCornerShape(Radius.sm),
            )
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .heightIn(min = Metrics.touchMin)
            .padding(vertical = Metrics.space1)
            .testTag(WeekStripTags.cell(cell.epochDay))
            .semantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        Box(
            modifier = Modifier
                .size(width = TODAY_MARKER_WIDTH, height = TODAY_MARKER_HEIGHT)
                .background(
                    when {
                        isToday -> Volt
                        else -> Color.Transparent
                    },
                ),
        )
        Kicker(
            cell.weekday.shortLabel(),
            color = when {
                isToday -> Volt
                selected -> TextPrimary
                else -> TextSecondary
            },
            asHeading = false,
        )
        Text(
            dayOfMonth.toString(),
            style = InstrumentType.numeralSm,
            color = TextPrimary,
        )
        Text(
            label,
            style = InstrumentType.caption,
            color = labelColor,
        )
    }
}

object WeekStripTags {
    const val STRIP = "week-strip"
    fun cell(epochDay: Long): String = "week-cell-$epochDay"
}

private val TODAY_MARKER_WIDTH = 16.dp
private val TODAY_MARKER_HEIGHT = 3.dp
