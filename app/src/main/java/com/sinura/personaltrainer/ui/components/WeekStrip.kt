package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import java.time.LocalDate

/**
 * Seven days, side by side, never scrolling.
 *
 * Shared by Plan and Home so the week is one thing rendered twice, not two things that agree
 * by convention. A Home-only variant would have drifted the moment either screen changed.
 *
 * A week you have to scroll is not a week you can see. Each cell is the same width and carries
 * the same five things in the same order, so the row reads down a shared baseline: today's
 * marker, the day letter, the date, what is on it, and whether it happened.
 */
@Composable
fun WeekStrip(
    days: List<SuggestedTrainingDay>,
    proposals: Map<Long, SuggestedTrainingDay>,
    loggedEpochDays: Set<Long>,
    today: Long,
    onOpenDay: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        days.forEach { day ->
            WeekCell(
                day = day,
                proposal = proposals[day.epochDay],
                isToday = day.epochDay == today,
                logged = day.epochDay in loggedEpochDays,
                onClick = { onOpenDay(day.epochDay) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekCell(
    day: SuggestedTrainingDay,
    proposal: SuggestedTrainingDay?,
    isToday: Boolean,
    logged: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayOfMonth = remember(day.epochDay) { LocalDate.ofEpochDay(day.epochDay).dayOfMonth }
    val pinned = !day.isRest
    val label = when {
        pinned -> day.routineName ?: day.focusTitle
        proposal != null -> proposal.routineName ?: proposal.focusTitle
        else -> "Rest"
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .heightIn(min = Metrics.touchMin)
            .padding(vertical = Metrics.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        // The same 3dp accent rule the nav bar uses for the selected tab, so "here" means the
        // same thing in both places. It is the strip's only accent.
        Box(
            modifier = Modifier
                .size(width = TODAY_MARKER_WIDTH, height = TODAY_MARKER_HEIGHT)
                .background(if (isToday) Volt else Color.Transparent),
        )
        Kicker(
            day.dayOfWeek.shortLabel().take(1),
            color = if (isToday) Volt else TextSecondary,
        )
        Text(
            dayOfMonth.toString(),
            style = InstrumentType.numeralSm,
            color = TextPrimary,
        )
        Text(
            label,
            style = InstrumentType.caption,
            // A proposal is quieter than a pin, so a previewed week never looks like a decided
            // one. Quiet is [TextSecondary], not [TextTertiary]: these cells are tappable.
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (logged) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "Logged",
                tint = TextSecondary,
                modifier = Modifier.size(LOGGED_TICK),
            )
        } else {
            Box(modifier = Modifier.size(LOGGED_TICK))
        }
    }
}

// The same 3dp rule the nav bar uses for the selected tab, so "here" reads identically in both.
private val TODAY_MARKER_WIDTH = 16.dp
private val TODAY_MARKER_HEIGHT = 3.dp
private val LOGGED_TICK = 12.dp
