package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A bar trend, drawn rather than charted.
 *
 * No charting library: this is one screen showing at most a couple of dozen bars, and the
 * whole app is offline and sideloaded — a dependency the size of a chart library to draw
 * rectangles would be a poor trade.
 *
 * Bars are scaled to the series' own maximum, so the shape shows change over time rather than
 * absolute size. That is the honest reading of a personal trend: the question is "more or less
 * than my other weeks", not "how does this compare to nothing".
 */
@Composable
fun TrendBars(
    values: List<Double>,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    if (values.isEmpty()) return
    val max = values.maxOrNull() ?: 0.0
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val count = values.size
        // A single bar filling the full width reads as a block, not a trend; cap the share
        // any one bar takes so a first week still looks like the start of a series.
        val slot = size.width / count.coerceAtLeast(MIN_SLOTS)
        val barWidth = (slot * BAR_SHARE).coerceAtLeast(1f)
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
        values.forEachIndexed { index, value ->
            val fraction = if (max > 0.0) (value / max).toFloat() else 0f
            val barHeight = (size.height * fraction).coerceAtLeast(if (value > 0.0) MIN_BAR_PX else 0f)
            val left = slot * index + (slot - barWidth) / 2f
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = radius,
            )
            if (barHeight > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/** [TrendBars] with the labels that make the bars mean something. */
@Composable
fun LabelledTrend(
    title: String,
    values: List<Double>,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        TrendBars(values = values)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                startLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                endLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val BAR_SHARE = 0.62f
private const val MIN_SLOTS = 6
private const val MIN_BAR_PX = 3f
