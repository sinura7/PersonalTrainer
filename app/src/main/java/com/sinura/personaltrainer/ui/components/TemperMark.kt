package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.R

/**
 * The Temper mark: the locked front still, not a second drawing of it.
 *
 * Volt stays out: this is identity, not a live action. Decoded through
 * [ThumbCache] so an empty-state 80dp mark does not inflate a 768 still
 * on the main thread.
 */
@Composable
fun TemperMark(
    modifier: Modifier = Modifier,
    size: Dp = TemperMarkSize,
) {
    val resources = LocalResources.current
    val art = R.drawable.temper_front_heat
    val still by produceState<ImageBitmap?>(
        initialValue = ThumbCache.peek(art, ThumbCache.MARK_SAMPLE),
        key1 = art,
    ) {
        value = ThumbCache.load(resources, art, ThumbCache.MARK_SAMPLE)
    }
    still?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clearAndSetSemantics { },
            contentScale = ContentScale.Fit,
        )
    }
}

val TemperMarkSize = 80.dp
