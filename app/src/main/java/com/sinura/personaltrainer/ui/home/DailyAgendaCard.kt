package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ScheduleModality
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
 * Today's occurrences, independently startable (P7.5).
 *
 * Morning cardio and evening strength are two rows. Completing one does
 * not start or hide the other. One live activity still blocks a second start.
 */
@Composable
fun DailyAgendaCard(
    items: List<AgendaItem>,
    sessionLive: Boolean,
    onStartOccurrence: (String) -> Unit,
) {
    GymCard {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
            Kicker("Today")
            Text(
                "Morning and evening stay separate.",
                style = InstrumentType.caption,
                color = TextSecondary,
            )
            GroupedList {
                items.forEachIndexed { index, item ->
                    if (index > 0) HairlineDivider()
                    val planned = item.occurrence.status == OccurrenceStatus.PLANNED
                    InstrumentRow(
                        title = "${item.timeLabel}  ·  ${item.title}",
                        subtitle = item.occurrence.status.name.lowercase().replaceFirstChar { it.titlecase() },
                        onClick = {
                            if (planned && !sessionLive) onStartOccurrence(item.occurrence.id)
                        },
                    )
                    if (
                        planned &&
                        !sessionLive &&
                        item.rule?.modality == ScheduleModality.CARDIO
                    ) {
                        // Strength is started from the hero ("follow today's plan").
                        // Cardio stays independently startable so two-a-day is not buried.
                        PrimaryGymButton(
                            text = "Start ${item.title}",
                            onClick = { onStartOccurrence(item.occurrence.id) },
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
            }
        }
    }
}
