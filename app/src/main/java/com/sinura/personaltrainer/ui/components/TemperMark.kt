package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.Heat3
import com.sinura.personaltrainer.ui.theme.Steel
import com.sinura.personaltrainer.ui.theme.SteelDim

/**
 * The Temper mark, drawn — not a PNG — so empty states and About stay on the token layer.
 *
 * Same plates as the Body figure. The viewer's-right pec is Heat3; everything else is steel.
 * Volt stays out: this is identity, not a live action.
 */
@Composable
fun TemperMark(
    modifier: Modifier = Modifier,
    size: Dp = TemperMarkSize,
) {
    Canvas(
        modifier = modifier
            .size(width = size * FIGURE_ASPECT, height = size)
            .clearAndSetSemantics { },
    ) {
        drawTemperFigure(
            view = BodyView.FRONT,
            fill = { plate ->
                when {
                    plate.isTemperAccent() -> Heat3
                    plate.muscle == null -> SteelDim
                    else -> Steel
                }
            },
        )
    }
}

val TemperMarkSize = 80.dp
