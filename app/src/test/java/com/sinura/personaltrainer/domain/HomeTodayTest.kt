package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeTodayTest {
    @Test
    fun emptyAgendaUsesTheSlotWeekLeftover() {
        assertEquals(HomeToday.Surface.WEEK_FALLBACK, HomeToday.surface(emptyList()))
        assertNull(HomeToday.startTagOccurrenceId(emptyList()))
    }

    @Test
    fun anyOccurrenceMakesAgendaTheOnlyToday() {
        val cardio = item("c", ScheduleModality.CARDIO)
        assertEquals(HomeToday.Surface.AGENDA, HomeToday.surface(listOf(cardio)))
    }

    @Test
    fun startTagPrefersPlannedStrengthOnATwoADay() {
        val cardio = item("c", ScheduleModality.CARDIO)
        val lift = item("s", ScheduleModality.STRENGTH)
        assertEquals("s", HomeToday.startTagOccurrenceId(listOf(cardio, lift)))
    }

    @Test
    fun startTagFallsBackToTheOnlyPlannedRow() {
        val cardio = item("c", ScheduleModality.CARDIO)
        assertEquals("c", HomeToday.startTagOccurrenceId(listOf(cardio)))
    }

    @Test
    fun doneRowsDoNotOwnTheStartTag() {
        val done = item("s", ScheduleModality.STRENGTH, OccurrenceStatus.DONE)
        val cardio = item("c", ScheduleModality.CARDIO)
        assertEquals("c", HomeToday.startTagOccurrenceId(listOf(done, cardio)))
    }

    private fun item(
        id: String,
        modality: ScheduleModality,
        status: OccurrenceStatus = OccurrenceStatus.PLANNED,
    ) = AgendaItem(
        occurrence = ScheduleOccurrence(
            id = id,
            ruleId = "r-$id",
            status = status,
            captured = CapturedCivilTime(1L, "UTC", 0, 20_000L),
            hour = 7,
            minute = 0,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ),
        rule = ScheduleRule(
            id = "r-$id",
            weekday = Weekday.MONDAY,
            hour = 7,
            minute = 0,
            modality = modality,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ),
    )
}
