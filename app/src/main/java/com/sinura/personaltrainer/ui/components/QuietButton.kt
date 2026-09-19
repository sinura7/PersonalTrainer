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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * A quiet, self-sized secondary control: Details ›, Apply, Edit.
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
) {
    val view = LocalView.current
    val ink: Color = when {
        !enabled -> TextDisabled
        accent -> Volt
        else -> TextPrimary
    }
    Row(
        modifier = modifier
            .heightIn(min = Metrics.touchMin)
            .widthIn(min = Metrics.touchMin)
            .clip(RoundedCornerShape(Radius.sm))
            .background(Surface2)
            .border(Metrics.hairline, if (accent && enabled) Volt else Hairline, RoundedCornerShape(Radius.sm))
            .clickable(enabled = enabled, role = Role.Button) {
                Haptics.tickLight(view)
                onClick()
            }
            .then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier)
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
