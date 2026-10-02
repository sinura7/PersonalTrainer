package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

/** Test hook for menu rows that carry a leading mark beside the label. */
object InstrumentMenuTags {
    fun leadingIcon(spokenLabel: String): String = "instrument-menu-icon-$spokenLabel"
}

/**
 * G4: a menu on the sheet surface with a hairline, not a shadow on the window.
 *
 * Material `DropdownMenu` draws on `surfaceContainer` and separates itself with
 * elevation. On Instrument that shadow is invisible, so the menu used to vanish
 * into Pit. [Surface3] plus a hairline is how every other overlay already reads.
 */
@Composable
fun InstrumentMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        containerColor = Surface3,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(Metrics.hairline, Hairline),
        content = content,
    )
}

/**
 * One Instrument menu row: Temper mark at 20 dp, label beside it, icon decorative
 * for TalkBack (W1a — the visible text is announced once).
 */
@Composable
fun InstrumentMenuItem(
    spokenLabel: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconTint: Color = TextSecondary,
    textColor: Color = TextPrimary,
    textStyle: TextStyle = InstrumentType.bodyStrong,
    text: (@Composable () -> Unit)? = null,
) {
    DropdownMenuItem(
        text = {
            if (text != null) {
                text()
            } else {
                Text(spokenLabel, style = textStyle, color = textColor)
            }
        },
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        leadingIcon = {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(Metrics.helpMark)
                    .testTag(InstrumentMenuTags.leadingIcon(spokenLabel)),
            )
        },
    )
}
