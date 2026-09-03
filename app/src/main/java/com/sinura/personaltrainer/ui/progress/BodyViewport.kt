package com.sinura.personaltrainer.ui.progress

import androidx.compose.ui.unit.Dp
import com.sinura.personaltrainer.ui.theme.Metrics

/**
 * Body's first-viewport budget (G5 / F12).
 *
 * The figure used to be a fixed 440 dp panel, so on a 360×640 phone the
 * silhouette was cut and every muscle row sat below the fold. Height now
 * tracks the screen; the legend sits above the figure; Front/Back chips
 * overlay the panel so they do not add a second 48 dp row.
 *
 * Arithmetic is font-1.0 token math, the same method F6 used for Home.
 * 640 includes the tab bar ([TAB_BAR_DP], matching `AppNav`).
 */
object BodyViewport {
    const val SHORT_WIDTH_DP = 360
    const val SHORT_HEIGHT_DP = 640
    const val TAB_BAR_DP = 64

    const val FIGURE_FRACTION = 0.45f
    const val FIGURE_MIN_DP = 300
    const val FIGURE_MAX_DP = 440

    /** [InstrumentType.kicker] line height at font 1.0. */
    const val KICKER_LINE_DP = 13

    /** [InstrumentType.caption] line height at font 1.0. */
    const val CAPTION_LINE_DP = 16

    fun figureHeightDp(screenHeightDp: Int): Int =
        (screenHeightDp * FIGURE_FRACTION).toInt().coerceIn(FIGURE_MIN_DP, FIGURE_MAX_DP)

    fun pickerHeightDp(): Int =
        dp(Metrics.space2) +
            dp(Metrics.touchMin) +
            dp(Metrics.space2) +
            dp(Metrics.touchMin) +
            dp(Metrics.space2)

    fun legendHeightDp(): Int =
        KICKER_LINE_DP + dp(Metrics.space1) + CAPTION_LINE_DP

    fun mapHeightDp(screenHeightDp: Int): Int =
        legendHeightDp() + dp(Metrics.space2) + figureHeightDp(screenHeightDp)

    fun firstMuscleRowTopDp(screenHeightDp: Int): Int =
        pickerHeightDp() +
            dp(Metrics.space2) +
            mapHeightDp(screenHeightDp) +
            dp(Metrics.cardGap) +
            KICKER_LINE_DP +
            dp(Metrics.cardGap)

    fun firstMuscleRowFits(
        widthDp: Int = SHORT_WIDTH_DP,
        heightDp: Int = SHORT_HEIGHT_DP,
    ): Boolean {
        if (widthDp < SHORT_WIDTH_DP) return false
        val usable = heightDp - TAB_BAR_DP
        return firstMuscleRowTopDp(heightDp) + dp(Metrics.rowMin) <= usable
    }

    private fun dp(value: Dp): Int = value.value.toInt()
}
