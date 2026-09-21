package com.sinura.personaltrainer.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sinura.personaltrainer.domain.ColdStartIntroCopy
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.TemperMark
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim
import kotlinx.coroutines.delay

object ColdStartIntroTags {
    const val ROOT = "cold-start-intro"
    const val SKIP = "cold-start-intro-skip"
}

@Composable
fun ColdStartIntro(
    onFinished: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val finishOnce = remember(onFinished) {
        var done = false
        {
            if (!done) {
                done = true
                Haptics.tickLight(view)
                onFinished()
            }
        }
    }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            finishOnce()
            return@LaunchedEffect
        }
        delay(ColdStartIntroCopy.AUTO_ADVANCE_MS)
        finishOnce()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Pit)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = finishOnce,
            )
            .semantics {
                contentDescription = "${ColdStartIntroCopy.WORDMARK}. ${ColdStartIntroCopy.SKIP}"
            }
            .testTag(ColdStartIntroTags.ROOT),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(VoltDim, Pit),
                        radius = 900f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Metrics.space8 * 6)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(VoltDim, Color.Transparent),
                    ),
                ),
        )

        ColdStartIntroContent(
            reduceMotion = reduceMotion,
            modifier = Modifier.align(Alignment.Center),
        )

        Text(
            text = ColdStartIntroCopy.SKIP,
            style = InstrumentType.caption,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Metrics.gutter * 2)
                .fillMaxWidth()
                .testTag(ColdStartIntroTags.SKIP),
        )
    }
}

@Composable
private fun ColdStartIntroContent(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val reveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) {
            reveal.animateTo(1f, Motion.introRevealSpec())
        }
    }

    val pulse by if (!reduceMotion) {
        rememberInfiniteTransition(label = "intro-volt-pulse").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = Motion.introGlowPulseSpec(),
            label = "intro-volt-alpha",
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }

    Column(
        modifier = modifier
            .padding(horizontal = Metrics.gutter)
            .graphicsLayer {
                alpha = reveal.value
                scaleX = 0.92f + reveal.value * 0.08f
                scaleY = 0.92f + reveal.value * 0.08f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(Metrics.space8 * 7)
                    .graphicsLayer { alpha = pulse }
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(VoltDim),
            )
            TemperMark(size = Metrics.space8 * 5)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap),
        ) {
            Kicker(
                text = ColdStartIntroCopy.KICKER,
                color = TextSecondary,
            )
            Text(
                text = ColdStartIntroCopy.WORDMARK,
                style = InstrumentType.display,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = ColdStartIntroCopy.TAGLINE,
                style = InstrumentType.body,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(reveal.value.coerceIn(0.2f, 1f))
                .height(Metrics.emphasisBorder)
                .clip(RoundedCornerShape(Radius.sm))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Pit, Volt, Pit),
                    ),
                ),
        )
        Spacer(modifier = Modifier.height(Metrics.kickerGap))
    }
}
