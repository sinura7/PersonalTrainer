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
    fun anchor(
        selection: HistoryPeriodSelection,
        today: CivilDate,
        weekStart: Weekday = Weekday.MONDAY,
        completedEpochDays: Set<Long> = emptySet(),
    ): CivilDate {
        if (selection.followToday) return today
        val date = CivilDate.fromEpochDay(selection.anchorEpochDay)
        if (canSelectDay(date.epochDay, today, completedEpochDays)) return date
        if (selection.horizon == AnalyticsHorizon.ALL_TIME ||
            startOf(date, selection.horizon, weekStart) <= today) return today
        // A restored month/week/year may point at an empty day in a period with
        // captured work. Keep that period, using one of its actual completed days.
        val completed = completedInPeriod(date, selection.horizon, weekStart, completedEpochDays)
        return completed.minOrNull()?.let(CivilDate::fromEpochDay) ?: today
    }

    fun canSelectDay(epochDay: Long, today: CivilDate, completedEpochDays: Set<Long> = emptySet()): Boolean =
        epochDay in MIN_HISTORY_EPOCH_DAY..MAX_HISTORY_EPOCH_DAY &&
            (epochDay <= today.epochDay || epochDay in completedEpochDays)

    fun canSelectMonth(
        month: CivilYearMonth,
        today: CivilDate,
        completedEpochDays: Set<Long> = emptySet(),
    ): Boolean {
        val first = month.atDay(1)
        if (first.epochDay !in MIN_HISTORY_EPOCH_DAY..MAX_HISTORY_EPOCH_DAY) return false
        return first <= today ||
            completedInPeriod(first, AnalyticsHorizon.MONTH, Weekday.MONDAY, completedEpochDays).isNotEmpty()
    }

    fun resolve(
        selection: HistoryPeriodSelection,
        today: CivilDate,
        weekStart: Weekday,
        earliestEpochDay: Long?,
        completedEpochDays: Set<Long> = emptySet(),
    ): HistoryPeriodRange {
        val date = anchor(selection, today, weekStart, completedEpochDays)
        val completed = completedInPeriod(date, selection.horizon, weekStart, completedEpochDays)
        // Device Today answers "now". Captured completed dates answer where saved
        // work belongs, including after travel moves the device date backwards.
        val visibleThrough = maxOf(today.epochDay, completed.maxOrNull() ?: today.epochDay)
        val start = if (selection.horizon == AnalyticsHorizon.ALL_TIME) {
            (earliestEpochDay ?: completed.minOrNull() ?: today.epochDay)
                .coerceIn(MIN_HISTORY_EPOCH_DAY, visibleThrough)
        } else {
            startOf(date, selection.horizon, weekStart).epochDay
        }
        val end = if (selection.horizon == AnalyticsHorizon.ALL_TIME) {
            visibleThrough + 1
        } else {
            endOf(date, selection.horizon, weekStart).coerceAtMost(visibleThrough + 1)
        }
        return HistoryPeriodRange(start.coerceAtLeast(MIN_HISTORY_EPOCH_DAY), end.coerceAtMost(MAX_HISTORY_EPOCH_DAY + 1))
    }

    fun previous(
        selection: HistoryPeriodSelection,
        today: CivilDate,
        weekStart: Weekday,
        completedEpochDays: Set<Long> = emptySet(),
    ): HistoryPeriodSelection? {
        if (selection.horizon == AnalyticsHorizon.ALL_TIME) return null
        // Resolve the boundary too: a selected Week uses the configured start day.
        val date = anchor(selection, today, weekStart, completedEpochDays)
        val previous = shifted(date, selection.horizon, -1)
        if (startOf(previous, selection.horizon, weekStart).epochDay < MIN_HISTORY_EPOCH_DAY) return null
        return navigableSelection(selection.horizon, previous, today, weekStart, completedEpochDays)
    }

    fun next(
        selection: HistoryPeriodSelection,
        today: CivilDate,
        weekStart: Weekday,
        completedEpochDays: Set<Long> = emptySet(),
    ): HistoryPeriodSelection? {
        if (selection.horizon == AnalyticsHorizon.ALL_TIME) return null
        val next = shifted(anchor(selection, today, weekStart, completedEpochDays), selection.horizon, 1)
        return navigableSelection(selection.horizon, next, today, weekStart, completedEpochDays)
    }

    fun current(selection: HistoryPeriodSelection, today: CivilDate): HistoryPeriodSelection =
        selection.copy(anchorEpochDay = today.epochDay, followToday = true)

    private fun navigableSelection(
        horizon: AnalyticsHorizon,
        date: CivilDate,
        today: CivilDate,
        weekStart: Weekday,
        completedEpochDays: Set<Long>,
    ): HistoryPeriodSelection? {
        if (date.epochDay !in MIN_HISTORY_EPOCH_DAY..MAX_HISTORY_EPOCH_DAY) return null
        if (date <= today) return selectionFor(horizon, date)
        val completed = completedInPeriod(date, horizon, weekStart, completedEpochDays)
        if (date.epochDay in completed) return selectionFor(horizon, date)
        if (startOf(date, horizon, weekStart) <= today) return selectionFor(horizon, today)
        return completed.minOrNull()?.let { selectionFor(horizon, CivilDate.fromEpochDay(it)) }
    }

    private fun completedInPeriod(
        date: CivilDate,
        horizon: AnalyticsHorizon,
        weekStart: Weekday,
        completedEpochDays: Set<Long>,
    ): List<Long> {
        val start = startOf(date, horizon, weekStart).epochDay
        val end = endOf(date, horizon, weekStart)
        return completedEpochDays.filter {
            it in MIN_HISTORY_EPOCH_DAY..MAX_HISTORY_EPOCH_DAY &&
                (horizon == AnalyticsHorizon.ALL_TIME || (it >= start && it < end))
        }
    }

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
