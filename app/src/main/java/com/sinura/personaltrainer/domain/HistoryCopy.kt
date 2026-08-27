package com.sinura.personaltrainer.domain

/**
 * Honesty for History chips and the calendar heat ramp.
 *
 * Week / Month / Year / All time only retotal. The calendar still pages
 * months; the session list is still all history. Body uses the same
 * [com.sinura.personaltrainer.ui.theme.heatColor] ramp for a different quantity.
 */
object HistoryCopy {
    const val HORIZON_CAPTION =
        "Totals for this window. Calendar and the list stay all history."

    const val CALENDAR_HEAT =
        "Heat is sets that month, relative to that month's hardest day."
}
