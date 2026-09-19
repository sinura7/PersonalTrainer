package com.sinura.personaltrainer.ui.workout


/**
 * Landscape survival for the workout log and the rest floor.
 *
 * Chrome alone used to exceed a 360 dp landscape height, so the log was
 * off-screen on a phone on a bench. Compact header (one row: the plan's words as the
 * title, no progress bar), hide idle rest, and scale the rest ring to
 * `min(280, height − 120)`. Rest still counts as chrome in [logBudgetDp]
 * after G-02 moved it into the lower dock with Log set: the idle row is 56 dp and the
 * running rest card (ADR-027) is 72 dp.
 */
object LandscapeChrome {
    const val LANDSCAPE_WIDTH_DP = 640
    const val LANDSCAPE_HEIGHT_DP = 360
    const val RING_MAX_DP = 280
    const val RING_RESERVE_DP = 120
    const val HEADER_ROW_DP = 56
    const val METRICS_ROW_DP = 40
    const val REST_IDLE_DP = 56
    const val REST_CARD_DP = 72
    const val LOG_MIN_DP = 96

    fun isLandscape(widthDp: Int, heightDp: Int): Boolean = widthDp > heightDp

    fun ringSizeDp(heightDp: Int): Int =
        minOf(RING_MAX_DP, (heightDp - RING_RESERVE_DP).coerceAtLeast(160))

    fun compactHeader(landscape: Boolean): Boolean = landscape

    fun hideIdleRest(landscape: Boolean): Boolean = landscape

    fun logBudgetDp(
        heightDp: Int,
        landscape: Boolean,
        restRunning: Boolean,
    ): Int {
        var chrome = HEADER_ROW_DP
        if (restRunning) {
            chrome += REST_CARD_DP
        } else if (!hideIdleRest(landscape)) {
            chrome += REST_IDLE_DP
        }
        return heightDp - chrome
    }

    fun logVisibleInLandscape(restRunning: Boolean = false): Boolean =
        logBudgetDp(LANDSCAPE_HEIGHT_DP, landscape = true, restRunning) >= LOG_MIN_DP
}
