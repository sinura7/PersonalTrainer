package com.sinura.personaltrainer.domain

/**
 * Honesty for Body chips and the silhouette wash.
 *
 * Day / Week / Month only retotal the figure. Year and all-time stay
 * History. Rest on the still is untrained in this window, not "never".
 */
object BodyHeatCopy {
    const val LEGEND_CAPTION = "Colour is muscle load in this window, not calendar sets."

    const val WINDOW_CAPTION =
        "The figure is this window. Rest is untrained here."

    const val EMPTY_LOG = "Finished sets light the figure."

    const val EMPTY_WINDOW =
        "Nothing in this window yet. Older work still shows recency."

    fun windowTitle(window: HeatWindow): String = window.label

    /** One tap to the widest window, instead of a chip hunt above a blank figure. */
    const val SHOW_MONTH = "Show this month"

    /**
     * The line under the figure: what it was built from, in units History can confirm.
     * "3 sessions this week · last finished yesterday". Null before anything has ever
     * finished — [EMPTY_LOG] already says so, and a line of zeros would say it twice.
     */
    fun facts(window: HeatWindow, windowSessions: Int, daysSinceLastFinished: Int?): String? {
        if (daysSinceLastFinished == null) return null
        val sessions = when (windowSessions) {
            0 -> "No sessions"
            1 -> "1 session"
            else -> "$windowSessions sessions"
        }
        return "$sessions ${window.label.lowercase()} · last finished " +
            finishedAgo(daysSinceLastFinished)
    }

    fun finishedAgo(days: Int): String = when (days) {
        0 -> "today"
        1 -> "yesterday"
        else -> "$days days ago"
    }
}
