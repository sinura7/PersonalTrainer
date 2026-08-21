package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary
import java.time.DayOfWeek

/**
 * The three preferences that shape a suggested week: how many days, which split, where the
 * week starts.
 *
 * Moved here from the old Schedule screen, unchanged. It now lives behind the Plan header's
 * "Tune" toggle rather than on a screen of its own, because tuning is something you do to the
 * week you are looking at — the old arrangement put the controls one navigation away from
 * their effect, and Settings held a second copy of the route to them.
 */
@Composable
fun PreferenceBlock(
    preferences: SchedulePreferences,
    onDays: (Int) -> Unit,
    onSplit: (SplitStyle) -> Unit,
    onWeekStart: (DayOfWeek) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space5)) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader("Training days", compact = true)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                items((SchedulePreferences.MIN_DAYS..SchedulePreferences.MAX_DAYS).toList()) { days ->
                    InstrumentChip(
                        label = "$days",
                        selected = preferences.trainingDaysPerWeek == days,
                        onClick = { onDays(days) },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader("Split", compact = true)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                items(SplitStyle.entries) { style ->
                    InstrumentChip(
                        label = style.displayName,
                        selected = preferences.splitStyle == style,
                        onClick = { onSplit(style) },
                    )
                }
            }
            Text(
                preferences.splitStyle.blurb,
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader("Week starts", compact = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { day ->
                    InstrumentChip(
                        label = if (day == DayOfWeek.MONDAY) "Monday" else "Sunday",
                        selected = preferences.weekStart == day,
                        onClick = { onWeekStart(day) },
                    )
                }
            }
        }
    }
}
