package com.sinura.personaltrainer.domain

/** Route-owned civil selection. It never changes a saved session's attribution. */
data class HistoryPeriodSelection(
    val horizon: AnalyticsHorizon,
    val anchorEpochDay: Long,
    val followToday: Boolean,
) {
    init {
        require(anchorEpochDay in MIN_HISTORY_EPOCH_DAY..MAX_HISTORY_EPOCH_DAY)
    }
}

/** The same half-open range drives the History list, calendar, totals and progress request. */
data class HistoryPeriodRange(
    val startEpochDay: Long,
    val endExclusiveEpochDay: Long,
) {
    init {
        require(startEpochDay in MIN_HISTORY_EPOCH_DAY..MAX_HISTORY_EPOCH_DAY)
        require(endExclusiveEpochDay > startEpochDay && endExclusiveEpochDay <= MAX_HISTORY_EPOCH_DAY + 1)
    }

    val endEpochDay: Long get() = endExclusiveEpochDay - 1

    operator fun contains(epochDay: Long): Boolean =
        epochDay >= startEpochDay && epochDay < endExclusiveEpochDay
}

object HistoryPeriodMath {
    fun anchor(selection: HistoryPeriodSelection, today: CivilDate): CivilDate =
        if (selection.followToday) today else CivilDate.fromEpochDay(
            selection.anchorEpochDay.coerceAtMost(today.epochDay),
        )

    fun resolve(
        selection: HistoryPeriodSelection,
        today: CivilDate,
        weekStart: Weekday,
        earliestEpochDay: Long?,
    ): HistoryPeriodRange {
        val date = anchor(selection, today)
        val start = if (selection.horizon == AnalyticsHorizon.ALL_TIME) {
            (earliestEpochDay ?: today.epochDay).coerceIn(MIN_HISTORY_EPOCH_DAY, today.epochDay)
        } else {
            startOf(date, selection.horizon, weekStart).epochDay
        }
        val end = if (selection.horizon == AnalyticsHorizon.ALL_TIME) {
            today.epochDay + 1
        } else {
            endOf(date, selection.horizon, weekStart).coerceAtMost(today.epochDay + 1)
        }
        return HistoryPeriodRange(start.coerceAtLeast(MIN_HISTORY_EPOCH_DAY), end)
    }

    fun previous(
        selection: HistoryPeriodSelection,
        today: CivilDate,
        weekStart: Weekday,
    ): HistoryPeriodSelection? {
        if (selection.horizon == AnalyticsHorizon.ALL_TIME) return null
        // Resolve the boundary too: a selected Week uses the configured start day.
        val date = anchor(selection, today)
        val previous = shifted(date, selection.horizon, -1)
        if (startOf(previous, selection.horizon, weekStart).epochDay < MIN_HISTORY_EPOCH_DAY) return null
        return selectionFor(selection.horizon, previous)
    }

    fun next(
        selection: HistoryPeriodSelection,
        today: CivilDate,
        weekStart: Weekday,
    ): HistoryPeriodSelection? {
        if (selection.horizon == AnalyticsHorizon.ALL_TIME) return null
        val next = shifted(anchor(selection, today), selection.horizon, 1)
        if (startOf(next, selection.horizon, weekStart) > today) return null
        return selectionFor(selection.horizon, if (next > today) today else next)
    }

    fun current(selection: HistoryPeriodSelection, today: CivilDate): HistoryPeriodSelection =
        selection.copy(anchorEpochDay = today.epochDay, followToday = true)

    private fun selectionFor(horizon: AnalyticsHorizon, date: CivilDate): HistoryPeriodSelection? =
        if (date.epochDay in MIN_HISTORY_EPOCH_DAY..MAX_HISTORY_EPOCH_DAY) {
            HistoryPeriodSelection(horizon = horizon, anchorEpochDay = date.epochDay, followToday = false)
        } else {
            null
        }

    private fun startOf(date: CivilDate, horizon: AnalyticsHorizon, weekStart: Weekday): CivilDate =
        when (horizon) {
            AnalyticsHorizon.DAY -> date
            AnalyticsHorizon.WEEK -> date.previousOrSame(weekStart)
            AnalyticsHorizon.MONTH -> CivilDate(date.year, date.month, 1)
            AnalyticsHorizon.YEAR -> CivilDate(date.year, 1, 1)
            AnalyticsHorizon.ALL_TIME -> date
        }

    private fun endOf(date: CivilDate, horizon: AnalyticsHorizon, weekStart: Weekday): Long =
        when (horizon) {
            AnalyticsHorizon.DAY -> date.epochDay + 1
            AnalyticsHorizon.WEEK -> date.previousOrSame(weekStart).epochDay + 7
            AnalyticsHorizon.MONTH -> CivilYearMonth.from(date).plusMonths(1).atDay(1).epochDay
            AnalyticsHorizon.YEAR -> CivilDate(date.year + 1, 1, 1).epochDay
            AnalyticsHorizon.ALL_TIME -> date.epochDay + 1
        }

    private fun shifted(date: CivilDate, horizon: AnalyticsHorizon, direction: Int): CivilDate =
        when (horizon) {
            AnalyticsHorizon.DAY -> date.plusDays(direction.toLong())
            AnalyticsHorizon.WEEK -> date.plusDays(direction * 7L)
            AnalyticsHorizon.MONTH -> {
                val month = CivilYearMonth.from(date).plusMonths(direction.toLong())
                month.atDay(date.day.coerceAtMost(CivilDate.lengthOfMonth(month.year, month.month)))
            }
            AnalyticsHorizon.YEAR -> {
                val year = date.year + direction
                CivilDate(year, date.month, date.day.coerceAtMost(CivilDate.lengthOfMonth(year, date.month)))
            }
            AnalyticsHorizon.ALL_TIME -> date
        }
}

// Platform civil-date formatting supports the Gregorian years -999999999 through 999999999.
// Reject a corrupt SavedStateHandle's arbitrary Long before it can reach that formatter.
internal const val MIN_HISTORY_EPOCH_DAY = -365243219162L
internal const val MAX_HISTORY_EPOCH_DAY = 365241780471L
