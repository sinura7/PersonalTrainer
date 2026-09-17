package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.Volt

/** Exclusive options wrap with their labels; each option keeps its own touch target. */
@Composable
fun InstrumentChoiceGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) { content() }
}

@Composable
fun InstrumentChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    InstrumentChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        compact = compact,
        role = Role.RadioButton,
        leading = { SelectionMark(selected = selected, checkbox = false, enabled = enabled) },
    )
}

@Composable
fun InstrumentToggleChip(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    InstrumentChip(
        label = label,
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        enabled = enabled,
        role = Role.Checkbox,
        leading = { SelectionMark(selected = checked, checkbox = true, enabled = enabled) },
    )
}

/** A value application is an action, with no selected/checked semantics. */
@Composable
fun InstrumentPreset(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        SecondaryGymButton(
            text = label,
            onClick = onClick,
            enabled = enabled,
            height = if (compact) Metrics.touchMin else Metrics.control,
            textStyle = if (compact) InstrumentType.bodyStrong else InstrumentType.title,
        )
        supporting?.let {
            Text(text = it, style = InstrumentType.caption, color = TextSecondary)
        }
    }
}

/** Suggestions describe an unapplied value. Selection and application live elsewhere. */
@Composable
fun InstrumentSuggestion(text: String, modifier: Modifier = Modifier) {
    Text(
        text = "Suggested · $text",
        modifier = modifier,
        style = InstrumentType.caption,
        color = TextSecondary,
    )
}

@Composable
private fun SelectionMark(selected: Boolean, checkbox: Boolean, enabled: Boolean) {
    val color = if (!enabled) TextDisabled else if (selected) Volt else TextSecondary
    val shape = if (checkbox) RoundedCornerShape(Metrics.space1) else Radius.full
    Box(
        modifier = Modifier.size(Metrics.chevron).border(Metrics.hairline, color, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected && checkbox) {
            Icon(TemperIcons.Check, contentDescription = null, tint = color)
        } else if (selected) {
            Box(Modifier.size(Metrics.space2).background(color, Radius.full))
        }
    }
}
