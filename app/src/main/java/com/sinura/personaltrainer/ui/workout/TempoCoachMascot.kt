package com.sinura.personaltrainer.ui.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.instrumentTween

/**
 * Look B mascot ([R.drawable.tempo_coach_avatar], wired in PR #457 / #460). One subtle
 * pulse per [appearanceKey] when motion is allowed.
 */
@Composable
internal fun TempoCoachMascot(
    appearanceKey: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val reducedMotion = LocalReducedMotion.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(appearanceKey, reducedMotion) {
        scale.snapTo(1f)
        if (reducedMotion) return@LaunchedEffect
        scale.animateTo(1.1f, instrumentTween(150))
        scale.animateTo(1f, instrumentTween(240))
    }
    Image(
        painter = painterResource(R.drawable.tempo_coach_avatar),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .scale(scale.value),
        contentScale = ContentScale.Crop,
    )
}
