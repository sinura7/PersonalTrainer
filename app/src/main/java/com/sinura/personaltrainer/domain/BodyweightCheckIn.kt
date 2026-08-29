package com.sinura.personaltrainer.domain

/**
 * Weekly weigh-in cadence.
 *
 * Auto follows the first training day of the user's week. An explicit
 * weekday in Settings wins. Home asks on that day when this week has no
 * log — not a second alarm path.
 */
object BodyweightCheckIn {
    fun dueWeekday(
        preferredDays: Set<Weekday>,
        weekStart: Weekday,
        daysPerWeek: Int,
        override: Weekday?,
    ): Weekday {
        if (override != null) return override
        val ordered = (0 until Weekday.DAYS_IN_WEEK).map { weekStart.plus(it.toLong()) }
        if (preferredDays.isNotEmpty()) {
            return ordered.first { it in preferredDays }
        }
        val index = WeeklySchedulePlanner.trainingDayIndices(daysPerWeek).first()
        return weekStart.plus(index.toLong())
    }

    fun weekStartEpochDay(todayEpochDay: Long, weekStart: Weekday): Long =
        CivilDate.fromEpochDay(todayEpochDay).previousOrSame(weekStart).epochDay

    fun isDueToday(
        todayEpochDay: Long,
        preferredDays: Set<Weekday>,
        weekStart: Weekday,
        daysPerWeek: Int,
        override: Weekday?,
        log: List<BodyweightEntry>,
    ): Boolean {
        val due = dueWeekday(preferredDays, weekStart, daysPerWeek, override)
        if (Weekday.fromEpochDay(todayEpochDay) != due) return false
        val start = weekStartEpochDay(todayEpochDay, weekStart)
        val end = start + (Weekday.DAYS_IN_WEEK - 1)
        return log.none { it.epochDay in start..end }
    }
}
