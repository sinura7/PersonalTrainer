package com.sinura.personaltrainer.ui.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.EquipmentGroups
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * What the coach emphasises, and what you actually have to lift with.
 *
 * Both are inputs to advice rather than to training itself, which is why they live here and
 * not on the Plan tab: nothing on this card changes a single number in your history, and
 * changing your goal is not something you do weekly.
 *
 * Equipment is opt-OUT. Everything counts as available until you say otherwise, because a
 * first run that assumed you owned nothing would silently produce a coach that never names a
 * lift, with nothing on screen to explain the silence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoachingSection(
    preferences: CoachPreferences,
    onGoal: (TrainingGoal) -> Unit,
    onEmphasis: (TrainingEmphasis) -> Unit,
    onToggleEquipment: (EquipmentType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap)) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader(title = "Goal", compact = true)
            SettingsRadioList(
                items = TrainingGoal.entries,
                selected = preferences.goal,
                title = { it.displayName },
                subtitle = { it.blurb },
                onSelect = onGoal,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader(title = "Emphasis", compact = true)
            SettingsRadioList(
                items = TrainingEmphasis.entries,
                selected = preferences.emphasis,
                title = { it.displayName },
                subtitle = { it.blurb },
                onSelect = onEmphasis,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader(title = "Equipment you have", compact = true)
            GymCard {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space4)) {
                    EquipmentGroups.ALL.forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                            Text(
                                group.title,
                                style = InstrumentType.caption,
                                color = TextSecondary,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                            ) {
                                group.types.forEach { equipment ->
                                    InstrumentChip(
                                        label = equipment.label,
                                        selected = preferences.allows(equipment),
                                        onClick = { onToggleEquipment(equipment) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
