package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * The one loud control on a screen.
 *
 * [hapticFeedback] exists so a control can opt out of the press tick.
 * Log set keeps the press tick and fires [Haptics.commit] only after a
 * durable write, via the screen's success collector.
 */
@Composable
fun PrimaryGymButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Metrics.control,
    hapticFeedback: Boolean = true,
    disabledReason: String? = null,
    interactionSource: MutableInteractionSource? = null,
    /**
     * A second, quieter line under [text]: the payload a commit will write
     * (`70 lbs × 10 · RPE 9`). The verb stays the button's name; TalkBack reads both.
     */
    supporting: String? = null,
    textStyle: TextStyle = InstrumentType.title,
) {
    val view = LocalView.current
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Button(
        onClick = {
            if (hapticFeedback) Haptics.tickLight(view)
            onClick()
        },
        enabled = enabled,
        interactionSource = interactions,
        border = if (focused) BorderStroke(Metrics.emphasisBorder, Pit) else null,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height.coerceAtLeast(Metrics.touchMin))
            .then(
                if (!enabled && !disabledReason.isNullOrBlank()) {
                    Modifier.semantics { stateDescription = disabledReason }
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = Volt,
            contentColor = Pit,
            disabledContainerColor = Surface2,
            disabledContentColor = TextDisabled,
        ),
    ) {
        if (supporting.isNullOrBlank()) {
            Text(
                text,
                style = textStyle,
                color = if (enabled) Pit else TextDisabled,
                textAlign = TextAlign.Center,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text,
                    style = textStyle,
                    color = if (enabled) Pit else TextDisabled,
                    textAlign = TextAlign.Center,
                )
                Text(
                    supporting,
                    style = InstrumentType.bodyStrong,
                    color = if (enabled) Pit else TextDisabled,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
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
    interactionSource: MutableInteractionSource? = null,
    textStyle: TextStyle = InstrumentType.title,
) {
    val view = LocalView.current
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height.coerceAtLeast(Metrics.touchMin))
            .clip(RoundedCornerShape(Radius.md))
            .background(if (pressed && enabled) SurfacePressed else Surface2)
            .border(
                if (focused) Metrics.emphasisBorder else Metrics.hairline,
                if (focused) Volt else Hairline,
                RoundedCornerShape(Radius.md),
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactions,
                indication = null,
            ) {
                Haptics.tickLight(view)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = Metrics.space3, vertical = Metrics.space2),
            style = textStyle,
            color = if (enabled) contentColor else TextDisabled,
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
