package com.sinura.personaltrainer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One family of corners.
 *
 * The theme previously passed no `shapes` to [androidx.compose.material3.MaterialTheme] at
 * all, so stock components kept Material's own radii — chips at 8, text fields at 4,
 * dialogs and sheets at 28 — while the app scattered seven more values by hand across
 * twenty-one call sites. A confirm dialog at 28 over a card at 20 holding a button at 16
 * beside a chip at 8 is four unrelated curvatures in a single glance, which is what makes
 * an interface read as assembled rather than drawn.
 *
 * Passing [InstrumentShapes] to the theme means a raw `Card` or `AlertDialog` written next
 * year still lands in this family without anyone remembering to say so.
 */
object Radius {
    /** Chips, tags, calendar cells. */
    val xs: Dp = 8.dp

    /** Inputs and grouped list containers. */
    val sm: Dp = 12.dp

    /** Cards and buttons — the workhorse. */
    val md: Dp = 16.dp

    /** Sheets, dialogs, hero panels. */
    val lg: Dp = 24.dp
}

val InstrumentShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.md),
    extraLarge = RoundedCornerShape(Radius.lg),
)
