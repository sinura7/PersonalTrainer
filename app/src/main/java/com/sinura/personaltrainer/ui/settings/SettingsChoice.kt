package com.sinura.personaltrainer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim

internal data class SettingsRadioOption(
    val title: String,
    val subtitle: String? = null,
)

@Composable
internal fun SettingsRadioList(
    items: List<SettingsRadioOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupedList(modifier = modifier.selectableGroup()) {
        items.forEachIndexed { index, item ->
            if (index > 0) HairlineDivider()
            val isSelected = index == selectedIndex
            InstrumentRow(
                title = item.title,
                subtitle = item.subtitle,
                selected = isSelected,
                onClick = { onSelect(index) },
                trailing = {
                    if (isSelected) {
                        Icon(
                            imageVector = TemperIcons.Check,
                            contentDescription = null,
                            tint = Volt,
                            modifier = Modifier.size(Metrics.icon),
                        )
                    }
                },
            )
        }
    }
}

@Composable
internal fun SettingsStrip(
    labels: List<String>,
    selected: (Int) -> Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(Radius.xs)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = selected(index)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Metrics.touchMin)
                    .clip(shape)
                    .background(if (isSelected) VoltDim else Surface2)
                    .border(Metrics.hairline, if (isSelected) Volt else Hairline, shape)
                    .selectable(
                        selected = isSelected,
                        role = Role.Button,
                        onClick = {
                            Haptics.tick(view)
                            onSelect(index)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = InstrumentType.bodyStrong,
                    color = if (isSelected) Volt else TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
