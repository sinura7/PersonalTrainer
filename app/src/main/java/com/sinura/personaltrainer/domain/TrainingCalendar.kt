package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime

data class CalendarDay(
    val date: CivilDate,
    /** False for the leading and trailing days that only exist to square off the grid. */
    val inMonth: Boolean,
    val sessionIds: List<String> = emptyList(),
    val activityIds: List<String> = emptyList(),
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
    val trained: Boolean get() = sessionIds.isNotEmpty() || activityIds.isNotEmpty()
}

data class TrainingMonth(
    val month: CivilYearMonth,
    /** Whole weeks, each starting on the user's configured week-start day. */
    val weeks: List<List<CalendarDay>> = emptyList(),
    val trainedDays: Int = 0,
    val work: SetWork = SetWork.NONE,
    val workingSets: Int = 0,
) {
    /** The week that contains [epochDay], or null when that day is not on this grid. */
    fun weekContaining(epochDay: Long): List<CalendarDay>? =
        weeks.firstOrNull { week -> week.any { it.date.epochDay == epochDay } }
}

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
        activities: List<ActivitySession> = emptyList(),
        time: TimePort = JvmTime,
        weekStart: Weekday = Weekday.MONDAY,
        zoneId: String = time.defaultZoneId(),
    ): TrainingMonth {
        val byDate = sessions
            .filter { it.isFinished }
            .groupBy { session -> time.civilDate(session.date, zoneId) }
        val activitiesByDate = activities
            .filter { it.isCompleted }
            .groupBy { CivilDate.fromEpochDay(it.localEpochDay) }

        val inMonthDates = (byDate.keys + activitiesByDate.keys)
            .filter { CivilYearMonth.from(it) == month }
            .toSet()
        val busiest = inMonthDates.maxOfOrNull { date ->
            val sets = byDate[date].orEmpty().sumOf { it.workingSetCount() } +
                activitiesByDate[date].orEmpty().sumOf { it.strengthSetCount() }
            sets
        } ?: 0

        val first = month.atDay(1).previousOrSame(weekStart)
        val lastDayOfMonth = month.atEndOfMonth()
        val weeks = mutableListOf<List<CalendarDay>>()
        var cursor = first
        // Whole weeks until the month is covered; a month can span four to six of them.
        while (cursor <= lastDayOfMonth) {
            weeks += (0 until DAYS_IN_WEEK).map { offset ->
                val date = cursor.plusDays(offset.toLong())
                val daySessions = byDate[date].orEmpty()
                val dayActivities = activitiesByDate[date].orEmpty()
                val sets = daySessions.sumOf { session -> session.workingSetCount() } +
                    dayActivities.sumOf { it.strengthSetCount() }
                CalendarDay(
                    date = date,
                    inMonth = CivilYearMonth.from(date) == month,
                    sessionIds = daySessions.map { it.id },
                    activityIds = dayActivities.map { it.id },
                    work = SetWork.sum(
                        daySessions.map { it.work() } + dayActivities.map { it.strengthWork() },
                    ),
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
            trainedDays = inMonthDates.size,
            work = SetWork.sum(
                inMonthDates.flatMap { date ->
                    byDate[date].orEmpty().map { it.work() } +
                        activitiesByDate[date].orEmpty().map { it.strengthWork() }
                },
            ),
            workingSets = inMonthDates.sumOf { date ->
                byDate[date].orEmpty().sumOf { it.workingSetCount() } +
                    activitiesByDate[date].orEmpty().sumOf { it.strengthSetCount() }
            },
        )
    }

    /**
     * Same month grid from session summaries — no set graph (P8.1).
     *
     * Attribution uses each summary's captured [SessionSummary.localEpochDay],
     * not the device zone of the later read.
     */
    fun buildSummaries(
        month: CivilYearMonth,
        summaries: List<SessionSummary>,
        weekStart: Weekday = Weekday.MONDAY,
    ): TrainingMonth {
        val byDate = summaries.groupBy { CivilDate.fromEpochDay(it.localEpochDay) }
        val inMonthDates = byDate.keys
            .filter { CivilYearMonth.from(it) == month }
            .toSet()
        val busiest = inMonthDates.maxOfOrNull { date ->
            byDate[date].orEmpty().sumOf { it.workingSets }
        } ?: 0

        val first = month.atDay(1).previousOrSame(weekStart)
        val lastDayOfMonth = month.atEndOfMonth()
        val weeks = mutableListOf<List<CalendarDay>>()
        var cursor = first
        while (cursor <= lastDayOfMonth) {
            weeks += (0 until DAYS_IN_WEEK).map { offset ->
                val date = cursor.plusDays(offset.toLong())
                val dayRows = byDate[date].orEmpty()
                val sets = dayRows.sumOf { it.workingSets }
                CalendarDay(
                    date = date,
                    inMonth = CivilYearMonth.from(date) == month,
                    sessionIds = dayRows.filter { it.kind != HistoryKind.ACTIVITY }.map { it.id },
                    activityIds = dayRows.filter { it.kind == HistoryKind.ACTIVITY }.map { it.id },
                    work = SetWork(
                        volumeKg = dayRows.sumOf { it.volumeKg },
                        bodyweightReps = 0,
                    ),
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
            trainedDays = inMonthDates.size,
            work = SetWork(
                volumeKg = inMonthDates.sumOf { date ->
                    byDate[date].orEmpty().sumOf { it.volumeKg }
                },
                bodyweightReps = 0,
            ),
            workingSets = inMonthDates.sumOf { date ->
                byDate[date].orEmpty().sumOf { it.workingSets }
            },
        )
    }

    /** Column headings, in the order [build] lays the weeks out. */
    fun weekdayOrder(weekStart: Weekday): List<Weekday> =
        (0 until DAYS_IN_WEEK).map { weekStart.plus(it.toLong()) }

    private const val DAYS_IN_WEEK = 7
}
