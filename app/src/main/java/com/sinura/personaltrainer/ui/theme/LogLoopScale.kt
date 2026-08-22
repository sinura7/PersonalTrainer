package com.sinura.personaltrainer.ui.theme

/**
 * How the log loop yields when the system font is huge.
 *
 * We do not clamp [androidx.compose.ui.unit.Density.fontScale]. A 2.0 user is
 * reading at 2.0. Rows wrap, floors become `heightIn`, and the numeral they
 * are about to log is never ellipsised. Names may still truncate.
 *
 * Side-by-side wells clip a three-digit half-kilo (102.5) once the scale
 * passes [STACK_WELLS_FROM] — half a phone is no longer wide enough. Stack
 * them. That is layout, not a second type scale.
 */
object LogLoopScale {
    const val STACK_WELLS_FROM = 1.6f

    fun stackEntryWells(fontScale: Float): Boolean = fontScale >= STACK_WELLS_FROM

    fun headlineLines(fontScale: Float): Int = if (fontScale >= STACK_WELLS_FROM) 3 else 2
}
