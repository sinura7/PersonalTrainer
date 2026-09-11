package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * The one loud control on a screen.
 *
 * [hapticFeedback] exists so the log-set button can opt out and fire the heavier commit
 * pattern itself, instead of buzzing twice for one press.
 */
@Composable
fun PrimaryGymButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Metrics.control,
    hapticFeedback: Boolean = true,
) {
    val view = LocalView.current
    Button(
        onClick = {
            if (hapticFeedback) Haptics.tickLight(view)
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = Volt,
            contentColor = Pit,
            disabledContainerColor = Surface2,
            disabledContentColor = TextDisabled,
        ),
    ) {
        Text(
            text,
            style = InstrumentType.title,
            color = if (enabled) Pit else TextDisabled,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SecondaryGymButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Metrics.control,
    /** Lets a destructive alternative wear [Danger] as ink without becoming a solid red slab. */
    contentColor: Color = TextPrimary,
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .clip(RoundedCornerShape(Radius.md))
            .background(Surface2)
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.md))
            .clickable(enabled = enabled, role = Role.Button) {
                Haptics.tickLight(view)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = Metrics.space3, vertical = Metrics.space2),
            style = InstrumentType.title,
            color = if (enabled) contentColor else TextDisabled,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A full-width destructive action.
 *
 * Danger as the outline and the ink rather than as a fill: a solid red button
 * the width of the dialog reads as the default, and this one never is.
 */
@Composable
fun DangerGymButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecondaryGymButton(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentColor = Danger,
    )
}
