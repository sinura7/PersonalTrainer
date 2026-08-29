package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.util.toJavaDayOfWeek
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
        val start = HeatWindow.CURRENT_WEEK.startMs(millis(thursday), zone, Weekday.SUNDAY)
        val expected = LocalDate.of(2026, 8, 16).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, start)
    }

    @Test
    fun currentWeekOnTheStartDayItselfBeginsThatMorning() {
        val sunday = LocalDate.of(2026, 8, 16)
        val start = HeatWindow.CURRENT_WEEK.startMs(millis(sunday, hour = 23), zone, Weekday.SUNDAY)
        assertEquals(sunday.atStartOfDay(zone).toInstant().toEpochMilli(), start)
    }

    @Test
    fun dayAndMonthIgnoreWeekStart() {
        val now = millis(LocalDate.of(2026, 8, 20))
        assertEquals(
            HeatWindow.DAY.startMs(now, zone),
            HeatWindow.DAY.startMs(now, zone, Weekday.SUNDAY),
        )
        assertEquals(
            HeatWindow.CURRENT_MONTH.startMs(now, zone),
            HeatWindow.CURRENT_MONTH.startMs(now, zone, Weekday.FRIDAY),
        )
    }

    @Test
    fun dayIsStartOfToday() {
        val thursday = LocalDate.of(2026, 8, 20)
        val start = HeatWindow.DAY.startMs(millis(thursday, hour = 23), zone)
        assertEquals(thursday.atStartOfDay(zone).toInstant().toEpochMilli(), start)
    }

    @Test
    fun monthStartsOnTheFirst() {
        val thursday = LocalDate.of(2026, 8, 20)
        val start = HeatWindow.CURRENT_MONTH.startMs(millis(thursday), zone)
        val expected = LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, start)
    }

    @Test
    fun heatWindowAndPlannerAgreeOnWhereTheWeekStarts() {
        // The single-source-of-truth check: SchedulePreferences.weekStart must move BOTH the
        // body map's "This week" boundary and the planner's week, or "this week" means two
        // different things on two screens.
        listOf(Weekday.MONDAY, Weekday.SUNDAY).forEach { weekStart ->
            val prefs = SchedulePreferences(weekStart = weekStart)
            val thursdayNoon = LocalDate.of(2026, 8, 20).atTime(12, 0)
            val nowMs = thursdayNoon.atZone(zone).toInstant().toEpochMilli()

            val heatStart = HeatWindow.CURRENT_WEEK.startMs(nowMs, zone, prefs.weekStart)
            val plannerWeekStart = thursdayNoon.toLocalDate()
                .with(
                    java.time.temporal.TemporalAdjusters.previousOrSame(
                        prefs.weekStart.toJavaDayOfWeek(),
                    ),
                )

            assertEquals(
                "heat window and planner disagree for $weekStart",
                plannerWeekStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                heatStart,
            )
        }
    }

    @Test
    fun monthStartIsCivilMidnightNotARollingOffset() {
        val afterDst = LocalDateTime.of(2026, 3, 10, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val start = HeatWindow.CURRENT_MONTH.startMs(afterDst, zone)
        val expected = LocalDate.of(2026, 3, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, start)
    }

    @Test
    fun storedLastThirtyDaysBecomesThisMonth() {
        assertEquals(HeatWindow.CURRENT_MONTH, HeatWindow.fromStorage("LAST_30_DAYS"))
        assertEquals(HeatWindow.DAY, HeatWindow.fromStorage("DAY"))
        assertEquals(HeatWindow.CURRENT_WEEK, HeatWindow.fromStorage(null))
        assertEquals(HeatWindow.CURRENT_WEEK, HeatWindow.fromStorage("nope"))
    }
}
