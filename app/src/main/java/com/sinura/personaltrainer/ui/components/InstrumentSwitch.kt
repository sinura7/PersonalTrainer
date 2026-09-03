package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltContainer

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
    val track = if (checked) VoltContainer else SurfacePressed
    val stroke = if (checked) Volt.copy(alpha = 0.55f) else HairlineStrong
    val thumb = if (checked) Volt else TextSecondary
    val shape = RoundedCornerShape(percent = 50)
    val interactive = if (onCheckedChange != null && enabled) {
        Modifier
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .then(interactive)
            .clip(shape)
            .background(track)
            .border(Metrics.hairline, stroke, shape)
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = if (checked) 20.dp else 0.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(thumb),
        )
    }
}
