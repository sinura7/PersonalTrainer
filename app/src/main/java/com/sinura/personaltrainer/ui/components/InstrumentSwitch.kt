package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltContainer
import com.sinura.personaltrainer.ui.theme.Radius

/**
 * G4: a switch that is not a filled Volt slab.
 *
 * Material's checked track resolves to `primary` (Volt). Three of those on Settings
 * read as three extra filled acts on a page that already has Export. Track is a
 * container; the thumb carries Volt when on.
 */
@Composable
fun InstrumentSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val track = if (checked && enabled) VoltContainer else SurfacePressed
    val stroke = if (checked && enabled) Volt.copy(alpha = 0.55f) else HairlineStrong
    val thumb = if (!enabled) TextDisabled else if (checked) Volt else TextSecondary
    val shape = Radius.full
    val interactive = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(if (onCheckedChange != null) Modifier.sizeIn(minWidth = Metrics.touchMin, minHeight = Metrics.touchMin) else Modifier)
            .then(interactive),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 32.dp)
                .clip(shape)
                .background(track)
                .border(Metrics.hairline, stroke, shape)
                .padding(Metrics.space1),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = if (checked) 20.dp else 0.dp)
                    .size(24.dp)
                    .clip(Radius.full)
                    .background(thumb),
            )
        }
    }
}
