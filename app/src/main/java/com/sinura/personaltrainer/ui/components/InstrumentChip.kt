package com.sinura.personaltrainer.ui.components


import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextDisabled
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
    /**
     * History suggestion that is not the selected value: a Volt dot in the chip's corner,
     * no fill and no Volt edge.
     *
     * It used to share the selected state's Volt border, which made a suggestion easy to
     * read as already applied — a suggestion is never rendered as a selection (ADR-027 §4),
     * and the design audit of 16 September named this the clearest case of it. The dot is
     * corner-set rather than a leading glyph so it costs the label no width.
     */
    recommended: Boolean = false,
    /**
     * Floor RPE 6–10: tighter horizontal padding so five equal chips fit at
     * 360 dp / font scale 2.0 without scrolling or clipping.
     */
    compact: Boolean = false,
    labelStyle: TextStyle = InstrumentType.bodyStrong,
    /**
     * Floor RPE uses radio. Warm-up and ramp chips stay toggle/checkbox.
     */
    role: Role = Role.Checkbox,
    spoken: String? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val view = LocalView.current
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    // VoltDim, not solid Volt: the palette declares this token as "selected chips, active
    // tracks" and nothing was using it, while every selected chip in the app burned a full
    // accent fill. A screen can hold a dozen chips; the accent is meant to appear once or
    // twice.
    val background by animateColorAsState(
        targetValue = when {
            pressed && enabled -> SurfacePressed
            selected -> VoltDim
            else -> Surface2
        },
        animationSpec = instrumentTween(Motion.FIELD_MS),
        label = "chip-fill",
    )
    Box(
        modifier = modifier
            .heightIn(min = Metrics.touchMin)
            .widthIn(min = Metrics.touchMin)
            .clip(RoundedCornerShape(Radius.xs))
            .background(background)
            .border(
                if (focused) Metrics.emphasisBorder else Metrics.hairline,
                // A Volt edge means this value is chosen. A suggestion wears the dot below.
                if (focused || selected) Volt else Hairline,
                RoundedCornerShape(Radius.xs),
            )
            // selectable, not clickable: this replaced FilterChip everywhere in the app, and
            // FilterChip published a Selected semantics property that a screen reader reads
            // out. With a plain clickable the selected state exists only as a colour swap,
            // which TalkBack cannot see at all.
            .then(
                if (role == Role.Checkbox || role == Role.Switch) {
                    Modifier.toggleable(
                        value = selected,
                        enabled = enabled,
                        role = role,
                        interactionSource = interactions,
                        indication = null,
                        onValueChange = { Haptics.tick(view); onClick() },
                    )
                } else {
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        role = role,
                        interactionSource = interactions,
                        indication = null,
                        onClick = { Haptics.tick(view); onClick() },
                    )
                },
            )
            .then(
                if (spoken == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = spoken }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (recommended && !selected) {
            // Inside the chip's own corner, and outside the label's padding, so the words
            // keep every pixel they had. Decorative: the spoken form carries "recommended".
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Metrics.space1)
                    .size(Metrics.markDot)
                    .background(if (enabled) Volt else TextDisabled, Radius.full),
            )
        }
        Row(
            // The padding moved off the box and onto the words, which is what leaves the
            // corner free for the dot above.
            modifier = Modifier.padding(
                horizontal = if (compact) Metrics.space2 else Metrics.space4,
                vertical = Metrics.space2,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            leading?.invoke()
            Text(
                label,
                style = labelStyle,
                // Volt ink on the dim fill: Pit ink was only legible against a solid accent.
                color = when {
                    !enabled -> TextDisabled
                    selected -> Volt
                    else -> TextSecondary
                },
            )
        }
    }
}
