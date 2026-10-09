package com.sinura.personaltrainer.domain

/**
 * Honesty for History chips and the calendar heat ramp.
 *
 * Day / Week / Month / Year / All share one selected civil range.
 * Lifetime records and completed blocks remain explicitly separate. Body uses the same
 * [com.sinura.personaltrainer.ui.theme.heatColor] ramp for a different quantity.
 */
object HistoryCopy {
    const val HORIZON_CAPTION =
        "Totals, calendar and sessions for the selected period."

    /**
     * A later read failed and the list below is the last one that loaded. Says what is
     * actually the case — recent changes may be absent — instead of "pull may be behind",
     * which named a mechanism no one on the gym floor has a word for.
     */
    const val STALE_LIST = "Showing the last history that loaded. Recent changes may not be shown."

    const val CALENDAR_HEAT =
        "Heat is sets that month, relative to that month's hardest day."

    const val EMPTY_TITLE = "No sessions yet"
    const val EMPTY_LOG = "Finished sessions land here."

    const val EMPTY_PERIOD_TITLE = "No sessions in this period"
    const val EMPTY_PERIOD = "Choose another period, or log a new session."
    const val LIFETIME_RECORDS = "Lifetime records"
    const val LIFETIME_BLOCKS = "Completed blocks · lifetime"
    const val PROGRESS_LOADING = "Calculating records and progress…"
    const val PROGRESS_FAILED = "Records and progress could not be loaded."

    const val CALENDAR_MONTH = "Month"

    const val MOVED_MOST = "Moved most"

    fun windowTitle(horizon: AnalyticsHorizon): String = when (horizon) {
        AnalyticsHorizon.DAY -> "Today"
        AnalyticsHorizon.WEEK -> "This week"
        AnalyticsHorizon.MONTH -> "This month"
        AnalyticsHorizon.YEAR -> "This year"
        AnalyticsHorizon.ALL_TIME -> "All time"
    }

    fun sessionsLabel(count: Int): String =
        if (count == 1) "session" else "sessions"

    fun activeDuration(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val remainder = safe % 60
        return when {
            hours == 0 -> "$safe min"
            remainder == 0 -> "$hours h"
            else -> "$hours h $remainder min"
        }
    }
}
