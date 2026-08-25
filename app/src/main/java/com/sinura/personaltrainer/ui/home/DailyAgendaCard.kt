package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.HomeToday
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.sessionLiftNames
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymCard
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
 * Morning cardio and evening strength are two rows. Completing one does
 * not start or hide the other. One live activity still blocks a second start.
 * One filled Volt starts the next planned row (strength preferred); other
 * planned rows stay tappable. Strength rows speak the same numbered order
 * Plan and the editor already built.
 */
@Composable
fun DailyAgendaCard(
    items: List<AgendaItem>,
    sessionLive: Boolean,
    onStartOccurrence: (String) -> Unit,
    onStartFree: () -> Unit,
    routines: List<Routine> = emptyList(),
) {
    val startTagId = HomeToday.startTagOccurrenceId(items)
    GymCard {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
            Kicker("Today")
            if (items.size > 1) {
                Text(
                    SessionOrderCopy.AGENDA_SEPARATE,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
            }
            GroupedList {
                items.forEachIndexed { index, item ->
                    if (index > 0) HairlineDivider()
                    val planned = item.occurrence.status == OccurrenceStatus.PLANNED
                    val names = sessionLiftNames(item.rule?.routineId, routines)
                    val tagged = item.occurrence.id == startTagId
                    InstrumentRow(
                        title = "${item.timeLabel}  ·  ${item.title}",
                        subtitle = SessionOrderCopy.occurrenceLine(
                            item.occurrence.status,
                            names,
                            item.rule?.modality ?: ScheduleModality.STRENGTH,
                        ),
                        onClick = if (planned && !sessionLive && !tagged) {
                            { onStartOccurrence(item.occurrence.id) }
                        } else {
                            null
                        },
                    )
                    if (planned && !sessionLive && tagged) {
                        PrimaryGymButton(
                            text = "Start ${item.title}",
                            onClick = { onStartOccurrence(item.occurrence.id) },
                            modifier = Modifier
                                .testTag(HomeTags.START)
                                .semantics { contentDescription = PLANNED_SESSION },
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
                TextButton(
                    onClick = onStartFree,
                    modifier = Modifier
                        .testTag(HomeTags.FREE)
                        .semantics { contentDescription = SessionOrderCopy.FREE_WORKOUT },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(SessionOrderCopy.FREE_WORKOUT, style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            }
        }
    }
}

private const val PLANNED_SESSION = "Start today's planned session"
