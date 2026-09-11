package com.sinura.personaltrainer.ui.components


import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim
import com.sinura.personaltrainer.ui.theme.instrumentTween

/**
 * A chip in the app's own language rather than Material's.
 *
 * The stock filter chip draws its selected state from `secondaryContainer`, which is one of
 * the roles the old theme never mapped — so every selected chip in the product was baseline
 * lavender. Even mapped, its tonal fill and 8dp corner belong to a different design system
 * than this one.
 */
@Composable
fun InstrumentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * A small glyph before the label. Defaults to nothing, so every existing chip in the app
     * is untouched — the in-workout lift switcher is the only caller that fills it.
     */
    leading: (@Composable () -> Unit)? = null,
) {
    val view = LocalView.current
    // VoltDim, not solid Volt: the palette declares this token as "selected chips, active
    // tracks" and nothing was using it, while every selected chip in the app burned a full
    // accent fill. A screen can hold a dozen chips; the accent is meant to appear once or
    // twice.
    val background by animateColorAsState(
        targetValue = if (selected) VoltDim else Surface2,
        animationSpec = instrumentTween(Motion.TAP),
        label = "chip-fill",
    )
    Box(
        modifier = modifier
            .heightIn(min = Metrics.touchMin)
            .clip(RoundedCornerShape(Radius.xs))
            .background(background)
            .border(
                Metrics.hairline,
                if (selected) Volt else Hairline,
                RoundedCornerShape(Radius.xs),
            )
            // selectable, not clickable: this replaced FilterChip everywhere in the app, and
            // FilterChip published a Selected semantics property that a screen reader reads
            // out. With a plain clickable the selected state exists only as a colour swap,
            // which TalkBack cannot see at all.
            .selectable(
                selected = selected,
                onClick = {
                    Haptics.tick(view)
                    onClick()
                },
            )
            .padding(horizontal = Metrics.space4),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            leading?.invoke()
            Text(
                label,
                style = InstrumentType.bodyStrong,
                // Volt ink on the dim fill: Pit ink was only legible against a solid accent.
                color = if (selected) Volt else TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
