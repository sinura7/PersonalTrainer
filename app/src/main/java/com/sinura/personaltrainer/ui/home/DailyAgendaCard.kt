package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.HomeToday
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.sessionLiftNames
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * Today's occurrences — Home's only today-surface when the planner
 * generated any row (P7.5). ThisWeekCard is the empty-agenda leftover.
 *
 * Morning cardio, a main lift session, and later accessory work are
 * separate rows. Completing one does not start or hide the other. One
 * live activity still blocks a second start. One filled Volt names the
 * next planned row (workout preferred over Stretch); every planned row
 * — including that one — opens a confirm with the session summary.
 * Confirm starts it. Strength rows speak the same numbered order Plan
 * and the editor already built.
 *
 * The group is the list. Start sits under it so a card does not wrap a card.
 */
@Composable
fun DailyAgendaCard(
    items: List<AgendaItem>,
    sessionLive: Boolean,
    onStartOccurrence: (String) -> Unit,
    onStartFree: () -> Unit,
    routines: List<Routine> = emptyList(),
    kicker: String = "Today",
) {
    val clockFormat = com.sinura.personaltrainer.ui.units.LocalClockFormat.current
    val startTagId = HomeToday.startTagOccurrenceId(items)
    val startItem = items.firstOrNull { it.occurrence.id == startTagId }
    var pendingOccurrenceId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingItem = items.firstOrNull { item ->
        item.occurrence.id == pendingOccurrenceId &&
            item.occurrence.status == OccurrenceStatus.PLANNED
    }?.takeUnless { sessionLive }

    LaunchedEffect(pendingOccurrenceId, items, sessionLive) {
        val id = pendingOccurrenceId ?: return@LaunchedEffect
        if (sessionLive) {
            pendingOccurrenceId = null
            return@LaunchedEffect
        }
        val stillPlanned = items.any {
            it.occurrence.id == id && it.occurrence.status == OccurrenceStatus.PLANNED
        }
        if (!stillPlanned) pendingOccurrenceId = null
    }

    if (pendingItem != null) {
        val confirm = HomeToday.startConfirm(pendingItem, routines, clockFormat)
        ConfirmActionDialog(
            title = confirm.heading,
            body = confirm.body,
            confirmLabel = confirm.confirmLabel,
            onConfirm = {
                val id = pendingItem.occurrence.id
                pendingOccurrenceId = null
                onStartOccurrence(id)
            },
            onDismiss = { pendingOccurrenceId = null },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
        Kicker(kicker)
        if (items.isEmpty()) {
            Text(
                com.sinura.personaltrainer.domain.PlanDayCopy.EMPTY,
                style = InstrumentType.body,
                color = TextPrimary,
            )
            Text(
                com.sinura.personaltrainer.domain.PlanDayCopy.EMPTY_BODY,
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        } else if (items.size > 1) {
            Text(
                SessionOrderCopy.AGENDA_SEPARATE,
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
        if (items.isNotEmpty()) {
            GroupedList {
                items.forEachIndexed { index, item ->
                    if (index > 0) HairlineDivider()
                    val planned = item.occurrence.status == OccurrenceStatus.PLANNED
                    val names = sessionLiftNames(item.rule?.routineId, routines)
                    InstrumentRow(
                        title = "${com.sinura.personaltrainer.domain.ClockCopy.format(item.occurrence.hour, item.occurrence.minute, clockFormat)}  ·  ${item.title}",
                        subtitle = SessionOrderCopy.occurrenceLine(
                            item.occurrence.status,
                            names,
                            item.rule?.modality ?: ScheduleModality.STRENGTH,
                        ),
                        modifier = Modifier.testTag(HomeTags.agendaRow(item.occurrence.id)),
                        onClick = if (planned && !sessionLive) {
                            { pendingOccurrenceId = item.occurrence.id }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        if (sessionLive) {
            Text(
                "Finish or discard the live session first.",
                style = InstrumentType.body,
                color = TextPrimary,
            )
        } else {
            startItem?.takeIf { it.occurrence.status == OccurrenceStatus.PLANNED }?.let { item ->
                PrimaryGymButton(
                    text = "Start ${item.title}",
                    onClick = { pendingOccurrenceId = item.occurrence.id },
                    modifier = Modifier
                        .testTag(HomeTags.START)
                        .semantics { contentDescription = PLANNED_SESSION },
                )
            }
            TextButton(
                onClick = onStartFree,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Metrics.touchMin)
                    .testTag(HomeTags.FREE)
                    .semantics { contentDescription = SessionOrderCopy.FREE_WORKOUT },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    SessionOrderCopy.FREE_WORKOUT,
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val PLANNED_SESSION = "Start today's planned session"
