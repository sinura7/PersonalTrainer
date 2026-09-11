package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import kotlinx.coroutines.delay

/**
 * Loading that looks like the cards that are coming, not a lone spinner.
 *
 * Local reads resolve in milliseconds, so this waits the same beat
 * [ScreenLoading] used to before drawing anything — a flash of ghost
 * cards is as noisy as a flash of a ring.
 */
@Composable
fun ScreenSkeleton(
    modifier: Modifier = Modifier,
    cards: Int = 3,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SKELETON_DELAY_MS)
        visible = true
    }
    if (!visible) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Metrics.gutter)
            .testTag(ScreenSkeletonTags.ROOT)
            .clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(Metrics.cardGap),
    ) {
        repeat(cards.coerceAtLeast(1)) {
            GymCard {
                SkeletonLiftRow()
            }
        }
    }
}

@Composable
private fun SkeletonLiftRow() {
    val bar = RoundedCornerShape(Radius.xs)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Box(
            modifier = Modifier
                .size(ThumbSize.header)
                .clip(bar)
                .background(Surface1),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Metrics.space4)
                    .clip(bar)
                    .background(Surface1),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(CAPTION_SHARE)
                    .height(Metrics.space3)
                    .clip(bar)
                    .background(Surface1),
            )
        }
    }
}

object ScreenSkeletonTags {
    const val ROOT = "screen-skeleton"
}

private const val SKELETON_DELAY_MS = 250L
private const val CAPTION_SHARE = 0.5f
