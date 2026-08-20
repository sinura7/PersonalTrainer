package com.sinura.personaltrainer.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class CalendarDay(
    val date: LocalDate,
    /** False for the leading and trailing days that only exist to square off the grid. */
    val inMonth: Boolean,
    val sessionIds: List<String> = emptyList(),
    val volumeKg: Double = 0.0,
    /**
     * How hard this day was relative to the hardest day of the same month, 0..1.
     *
     * Scaled within the month rather than against a lifetime maximum: the question a calendar
     * answers is "how did this month go", and one outlier session from two years ago would
     * otherwise flatten every day of it to nothing.
     */
    val intensity: Float = 0f,
) {
    val trained: Boolean get() = sessionIds.isNotEmpty()
}

data class TrainingMonth(
    val month: YearMonth,
    /** Whole weeks, each starting on the user's configured week-start day. */
    val weeks: List<List<CalendarDay>> = emptyList(),
    val trainedDays: Int = 0,
    val volumeKg: Double = 0.0,
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
        month: YearMonth,
        sessions: List<WorkoutSession>,
        zone: ZoneId = ZoneId.systemDefault(),
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): TrainingMonth {
        val byDate = sessions
            .filter { it.isFinished }
            .groupBy { session ->
                Instant.ofEpochMilli(session.date).atZone(zone).toLocalDate()
            }

        val inMonth = byDate.filterKeys { YearMonth.from(it) == month }
        val busiest = inMonth.values
            .maxOfOrNull { day -> day.sumOf { it.workingVolumeKg() } }
            ?: 0.0

        val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(weekStart))
        val lastDayOfMonth = month.atEndOfMonth()
        val weeks = mutableListOf<List<CalendarDay>>()
        var cursor = first
        // Whole weeks until the month is covered; a month can span four to six of them.
        while (cursor <= lastDayOfMonth) {
            weeks += (0 until DAYS_IN_WEEK).map { offset ->
                val date = cursor.plusDays(offset.toLong())
                val daySessions = byDate[date].orEmpty()
                val volume = daySessions.sumOf { it.workingVolumeKg() }
                CalendarDay(
                    date = date,
                    inMonth = YearMonth.from(date) == month,
                    sessionIds = daySessions.map { it.id },
                    volumeKg = volume,
                    // Clamped because `busiest` only considers in-month days, while the
                    // leading and trailing padding days of the grid keep their real volume:
                    // a heavy end-of-previous-month session divided by a light current
                    // month yields a ratio above 1, which downstream becomes an out-of-range
                    // colour alpha and throws.
                    intensity = if (busiest > 0.0) {
                        (volume / busiest).toFloat().coerceIn(0f, 1f)
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
            volumeKg = inMonth.values.sumOf { day -> day.sumOf { it.workingVolumeKg() } },
            workingSets = inMonth.values.sumOf { day ->
                day.sumOf { session -> session.sets.count { !it.isWarmup } }
            },
        )
    }

    /** Column headings, in the order [build] lays the weeks out. */
    fun weekdayOrder(weekStart: DayOfWeek): List<DayOfWeek> =
        (0 until DAYS_IN_WEEK).map { weekStart.plus(it.toLong()) }

    private const val DAYS_IN_WEEK = 7
}
