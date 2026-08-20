package com.sinura.personaltrainer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sinura.personaltrainer.R

/**
 * Two bundled faces, and one rule about digits.
 *
 * **Space Grotesk** carries every numeral and every screen title. It is a geometric
 * grotesque with Space Mono in its ancestry — techy, characterful, and near-uniform in the
 * digits, which is exactly the machined look a readout wants. **Inter** carries UI text,
 * where it is unmatched at 11–16sp on Android.
 *
 * Both are bundled as static instances under `res/font` rather than variable fonts, and
 * subset to the glyphs this app actually draws: five files, about 320KB, no download at
 * runtime, and no dependence on the variable-font APIs.
 *
 * **Tabular figures are law.** Every style here sets `tnum`, so a weight, a rep count or a
 * clock never reflows as its digits change — `1:59` to `2:00` cannot shift, and a column of
 * weights lines up down a list. This is the point of the whole exercise: the face this
 * replaced was `FontFamily.Monospace`, which on a device resolves to the system monospace —
 * logcat's typeface, carrying the largest numbers in the product — and its `tnum` setting
 * did nothing at all, because every glyph in a monospaced face is already fixed-width.
 *
 * The subsetting step verifies this rather than assuming it: with `tnum` off these faces
 * have proportional digits of differing widths, and with it on all ten are identical.
 */

private const val TABULAR = "tnum"

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    fontFeatureSettings = TABULAR,
)

/**
 * The named roles screens actually reach for.
 *
 * The numeral ramp exists because the old code had no display tier at all: one shared
 * numeric style was copied at a dozen call sites, each inventing its own size — 56, 52,
 * 48, 32, 28, 22, 20sp — a second type scale living outside the theme where nothing
 * governed it. These five sizes are the whole vocabulary; a call site picks one.
 */
object InstrumentType {
    /** The rest clock, and the weight and rep entry. One per screen, at most. */
    val numeralHero = style(SpaceGrotesk, FontWeight.Medium, 76.sp, 80.sp, (-0.8).sp)

    /** Session volume on the summary, estimated 1RM on a lift's own screen. */
    val numeralXl = style(SpaceGrotesk, FontWeight.Medium, 56.sp, 60.sp, (-0.6).sp)

    /** Stat tiles. */
    val numeralLg = style(SpaceGrotesk, FontWeight.Medium, 36.sp, 40.sp, (-0.2).sp)

    /** Set rows, "set 3 of 4", the docked rest clock. */
    val numeralMd = style(SpaceGrotesk, FontWeight.Medium, 24.sp, 28.sp)

    /** Inline metrics and the trailing value of a list row. */
    val numeralSm = style(SpaceGrotesk, FontWeight.Medium, 17.sp, 20.sp)

    /** Screen titles. */
    val display = style(SpaceGrotesk, FontWeight.Bold, 28.sp, 32.sp, (-0.3).sp)

    /** Card titles, exercise names. */
    val title = style(Inter, FontWeight.SemiBold, 16.sp, 20.sp)

    /** Prose and helper text. */
    val body = style(Inter, FontWeight.Normal, 14.sp, 20.sp)

    /** Emphasis inside prose. */
    val bodyStrong = style(Inter, FontWeight.Medium, 14.sp, 20.sp)

    /** Meta lines and axis labels. Pair with [TextSecondary]. */
    val caption = style(Inter, FontWeight.Normal, 12.sp, 16.sp)

    /**
     * The instrument label voice: REST, LAST 7 DAYS, VOLUME.
     *
     * Always uppercase at the call site, and always tracked — the old UI set its all-caps
     * micro-labels with no letter-spacing at all, which is what made them look cramped
     * rather than deliberate.
     */
    val kicker = style(Inter, FontWeight.SemiBold, 11.sp, 13.sp, 0.88.sp)

    /**
     * `kg`, `lbs`, `min`. Subordinate on purpose: the numeral carries and the unit
     * whispers, baseline-aligned beside it. The old steppers set the unit at nearly the
     * same size as the number it qualified.
     */
    val unit = style(Inter, FontWeight.Medium, 13.sp, 16.sp)
}

/**
 * All fifteen Material roles, filled.
 *
 * Seven of them used to be defined and the other eight fell through to stock Roboto with
 * Google's own tracking, so text set two lines apart could be drawn from two different
 * typographic systems. Anything not yet migrated to [InstrumentType] still lands on the
 * bundled faces because of this map.
 */
val Typography = Typography(
    displayLarge = style(SpaceGrotesk, FontWeight.Bold, 57.sp, 64.sp, (-1.2).sp),
    displayMedium = style(SpaceGrotesk, FontWeight.Bold, 45.sp, 52.sp, (-0.8).sp),
    displaySmall = style(SpaceGrotesk, FontWeight.Bold, 36.sp, 44.sp, (-0.4).sp),
    headlineLarge = style(SpaceGrotesk, FontWeight.Bold, 32.sp, 38.sp, (-0.4).sp),
    headlineMedium = style(SpaceGrotesk, FontWeight.Bold, 28.sp, 34.sp, (-0.3).sp),
    headlineSmall = style(SpaceGrotesk, FontWeight.Bold, 24.sp, 30.sp, (-0.2).sp),
    titleLarge = style(Inter, FontWeight.SemiBold, 20.sp, 26.sp),
    titleMedium = style(Inter, FontWeight.SemiBold, 16.sp, 22.sp),
    titleSmall = style(Inter, FontWeight.SemiBold, 14.sp, 20.sp),
    bodyLarge = style(Inter, FontWeight.Normal, 16.sp, 24.sp),
    bodyMedium = style(Inter, FontWeight.Normal, 14.sp, 20.sp),
    bodySmall = style(Inter, FontWeight.Normal, 12.sp, 16.sp),
    labelLarge = style(Inter, FontWeight.SemiBold, 14.sp, 18.sp, 0.1.sp),
    labelMedium = style(Inter, FontWeight.SemiBold, 12.sp, 16.sp, 0.4.sp),
    labelSmall = style(Inter, FontWeight.SemiBold, 11.sp, 14.sp, 0.8.sp),
)
