package com.sinura.personaltrainer.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * System animator scale, previews, and the page-level reduced-motion
 * pass (P9.2 / FND-045). One policy: durations collapse to zero. This
 * is not a second theme. The host Activity re-reads the scales on
 * resume so a Settings change is picked up without process death.
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

/**
 * The motion vocabulary.
 *
 * There were three animations in the entire app before this — a 900ms alpha blink on the
 * rest bar, one `animateContentSize`, and a pair of default-spec colour lerps — and no two
 * of them shared a duration or a curve, because there was nowhere to put one. Consistency
 * of timing is most of what separates motion that feels engineered from motion that feels
 * decorative, and it is structurally impossible without a file like this.
 *
 * Motion here is mechanical: short, decisive, spring-settled, and paired with a haptic
 * whenever a piece of data is committed. Nothing floats and nothing bounces twice.
 */
object Motion {
    /** Press feedback and chip selection. */
    const val TAP = 90

    /** Colour changes, small reveals. */
    const val FAST = 150

    /** The default: content swaps, expansions, screen transitions. */
    const val BASE = 240

    /** Sheets, and the rest timer changing state. */
    const val SLOW = 350

    /** One-shot reveals that are meant to be watched, such as a chart drawing in. */
    const val DRAW = 650

    fun durationMs(reduced: Boolean, fullMs: Int): Int = if (reduced) 0 else fullMs

    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Pressed states: stiff, barely overshooting. */
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 900f)

    /** Layout settling and list placement. */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Reserved for a record breaking. The only place in the app allowed to bounce. */
    fun <T> celebrate(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 600f)
}
