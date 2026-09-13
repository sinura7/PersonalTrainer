package com.sinura.personaltrainer.ui.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.theme.Metrics

@Composable
internal fun SchedulePrefsSection(
    preferences: SchedulePreferences,
    preferredDays: Set<Weekday>,
    trainingAge: TrainingAge,
    trainingPlace: TrainingPlace?,
    onDays: (Int) -> Unit,
    onSplit: (SplitStyle) -> Unit,
    onWeekStart: (Weekday) -> Unit,
    onTogglePreferredDay: (Weekday) -> Unit,
    onTrainingAge: (TrainingAge) -> Unit,
    onTrainingPlace: (TrainingPlace) -> Unit,
) {
    val days = (SchedulePreferences.MIN_DAYS..SchedulePreferences.MAX_DAYS).toList()
    val weekStarts = listOf(Weekday.MONDAY, Weekday.SUNDAY)
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap)) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader(title = "Training days", compact = true)
            GymCard {
                SettingsStrip(
                    labels = days.map { it.toString() },
                    selected = { preferences.trainingDaysPerWeek == days[it] },
                    onSelect = { onDays(days[it]) },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader(title = "Split", compact = true)
            SettingsRadioList(
                items = SplitStyle.entries,
                selected = preferences.splitStyle,
                title = { it.displayName },
                subtitle = { it.blurb },
                onSelect = onSplit,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader(title = "Week starts", compact = true)
            SettingsRadioList(
                items = weekStarts,
                selected = preferences.weekStart,
                title = { it.titleLabel() },
                onSelect = onWeekStart,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader(title = "Training weekdays", compact = true)
            GymCard {
                SettingsStrip(
                    labels = Weekday.entries.map { it.shortLabel() },
                    selected = { Weekday.entries[it] in preferredDays },
                    onSelect = { onTogglePreferredDay(Weekday.entries[it]) },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader(title = "Training age", compact = true)
            SettingsRadioList(
                items = TrainingAge.entries,
                selected = trainingAge,
                title = { it.displayName },
                subtitle = { it.blurb },
                onSelect = onTrainingAge,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader(title = "Where you train", compact = true)
            SettingsRadioList(
                items = TrainingPlace.entries,
                selected = trainingPlace,
                title = { it.displayName },
                subtitle = { it.blurb },
                onSelect = onTrainingPlace,
            )
        }
    }
}
