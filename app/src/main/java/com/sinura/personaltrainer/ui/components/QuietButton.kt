package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * A quiet, self-sized secondary control: Lift 1 of 3 ⌄, Apply, Edit.
 *
 * [SecondaryGymButton] fills its row and is the right size for a dialog's alternative
 * action; this is the same surface at the size of its own label, for the actions that sit
 * beside a numeral or a kicker and must never compete with the one filled Volt.
 */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    /** Volt ink for a control that reports a live state (Applied). Never a Volt fill. */
    accent: Boolean = false,
    spoken: String? = null,
    /** No fill or border: a quiet inline link that still keeps its 48 dp target. */
    plain: Boolean = false,
    /** What "double-tap to …" says when the visible words name a thing, not the act. */
    onClickLabel: String? = null,
) {
    val view = LocalView.current
    // An accented control reports a state (Applied), so it keeps its ink while disabled.
    val ink: Color = when {
        accent -> Volt
        !enabled -> TextDisabled
        else -> TextPrimary
    }
    Row(
        modifier = modifier
            .heightIn(min = Metrics.touchMin)
            .widthIn(min = Metrics.touchMin)
            .clip(RoundedCornerShape(Radius.md))
            .then(if (plain) Modifier else Modifier.background(Surface2))
            .then(if (plain) Modifier else Modifier.border(Metrics.hairline, if (accent) Volt else Hairline, RoundedCornerShape(Radius.md)))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = onClickLabel) {
                Haptics.tickLight(view)
                onClick()
            }
            // A spoken form replaces the visible words rather than joining them (they were read
            // one after the other). It sits after clickable, so the role and action survive.
            .then(if (spoken != null) Modifier.clearAndSetSemantics { contentDescription = spoken } else Modifier)
            .padding(horizontal = Metrics.space3),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = ink, modifier = Modifier.size(Metrics.chevron))
        }
        Text(text, style = InstrumentType.bodyStrong, color = ink, maxLines = 1)
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = ink, modifier = Modifier.size(Metrics.chevron))
        }
    }
}
