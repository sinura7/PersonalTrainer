package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime

data class CalendarDay(
    val date: CivilDate,
    /** False for the leading and trailing days that only exist to square off the grid. */
    val inMonth: Boolean,
    val sessionIds: List<String> = emptyList(),
    val work: SetWork = SetWork.NONE,
    /**
     * How hard this day was relative to the hardest day of the same month, 0..1.
     *
     * Scaled within the month rather than against a lifetime maximum: the question a calendar
     * answers is "how did this month go", and one outlier session from two years ago would
     * otherwise flatten every day of it to nothing.
     *
     * Measured in **working sets**, not tonnage. Tonnage is not a unit every lift has: once
     * bodyweight lifts stopped being priced at an invented 40 kg a rep, a calisthenics month
     * would have coloured every square at zero. Sets are what every lift has in common, and
     * they are a better answer to "how big was this day" in any case — thirty sets of squats
     * and thirty of pull-ups are both a lot of training.
     */
    val intensity: Float = 0f,
) {
    val trained: Boolean get() = sessionIds.isNotEmpty()
}

data class TrainingMonth(
    val month: CivilYearMonth,
    /** Whole weeks, each starting on the user's configured week-start day. */
    val weeks: List<List<CalendarDay>> = emptyList(),
    val trainedDays: Int = 0,
    val work: SetWork = SetWork.NONE,
    val workingSets: Int = 0,
)

/**
 * The month grid behind the History calendar.
 *
 * Weeks begin on the user's configured start day, the same one the planner and the heat map
 * use — a calendar that started its weeks somewhere else would make "this week" mean a third
 * thing in the same app.
 */
object TrainingCalendarBuilder {
    fun build(
        month: CivilYearMonth,
        sessions: List<WorkoutSession>,
        time: TimePort = JvmTime,
        weekStart: Weekday = Weekday.MONDAY,
        zoneId: String = time.defaultZoneId(),
    ): TrainingMonth {
        val byDate = sessions
            .filter { it.isFinished }
            .groupBy { session -> time.civilDate(session.date, zoneId) }

        val inMonth = byDate.filterKeys { CivilYearMonth.from(it) == month }
        val busiest = inMonth.values
            .maxOfOrNull { day -> day.sumOf { session -> session.workingSetCount() } }
            ?: 0

        val first = month.atDay(1).previousOrSame(weekStart)
        val lastDayOfMonth = month.atEndOfMonth()
        val weeks = mutableListOf<List<CalendarDay>>()
        var cursor = first
        // Whole weeks until the month is covered; a month can span four to six of them.
        while (cursor <= lastDayOfMonth) {
            weeks += (0 until DAYS_IN_WEEK).map { offset ->
                val date = cursor.plusDays(offset.toLong())
                val daySessions = byDate[date].orEmpty()
                val sets = daySessions.sumOf { session -> session.workingSetCount() }
                CalendarDay(
                    date = date,
                    inMonth = CivilYearMonth.from(date) == month,
                    sessionIds = daySessions.map { it.id },
                    work = SetWork.sum(daySessions.map { it.work() }),
                    // Clamped because `busiest` only considers in-month days, while the
                    // leading and trailing padding days of the grid keep their real volume:
                    // a heavy end-of-previous-month session divided by a light current
                    // month yields a ratio above 1, which downstream becomes an out-of-range
                    // colour alpha and throws.
                    intensity = if (busiest > 0) {
                        (sets.toDouble() / busiest).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                )
            }
            cursor = cursor.plusDays(DAYS_IN_WEEK.toLong())
        }

        return TrainingMonth(
            month = month,
            weeks = weeks,
            trainedDays = inMonth.size,
            work = SetWork.sum(inMonth.values.flatten().map { it.work() }),
            workingSets = inMonth.values.sumOf { day ->
                day.sumOf { session -> session.sets.count { !it.isWarmup } }
            },
        )
    }

    /** Column headings, in the order [build] lays the weeks out. */
    fun weekdayOrder(weekStart: Weekday): List<Weekday> =
        (0 until DAYS_IN_WEEK).map { weekStart.plus(it.toLong()) }

    private const val DAYS_IN_WEEK = 7
}
