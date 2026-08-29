package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun startTagPrefersAWorkoutOverAnAuxiliaryPack() {
        val cardio = item("c", ScheduleModality.CARDIO, hour = 7)
        val main = item("s", ScheduleModality.STRENGTH, hour = 18)
        val stretch = item(
            "e",
            ScheduleModality.STRENGTH,
            hour = 20,
            templateId = ScheduleKind.aux("stretch"),
        )
        assertEquals("s", HomeToday.startTagOccurrenceId(listOf(cardio, main, stretch)))
    }

    @Test
    fun startTagPrefersMixedOverAnAuxiliaryPack() {
        val mixed = item("m", ScheduleModality.MIXED, hour = 18)
        val stretch = item(
            "e",
            ScheduleModality.STRENGTH,
            hour = 20,
            templateId = ScheduleKind.aux("stretch"),
        )
        assertEquals("m", HomeToday.startTagOccurrenceId(listOf(mixed, stretch)))
    }

    @Test
    fun startTagUsesAuxWhenItIsTheOnlyStrength() {
        val cardio = item("c", ScheduleModality.CARDIO, hour = 7)
        val stretch = item(
            "e",
            ScheduleModality.STRENGTH,
            hour = 20,
            templateId = ScheduleKind.aux("stretch"),
        )
        assertEquals("e", HomeToday.startTagOccurrenceId(listOf(cardio, stretch)))
    }

    @Test
    fun startTagMovesToTheLaterStrengthAfterTheMainIsDone() {
        val cardio = item("c", ScheduleModality.CARDIO, hour = 7)
        val main = item("s", ScheduleModality.STRENGTH, OccurrenceStatus.DONE, hour = 18)
        val extra = item("e", ScheduleModality.STRENGTH, hour = 20)
        assertEquals("e", HomeToday.startTagOccurrenceId(listOf(cardio, main, extra)))
    }

    @Test
    fun startConfirmNamesTheClockKindAndLiftOrder() {
        val planned = item("s", ScheduleModality.STRENGTH, hour = 18, routineId = "r-Push")
        val confirm = HomeToday.startConfirm(
            planned.copy(routineName = "Push"),
            listOf(pushRoutine()),
            ClockFormat.TWELVE,
            TODAY,
        )
        assertEquals("Start Push?", confirm.heading)
        assertEquals(HomeToday.CONFIRM, confirm.confirmLabel)
        assertFalse(confirm.leftover)
        assertTrue(confirm.body.startsWith("6 PM · Workout"))
        assertTrue(confirm.body.contains("1 Squat"))
        assertTrue(confirm.body.contains("2 Row"))
        assertTrue(confirm.body.contains("2 lifts · about 13 min"))
    }

    @Test
    fun startConfirmListsEveryLiftNotAThreeLiftPreview() {
        val planned = item("s", ScheduleModality.STRENGTH, hour = 18, routineId = "r-Push")
        val routine = pushRoutine(
            "Squat",
            "Row",
            "Bench",
            "Fly",
        )
        val confirm = HomeToday.startConfirm(
            planned.copy(routineName = "Push"),
            listOf(routine),
            ClockFormat.TWELVE,
            TODAY,
        )
        assertTrue(confirm.body.contains("1 Squat"))
        assertTrue(confirm.body.contains("4 Fly"))
        assertFalse(confirm.body.contains("4 lifts · 1 Squat"))
        assertTrue(confirm.body.contains("4 lifts · about"))
    }

    @Test
    fun startConfirmCardioIsReady() {
        val confirm = HomeToday.startConfirm(
            item("c", ScheduleModality.CARDIO, hour = 7),
            emptyList(),
            ClockFormat.TWELVE,
            TODAY,
        )
        assertEquals("Start Cardio?", confirm.heading)
        assertTrue(confirm.body.startsWith("7 AM · Cardio"))
        assertTrue(confirm.body.contains(SessionOrderCopy.READY))
    }

    @Test
    fun startConfirmLeadsWithTheAuxiliaryCaption() {
        val stretch = item(
            "e",
            ScheduleModality.STRENGTH,
            hour = 20,
            templateId = ScheduleKind.aux("stretch"),
            routineId = "r-stretch",
        )
        val routine = Routine(
            id = "r-stretch",
            name = "Stretch",
            notes = "",
            createdAt = 0L,
            updatedAt = 0L,
            exercises = listOf("Calf stretch", "Couch stretch").mapIndexed { index, name ->
                lift("r-stretch", index, name)
            },
        )
        val confirm = HomeToday.startConfirm(
            stretch.copy(routineName = "Stretch"),
            listOf(routine),
            ClockFormat.TWELVE,
            TODAY,
        )
        assertEquals("Start Stretch?", confirm.heading)
        assertTrue(confirm.body.contains(AuxiliaryPacks.Stretch.caption))
        assertTrue(confirm.body.contains("1 Calf stretch"))
        assertTrue(confirm.body.contains("2 Couch stretch"))
        assertFalse(confirm.body.contains("lifts · about"))
    }

    @Test
    fun fallbackStartConfirmNamesTheSessionAndLiftOrder() {
        val confirm = HomeToday.fallbackStartConfirm(
            leftoverDay(isRest = false, name = "Push"),
            listOf(pushRoutine()),
        )
        assertEquals("Start Push?", confirm.heading)
        assertEquals(HomeToday.CONFIRM, confirm.confirmLabel)
        assertFalse(confirm.leftover)
        assertTrue(confirm.body.startsWith("1 Squat"))
        assertTrue(confirm.body.contains("2 Row"))
        assertTrue(confirm.body.contains("2 lifts · about 13 min"))
    }

    @Test
    fun fallbackStartConfirmWithoutARoutineSaysNoLiftsYet() {
        val confirm = HomeToday.fallbackStartConfirm(
            leftoverDay(isRest = false, name = null),
            emptyList(),
        )
        assertEquals("Start Push?", confirm.heading)
        assertEquals(SessionOrderCopy.EMPTY_PREVIEW, confirm.body)
    }

    @Test
    fun startConfirmLeftoverIsDoItToday() {
        val planned = item("s", ScheduleModality.STRENGTH, hour = 18, routineId = "r-Push")
            .copy(routineName = "Push")
        val confirm = HomeToday.startConfirm(
            planned,
            listOf(pushRoutine()),
            ClockFormat.TWELVE,
            TODAY + 1,
        )
        assertEquals("Do Push today?", confirm.heading)
        assertEquals(MoveToToday.DO_IT_TODAY, confirm.confirmLabel)
        assertTrue(confirm.leftover)
        assertTrue(confirm.body.contains(MoveToToday.leftoverNote(Weekday.FRIDAY)))
    }

    @Test
    fun startTagFallsToStillOpenWhenTodayIsEmpty() {
        val leftover = item("s", ScheduleModality.STRENGTH, hour = 18)
        assertEquals("s", HomeToday.startTagOccurrenceId(emptyList(), listOf(leftover)))
    }

    @Test
    fun todayWorkoutBeatsStillOpen() {
        val todayItem = item("t", ScheduleModality.STRENGTH, hour = 18)
        val leftover = item("s", ScheduleModality.STRENGTH, hour = 18)
        assertEquals("t", HomeToday.startTagOccurrenceId(listOf(todayItem), listOf(leftover)))
    }

    @Test
    fun stillOpenKeepsAgendaSurfaceWhenTodayIsEmpty() {
        assertEquals(
            HomeToday.Surface.AGENDA,
            HomeToday.surface(
                emptyList(),
                leftoverBelongs = true,
                stillOpen = listOf(item("s", ScheduleModality.STRENGTH)),
            ),
        )
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
        templateId: String? = null,
        routineId: String? = null,
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
            routineId = routineId,
            templateId = templateId,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ),
    )

    private fun pushRoutine(vararg names: String): Routine {
        val lifts = names.toList().ifEmpty { listOf("Squat", "Row") }
        return Routine(
            id = "r-Push",
            name = "Push",
            notes = "",
            createdAt = 0L,
            updatedAt = 0L,
            exercises = lifts.mapIndexed { index, name -> lift("r-Push", index, name) },
        )
    }

    private fun lift(routineId: String, index: Int, name: String) = RoutineExercise(
        id = "$routineId-$index",
        routineId = routineId,
        exercise = Exercise(
            id = "ex-$routineId-$index",
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

    private companion object {
        const val TODAY = 20_000L
    }
}
