package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeTodayTest {
    @Test
    fun emptyAgendaUsesTheSlotWeekLeftoverOnlyWhenItBelongs() {
        assertEquals(HomeToday.Surface.AGENDA, HomeToday.surface(emptyList()))
        assertEquals(
            HomeToday.Surface.WEEK_FALLBACK,
            HomeToday.surface(emptyList(), leftoverBelongs = true),
        )
        assertNull(HomeToday.startTagOccurrenceId(emptyList()))
    }

    @Test
    fun anyOccurrenceMakesAgendaTheOnlyToday() {
        val cardio = item("c", ScheduleModality.CARDIO)
        assertEquals(HomeToday.Surface.AGENDA, HomeToday.surface(listOf(cardio)))
    }

    @Test
    fun startTagPrefersPlannedStrengthOnATwoADay() {
        val cardio = item("c", ScheduleModality.CARDIO, hour = 7)
        val lift = item("s", ScheduleModality.STRENGTH, hour = 18)
        assertEquals("s", HomeToday.startTagOccurrenceId(listOf(cardio, lift)))
    }

    @Test
    fun startTagPrefersTheFirstPlannedStrengthOnADayStack() {
        val cardio = item("c", ScheduleModality.CARDIO, hour = 7)
        val main = item("s", ScheduleModality.STRENGTH, hour = 18)
        val extra = item("e", ScheduleModality.STRENGTH, hour = 20)
        assertEquals("s", HomeToday.startTagOccurrenceId(listOf(cardio, main, extra)))
    }

    @Test
    fun startTagMovesToTheLaterStrengthAfterTheMainIsDone() {
        val cardio = item("c", ScheduleModality.CARDIO, hour = 7)
        val main = item("s", ScheduleModality.STRENGTH, OccurrenceStatus.DONE, hour = 18)
        val extra = item("e", ScheduleModality.STRENGTH, hour = 20)
        assertEquals("e", HomeToday.startTagOccurrenceId(listOf(cardio, main, extra)))
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

    @Test
    fun sheetStartPrefersTheTaggedOccurrenceOverTheLeftoverSlot() {
        val leftover = leftoverDay(isRest = false, name = "Push")
        val cardio = item("c", ScheduleModality.CARDIO)
        val start = HomeToday.sheetStart(listOf(cardio), leftover, emptyList())
        assertEquals("c", start?.occurrenceId)
        assertEquals("Cardio", start?.title)
        assertNull(start?.leftover)
    }

    @Test
    fun sheetStartUsesTheLeftoverSlotWhenTheAgendaIsEmpty() {
        val leftover = leftoverDay(isRest = false, name = "Push")
        val start = HomeToday.sheetStart(emptyList(), leftover, emptyList())
        assertEquals("Push", start?.title)
        assertNull(start?.occurrenceId)
        assertEquals(leftover, start?.leftover)
    }

    @Test
    fun sheetStartIsNullOnRestWhenNothingIsPlanned() {
        val rest = leftoverDay(isRest = true, name = null)
        assertNull(HomeToday.sheetStart(emptyList(), rest, emptyList()))
        assertNull(HomeToday.sheetStart(emptyList(), null, emptyList()))
    }

    @Test
    fun sheetStartPreviewIsTheNumberedSessionOrder() {
        val leftover = leftoverDay(isRest = false, name = "Push")
        val routines = listOf(
            Routine(
                id = "r-Push",
                name = "Push",
                notes = "",
                createdAt = 0L,
                updatedAt = 0L,
                exercises = listOf("Squat", "Row").mapIndexed { index, name ->
                    RoutineExercise(
                        id = "item-$index",
                        routineId = "r-Push",
                        exercise = Exercise(
                            id = "ex-$index",
                            name = name,
                            muscleGroup = "Quads",
                            notes = "",
                            isCustom = false,
                        ),
                        sortOrder = index,
                        targetSets = 3,
                        targetReps = 5,
                        targetWeightKg = null,
                        restSeconds = 90,
                    )
                },
            ),
        )
        val start = HomeToday.sheetStart(emptyList(), leftover, routines)
        assertEquals("1 Squat · 2 Row", start?.preview)
    }

    private fun leftoverDay(isRest: Boolean, name: String?) = SuggestedTrainingDay(
        epochDay = 20_000L,
        dayOfWeek = Weekday.MONDAY,
        isRest = isRest,
        focusKind = SessionFocusKind.PUSH,
        focusTitle = "Push",
        routineId = name?.let { "r-$it" },
        routineName = name,
        reason = "Pinned to your week.",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
    )

    private fun item(
        id: String,
        modality: ScheduleModality,
        status: OccurrenceStatus = OccurrenceStatus.PLANNED,
        hour: Int = 7,
    ) = AgendaItem(
        occurrence = ScheduleOccurrence(
            id = id,
            ruleId = "r-$id",
            status = status,
            captured = CapturedCivilTime(1L, "UTC", 0, 20_000L),
            hour = hour,
            minute = 0,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ),
        rule = ScheduleRule(
            id = "r-$id",
            weekday = Weekday.MONDAY,
            hour = hour,
            minute = 0,
            modality = modality,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ),
    )
}
