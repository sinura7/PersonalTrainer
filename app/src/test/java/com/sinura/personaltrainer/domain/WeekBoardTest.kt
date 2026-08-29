package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekBoardTest {
    @Test
    fun emptyDayIsRest() {
        assertEquals(DayFill.EMPTY, DayFill.of(emptyList()))
        assertEquals(WeekBoard.REST, WeekBoard.caption(emptyList()))
    }

    @Test
    fun noneSomeAllCountResolvedBlocks() {
        val planned = item("a", OccurrenceStatus.PLANNED)
        val done = item("b", OccurrenceStatus.DONE)
        val skipped = item("c", OccurrenceStatus.SKIPPED)
        assertEquals(DayFill.NONE, DayFill.of(listOf(planned, planned.copy(occurrence = planned.occurrence.copy(id = "d")))))
        assertEquals(DayFill.PARTIAL, DayFill.of(listOf(planned, done)))
        assertEquals(DayFill.ALL, DayFill.of(listOf(done, skipped)))
    }

    @Test
    fun singleBlockCaptionIsTheKindNeverTheRoutineName() {
        val fridayNamed = item(
            id = "s",
            status = OccurrenceStatus.PLANNED,
            modality = ScheduleModality.STRENGTH,
            routineName = "Friday",
        )
        assertEquals("Workout", WeekBoard.caption(listOf(fridayNamed)))
        val walk = item(
            id = "c",
            status = OccurrenceStatus.PLANNED,
            modality = ScheduleModality.CARDIO,
            templateId = ScheduleKind.cardio(CardioType.WALK),
        )
        assertEquals("Walk", WeekBoard.caption(listOf(walk)))
        val stretch = item(
            id = "a",
            status = OccurrenceStatus.PLANNED,
            modality = ScheduleModality.STRENGTH,
            templateId = ScheduleKind.aux("stretch"),
        )
        assertEquals("Stretch", WeekBoard.caption(listOf(stretch)))
    }

    @Test
    fun twoBlocksCaptionIsTheCount() {
        val walk = item("c", OccurrenceStatus.PLANNED, ScheduleModality.CARDIO)
        val lift = item("s", OccurrenceStatus.PLANNED, ScheduleModality.STRENGTH)
        assertEquals("2", WeekBoard.caption(listOf(walk, lift)))
    }

    @Test
    fun forWeekNeverLabelsSaturdayFriday() {
        val friday = 20_000L
        assertEquals(Weekday.FRIDAY, Weekday.fromEpochDay(friday))
        val saturday = friday + 1
        val weekStart = friday - 4
        val rule = ScheduleRule(
            id = "rule-fri",
            weekday = Weekday.FRIDAY,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
            routineId = "r-friday",
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        val occ = ScheduleOccurrence(
            id = "occ-fri",
            ruleId = rule.id,
            status = OccurrenceStatus.PLANNED,
            captured = CapturedCivilTime(1L, "UTC", 0, friday),
            hour = 18,
            minute = 0,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        val cells = WeekBoard.forWeek(
            weekStart,
            listOf(occ),
            listOf(rule),
            mapOf("r-friday" to "Friday"),
        )
        val sat = cells.single { it.epochDay == saturday }
        val fri = cells.single { it.epochDay == friday }
        assertEquals(WeekBoard.REST, sat.caption)
        assertEquals(DayFill.EMPTY, sat.fill)
        assertEquals("Workout", fri.caption)
        assertFalse(sat.caption.equals("Friday", ignoreCase = true))
    }

    @Test
    fun leftoverFridayDoesNotBelongOnSaturday() {
        val friday = 20_000L
        val saturday = friday + 1
        val leftover = SuggestedTrainingDay(
            epochDay = saturday,
            dayOfWeek = Weekday.SATURDAY,
            isRest = false,
            focusKind = SessionFocusKind.LEGS,
            focusTitle = "Legs",
            routineId = "r-friday",
            routineName = "Friday",
            reason = "Pinned to your week.",
            emphasisMuscles = emptyList(),
            confidence = ScheduleConfidence.HIGH,
        )
        val rule = ScheduleRule(
            id = "rule-fri",
            weekday = Weekday.FRIDAY,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
            routineId = "r-friday",
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        val occ = ScheduleOccurrence(
            id = "occ-fri",
            ruleId = rule.id,
            status = OccurrenceStatus.PLANNED,
            captured = CapturedCivilTime(1L, "UTC", 0, friday),
            hour = 18,
            minute = 0,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        assertFalse(WeekBoard.leftoverBelongsOn(saturday, leftover, listOf(occ), listOf(rule)))
        assertTrue(
            WeekBoard.leftoverBelongsOn(
                friday,
                leftover.copy(epochDay = friday, dayOfWeek = Weekday.FRIDAY),
                listOf(occ),
                listOf(rule),
            ),
        )
    }

    @Test
    fun summaryCountsBlocksNotLeftoverPins() {
        val weekStart = 20_000L
        val rule = ScheduleRule(
            id = "r",
            weekday = Weekday.MONDAY,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        val planned = ScheduleOccurrence(
            id = "p",
            ruleId = "r",
            status = OccurrenceStatus.PLANNED,
            captured = CapturedCivilTime(1L, "UTC", 0, weekStart),
            hour = 18,
            minute = 0,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        val done = planned.copy(
            id = "d",
            captured = planned.captured.copy(localEpochDay = weekStart + 1),
            status = OccurrenceStatus.DONE,
        )
        val cells = WeekBoard.forWeek(weekStart, listOf(planned, done), listOf(rule))
        assertEquals("2 planned · 1 done this week", WeekBoard.summary(cells))
    }

    private fun item(
        id: String,
        status: OccurrenceStatus,
        modality: ScheduleModality = ScheduleModality.STRENGTH,
        routineName: String? = null,
        templateId: String? = null,
    ) = AgendaItem(
        occurrence = ScheduleOccurrence(
            id = id,
            ruleId = "r-$id",
            status = status,
            captured = CapturedCivilTime(1L, "UTC", 0, 20_000L),
            hour = 18,
            minute = 0,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ),
        rule = ScheduleRule(
            id = "r-$id",
            weekday = Weekday.MONDAY,
            hour = 18,
            minute = 0,
            modality = modality,
            templateId = templateId,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ),
        routineName = routineName,
    )
}
