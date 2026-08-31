package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayBlockOrderTest {
    @Test
    fun moveUpSwapsHoursWithoutInventingNewOnes() {
        val cardio = item("c", hour = 7)
        val lift = item("s", hour = 18)
        val extra = item("e", hour = 20)
        val moved = DayBlockOrder.move(listOf(cardio, lift, extra), fromIndex = 2, delta = -1)
        assertEquals(
            listOf(
                DayBlockOrder.HourMove("c", "rule-c", 7),
                DayBlockOrder.HourMove("e", "rule-e", 18),
                DayBlockOrder.HourMove("s", "rule-s", 20),
            ),
            moved,
        )
    }

    @Test
    fun moveDownOnTheLastRowIsANoOp() {
        val items = listOf(item("a", hour = 18), item("b", hour = 20))
        assertTrue(DayBlockOrder.move(items, fromIndex = 1, delta = 1).isEmpty())
        assertTrue(DayBlockOrder.move(items, fromIndex = 0, delta = 0).isEmpty())
        assertTrue(DayBlockOrder.move(listOf(item("a", hour = 18)), fromIndex = 0, delta = 1).isEmpty())
    }

    private fun item(id: String, hour: Int) = AgendaItem(
        occurrence = ScheduleOccurrence(
            id = id,
            ruleId = "rule-$id",
            status = OccurrenceStatus.PLANNED,
            captured = CapturedCivilTime(1L, "UTC", 0, 20_000L),
            hour = hour,
            minute = 0,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ),
        rule = ScheduleRule(
            id = "rule-$id",
            weekday = Weekday.MONDAY,
            hour = hour,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ),
    )
}
