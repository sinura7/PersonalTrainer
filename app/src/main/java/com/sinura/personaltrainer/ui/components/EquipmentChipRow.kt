package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.EquipmentGroups
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.ui.theme.Metrics

@Composable
fun EquipmentChipRow(
    selected: EquipmentType,
    onSelect: (EquipmentType) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        EquipmentGroups.ALL.forEach { group ->
            items(group.types, key = { it.name }) { equipment ->
                InstrumentChip(
                    label = equipment.label,
                    selected = equipment == selected,
                    onClick = { onSelect(equipment) },
                )
            }
        }
    }
}
