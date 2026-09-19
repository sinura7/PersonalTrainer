package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import com.sinura.personaltrainer.ui.theme.Warn

/**
 * Seven days, side by side, never scrolling.
 *
 * Shared by Plan and Home. Captions come from occurrence [WeekBoardCell]s,
 * never leftover slot-week routine names. Today is the 3 dp Volt bar.
 * Selected (when it is not today) is a hairline bar plus a pressed fill —
 * not a second Volt. Fill colour is rest / none / some / all, not brand green.
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WeekStripTags.STRIP)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        cells.forEach { cell ->
            WeekCell(
                cell = cell,
                proposal = proposals[cell.epochDay],
                isToday = cell.epochDay == today,
                selected = cell.epochDay == selected,
                spoken = WeekBoard.spoken(cell, today, selected),
                onClick = { onSelectDay(cell.epochDay) },
                modifier = Modifier.weight(1f),
            )
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
        cell.fill != DayFill.EMPTY -> cell.caption
        preview -> proposal?.focusTitle ?: WeekBoard.REST
        else -> WeekBoard.REST
    }
    val labelColor = when {
        selected -> TextPrimary
        preview -> TextTertiary
        cell.fill == DayFill.NONE -> Danger
        cell.fill == DayFill.PARTIAL -> Warn
        cell.fill == DayFill.EMPTY -> TextSecondary
        else -> TextSecondary
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (selected) SurfacePressed else Surface2)
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
                        selected -> HairlineStrong
                        else -> Color.Transparent
                    },
                ),
        )
        Kicker(
            cell.weekday.shortLabel().take(1),
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (cell.fill == DayFill.ALL) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(LOGGED_TICK),
            )
        } else {
            Box(modifier = Modifier.size(LOGGED_TICK))
        }
    }
}

object WeekStripTags {
    const val STRIP = "week-strip"
    fun cell(epochDay: Long): String = "week-cell-$epochDay"
}

private val TODAY_MARKER_WIDTH = 16.dp
private val TODAY_MARKER_HEIGHT = 3.dp
private val LOGGED_TICK = 12.dp
