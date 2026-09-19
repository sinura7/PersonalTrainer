package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * Kit as a quiet tag: Machine, Barbell, Cable.
 *
 * Not a filter chip — those select. This names what you hold on a row
 * or a lift card, so a machine day reads as machines without opening
 * the still. D-08 asked for a MachineCard or an equipment chip; the
 * chip is the one vocabulary, on every list and card.
 */
@Composable
fun EquipmentChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.xs)
    Box(
        modifier = modifier
            .border(Metrics.hairline, Hairline, shape)
            .padding(horizontal = Metrics.space2, vertical = Metrics.space1),
    ) {
        Kicker(label, color = TextTertiary, asHeading = false)
    }
}

@Composable
fun EquipmentChip(
    equipment: EquipmentType,
    modifier: Modifier = Modifier,
) = EquipmentChip(label = equipment.label, modifier = modifier)
