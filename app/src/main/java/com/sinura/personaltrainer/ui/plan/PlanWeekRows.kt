package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.DayFill
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.WeekBoard
import com.sinura.personaltrainer.domain.WeekBoardCell
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.DateCopy
import java.time.LocalDate

/** Blueprint 4.2 row heights: a training day is 72dp tall, a rest day 56dp. */
internal object PlanWeekRowMetrics {
    val TRAINING: Dp = 72.dp
    val REST: Dp = 56.dp
}

/**
 * One civil day of the Plan week. The header opens Plan day; each planned activity is its
 * own row underneath (tagged [PlanTags.agenda]) and opens the same day.
 */
@Composable
internal fun PlanWeekDayRow(
    cell: WeekBoardCell,
    items: List<AgendaItem>,
    routines: Map<String, Routine>,
    proposal: SuggestedTrainingDay?,
    isToday: Boolean,
    onOpenDay: () -> Unit,
) {
    val date = LocalDate.ofEpochDay(cell.epochDay)
    val rest = cell.fill == DayFill.EMPTY && items.isEmpty()
    val label = DateCopy.weekdayShort(date) + " " + date.dayOfMonth
    val status = when {
        !rest -> WeekBoard.status(cell)
        proposal != null -> "Suggested ${proposal.focusTitle}"
        else -> WeekBoard.REST
    }
    val spoken = buildString {
        append(DateCopy.weekdayFullDate(date))
        append(", ")
        append(status)
        if (isToday) append(", today")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (rest && proposal == null) PlanWeekRowMetrics.REST else PlanWeekRowMetrics.TRAINING)
            .testTag(PlanTags.day(cell.epochDay)),
    ) {
        HairlineDivider(startIndent = 0.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.touchMin)
                .testTag(PlanTags.dayHeader(cell.epochDay))
                .clickable(role = Role.Button, onClickLabel = "Open this day", onClick = onOpenDay)
                .semantics {
                    heading()
                    contentDescription = spoken
                }
                .padding(vertical = Metrics.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Text(label, style = InstrumentType.bodyStrong, color = TextPrimary)
            if (isToday) Text("today", style = InstrumentType.caption, color = TextPrimary)
            Text(
                status,
                style = InstrumentType.caption,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        items.forEach { item ->
            val routine = item.rule?.takeUnless {
                it.modality == ScheduleModality.CARDIO || ScheduleKind.cardioType(it.templateId) != null
            }?.routineId?.let(routines::get)
            PlanAgendaRow(item = item, exercise = routine?.exercises?.firstOrNull()?.exercise, onOpen = onOpenDay)
        }
    }
}
