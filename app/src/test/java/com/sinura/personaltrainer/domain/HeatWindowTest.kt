package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * "This week" must mean the same thing in the body map as it does in the weekly planner,
 * which honours the user's configurable week start.
 */
class HeatWindowTest {
    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")

    private fun millis(date: LocalDate, hour: Int = 12) =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun currentWeekDefaultsToMonday() {
        val thursday = LocalDate.of(2026, 8, 20)
        val start = HeatWindow.CURRENT_WEEK.startMs(millis(thursday), zone)
        val expected = LocalDate.of(2026, 8, 17).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, start)
    }

    @Test
    fun currentWeekHonoursASundayWeekStart() {
        // Regression: the heat map hardcoded Monday while SchedulePreferences let the user
        // pick any day, so a Sunday session landed in the "wrong" week on the Progress tab.
        val thursday = LocalDate.of(2026, 8, 20)
        val start = HeatWindow.CURRENT_WEEK.startMs(millis(thursday), zone, DayOfWeek.SUNDAY)
        val expected = LocalDate.of(2026, 8, 16).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, start)
    }

    @Test
    fun currentWeekOnTheStartDayItselfBeginsThatMorning() {
        val sunday = LocalDate.of(2026, 8, 16)
        val start = HeatWindow.CURRENT_WEEK.startMs(millis(sunday, hour = 23), zone, DayOfWeek.SUNDAY)
        assertEquals(sunday.atStartOfDay(zone).toInstant().toEpochMilli(), start)
    }

    @Test
    fun rollingWindowsIgnoreWeekStart() {
        val now = millis(LocalDate.of(2026, 8, 20))
        assertEquals(
            HeatWindow.LAST_7_DAYS.startMs(now, zone),
            HeatWindow.LAST_7_DAYS.startMs(now, zone, DayOfWeek.SUNDAY),
        )
        assertEquals(
            HeatWindow.LAST_14_DAYS.startMs(now, zone),
            HeatWindow.LAST_14_DAYS.startMs(now, zone, DayOfWeek.FRIDAY),
        )
    }

    @Test
    fun rollingWindowCrossesADstBoundaryWithoutDrift() {
        // 2026-03-08 is the US spring-forward. minusDays must stay calendar-correct.
        val afterDst = LocalDateTime.of(2026, 3, 10, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val start = HeatWindow.LAST_7_DAYS.startMs(afterDst, zone)
        val expected = LocalDateTime.of(2026, 3, 3, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, start)
    }
}
