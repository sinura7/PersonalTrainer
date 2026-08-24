package com.sinura.personaltrainer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Accessibility setting consumed by previews now and by the page-level
 * reduced-motion pass in Phase 9.
 *
 * Defining the seam here prevents screenshot fixtures from inventing a
 * second motion policy. Existing animations keep their current behavior
 * until each is deliberately mapped in that pass.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

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
