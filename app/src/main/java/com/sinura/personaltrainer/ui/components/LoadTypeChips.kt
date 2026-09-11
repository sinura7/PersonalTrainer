package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoadTypeCopy
import com.sinura.personaltrainer.ui.theme.Metrics

object LoadTypeTags {
    fun chip(type: LoadType): String =
        "load-type-${type.name.lowercase().replace('_', '-')}"
}

/**
 * How this lift loads: plates, a stack, bodyweight, added, or assisted.
 *
 * One selected chip, always. A custom with no mark used to become plates by silence,
 * which is how a named push-up ended up with a kilogram well.
 */
@Composable
fun LoadTypeChipRow(
    selected: LoadType,
    onSelect: (LoadType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Kicker(LoadTypeCopy.KICKER)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            items(LoadType.entries, key = { it.name }) { type ->
                InstrumentChip(
                    label = type.label,
                    selected = selected == type,
                    onClick = { onSelect(type) },
                    modifier = Modifier.testTag(LoadTypeTags.chip(type)),
                )
            }
        }
    }
}
