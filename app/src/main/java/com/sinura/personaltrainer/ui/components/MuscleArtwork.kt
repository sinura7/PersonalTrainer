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
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius

/**
 * One Temper still per mapped Body muscle.
 *
 * Identity, not live load: a fixed Heat3 wash on that body part, cropped
 * from the same person as the Body figure. Weekly heat stays the silhouette.
 * [CanonicalMuscle.OTHER] is not a mapped row; it reuses the unlit front
 * figure so a stray full-body credit cannot blank the leading slot.
 */
@DrawableRes
internal fun muscleArtwork(muscle: CanonicalMuscle): Int = when (muscle) {
    CanonicalMuscle.CHEST -> R.drawable.muscle_chest
    CanonicalMuscle.BACK -> R.drawable.muscle_back
    CanonicalMuscle.SHOULDERS -> R.drawable.muscle_shoulders
    CanonicalMuscle.BICEPS -> R.drawable.muscle_biceps
    CanonicalMuscle.TRICEPS -> R.drawable.muscle_triceps
    CanonicalMuscle.QUADRICEPS -> R.drawable.muscle_quadriceps
    CanonicalMuscle.HAMSTRINGS -> R.drawable.muscle_hamstrings
    CanonicalMuscle.GLUTES -> R.drawable.muscle_glutes
    CanonicalMuscle.CALVES -> R.drawable.muscle_calves
    CanonicalMuscle.CORE -> R.drawable.muscle_core
    CanonicalMuscle.OTHER -> R.drawable.temper_front_unlit
}

/**
 * The picture beside a Body muscle name.
 *
 * Decorative: the row already speaks the muscle, recency, and See lifts.
 */
@Composable
fun MuscleStill(
    muscle: CanonicalMuscle,
    modifier: Modifier = Modifier,
    size: Dp = ThumbSize.row,
) {
    val art = muscleArtwork(muscle)
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
