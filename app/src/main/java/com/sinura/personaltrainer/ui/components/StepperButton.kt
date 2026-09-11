package com.sinura.personaltrainer.ui.components


import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.instrumentTween
import kotlinx.coroutines.delay

/**
 * A plate. Press and hold to repeat, with a detent under the thumb for every step.
 *
 * The code that this replaces carried a comment conceding the cost of its own design —
 * "20 kg to 140 kg is 48 taps at the 2.5 kg step" — and then made all 48 taps identical
 * and silent. A physical weight selector clicks per detent and accelerates when held; this
 * one now does both, which turns the weakest part of the app's strongest control into
 * something that feels like equipment.
 */
@Composable
fun StepperButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // The repeat loop must call the CURRENT lambda, not the one that existed when the press
    // began. LaunchedEffect is remember(key) { ... }, so while `pressed` stays true the
    // running coroutine keeps its original block — and the weight plate's lambda closes over
    // the current weight to compute an absolute target. Captured, every repeat would
    // recompute the same number from the pre-press value: the weight would move one step and
    // then sit still while the haptics kept firing.
    val currentOnClick by rememberUpdatedState(onClick)
    // A press that turned into a hold has already delivered its steps. Compose calls
    // onClick on release, which would otherwise add one more on top of the repeat run.
    var repeatedThisPress by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        // Cleared here rather than on release: a press that is cancelled instead of clicked
        // — dragged off the plate, or stolen by a parent scroll — never reaches the click
        // handler, and a flag left set would silently swallow the next genuine tap.
        repeatedThisPress = false
        delay(HOLD_BEFORE_REPEAT_MS)
        repeatedThisPress = true
        var repeats = 0
        while (true) {
            currentOnClick()
            Haptics.tickLight(view)
            repeats++
            delay(if (repeats >= REPEATS_BEFORE_FAST) FAST_REPEAT_MS else REPEAT_MS)
        }
    }

    val background by animateColorAsState(
        targetValue = if (pressed) SurfacePressed else Surface2,
        animationSpec = instrumentTween(Motion.TAP),
        label = "stepper-press",
    )

    Box(
        modifier = modifier
            .heightIn(min = Metrics.commit)
            .clip(RoundedCornerShape(Radius.sm))
            .background(background)
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.sm))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {
                    if (repeatedThisPress) {
                        repeatedThisPress = false
                    } else {
                        onClick()
                        Haptics.tick(view)
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = Metrics.space2, vertical = Metrics.space2),
            style = InstrumentType.numeralMd,
            color = TextPrimary,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

private const val HOLD_BEFORE_REPEAT_MS = 400L
private const val REPEAT_MS = 150L
private const val FAST_REPEAT_MS = 60L
private const val REPEATS_BEFORE_FAST = 8
