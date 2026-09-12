package com.sinura.personaltrainer.ui.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.EquipmentGroups
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

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
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space4)) {
        GroupedList(modifier = Modifier.selectableGroup()) {
            TrainingGoal.entries.forEachIndexed { index, goal ->
                if (index > 0) HairlineDivider()
                val selected = preferences.goal == goal
                InstrumentRow(
                    title = goal.displayName,
                    subtitle = goal.blurb,
                    modifier = Modifier.selectable(
                        selected = selected,
                        onClick = { onGoal(goal) },
                        role = Role.RadioButton,
                    ),
                    trailing = {
                        if (selected) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = Volt)
                        }
                    },
                )
            }
        }
        GroupedList(modifier = Modifier.selectableGroup()) {
            TrainingEmphasis.entries.forEachIndexed { index, emphasis ->
                if (index > 0) HairlineDivider()
                val selected = preferences.emphasis == emphasis
                InstrumentRow(
                    title = emphasis.displayName,
                    subtitle = emphasis.blurb,
                    modifier = Modifier.selectable(
                        selected = selected,
                        onClick = { onEmphasis(emphasis) },
                        role = Role.RadioButton,
                    ),
                    trailing = {
                        if (selected) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = Volt)
                        }
                    },
                )
            }
        }
        GymCard {
            Kicker("Equipment you have")
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
