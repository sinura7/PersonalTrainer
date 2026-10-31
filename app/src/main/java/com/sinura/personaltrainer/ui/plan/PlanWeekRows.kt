package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.R
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

/** Blueprint 4.2 row heights are minimums: a training day at least 72dp, a rest day at least 56dp. */
internal object PlanWeekRowMetrics {
    val TRAINING: Dp = Metrics.planTrainingRow
    val REST: Dp = Metrics.planRestRow
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
    onAddToDay: (() -> Unit)? = null,
) {
    val date = LocalDate.ofEpochDay(cell.epochDay)
    val rest = cell.fill == DayFill.EMPTY && items.isEmpty()
    val label = DateCopy.weekdayShort(date) + " " + date.dayOfMonth
    val suggestedRest = rest && proposal?.isRest == true
    val status = when {
        !rest -> WeekBoard.status(cell)
        proposal != null && !proposal.isRest -> stringResource(R.string.plan_suggested, proposal.focusTitle)
        else -> WeekBoard.REST
    }
    val todayWord = stringResource(R.string.plan_today)
    val suggestedRestWord = stringResource(R.string.plan_suggested_rest_spoken)
    val spoken = buildString {
        append(DateCopy.weekdayFullDate(date))
        append(", ")
        append(status)
        if (suggestedRest) append(", ").append(suggestedRestWord)
        if (isToday) append(", ").append(todayWord)
    }
    val openDay = stringResource(R.string.plan_open_day)
    val addLabel = stringResource(R.string.plan_add)
    val addSpoken = stringResource(R.string.plan_add_day, label)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (rest && (proposal == null || proposal.isRest)) PlanWeekRowMetrics.REST else PlanWeekRowMetrics.TRAINING)
            .testTag(PlanTags.day(cell.epochDay)),
    ) {
        HairlineDivider(startIndent = 0.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Metrics.touchMin)
                .testTag(PlanTags.dayHeader(cell.epochDay))
                .clickable(role = Role.Button, onClickLabel = openDay, onClick = onOpenDay)
                .semantics {
                    heading()
                    contentDescription = spoken
                }
                .padding(vertical = Metrics.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Text(label, style = InstrumentType.bodyStrong, color = TextPrimary)
            if (isToday) Text(text = todayWord, style = InstrumentType.caption, color = TextPrimary)
            Text(
                status,
                style = InstrumentType.caption,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        if (onAddToDay != null) {
            TextButton(
                onClick = onAddToDay,
                modifier = Modifier
                    .heightIn(min = Metrics.touchMin)
                    .widthIn(min = Metrics.touchMin)
                    .testTag(PlanTags.dayAdd(cell.epochDay))
                    .semantics { contentDescription = addSpoken },
            ) {
                Text(text = addLabel, style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
        }
        items.forEach { item ->
            val routine = item.rule?.takeUnless {
                it.modality == ScheduleModality.CARDIO || ScheduleKind.cardioType(it.templateId) != null
            }?.routineId?.let(routines::get)
            PlanAgendaRow(item = item, exercise = routine?.exercises?.firstOrNull()?.exercise, onOpen = onOpenDay)
        }
    }
}
