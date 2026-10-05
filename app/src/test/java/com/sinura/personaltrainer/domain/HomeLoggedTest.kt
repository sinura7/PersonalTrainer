package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLoggedTest {
    @Test
    fun offPlanExcludesSessionsLinkedFromDoneRows() {
        val summary = summary("s1", DAY)
        val linked = summary("linked", DAY)
        val agenda = listOf(
            doneItem("occ-1", completedActivityId = "linked"),
        )
        val offPlan = HomeLogged.offPlanForDay(DAY, agenda, listOf(summary, linked))
        assertEquals(listOf("s1"), offPlan.map { it.id })
    }

    @Test
    fun emptyCopyHiddenWhenOffPlanOrDoneRowsExist() {
        assertFalse(
            HomeLogged.showEmptyCopy(
                agenda = emptyList(),
                stillOpen = emptyList(),
                offPlan = listOf(summary("s1", DAY)),
            ),
        )
        assertFalse(
            HomeLogged.showEmptyCopy(
                agenda = listOf(doneItem("occ-1", completedActivityId = "s1")),
                stillOpen = emptyList(),
                offPlan = emptyList(),
            ),
        )
        assertTrue(
            HomeLogged.showEmptyCopy(
                agenda = emptyList(),
                stillOpen = emptyList(),
                offPlan = emptyList(),
            ),
        )
    }

    @Test
    fun byDayGroupsSummariesOnLocalEpochDay() {
        val map = HomeLogged.byDay(
            listOf(
                summary("a", DAY),
                summary("b", DAY + 1),
            ),
        )
        assertEquals(1, map[DAY]?.size)
        assertEquals(1, map[DAY + 1]?.size)
    }

    private fun summary(id: String, day: Long) = SessionSummary(
        id = id,
        routineId = "r1",
        routineName = "Push",
        date = 1L,
        finishedAt = 2L,
        durationMinutes = 45,
        workingSets = 12,
        volumeKg = 8000.0,
        localEpochDay = day,
    )

    private fun doneItem(
        occurrenceId: String,
        completedActivityId: String,
    ) = AgendaItem(
        occurrence = ScheduleOccurrence(
            id = occurrenceId,
            ruleId = "rule-$occurrenceId",
            status = OccurrenceStatus.DONE,
            captured = CapturedCivilTime(1L, "UTC", 0, DAY),
            hour = 18,
            minute = 0,
            createdAtMs = 1L,
            updatedAtMs = 1L,
            completedActivityId = completedActivityId,
        ),
        rule = ScheduleRule(
            id = "rule-$occurrenceId",
            weekday = Weekday.MONDAY,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
            routineId = "r1",
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ),
    )

    private companion object {
        const val DAY = 20_000L
    }
}
