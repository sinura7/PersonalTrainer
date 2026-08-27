package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.R

/**
 * The Temper mark: the locked front still, not a second drawing of it.
 *
 * Volt stays out: this is identity, not a live action.
 */
@Composable
fun TemperMark(
    modifier: Modifier = Modifier,
    size: Dp = TemperMarkSize,
) {
    Image(
        painter = painterResource(R.drawable.temper_front_heat),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { },
        contentScale = ContentScale.Fit,
    )
}

val TemperMarkSize = 80.dp
