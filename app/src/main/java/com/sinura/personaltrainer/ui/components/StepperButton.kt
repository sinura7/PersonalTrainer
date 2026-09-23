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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.StepperRepeat
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextDisabled
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
 * something that feels like equipment. Hold waits 450 ms, then
 * repeats at most five times a second — not the old 60 ms buzz.
 */
@Composable
fun StepperButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true,
    plateWidth: Dp? = null,
    plateHeight: Dp? = null,
    /** Round plates beside the hero numerals pass [com.sinura.personaltrainer.ui.theme.Radius.full]. */
    shape: Shape = RoundedCornerShape(Radius.sm),
    textStyle: TextStyle? = null,
    /**
     * How far inside the touch target the plate is actually drawn.
     *
     * The circle a thumb aims at and the area that answers it do not have to be the same
     * box, and on the workout floor they are not: the target stays at
     * [Metrics.touchMin] while the plate draws smaller, so the control can be tightened
     * without costing anyone a tap. Zero — every other caller — composes exactly as before.
     */
    plateInset: Dp = 0.dp,
    /**
     * A plate that is the point of the moment rather than a quiet neighbour: a lighter fill
     * and a harder edge. Never a Volt fill — this screen has one of those and it is Log set
     * (ADR-005, ADR-027 §6).
     */
    emphasis: Boolean = false,
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

    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        // Cleared here rather than on release: a press that is cancelled instead of clicked
        // — dragged off the plate, or stolen by a parent scroll — never reaches the click
        // handler, and a flag left set would silently swallow the next genuine tap.
        repeatedThisPress = false
        delay(StepperRepeat.HOLD_BEFORE_REPEAT_MS)
        repeatedThisPress = true
        while (true) {
            currentOnClick()
            Haptics.tickLight(view)
            delay(StepperRepeat.REPEAT_MS)
        }
    }

    val background by animateColorAsState(
        targetValue = when {
            pressed && enabled -> SurfacePressed
            emphasis -> Surface3
            else -> Surface2
        },
        animationSpec = instrumentTween(Motion.TAP),
        label = "stepper-press",
    )

    val sized = when {
        plateWidth != null && plateHeight != null ->
            modifier.size(width = plateWidth, height = plateHeight)
        else -> modifier
            .heightIn(min = if (compact) Metrics.touchMin else Metrics.commit)
            .then(if (compact) Modifier.widthIn(min = Metrics.touchMin) else Modifier)
    }
    val press = Modifier.clickable(
        enabled = enabled,
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
    )
    val chrome = Modifier
        .clip(shape)
        .background(background)
        .border(Metrics.hairline, if (emphasis) HairlineStrong else Hairline, shape)
    val style = (textStyle ?: if (compact) InstrumentType.bodyStrong else InstrumentType.numeralMd)
        .copy(textDirection = TextDirection.Ltr)
    val glyph: @Composable () -> Unit = {
        Text(
            label,
            modifier = Modifier.padding(horizontal = Metrics.space2, vertical = Metrics.space2),
            style = style,
            color = if (enabled) TextPrimary else TextDisabled,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
    // The inset plate draws a circle of fixed size, so its − / + is a fixed size too: the style's
    // design size read as dp, which the system font scale does not grow, laid out in the whole
    // circle rather than a padded slice of it. Scaled as text, at font 1.6 and 2.0 the glyph's
    // line outgrew the 20 dp the padding left it and was cut to "_" or "." (packet W1d). The
    // spoken words are the caller's, on the target, and do not change.
    val insetGlyph: @Composable () -> Unit = {
        val density = LocalDensity.current
        Text(
            label,
            style = LogLoopScale.fixedGlyph(style, density),
            color = if (enabled) TextPrimary else TextDisabled,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
    if (plateInset > 0.dp) {
        // Two boxes: the outer one is the target and owns the press, the hold-to-repeat and
        // the spoken role; the inner one is everything you can see, inset inside it. Only a
        // caller that fixed both dimensions can ask for this, which is why `matchParentSize`
        // is safe here — with nothing else to measure from, a wrapping parent would collapse.
        Box(modifier = sized.then(press), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.matchParentSize().padding(plateInset).then(chrome),
                contentAlignment = Alignment.Center,
            ) { insetGlyph() }
        }
    } else {
        // One box, exactly as before: callers that size from their own content need the fill
        // and the border on the box the content measures.
        Box(modifier = sized.then(chrome).then(press), contentAlignment = Alignment.Center) {
            glyph()
        }
    }
}
