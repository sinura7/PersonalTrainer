package com.sinura.personaltrainer.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * One marked week. Not a mesocycle, not a set-count scaler.
 *
 * The coach can already see overreaching. This is the affordance: the lifter marks *this*
 * week, the strip says so, and progression holds load. Next Monday the mark is inert
 * because readers compare to the current [weekStartEpochDay] — no cleanup job.
 */
object LighterWeek {
    const val CAPTION = "Lighter week"
    const val TUNE_LABEL = "Lighter week"

    fun weekStartEpochDay(today: LocalDate, weekStart: DayOfWeek): Long =
        today.with(TemporalAdjusters.previousOrSame(weekStart)).toEpochDay()

    fun isCurrent(markedStartEpochDay: Long?, weekStartEpochDay: Long?): Boolean =
        markedStartEpochDay != null && weekStartEpochDay != null &&
            markedStartEpochDay == weekStartEpochDay
}
