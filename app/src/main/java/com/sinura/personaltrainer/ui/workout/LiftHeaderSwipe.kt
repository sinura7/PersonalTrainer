package com.sinura.personaltrainer.ui.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Motion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val SwipeCommitThreshold = 72.dp
private val EdgeBounceMax = 28.dp

/**
 * Horizontal drag on the lift identity: previous / next in session order. At the first or last
 * lift the drag rubber-bands and does not switch. [detectHorizontalDragGestures] keeps vertical
 * list scrolling from accidental switches.
 */
internal fun Modifier.liftHeaderSwipe(
    enabled: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier = composed {
    if (!enabled) return@composed this
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val commitPx = with(density) { SwipeCommitThreshold.toPx() }
    val bounceMaxPx = with(density) { EdgeBounceMax.toPx() }
    val edgeBounceBack: AnimationSpec<Float> = if (LocalReducedMotion.current) snap() else Motion.celebrate()
    val cancelBack: AnimationSpec<Float> = if (LocalReducedMotion.current) snap() else Motion.settle()
    this
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .pointerInput(canPrevious, canNext) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    val atStart = !canPrevious && dragAmount > 0f
                    val atEnd = !canNext && dragAmount < 0f
                    val raw = offsetX.value + dragAmount
                    val next = when {
                        atStart || atEnd -> raw.coerceIn(-bounceMaxPx, bounceMaxPx)
                        else -> raw
                    }
                    scope.launch { offsetX.snapTo(next) }
                },
                onDragEnd = {
                    val delta = offsetX.value
                    when {
                        delta <= -commitPx && canNext -> {
                            onNext()
                            scope.launch { offsetX.snapTo(0f) }
                        }
                        delta >= commitPx && canPrevious -> {
                            onPrevious()
                            scope.launch { offsetX.snapTo(0f) }
                        }
                        else -> scope.launch { offsetX.animateTo(0f, edgeBounceBack) }
                    }
                },
                onDragCancel = {
                    scope.launch { offsetX.animateTo(0f, cancelBack) }
                },
            )
        }
}
