package com.sinura.personaltrainer.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/**
 * System animator scale, previews, and the page-level reduced-motion
 * pass (P9.2 / FND-045 / ADR-023). One policy: durations collapse to
 * zero. This is not a second theme. The host Activity re-reads the
 * scales on resume so a Settings change is picked up without process
 * death.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

fun systemReduceMotion(context: Context): Boolean {
    val resolver = context.contentResolver
    val animator = Settings.Global.getFloat(
        resolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    val transition = Settings.Global.getFloat(
        resolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        1f,
    )
    return animator == 0f || transition == 0f
}

@Composable
fun <T> instrumentTween(durationMs: Int): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else tween(durationMs)

@Composable
fun <T> instrumentLinear(durationMs: Int): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) {
        snap()
    } else {
        tween(durationMs, easing = LinearEasing)
    }

@Composable
fun recordEnter(): EnterTransition =
    if (LocalReducedMotion.current) {
        fadeIn(snap())
    } else {
        scaleIn(initialScale = 0.92f, animationSpec = Motion.celebrate()) +
            fadeIn(tween(Motion.FAST))
    }

@Composable
fun LazyItemScope.instrumentAnimateItem(): Modifier =
    if (LocalReducedMotion.current) {
        Modifier
    } else {
        Modifier.animateItem(placementSpec = Motion.settle<IntOffset>())
    }

/**
 * The motion vocabulary.
 *
 * Motion here is mechanical: short, decisive, spring-settled, and paired with a haptic
 * whenever a piece of data is committed. Nothing floats and nothing bounces twice.
 * Reduced motion collapses token durations to zero (ADR-005 §5, ADR-023).
 */
object Motion {
    /** Press feedback and chip selection. */
    const val TAP = 90

    /** Colour changes, small reveals. */
    const val FAST = 150

    /** The default: content swaps, expansions, screen transitions. */
    const val BASE = 240

    /** One-shot reveals that are meant to be watched, such as a chart drawing in. */
    const val DRAW = 650

    /** Rest-clock pulse while the last ten seconds run. */
    const val PULSE_MS = 500

    /** Rest sweep tracks one civil second. */
    const val TICK_MS = 1_000

    /** Record-banner gold flash. */
    const val FLASH_MS = 900
    const val FLASH_DELAY_MS = 120

    /** Stagger between personal-record lines on the summary. */
    const val RECORD_STAGGER_MS = 140L

    /** How long a status banner stays readable. Not collapsed by reduced motion. */
    const val STATUS_DWELL_MS = 2_600L

    /** Gold flash on a finished rest before the dock returns to idle. */
    const val FINISHED_DWELL_MS = 3_500L

    fun durationMs(reduced: Boolean, fullMs: Int): Int = if (reduced) 0 else fullMs

    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Layout settling and list placement. */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Reserved for a record breaking. The only place in the app allowed to bounce. */
    fun <T> celebrate(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 600f)
}
