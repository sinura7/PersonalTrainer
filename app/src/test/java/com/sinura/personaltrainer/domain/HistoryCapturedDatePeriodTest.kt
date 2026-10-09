package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCapturedDatePeriodTest {
    private val today = CivilDate(2026, 12, 31)
    private val captured = CivilDate(2027, 1, 1)
    private val completed = setOf(CivilDate(2024, 2, 29).epochDay, captured.epochDay)

    @Test
    fun allIncludesCapturedCompletedDaysAheadOfDeviceToday() {
        val range = resolve(AnalyticsHorizon.ALL_TIME, today)
        assertEquals(CivilDate(2024, 2, 29).epochDay, range.startEpochDay)
        assertEquals(captured.epochDay + 1, range.endExclusiveEpochDay)
        assertTrue(captured.epochDay in range)
        assertFalse(captured.plusDays(1).epochDay in range)
        assertEquals(
            HistoryPeriodRange(captured.epochDay, captured.epochDay + 1),
            HistoryPeriodMath.resolve(selection(AnalyticsHorizon.ALL_TIME, today), today, Weekday.MONDAY, captured.epochDay, setOf(captured.epochDay)),
        )
    }

    @Test
    fun currentPeriodsExtendOnlyForCompletedDatesInsideTheirOwnPeriod() {
        assertEquals(today.epochDay + 1, resolve(AnalyticsHorizon.DAY, today, follow = true).endExclusiveEpochDay)
        assertEquals(today.epochDay + 1, resolve(AnalyticsHorizon.MONTH, today, follow = true).endExclusiveEpochDay)
        assertEquals(today.epochDay + 1, resolve(AnalyticsHorizon.YEAR, today, follow = true).endExclusiveEpochDay)
        for (start in listOf(Weekday.MONDAY, Weekday.SUNDAY)) {
            val week = HistoryPeriodMath.resolve(selection(AnalyticsHorizon.WEEK, today, true), today, start, null, completed)
            assertEquals(captured.epochDay + 1, week.endExclusiveEpochDay)
            assertTrue(captured.epochDay in week)
        }
        val earlier = CivilDate(2026, 12, 30)
        val withinMonth = setOf(today.epochDay)
        for (horizon in listOf(AnalyticsHorizon.WEEK, AnalyticsHorizon.MONTH, AnalyticsHorizon.YEAR)) {
            val range = HistoryPeriodMath.resolve(selection(horizon, earlier, true), earlier, Weekday.MONDAY, null, withinMonth)
            assertEquals(today.epochDay + 1, range.endExclusiveEpochDay)
        }
    }

    @Test
    fun futureCompletedDayAndMonthAreSelectableButEmptyFutureDatesAreNot() {
        assertTrue(HistoryPeriodMath.canSelectDay(captured.epochDay, today, completed))
        assertFalse(HistoryPeriodMath.canSelectDay(captured.plusDays(1).epochDay, today, completed))
        assertTrue(HistoryPeriodMath.canSelectMonth(CivilYearMonth(2027, 1), today, completed))
        assertFalse(HistoryPeriodMath.canSelectMonth(CivilYearMonth(2027, 2), today, completed))
        assertEquals(HistoryPeriodRange(captured.epochDay, captured.epochDay + 1), resolve(AnalyticsHorizon.DAY, captured))
        for (horizon in listOf(AnalyticsHorizon.MONTH, AnalyticsHorizon.YEAR)) {
            assertEquals(captured, HistoryPeriodMath.anchor(selection(horizon, CivilDate(2027, 1, 15)), today, Weekday.MONDAY, completed))
            assertEquals(captured.epochDay + 1, resolve(horizon, CivilDate(2027, 1, 15)).endExclusiveEpochDay)
        }
    }

    @Test
    fun navigationUsesCompletedMembershipRatherThanLatestDateAlone() {
        for (horizon in listOf(AnalyticsHorizon.DAY, AnalyticsHorizon.MONTH, AnalyticsHorizon.YEAR)) {
            val next = checkNotNull(HistoryPeriodMath.next(selection(horizon, today), today, Weekday.MONDAY, completed))
            assertEquals(captured.epochDay, next.anchorEpochDay)
            assertFalse(next.followToday)
            assertNull(HistoryPeriodMath.next(next, today, Weekday.MONDAY, completed))
            val current = HistoryPeriodMath.current(next, today)
            assertTrue(current.followToday)
            assertEquals(today, HistoryPeriodMath.anchor(current, today, Weekday.MONDAY, completed))
        }
        val sparse = setOf(CivilDate(2029, 1, 1).epochDay)
        assertNull(HistoryPeriodMath.next(selection(AnalyticsHorizon.YEAR, today), today, Weekday.MONDAY, sparse))
        assertNull(HistoryPeriodMath.previous(selection(AnalyticsHorizon.YEAR, CivilDate(2029, 1, 1)), today, Weekday.MONDAY, sparse))
        assertFalse(HistoryPeriodMath.canSelectDay(CivilDate(2028, 1, 1).epochDay, today, sparse))
    }

    @Test
    fun unknownFutureSavedAnchorFallsBackWithoutInventingACompletedDay() {
        val unknown = captured.plusDays(1)
        assertEquals(today, HistoryPeriodMath.anchor(selection(AnalyticsHorizon.DAY, unknown), today, Weekday.MONDAY, completed))
        assertEquals(today, HistoryPeriodMath.anchor(selection(AnalyticsHorizon.ALL_TIME, unknown), today, Weekday.MONDAY, completed))
        val currentMonthUnknown = CivilDate(2026, 12, 31)
        val earlierToday = CivilDate(2026, 12, 20)
        assertEquals(earlierToday, HistoryPeriodMath.anchor(selection(AnalyticsHorizon.MONTH, currentMonthUnknown), earlierToday, Weekday.MONDAY, setOf(earlierToday.minusDays(1).epochDay)))
    }

    @Test
    fun historicalMonthAndYearRemainCompleteDespiteCapturedFutureWork() {
        assertEquals(HistoryPeriodRange(CivilDate(2024, 2, 1).epochDay, CivilDate(2024, 3, 1).epochDay), resolve(AnalyticsHorizon.MONTH, CivilDate(2024, 2, 29)))
        assertEquals(HistoryPeriodRange(CivilDate(2024, 1, 1).epochDay, CivilDate(2025, 1, 1).epochDay), resolve(AnalyticsHorizon.YEAR, CivilDate(2024, 2, 29)))
    }

    @Test
    fun malformedCatalogDaysCannotExpandTheSupportedRange() {
        val bad = setOf(Long.MIN_VALUE, Long.MAX_VALUE)
        assertEquals(HistoryPeriodRange(today.epochDay, today.epochDay + 1), HistoryPeriodMath.resolve(selection(AnalyticsHorizon.ALL_TIME, today), today, Weekday.MONDAY, null, bad))
        assertFalse(HistoryPeriodMath.canSelectDay(Long.MAX_VALUE, today, bad))
        val last = CivilDate(999_999_999, 12, 31)
        assertNull(HistoryPeriodMath.next(selection(AnalyticsHorizon.DAY, last), last, Weekday.MONDAY, setOf(last.epochDay)))
        assertEquals(MAX_HISTORY_EPOCH_DAY + 1, HistoryPeriodMath.resolve(selection(AnalyticsHorizon.ALL_TIME, last), last, Weekday.MONDAY, null, setOf(last.epochDay)).endExclusiveEpochDay)
    }

    private fun resolve(horizon: AnalyticsHorizon, date: CivilDate, follow: Boolean = false): HistoryPeriodRange =
        HistoryPeriodMath.resolve(selection(horizon, date, follow), today, Weekday.MONDAY, completed.minOrNull(), completed)

    private fun selection(horizon: AnalyticsHorizon, date: CivilDate, follow: Boolean = false) =
        HistoryPeriodSelection(horizon, date.epochDay, follow)
}
