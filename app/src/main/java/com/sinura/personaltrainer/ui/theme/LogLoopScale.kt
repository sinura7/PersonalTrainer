package com.sinura.personaltrainer.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
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
 *
 * Three more places yield by size rather than by layout (packet W1d): a
 * stats value too wide for its cell steps down to fit one line
 * ([statValueFloor]); a hero value wider than its sample steps down the
 * numeral ramp ([fittedNumeral]); and a glyph inside a plate of fixed size
 * is drawn at a fixed size ([fixedGlyph]).
 */
object LogLoopScale {
    const val STACK_WELLS_FROM = 1.6f
    private const val TILE_NUMERAL_SP = 36f
    private const val TILE_NUMERAL_LINE_SP = 40f

    fun stackEntryWells(fontScale: Float): Boolean = fontScale >= STACK_WELLS_FROM

    fun stackTiles(fontScale: Float): Boolean = stackEntryWells(fontScale)

    fun headlineLines(fontScale: Float): Int = if (stackEntryWells(fontScale)) 3 else 2

    /**
     * A stats value laid out at its floor: `numeralSm`'s face and line at the caption's size. The
     * value is drawn at the largest size from `numeralSm` down to this one that holds it on one
     * line, and never smaller: the number is not set below the label that names it, which the
     * reader has already chosen to read at this scale.
     *
     * The line is not trimmed to the glyphs, so a value drawn smaller keeps `numeralSm`'s line
     * and its baseline where a full-size value's sits; at full size nothing changes.
     */
    val statValueFloor: TextStyle = InstrumentType.numeralSm.copy(
        fontSize = InstrumentType.caption.fontSize,
        lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Proportional, trim = LineHeightStyle.Trim.None),
    )

    /** How far a stats value steps down at a time on its way to fitting. */
    val STAT_VALUE_STEP: TextUnit = 0.5.sp

    /**
     * The hero numeral's style for [text] in [roomPx]: the sample's [style] whenever the value
     * fits, and otherwise the first smaller size in the ramp — `numeralLg`, then `numeralMd` —
     * that does. A value too wide even for `numeralMd` is drawn at `numeralMd` scaled to the
     * room: the one size outside the ramp the numeral is drawn at, and only for a value no real
     * load reaches.
     */
    fun fittedNumeral(text: String, style: TextStyle, roomPx: Int, widthPx: (String, TextStyle) -> Int): TextStyle {
        if (widthPx(text, style) <= roomPx) return style
        listOf(InstrumentType.numeralLg, InstrumentType.numeralMd)
            .filter { it.fontSize < style.fontSize }
            .firstOrNull { widthPx(text, it) <= roomPx }
            ?.let { return it }
        val floor = InstrumentType.numeralMd
        val scale = (roomPx.toFloat() / widthPx(text, floor)).coerceAtMost(1f)
        return floor.copy(fontSize = floor.fontSize * scale, lineHeight = floor.lineHeight * scale)
    }

    /**
     * [style] at its design size read as dp, which the system font scale does not grow: for a
     * glyph that fills a plate of fixed size, such as the entry's − and +, where text that grew
     * would outgrow the plate and be cut.
     */
    fun fixedGlyph(style: TextStyle, density: Density): TextStyle = with(density) {
        style.copy(fontSize = style.fontSize.value.dp.toSp(), lineHeight = style.lineHeight.value.dp.toSp())
    }

    fun tileNumeral(fontScale: Float): TextStyle {
        val scale = fontScale.coerceAtLeast(1f)
        if (scale < STACK_WELLS_FROM) return InstrumentType.numeralLg
        return InstrumentType.numeralLg.copy(
            fontSize = (TILE_NUMERAL_SP / scale).sp,
            lineHeight = (TILE_NUMERAL_LINE_SP / scale).sp,
        )
    }
}
