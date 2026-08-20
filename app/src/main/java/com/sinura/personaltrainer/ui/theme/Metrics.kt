package com.sinura.personaltrainer.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing, on a 4dp grid.
 *
 * The old token object held five values and was consumed by eleven of forty UI files;
 * roughly a hundred and ten inline padding literals sat beside the tokens they duplicated.
 * Worse, its screen padding and its section gap were both 20dp, so the scale could not
 * express hierarchy at all — the distance between two unrelated sections equalled the
 * page margin.
 *
 * Here a section owns the whitespace above it ([sectionGap] 28) and cards inside a section
 * sit close together ([cardGap] 8), so grouping is visible before a single word is read.
 *
 * Touch sizes are floors, not suggestions. [commit] and the stepper plates exist because
 * this is used one-handed, sweating, with a bar loaded; density gains are never taken out
 * of them.
 */
object Metrics {
    val space1: Dp = 4.dp
    val space2: Dp = 8.dp
    val space3: Dp = 12.dp
    val space4: Dp = 16.dp
    val space5: Dp = 20.dp
    val space6: Dp = 24.dp
    val space7: Dp = 32.dp
    val space8: Dp = 40.dp

    /** Screen margin. Tighter than the 20dp it replaces; the 4dp goes to the numerals. */
    val gutter: Dp = space4

    val cardPadding: Dp = space4

    /** Between sibling cards inside one section. */
    val cardGap: Dp = space2

    /** Between sections. */
    val sectionGap: Dp = 28.dp

    /** Between a section's kicker and its content. */
    val kickerGap: Dp = space2

    val hairline: Dp = 1.dp

    /** A border that has to read as a state, not just an edge: selected, or being edited. */
    val emphasisBorder: Dp = 2.dp

    /** Absolute minimum touch target. */
    val touchMin: Dp = 48.dp

    /** A list row with a value on the trailing edge. */
    val rowMin: Dp = 56.dp

    /** Buttons and controls that get used mid-session. */
    val control: Dp = 56.dp

    /** The one action worth a bigger target than anything else: logging a set. */
    val commit: Dp = 72.dp

    /** Bottom-of-list clearance so a floating action never covers the last row. */
    val fabClearance: Dp = 88.dp
}
