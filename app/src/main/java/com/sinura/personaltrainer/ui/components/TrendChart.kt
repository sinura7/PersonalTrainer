package com.sinura.personaltrainer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.ChartNow
import com.sinura.personaltrainer.ui.theme.ChartPast
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * Two charts, drawn rather than charted, and each for a different kind of number.
 *
 * No charting library, for the same reason as before: this is a couple of dozen points on
 * one screen in an offline, sideloaded app, and a dependency that size to draw rectangles
 * would be a poor trade.
 *
 * What changed is that there are now two of them. [TrendBars] measures out of nothing —
 * weekly tonnage, where zero is a real value and the area of the bar is meaningful.
 * [LineTrend] measures a *level* — an estimated one-rep max, where zero is not a
 * meaningful floor and the whole story lives in a few percent of change.
 *
 * Drawing the second kind as the first was the single worst piece of information design in
 * the product: an e1RM going 100 → 102.5 → 105kg, scaled from zero against the series' own
 * maximum, drew as bars at 95%, 98% and 100% of the height — three visually identical
 * rectangles. The one chart that answers "am I getting stronger" was mathematically
 * incapable of showing that you were.
 */

/** Marker shape for a [TrendSeries] point when several gyms share one chart. */
enum class TrendPointShape {
    Circle,
    Square,
    Triangle,
    Diamond,
    Ring,
}

/** One gym's line on a shared plot; spacing is per-series session order. */
data class TrendSeries(
    val values: List<Double>,
    val pointShape: TrendPointShape = TrendPointShape.Circle,
)

private val SERIES_STROKES = listOf(ChartNow, Volt, PrGold, TextSecondary, ChartPast)

/** A trend of additive quantities. Zero is a real floor here, so bars start from it. */
@Composable
fun TrendBars(
    values: List<Double>,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    contentDescription: String? = null,
    progress: Float = 1f,
) {
    if (values.isEmpty()) return
    val max = values.maxOrNull() ?: 0.0
    // A gradient across the series rather than a flat fill, so the most recent bar is the
    // one wearing the accent — the eye lands on now, not on the oldest week on the left.
    val brush = Brush.horizontalGradient(listOf(ChartPast, ChartNow))
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        clipRect(right = size.width * progress.coerceIn(0f, 1f)) {
            val count = values.size
            // A single bar filling the full width reads as a block, not a trend; cap the share
            // any one bar takes so a first week still looks like the start of a series.
            val slot = size.width / count.coerceAtLeast(MIN_SLOTS)
            val barWidth = (slot * BAR_SHARE).coerceAtLeast(1f)
            val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

            // One hairline baseline, instead of a full-height track behind every bar. Those
            // tracks turned each week into "percent of my best week achieved", which is the
            // vocabulary of a habit meter, not of a measurement.
            drawRect(
                color = Hairline,
                topLeft = Offset(0f, size.height - 1f),
                size = Size(size.width, 1f),
            )

            values.forEachIndexed { index, value ->
                val fraction = if (max > 0.0) (value / max).toFloat() else 0f
                val barHeight = (size.height * fraction).coerceAtLeast(if (value > 0.0) MIN_BAR_PX else 0f)
                if (barHeight <= 0f) return@forEachIndexed
                val left = slot * index + (slot - barWidth) / 2f
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                    alpha = if (index == values.lastIndex) 1f else PAST_ALPHA,
                )
            }
        }
    }
}

/**
 * A trend of levels, on a domain focused around the data.
 *
 * The y-axis spans the series' own range with a margin, never zero to maximum, because for
 * a one-rep max the interesting movement is a few percent and anchoring at zero throws all
 * of it away.
 */
@Composable
fun LineTrend(
    values: List<Double>,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    contentDescription: String? = null,
    progress: Float = 1f,
    prValue: Double? = null,
    series: List<TrendSeries>? = null,
) {
    val plotted = series?.takeIf { it.size > 1 } ?: listOf(TrendSeries(values))
    if (plotted.all { it.values.isEmpty() }) return
    val allValues = plotted.flatMap { it.values }
    val seriesMin = allValues.min()
    val seriesMax = allValues.max()
    val min = minOf(seriesMin, prValue ?: seriesMin)
    val max = maxOf(seriesMax, prValue ?: seriesMax)
    // A flat series still needs a domain with width, or every point lands on one pixel.
    val span = (max - min).takeIf { it > EPSILON } ?: (max.takeIf { it > EPSILON }?.times(FLAT_SPAN_SHARE) ?: 1.0)
    val pad = span * DOMAIN_PAD_SHARE
    val low = min - pad
    val high = max + pad
    val range = (high - low).takeIf { it > EPSILON } ?: 1.0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        clipRect(right = size.width * progress.coerceIn(0f, 1f)) {
            val inset = DOT_RADIUS_PX * 2f
            val usableHeight = size.height - inset * 2f

            fun yFor(value: Double): Float {
                val fraction = ((value - low) / range).toFloat().coerceIn(0f, 1f)
                return inset + usableHeight * (1f - fraction)
            }

            fun pointsFor(lineValues: List<Double>): List<Offset> {
                val step = if (lineValues.size > 1) size.width / (lineValues.size - 1) else 0f
                return lineValues.indices.map { index ->
                    val x = if (lineValues.size > 1) step * index else size.width / 2f
                    Offset(x, yFor(lineValues[index]))
                }
            }

            prValue?.let { mark ->
                val y = yFor(mark)
                drawLine(
                    color = PrGold,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f),
                )
            }

            plotted.forEachIndexed { seriesIndex, line ->
                val lineValues = line.values
                if (lineValues.isEmpty()) return@forEachIndexed
                val strokeColor = SERIES_STROKES[seriesIndex % SERIES_STROKES.size]
                val points = pointsFor(lineValues)
                if (points.size > 1) {
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = linePath,
                        color = strokeColor,
                        style = Stroke(width = LINE_WIDTH_PX, cap = StrokeCap.Round),
                    )
                }
                val mark = points.lastOrNull() ?: return@forEachIndexed
                drawTrendPoint(strokeColor, mark, line.pointShape)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrendPoint(
    color: Color,
    center: Offset,
    shape: TrendPointShape,
) {
    when (shape) {
        TrendPointShape.Circle -> drawCircle(color = color, radius = DOT_RADIUS_PX, center = center)
        TrendPointShape.Square -> {
            val half = DOT_RADIUS_PX
            drawRect(color = color, topLeft = Offset(center.x - half, center.y - half), size = Size(half * 2f, half * 2f))
        }
        TrendPointShape.Triangle -> {
            val path = Path().apply {
                moveTo(center.x, center.y - DOT_RADIUS_PX)
                lineTo(center.x + DOT_RADIUS_PX, center.y + DOT_RADIUS_PX)
                lineTo(center.x - DOT_RADIUS_PX, center.y + DOT_RADIUS_PX)
                close()
            }
            drawPath(path, color)
        }
        TrendPointShape.Diamond -> {
            val path = Path().apply {
                moveTo(center.x, center.y - DOT_RADIUS_PX)
                lineTo(center.x + DOT_RADIUS_PX, center.y)
                lineTo(center.x, center.y + DOT_RADIUS_PX)
                lineTo(center.x - DOT_RADIUS_PX, center.y)
                close()
            }
            drawPath(path, color)
        }
        TrendPointShape.Ring -> {
            drawCircle(color = color, radius = DOT_RADIUS_PX, center = center, style = Stroke(width = 3f))
        }
    }
}

/**
 * A chart with the annotation that makes it readable.
 *
 * The pair of end labels used to be the entire annotation, and the two charts on one screen
 * used those two slots for different things — dates on one, values on the other — so the
 * grammar changed between cards and no magnitude appeared anywhere on the volume chart at
 * all. Here the current value is stated as a numeral above the plot, the change against the
 * previous point sits beside it, and the end labels are always the range of the x-axis.
 */
@Composable
fun LabelledTrend(
    title: String,
    values: List<Double>,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    headlineValue: String? = null,
    headlineUnit: String? = null,
    deltaLabel: String? = null,
    deltaIsGain: Boolean = true,
    line: Boolean = false,
    contentDescription: String? = null,
    prValue: Double? = null,
    series: List<TrendSeries>? = null,
) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(values, reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                tween(durationMillis = Motion.DRAW, easing = Motion.Standard),
            )
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
        Kicker(title)
        if (headlineValue != null) {
            FlowRow(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                Text(
                    headlineValue,
                    modifier = Modifier.alignByBaseline(),
                    style = InstrumentType.numeralLg,
                    color = TextPrimary,
                )
                if (headlineUnit != null) {
                    Text(
                        headlineUnit,
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = Metrics.space1),
                        style = InstrumentType.unit,
                        color = TextSecondary,
                    )
                }
                if (deltaLabel != null) {
                    Text(
                        deltaLabel,
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = Metrics.space2),
                        style = InstrumentType.bodyStrong,
                        // A down week is not an error. It is reported in the quiet colour,
                        // never in red.
                        color = if (deltaIsGain) Volt else TextSecondary,
                    )
                }
            }
        }
        if (line) {
            LineTrend(
                values = values,
                contentDescription = contentDescription,
                progress = progress.value,
                prValue = prValue,
                series = series,
            )
        } else {
            TrendBars(
                values = values,
                contentDescription = contentDescription,
                progress = progress.value,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(startLabel, style = InstrumentType.caption, color = TextTertiary)
            Text(endLabel, style = InstrumentType.caption, color = TextTertiary)
        }
    }
}

private const val BAR_SHARE = 0.62f
private const val MIN_SLOTS = 6
private const val MIN_BAR_PX = 3f
private const val PAST_ALPHA = 0.55f
private const val AREA_ALPHA = 0.14f
private const val LINE_WIDTH_PX = 5f
private const val DOT_RADIUS_PX = 7f
private const val DOMAIN_PAD_SHARE = 0.15
private const val FLAT_SPAN_SHARE = 0.1
private const val EPSILON = 1e-6
