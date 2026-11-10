package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.SectionEdge
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

data class StatStripCell(
    val kicker: String,
    val value: String,
    val unit: String? = null,
    val valueColor: Color = TextPrimary,
)

@Composable
fun StatStrip(
    cells: List<StatStripCell>,
    modifier: Modifier = Modifier,
) {
    require(cells.size in 2..3) { "StatStrip supports 2 or 3 cells" }
    val stack = LocalDensity.current.fontScale >= LogLoopScale.STACK_WELLS_FROM
    val shape = RoundedCornerShape(Radius.md)
    if (stack) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Surface1)
                .borderedPanel(shape)
                .padding(vertical = Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            cells.forEach { cell -> StatStripStackedRow(cell) }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.statStripMin)
                .clip(shape)
                .background(Surface1)
                .borderedPanel(shape),
        ) {
            cells.forEachIndexed { index, cell ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(Metrics.hairline)
                            .heightIn(min = Metrics.statStripMin)
                            .background(Hairline),
                    )
                }
                StatStripCell(cell, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatStripCell(cell: StatStripCell, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = Metrics.statStripMin)
            .padding(horizontal = Metrics.space3, vertical = Metrics.space2),
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Kicker(cell.kicker, color = TextSecondary, asHeading = false)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            Text(
                cell.value,
                style = InstrumentType.numeralLg,
                color = cell.valueColor,
                textAlign = TextAlign.Center,
            )
            if (cell.unit != null) {
                Text(
                    cell.unit,
                    modifier = Modifier.padding(start = Metrics.space1),
                    style = InstrumentType.unit,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun StatStripStackedRow(cell: StatStripCell) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Metrics.space4, vertical = Metrics.space1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Kicker(cell.kicker, color = TextSecondary, asHeading = false)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(cell.value, style = InstrumentType.numeralLg, color = cell.valueColor)
            if (cell.unit != null) {
                Text(
                    cell.unit,
                    modifier = Modifier.padding(start = Metrics.space1),
                    style = InstrumentType.unit,
                    color = TextSecondary,
                )
            }
        }
    }
}

private fun Modifier.borderedPanel(shape: RoundedCornerShape): Modifier =
    border(Metrics.hairline, SectionEdge, shape)
