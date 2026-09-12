package com.sinura.personaltrainer.ui.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.plan.PreferenceBlock
import com.sinura.personaltrainer.ui.theme.Metrics

@OptIn(ExperimentalLayoutApi::class)
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
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space4)) {
        GymCard {
            PreferenceBlock(
                preferences = preferences,
                onDays = onDays,
                onSplit = onSplit,
                onWeekStart = onWeekStart,
            )
        }
        GymCard {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space4)) {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
                    GymSectionHeader(title = "Training weekdays", compact = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                        Weekday.entries.forEach { day ->
                            InstrumentChip(
                                label = day.shortLabel(),
                                selected = day in preferredDays,
                                onClick = { onTogglePreferredDay(day) },
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
                    GymSectionHeader(title = "Training age", compact = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                        TrainingAge.entries.forEach { age ->
                            InstrumentChip(
                                label = age.displayName,
                                selected = trainingAge == age,
                                onClick = { onTrainingAge(age) },
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
                    GymSectionHeader(title = "Where you train", compact = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                        TrainingPlace.entries.forEach { place ->
                            InstrumentChip(
                                label = place.shortLabel,
                                selected = trainingPlace == place,
                                onClick = { onTrainingPlace(place) },
                            )
                        }
                    }
                }
            }
        }
    }
}
