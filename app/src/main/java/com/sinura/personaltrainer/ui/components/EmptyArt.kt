package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Steel
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * Gym-floor empty pictures, drawn — not a second PNG pack, not the
 * Temper still reused as a shrug.
 *
 * Unit box is 0..1. Tests dump this list; [EmptyIllustration] paints it.
 */
internal enum class EmptyInkKind { STEEL, VOLT }

internal sealed class EmptyInk {
    abstract val kind: EmptyInkKind

    data class Line(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        override val kind: EmptyInkKind,
    ) : EmptyInk()

    data class RoundRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val radius: Float,
        val dashed: Boolean,
        override val kind: EmptyInkKind,
    ) : EmptyInk()

    data class Arc(
        val cx: Float,
        val cy: Float,
        val r: Float,
        val startDeg: Float,
        val sweepDeg: Float,
        override val kind: EmptyInkKind,
    ) : EmptyInk()
}

internal fun inkFor(scene: EmptyScene): List<EmptyInk> = when (scene) {
    EmptyScene.RACK -> rackInk()
    EmptyScene.PLAN -> planInk()
    EmptyScene.CATALOG -> catalogInk()
    EmptyScene.LOG -> logInk()
    EmptyScene.GONE -> goneInk()
    EmptyScene.RETRY -> retryInk()
}

internal fun emptyInkPoints(ink: EmptyInk): List<Pair<Float, Float>> = when (ink) {
    is EmptyInk.Line -> listOf(ink.x1 to ink.y1, ink.x2 to ink.y2)
    is EmptyInk.RoundRect -> listOf(
        ink.left to ink.top,
        ink.right to ink.bottom,
    )
    is EmptyInk.Arc -> listOf(
        (ink.cx - ink.r) to (ink.cy - ink.r),
        (ink.cx + ink.r) to (ink.cy + ink.r),
    )
}

private fun rackInk(): List<EmptyInk> = listOf(
    steelLine(0.22f, 0.10f, 0.22f, 0.90f),
    steelLine(0.78f, 0.10f, 0.78f, 0.90f),
    steelLine(0.22f, 0.40f, 0.38f, 0.40f),
    steelLine(0.38f, 0.40f, 0.38f, 0.50f),
    steelLine(0.78f, 0.40f, 0.62f, 0.40f),
    steelLine(0.62f, 0.40f, 0.62f, 0.50f),
) + plus(0.50f, 0.40f, 0.10f)

private fun planInk(): List<EmptyInk> {
    val ticks = (0 until 7).map { i ->
        val x = 0.12f + i * 0.12f
        steelLine(x, 0.72f, x, 0.86f)
    }
    val bars = (0 until 3).map { i ->
        val x = 0.12f + i * 0.12f
        steelRect(x - 0.035f, 0.28f, x + 0.035f, 0.62f, 0.02f)
    }
    return ticks + bars + plus(0.12f + 3 * 0.12f, 0.44f, 0.08f)
}

private fun catalogInk(): List<EmptyInk> {
    val tiles = listOf(
        0.14f to 0.14f,
        0.52f to 0.14f,
        0.14f to 0.52f,
    ).map { (l, t) -> steelRect(l, t, l + 0.34f, t + 0.34f, 0.06f) }
    val plusTile = steelRect(0.52f, 0.52f, 0.86f, 0.86f, 0.06f)
    return tiles + plusTile + plus(0.69f, 0.69f, 0.08f)
}

private fun logInk(): List<EmptyInk> = listOf(
    steelRect(0.16f, 0.14f, 0.86f, 0.40f, 0.05f),
    steelRect(0.12f, 0.36f, 0.82f, 0.62f, 0.05f),
    steelRect(0.18f, 0.58f, 0.88f, 0.86f, 0.05f),
) + plus(0.53f, 0.72f, 0.08f)

private fun goneInk(): List<EmptyInk> = listOf(
    EmptyInk.RoundRect(
        left = 0.16f,
        top = 0.22f,
        right = 0.84f,
        bottom = 0.78f,
        radius = 0.08f,
        dashed = true,
        kind = EmptyInkKind.STEEL,
    ),
)

private fun retryInk(): List<EmptyInk> = listOf(
    EmptyInk.Arc(
        cx = 0.50f,
        cy = 0.50f,
        r = 0.30f,
        startDeg = -40f,
        sweepDeg = 280f,
        kind = EmptyInkKind.STEEL,
    ),
) + plus(0.72f, 0.28f, 0.08f)

private fun steelLine(x1: Float, y1: Float, x2: Float, y2: Float) =
    EmptyInk.Line(x1, y1, x2, y2, EmptyInkKind.STEEL)

private fun steelRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
) = EmptyInk.RoundRect(left, top, right, bottom, radius, dashed = false, EmptyInkKind.STEEL)

private fun plus(cx: Float, cy: Float, arm: Float): List<EmptyInk> = listOf(
    EmptyInk.Line(cx - arm, cy, cx + arm, cy, EmptyInkKind.VOLT),
    EmptyInk.Line(cx, cy - arm, cx, cy + arm, EmptyInkKind.VOLT),
)

internal fun DrawScope.drawEmptyScene(scene: EmptyScene) {
    val w = size.minDimension
    val hair = Metrics.hairline.toPx().coerceAtLeast(1f)
    val plusStroke = Metrics.emphasisBorder.toPx().coerceAtLeast(hair * 2f)
    inkFor(scene).forEach { ink ->
        val color = if (ink.kind == EmptyInkKind.VOLT) Volt else Steel
        val stroke = Stroke(
            width = if (ink.kind == EmptyInkKind.VOLT) plusStroke else hair,
            cap = StrokeCap.Round,
            pathEffect = dashFor(ink, w),
        )
        when (ink) {
            is EmptyInk.Line -> drawLine(
                color = color,
                start = Offset(ink.x1 * w, ink.y1 * w),
                end = Offset(ink.x2 * w, ink.y2 * w),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            is EmptyInk.RoundRect -> {
                val left = ink.left * w
                val top = ink.top * w
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size((ink.right - ink.left) * w, (ink.bottom - ink.top) * w),
                    cornerRadius = CornerRadius(ink.radius * w, ink.radius * w),
                    style = stroke,
                )
            }
            is EmptyInk.Arc -> {
                val d = ink.r * 2f * w
                drawArc(
                    color = color,
                    startAngle = ink.startDeg,
                    sweepAngle = ink.sweepDeg,
                    useCenter = false,
                    topLeft = Offset((ink.cx - ink.r) * w, (ink.cy - ink.r) * w),
                    size = Size(d, d),
                    style = stroke,
                )
            }
        }
    }
}

private fun dashFor(ink: EmptyInk, box: Float): PathEffect? {
    val dashed = (ink as? EmptyInk.RoundRect)?.dashed == true
    if (!dashed) return null
    val on = (box * 0.06f).coerceAtLeast(4f)
    return PathEffect.dashPathEffect(floatArrayOf(on, on * 0.7f))
}

val EmptyArtSize = 96.dp
val EmptyArtCompactSize = 56.dp

object EmptyTags {
    fun art(scene: EmptyScene): String = "empty-art-${scene.name.lowercase()}"
}

@Composable
fun EmptyIllustration(
    scene: EmptyScene,
    modifier: Modifier = Modifier,
    size: Dp = EmptyArtSize,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .testTag(EmptyTags.art(scene))
            .clearAndSetSemantics { },
    ) {
        drawEmptyScene(scene)
    }
}
