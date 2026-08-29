package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HorizonMathTest {
    private val today = CivilDate(2026, 8, 24)
    private val weekStart = Weekday.MONDAY

    @Test
    fun yearAndAllTimeAreComparableOnTheSameMetrics() {
        val projections = listOf(
            day(CivilDate(2025, 12, 31).epochDay, sessions = 1, minutes = 30),
            day(CivilDate(2026, 1, 2).epochDay, sessions = 1, minutes = 40),
            day(today.epochDay, sessions = 2, minutes = 80),
        )
        val year = HorizonMath.totals(AnalyticsHorizon.YEAR, projections, today, weekStart)
        val allTime = HorizonMath.totals(AnalyticsHorizon.ALL_TIME, projections, today, weekStart)
        assertEquals(3, year.sessionCount)
        assertEquals(4, allTime.sessionCount)
        assertEquals(year.activeMinutes, 120)
        assertEquals(allTime.activeMinutes, 150)
        assertTrue(allTime.sessionCount >= year.sessionCount)
        assertEquals(AnalyticsHorizon.YEAR, year.horizon)
        assertEquals(AnalyticsHorizon.ALL_TIME, allTime.horizon)
    }

    @Test
    fun monthStartsOnTheFirstOfTheMonth() {
        val (start, end) = HorizonMath.range(
            AnalyticsHorizon.MONTH,
            today,
            Weekday.MONDAY,
            earliestEpochDay = null,
        )
        assertEquals(CivilDate(2026, 8, 1).epochDay, start)
        assertEquals(today.epochDay, end)
        val emptyAllTime = HorizonMath.totals(
            AnalyticsHorizon.ALL_TIME,
            emptyList(),
            today,
            weekStart,
        )
        assertEquals(today.epochDay, emptyAllTime.startEpochDay)
        assertEquals(0, emptyAllTime.sessionCount)
        assertEquals(0, emptyAllTime.trainedDays)
    }

    @Test
    fun weekStartsOnConfiguredWeekStart() {
        val (start, end) = HorizonMath.range(
            AnalyticsHorizon.WEEK,
            today,
            Weekday.MONDAY,
            earliestEpochDay = null,
        )
        assertEquals(CivilDate(2026, 8, 24).previousOrSame(Weekday.MONDAY).epochDay, start)
        assertEquals(today.epochDay, end)
    }

    @Test
    fun dayIsOnlyToday() {
        val projections = listOf(
            day(CivilDate(2026, 8, 23).epochDay, sessions = 1, minutes = 30),
            day(today.epochDay, sessions = 2, minutes = 80),
        )
        val dayTotals = HorizonMath.totals(
            AnalyticsHorizon.DAY,
            projections,
            today,
            weekStart,
        )
        assertEquals(2, dayTotals.sessionCount)
        assertEquals(1, dayTotals.trainedDays)
        assertEquals(today.epochDay, dayTotals.startEpochDay)
        assertEquals(today.epochDay, dayTotals.endEpochDay)
        val (start, end) = HorizonMath.range(
            AnalyticsHorizon.DAY,
            today,
            weekStart,
            earliestEpochDay = null,
        )
        assertEquals(today.epochDay, start)
        assertEquals(today.epochDay, end)
    }

    private fun day(epochDay: Long, sessions: Int, minutes: Int) = DailyProjection(
        localEpochDay = epochDay,
        sessionCount = sessions,
        workingSets = sessions * 10,
        volumeKg = 100.0,
        activeMinutes = minutes,
        cardioSeconds = 0L,
        cardioDistanceMeters = 0.0,
        sessionIds = List(sessions) { "s-$epochDay-$it" },
    )
}
