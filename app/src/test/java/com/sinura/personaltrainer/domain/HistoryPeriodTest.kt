package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPeriodTest {
    private val today = CivilDate(2026, 10, 9)

    @Test
    fun currentPeriodsRetainTheExistingToDateTotals() {
        val rows = listOf(
            projection(CivilDate(2025, 12, 31), 2),
            projection(CivilDate(2026, 1, 1), 3),
            projection(CivilDate(2026, 10, 1), 4),
            projection(today.minusDays(1), 5),
            projection(today, 6),
            projection(today.plusDays(1), 7),
        )
        for (weekStart in listOf(Weekday.MONDAY, Weekday.SUNDAY)) {
            for (horizon in AnalyticsHorizon.entries) {
                val range = HistoryPeriodMath.resolve(
                    selection(horizon, today, follow = true), today, weekStart,
                    rows.minOf { it.localEpochDay },
                )
                assertEquals(
                    HorizonMath.totals(horizon, rows, today, weekStart),
                    HorizonMath.totals(horizon, rows, range),
                )
                assertEquals(today.epochDay + 1, range.endExclusiveEpochDay)
            }
        }
    }

    @Test
    fun historicalLeapMonthIncludesBothBoundariesExactlyOnce() {
        val range = resolve(AnalyticsHorizon.MONTH, CivilDate(2024, 2, 29))
        assertEquals(CivilDate(2024, 2, 1).epochDay, range.startEpochDay)
        assertEquals(CivilDate(2024, 3, 1).epochDay, range.endExclusiveEpochDay)
        assertFalse(CivilDate(2024, 1, 31).epochDay in range)
        assertTrue(CivilDate(2024, 2, 1).epochDay in range)
        assertTrue(CivilDate(2024, 2, 29).epochDay in range)
        assertFalse(CivilDate(2024, 3, 1).epochDay in range)
        val rows = listOf(
            projection(CivilDate(2024, 1, 31), 11),
            projection(CivilDate(2024, 2, 1), 2),
            projection(CivilDate(2024, 2, 29), 3),
            projection(CivilDate(2024, 3, 1), 13),
        )
        val totals = HorizonMath.totals(AnalyticsHorizon.MONTH, rows, range)
        assertEquals(5, totals.sessionCount)
        assertEquals(2, totals.trainedDays)
        assertEquals(50, totals.workingSets)
        assertEquals(500.0, totals.volumeKg, 0.0)
        assertEquals(75, totals.activeMinutes)
        assertEquals(150L, totals.cardioSeconds)
        assertEquals(1250.0, totals.cardioDistanceMeters, 0.0)
    }

    @Test
    fun historicalWeekCrossesTheYearUsingConfiguredWeekStart() {
        val anchor = CivilDate(2026, 1, 1)
        val monday = HistoryPeriodMath.resolve(selection(AnalyticsHorizon.WEEK, anchor), today, Weekday.MONDAY, null)
        val sunday = HistoryPeriodMath.resolve(selection(AnalyticsHorizon.WEEK, anchor), today, Weekday.SUNDAY, null)
        assertEquals(HistoryPeriodRange(CivilDate(2025, 12, 29).epochDay, CivilDate(2026, 1, 5).epochDay), monday)
        assertEquals(HistoryPeriodRange(CivilDate(2025, 12, 28).epochDay, CivilDate(2026, 1, 4).epochDay), sunday)
        assertTrue(CivilDate(2025, 12, 28).epochDay in sunday)
        assertFalse(CivilDate(2025, 12, 28).epochDay in monday)
    }

    @Test
    fun historicalYearCoversItsWholeLeapYearAndDayCoversOneCivilDay() {
        val year = resolve(AnalyticsHorizon.YEAR, CivilDate(2024, 6, 12))
        assertEquals(CivilDate(2024, 1, 1).epochDay, year.startEpochDay)
        assertEquals(CivilDate(2025, 1, 1).epochDay, year.endExclusiveEpochDay)
        assertEquals(366L, year.endExclusiveEpochDay - year.startEpochDay)
        val day = resolve(AnalyticsHorizon.DAY, CivilDate(2024, 2, 29))
        assertEquals(1L, day.endExclusiveEpochDay - day.startEpochDay)
        assertTrue(CivilDate(2024, 2, 29).epochDay in day)
        assertFalse(CivilDate(2024, 3, 1).epochDay in day)
    }

    @Test
    fun allUsesEarliestThroughTodayAndHasNoIndependentPaging() {
        val chosen = selection(AnalyticsHorizon.ALL_TIME, CivilDate(2023, 2, 28))
        val earliest = CivilDate(2020, 2, 29).epochDay
        val range = HistoryPeriodMath.resolve(chosen, today, Weekday.MONDAY, earliest)
        assertEquals(earliest, range.startEpochDay)
        assertEquals(today.epochDay + 1, range.endExclusiveEpochDay)
        assertNull(HistoryPeriodMath.previous(chosen, today, Weekday.MONDAY))
        assertNull(HistoryPeriodMath.next(chosen, today, Weekday.MONDAY))
        assertEquals(
            HistoryPeriodRange(today.epochDay, today.epochDay + 1),
            HistoryPeriodMath.resolve(chosen, today, Weekday.MONDAY, null),
        )
        assertEquals(
            HistoryPeriodRange(today.epochDay, today.epochDay + 1),
            HistoryPeriodMath.resolve(chosen, today, Weekday.MONDAY, today.epochDay + 10),
        )
    }

    @Test
    fun monthAndYearPagingClampValidDaysAndRefuseFutureOnlyPeriods() {
        val january = selection(AnalyticsHorizon.MONTH, CivilDate(2024, 1, 31))
        val february = checkNotNull(HistoryPeriodMath.next(january, today, Weekday.MONDAY))
        assertEquals(CivilDate(2024, 2, 29).epochDay, february.anchorEpochDay)
        val leapDay = selection(AnalyticsHorizon.YEAR, CivilDate(2024, 2, 29))
        assertEquals(
            CivilDate(2023, 2, 28).epochDay,
            checkNotNull(HistoryPeriodMath.previous(leapDay, today, Weekday.MONDAY)).anchorEpochDay,
        )
        for (horizon in AnalyticsHorizon.entries.filterNot { it == AnalyticsHorizon.ALL_TIME }) {
            assertNull(HistoryPeriodMath.next(selection(horizon, today), today, Weekday.MONDAY))
            val prior = checkNotNull(HistoryPeriodMath.previous(selection(horizon, today), today, Weekday.MONDAY))
            assertFalse(prior.followToday)
            assertTrue(checkNotNull(HistoryPeriodMath.next(prior, today, Weekday.MONDAY)).anchorEpochDay <= today.epochDay)
        }
    }

    @Test
    fun currentFollowsCivilDateWhilePastAndFutureAnchorsResolveHonestly() {
        val past = selection(AnalyticsHorizon.MONTH, CivilDate(2024, 2, 29))
        val tomorrow = today.plusDays(1)
        assertEquals(resolve(AnalyticsHorizon.MONTH, CivilDate(2024, 2, 29)), HistoryPeriodMath.resolve(past, tomorrow, Weekday.MONDAY, null))
        val current = HistoryPeriodMath.current(past, today)
        assertTrue(current.followToday)
        assertEquals(today.epochDay, current.anchorEpochDay)
        assertEquals(tomorrow.epochDay + 1, HistoryPeriodMath.resolve(current, tomorrow, Weekday.MONDAY, null).endExclusiveEpochDay)
        val future = selection(AnalyticsHorizon.DAY, tomorrow)
        assertEquals(HistoryPeriodRange(today.epochDay, today.epochDay + 1), HistoryPeriodMath.resolve(future, today, Weekday.MONDAY, null))
    }

    @Test
    fun invalidSavedLongAndReversedRangesAreRejectedBeforeFormatting() {
        assertEquals(CivilDate(-999_999_999, 1, 1).epochDay, MIN_HISTORY_EPOCH_DAY)
        assertEquals(CivilDate(999_999_999, 12, 31).epochDay, MAX_HISTORY_EPOCH_DAY)
        assertThrows(IllegalArgumentException::class.java) { HistoryPeriodSelection(AnalyticsHorizon.DAY, Long.MAX_VALUE, false) }
        assertThrows(IllegalArgumentException::class.java) { HistoryPeriodSelection(AnalyticsHorizon.DAY, Long.MIN_VALUE, false) }
        assertThrows(IllegalArgumentException::class.java) { HistoryPeriodRange(today.epochDay, today.epochDay) }
        assertThrows(IllegalArgumentException::class.java) { HistoryPeriodRange(today.epochDay, today.epochDay - 1) }
        assertNull(HistoryPeriodMath.previous(selection(AnalyticsHorizon.DAY, CivilDate(-999_999_999, 1, 1)), today, Weekday.MONDAY))
    }

    private fun resolve(horizon: AnalyticsHorizon, anchor: CivilDate): HistoryPeriodRange =
        HistoryPeriodMath.resolve(selection(horizon, anchor), today, Weekday.MONDAY, null)

    private fun selection(horizon: AnalyticsHorizon, anchor: CivilDate, follow: Boolean = false) =
        HistoryPeriodSelection(horizon, anchor.epochDay, follow)

    private fun projection(date: CivilDate, sessions: Int) = DailyProjection(
        localEpochDay = date.epochDay, sessionCount = sessions, workingSets = sessions * 10,
        volumeKg = sessions * 100.0, activeMinutes = sessions * 15, cardioSeconds = sessions * 30L,
        cardioDistanceMeters = sessions * 250.0, sessionIds = List(sessions) { "${date.epochDay}-$it" },
    )
}
