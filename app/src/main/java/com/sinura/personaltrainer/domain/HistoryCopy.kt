package com.sinura.personaltrainer.domain

/**
 * Honesty for History chips and the calendar heat ramp.
 *
 * Day / Week / Month / Year / All only retotal. The calendar still pages
 * months; the session list is still all history. Body uses the same
 * [com.sinura.personaltrainer.ui.theme.heatColor] ramp for a different quantity.
 */
object HistoryCopy {
    const val HORIZON_CAPTION =
        "Totals for this window. Calendar and the list stay all history."

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
}
