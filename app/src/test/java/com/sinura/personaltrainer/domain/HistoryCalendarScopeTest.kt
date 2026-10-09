package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCalendarScopeTest {
    @Test
    fun dayRetainsFullMonthIntensityAndHidesOutsideRecords() {
        val selected = CivilDate(2026, 8, 5)
        val summaries = listOf(
            summary("selected", selected, sets = 2),
            summary("outside", CivilDate(2026, 8, 12), sets = 8),
            summary("padding", CivilDate(2026, 7, 27), sets = 20),
        )
        val range = HistoryPeriodRange(selected.epochDay, selected.epochDay + 1)
        val scoped = calendar(CivilYearMonth(2026, 8), summaries, range)
        val original = calendar(CivilYearMonth(2026, 8), summaries)
        assertEquals(original.day(selected).intensity, scoped.day(selected).intensity, 0f)
        assertEquals(0.25f, scoped.day(selected).intensity, 0f)
        assertEquals(listOf("selected"), scoped.day(selected).sessionIds)
        assertFalse(scoped.day(CivilDate(2026, 8, 12)).trained)
        assertFalse(scoped.day(CivilDate(2026, 7, 27)).trained)
        assertEquals(SetWork.NONE, scoped.day(CivilDate(2026, 8, 12)).work)
        assertEquals(0f, scoped.day(CivilDate(2026, 8, 12)).intensity, 0f)
        assertEquals(1, scoped.trainedDays)
        assertEquals(2, scoped.workingSets)
        assertEquals(200.0, scoped.work.volumeKg, 0.0)
    }

    @Test
    fun crossYearWeekUsesEachFullMonthsHeatScale() {
        val decemberDay = CivilDate(2025, 12, 29)
        val januaryDay = CivilDate(2026, 1, 2)
        val summaries = listOf(
            summary("december", decemberDay, sets = 2),
            summary("december-scale", CivilDate(2025, 12, 15), sets = 8),
            summary("january", januaryDay, sets = 3),
            summary("january-scale", CivilDate(2026, 1, 20), sets = 6),
        )
        val range = HistoryPeriodRange(decemberDay.epochDay, CivilDate(2026, 1, 5).epochDay)
        val grid = calendar(CivilYearMonth(2026, 1), summaries, range)
        val week = grid.weekContaining(januaryDay.epochDay)!!
        assertEquals(decemberDay.epochDay, week.first().date.epochDay)
        assertEquals(0.25f, grid.day(decemberDay).intensity, 0f)
        assertEquals(0.5f, grid.day(januaryDay).intensity, 0f)
        assertEquals(listOf("december", "january"), week.flatMap { it.sessionIds })
        assertTrue(week.all { it.intensity in 0f..1f })
        assertFalse(grid.day(CivilDate(2026, 1, 20)).trained)
    }

    @Test
    fun monthAndTotalsUseTheSameHalfOpenBoundaryAndCompositeIdentity() {
        val first = CivilDate(2024, 2, 1)
        val last = CivilDate(2024, 2, 29)
        val summaries = listOf(
            summary("before", first.plusDays(-1), sets = 9),
            summary("same-id", first, sets = 2),
            summary("same-id", first, sets = 0, kind = HistoryKind.ACTIVITY),
            summary("last", last, sets = 3),
            summary("after", last.plusDays(1), sets = 10),
        )
        val range = HistoryPeriodRange(first.epochDay, last.epochDay + 1)
        val scoped = summaries.filter { it.localEpochDay in range }
        val grid = calendar(CivilYearMonth(2024, 2), summaries, range)
        val totals = HorizonMath.totals(AnalyticsHorizon.MONTH, DailyProjectionBuilder.project(summaries), range)
        assertEquals(listOf("same-id"), grid.day(first).sessionIds)
        assertEquals(listOf("same-id"), grid.day(first).activityIds)
        assertEquals(3, totals.sessionCount)
        assertEquals(scoped.sumOf { it.workingSets }, totals.workingSets)
        assertEquals(scoped.sumOf { it.volumeKg }, totals.volumeKg, 0.0)
        assertEquals(scoped.sumOf { it.durationMinutes }, totals.activeMinutes)
        assertEquals(2, grid.trainedDays)
        assertEquals(totals.workingSets, grid.workingSets)
        assertEquals(totals.volumeKg, grid.work.volumeKg, 0.0)
        assertFalse(grid.day(first.plusDays(-1)).trained)
        assertFalse(grid.day(last.plusDays(1)).trained)
        assertEquals(setOf("same-id", "last"), grid.weeks.flatten().flatMap { it.sessionIds + it.activityIds }.toSet())
    }

    @Test
    fun cardioOnlyDayIsTrainedWithoutInventingStrengthWork() {
        val day = CivilDate(2026, 8, 10)
        val summary = summary("run", day, sets = 0, kind = HistoryKind.ACTIVITY)
        val grid = calendar(CivilYearMonth(2026, 8), listOf(summary), HistoryPeriodRange(day.epochDay, day.epochDay + 1))
        assertTrue(grid.day(day).trained)
        assertEquals(listOf("run"), grid.day(day).activityIds)
        assertTrue(grid.day(day).sessionIds.isEmpty())
        assertEquals(0f, grid.day(day).intensity, 0f)
        assertEquals(SetWork.NONE, grid.day(day).work)
        assertEquals(0, grid.workingSets)
    }

    @Test
    fun unscopedPaddingBehaviorRemainsCompatible() {
        val padding = CivilDate(2026, 7, 27)
        val rows = listOf(summary("padding", padding, sets = 6), summary("month", CivilDate(2026, 8, 5), sets = 1))
        val default = TrainingCalendarBuilder.buildSummaries(CivilYearMonth(2026, 8), rows)
        val explicit = calendar(CivilYearMonth(2026, 8), rows, null)
        assertEquals(default, explicit)
        assertEquals(listOf("padding"), explicit.day(padding).sessionIds)
        assertEquals(1f, explicit.day(padding).intensity, 0f)
        assertEquals(1, explicit.trainedDays)
        assertEquals(1, explicit.workingSets)
    }

    private fun calendar(month: CivilYearMonth, summaries: List<SessionSummary>, range: HistoryPeriodRange? = null) =
        TrainingCalendarBuilder.buildSummaries(month, summaries, Weekday.MONDAY, range)

    private fun TrainingMonth.day(date: CivilDate) = weeks.flatten().single { it.date == date }

    private fun summary(id: String, day: CivilDate, sets: Int, kind: HistoryKind = HistoryKind.WORKOUT) = SessionSummary(
        id = id, routineId = null, routineName = id, date = day.epochDay * 86_400_000L,
        finishedAt = day.epochDay * 86_400_000L + 1, durationMinutes = 20,
        workingSets = sets, volumeKg = sets * 100.0, localEpochDay = day.epochDay,
        cardioSeconds = if (kind == HistoryKind.ACTIVITY) 1_200L else 0L, kind = kind,
    )
}
