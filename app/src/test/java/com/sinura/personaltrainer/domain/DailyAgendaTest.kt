package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyAgendaTest {
    @Test
    fun sortsMorningCardioBeforeEveningStrength() {
        val day = 20_000L
        val cardio = occ("c", "r-c", day, 7)
        val lift = occ("s", "r-s", day, 18)
        val other = occ("x", "r-x", day + 1, 7)
        val items = DailyAgenda.forDay(
            day,
            listOf(lift, other, cardio),
            listOf(
                rule("r-c", ScheduleModality.CARDIO),
                rule("r-s", ScheduleModality.STRENGTH),
            ),
        )
        assertEquals(listOf("c", "s"), items.map { it.occurrence.id })
        assertEquals("Cardio", items[0].title)
        assertEquals("Strength", items[1].title)
        assertEquals("07:00", items[0].timeLabel)
    }

    @Test
    fun startableKeepsOnlyPlanned() {
        val day = 20_000L
        val planned = occ("p", "r", day, 7)
        val done = planned.copy(id = "d", status = OccurrenceStatus.DONE)
        val items = DailyAgenda.forDay(day, listOf(planned, done), listOf(rule("r", ScheduleModality.CARDIO)))
        assertEquals(listOf("p"), DailyAgenda.startable(items).map { it.occurrence.id })
    }

    @Test
    fun strengthTitlePrefersTheRoutineName() {
        val day = 20_000L
        val planned = occ("s", "r-s", day, 18)
        val items = DailyAgenda.forDay(
            day,
            listOf(planned),
            listOf(rule("r-s", ScheduleModality.STRENGTH).copy(routineId = "routine-1")),
            mapOf("routine-1" to "Monday"),
        )
        assertEquals("Monday", items.single().title)
    }

    @Test
    fun minutesOfDayClamps() {
        assertEquals(0, DailyAgenda.minutesOfDay(0L, 0L))
        assertEquals(90, DailyAgenda.minutesOfDay(90 * 60_000L, 0L))
        assertEquals(24 * 60 - 1, DailyAgenda.minutesOfDay(100 * 60 * 60_000L, 0L))
    }

    @Test
    fun twoADayMarksOnlyDaysWithTwoOccurrences() {
        val day = 20_000L
        val marked = DailyAgenda.twoADayEpochDays(
            listOf(
                occ("c", "r-c", day, 7),
                occ("s", "r-s", day, 18),
                occ("x", "r-x", day + 1, 7),
            ),
        )
        assertEquals(setOf(day), marked)
    }

    private fun rule(id: String, modality: ScheduleModality) = ScheduleRule(
        id = id,
        weekday = Weekday.MONDAY,
        hour = 7,
        minute = 0,
        modality = modality,
        createdAtMs = 1L,
        updatedAtMs = 1L,
    )

    private fun occ(id: String, ruleId: String, day: Long, hour: Int) = ScheduleOccurrence(
        id = id,
        ruleId = ruleId,
        status = OccurrenceStatus.PLANNED,
        captured = CapturedCivilTime(1L, "UTC", 0, day),
        hour = hour,
        minute = 0,
        createdAtMs = 1L,
        updatedAtMs = 1L,
    )
}
