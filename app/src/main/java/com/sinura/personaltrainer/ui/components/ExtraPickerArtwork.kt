package com.sinura.personaltrainer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius

/**
 * Extra / cardio picker pictures. Cardio has its own stills; warm-up and
 * mobility rows reuse the catalog still named on [AuxiliaryPacks] (ADR-022).
 */
@DrawableRes
internal fun cardioPickerArtwork(type: CardioType): Int = when (type) {
    CardioType.WALK -> R.drawable.cardio_walk
    CardioType.RUN -> R.drawable.cardio_run
    CardioType.RIDE -> R.drawable.cardio_ride
    CardioType.ROW -> R.drawable.cardio_row
    CardioType.SWIM -> R.drawable.cardio_swim
    CardioType.HIKE -> R.drawable.cardio_hike
    CardioType.SKI, CardioType.OTHER -> R.drawable.temper_front_unlit
}

@DrawableRes
internal fun extraPackArtwork(packId: String): Int {
    val key = AuxiliaryPacks.byId(packId)?.imageKey ?: return R.drawable.temper_front_unlit
    return keyedArtwork(key) ?: R.drawable.temper_front_unlit
}

/**
 * One keyed still without an equipment badge. Extra packs are not a lift
 * row; the badge would name the wrong thing.
 */
@Composable
fun PickerStill(
    @DrawableRes art: Int,
    modifier: Modifier = Modifier,
    size: Dp = ThumbSize.picker,
) {
    val resources = LocalResources.current
    val still by produceState<ImageBitmap?>(
        initialValue = ThumbCache.peek(art, ThumbCache.THUMB_SAMPLE),
        key1 = art,
    ) {
        value = ThumbCache.load(resources, art, ThumbCache.THUMB_SAMPLE)
    }
    val shape = RoundedCornerShape(Radius.xs)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Pit)
            .border(Metrics.hairline, Hairline, shape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        still?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Metrics.space1),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
