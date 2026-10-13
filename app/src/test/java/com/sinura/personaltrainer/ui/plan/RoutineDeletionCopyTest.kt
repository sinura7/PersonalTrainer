package com.sinura.personaltrainer.ui.plan

import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.ScheduleRule
import com.sinura.personaltrainer.domain.Weekday
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineDeletionCopyTest {
    private val rule = ScheduleRule(id = "rule", weekday = Weekday.MONDAY, hour = 18, minute = 0, modality = ScheduleModality.STRENGTH,
        routineId = "routine", createdAtMs = 1, updatedAtMs = 1)
    private fun occurrence(id: String, day: CivilDate, status: OccurrenceStatus) = ScheduleOccurrence(
        id = id, ruleId = rule.id, status = status, captured = CapturedCivilTime(1, "UTC", 0, day.epochDay), hour = 18, minute = 0,
        createdAtMs = 1, updatedAtMs = 1)

    @Test fun movedPlannedDateIsNamedSeparatelyFromItsRecurringWeekday() {
        val state = PlanUiState(rules = listOf(rule), occurrences = listOf(
            occurrence("moved", CivilDate(2026, 10, 14), OccurrenceStatus.PLANNED),
            occurrence("missed", CivilDate(2026, 10, 12), OccurrenceStatus.MISSED)))
        val text = routineDeletionBody(state, "routine")
        assertTrue(text.contains("Weekly schedule: Monday"))
        assertTrue(text.contains("Planned dates in this week: Wed 14 Oct 2026"))
        assertFalse(text.contains("Mon 12 Oct 2026"))
        assertFalse(text.contains("Unfinished"))
        assertTrue(text.contains("Past schedule outcomes stay unchanged"))
        assertTrue(text.contains("Completed sessions stay saved in History"))
    }

    @Test fun oneOffWorkDoesNotInventWeeklyScopeAndOtherRoutinesAreExcluded() {
        val state = PlanUiState(rules = listOf(rule.copy(enabled = false), rule.copy(id = "other", routineId = "other")),
            occurrences = listOf(occurrence("once", CivilDate(2026, 10, 14), OccurrenceStatus.PLANNED)))
        val text = routineDeletionBody(state, "routine")
        assertFalse(text.contains("Weekly schedule"))
        assertTrue(text.contains("Wed 14 Oct 2026"))
        assertTrue(text.contains("All planned sessions"))
    }
}
