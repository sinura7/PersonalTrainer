package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRecordsTest {
    @Test fun recordedDatesAndRepeatedSessionsRemainDistinct() {
        val first = record("first", 20_000, 10)
        val second = record("second", 20_000, 20)
        val backdated = record("backdated", 19_999, 30)
        assertEquals(listOf(second, first), HomeRecords.unlinkedForDay(20_000, listOf(first, backdated, second), emptyList()))
    }

    @Test fun onlyTheExplicitCompletedLinkRemovesTheStandaloneRecord() {
        val first = record("first", 20_000, 10)
        val second = record("second", 20_000, 20)
        val occurrence = ScheduleOccurrence(
            id = "o", ruleId = "r", status = OccurrenceStatus.DONE,
            captured = CapturedCivilTime(1, "UTC", 0, 20_000), hour = 12, minute = 0,
            completedActivityId = first.id, createdAtMs = 1, updatedAtMs = 1,
        )
        val item = AgendaItem(occurrence, null, null)
        assertEquals(listOf(second), HomeRecords.unlinkedForDay(20_000, listOf(first, second), listOf(item)))
        assertEquals(listOf(second, first), HomeRecords.unlinkedForDay(20_000, listOf(first, second), listOf(item.copy(occurrence = occurrence.copy(status = OccurrenceStatus.SKIPPED)))))
    }

    @Test fun summaryUsesRecordedMetricsAndReadableDuration() {
        assertEquals("3 working sets · 1 h 20 min", HomeRecords.metrics(record("a", 20_000, 1)))
        val cardio = record("c", 20_000, 2).copy(kind = HistoryKind.ACTIVITY, workingSets = 0, durationMinutes = 60)
        assertEquals("1 h cardio", HomeRecords.metrics(cardio))
        assertEquals("3 working sets", HomeRecords.metrics(cardio.copy(workingSets = 3, durationMinutes = 0)))
    }

    @Test fun skippedDaysNeverClaimTrainingCompletion() {
        val cell = WeekBoardCell(
            epochDay = 20_000, weekday = Weekday.FRIDAY, fill = DayFill.ALL,
            caption = "Workout", plannedCount = 1, resolvedCount = 1,
            completedCount = 0, skippedCount = 1,
        )
        assertEquals("Skip", WeekBoard.statusLabel(cell))
        assertTrue(WeekBoard.spoken(cell, 20_001, 20_000).contains("1 skipped"))
        assertFalse(WeekBoard.spoken(cell, 20_001, 20_000).contains("completed"))
        assertEquals("1 planned · 0 done this week · 1 skipped", WeekBoard.summary(listOf(cell)))
        assertEquals("Mixed", WeekBoard.statusLabel(cell.copy(recordedCount = 1)))
    }

    @Test fun unplannedTrainingAndRemainingWorkAreVisibleInTheDayCell() {
        val empty = WeekBoardCell(20_000, Weekday.FRIDAY, DayFill.EMPTY, "Rest", 0, 0)
        assertEquals("Done", WeekBoard.statusLabel(empty.copy(recordedCount = 1)))
        assertEquals("More", WeekBoard.statusLabel(empty.copy(plannedCount = 1, recordedCount = 1)))
        assertEquals("Missed", WeekBoard.statusLabel(empty.copy(plannedCount = 1, missedCount = 1)))
    }

    @Test fun historicalCompletionNeverSaysToday() {
        assertEquals("TRAINING COMPLETE", MastheadCopy.headline(null, true, null, isToday = false))
        assertEquals("TRAINED TODAY", MastheadCopy.headline(null, true, null, isToday = true))
    }

    @Test fun completedAndMissedActivitiesNeverClaimTheWholeDayIsComplete() {
        val occurrence = ScheduleOccurrence(
            id = "missed", ruleId = "r", status = OccurrenceStatus.MISSED,
            captured = CapturedCivilTime(1, "UTC", 0, 20_000), hour = 12, minute = 0,
            createdAtMs = 1, updatedAtMs = 1,
        )
        val agenda = listOf(
            AgendaItem(occurrence, null, null),
            AgendaItem(occurrence.copy(id = "done", status = OccurrenceStatus.DONE), null, null),
        )
        assertEquals(
            "TRAINING RECORDED",
            MastheadCopy.headline(
                day = null, loggedToday = true, liftCount = null,
                agenda = agenda, isToday = false,
            ),
        )
    }

    private fun record(id: String, day: Long, instant: Long) = SessionSummary(
        id = id, routineId = "same-routine", routineName = "Lower A", date = instant,
        finishedAt = instant + 1, durationMinutes = 80, workingSets = 3,
        volumeKg = 100.0, localEpochDay = day,
    )
}
