package com.sinura.personaltrainer.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

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
 *
 * Stat-tile numerals are the exception: 36 sp at 2.0 is 72 points in a
 * fixed-width tile and becomes "1,2…". [tileNumeral] keeps the painted
 * size near the 36 sp design size once wells stack.
 */
object LogLoopScale {
    const val STACK_WELLS_FROM = 1.6f
    private const val TILE_NUMERAL_SP = 36f
    private const val TILE_NUMERAL_LINE_SP = 40f

    fun stackEntryWells(fontScale: Float): Boolean = fontScale >= STACK_WELLS_FROM

    fun stackTiles(fontScale: Float): Boolean = stackEntryWells(fontScale)

    fun hideLiveBarCluster(fontScale: Float): Boolean = stackEntryWells(fontScale)

    fun headlineLines(fontScale: Float): Int = if (stackEntryWells(fontScale)) 3 else 2

    fun tileNumeral(fontScale: Float): TextStyle {
        val scale = fontScale.coerceAtLeast(1f)
        if (scale < STACK_WELLS_FROM) return InstrumentType.numeralLg
        return InstrumentType.numeralLg.copy(
            fontSize = (TILE_NUMERAL_SP / scale).sp,
            lineHeight = (TILE_NUMERAL_LINE_SP / scale).sp,
        )
    }
}
