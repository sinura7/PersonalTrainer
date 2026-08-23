package com.sinura.personaltrainer.domain

import java.time.DayOfWeek
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomWeekPolicyTest {
    private val squat = Exercise(
        id = "squat",
        name = "Back squat",
        muscleGroup = "Quads",
        notes = "",
        isCustom = false,
        equipment = EquipmentType.BARBELL,
        loadType = LoadType.EXTERNAL,
        movementKey = "squat",
        muscles = listOf(MuscleCredit("Quads", 1.0)),
    )
    private val bench = squat.copy(id = "bench", name = "Bench press", muscleGroup = "Chest", movementKey = "bench-press")

    @Test
    fun confirmNeedsAtLeastOneLift() {
        assertFalse(CustomWeekPolicy.canConfirm(emptyMap()))
        assertFalse(CustomWeekPolicy.canConfirm(mapOf(DayOfWeek.MONDAY to emptyList())))
        assertTrue(
            CustomWeekPolicy.canConfirm(
                mapOf(DayOfWeek.MONDAY to listOf(lift("1", squat))),
            ),
        )
    }

    @Test
    fun addingSkipsDuplicatesAndKeepsOrder() {
        val ids = AtomicInteger(0)
        val first = CustomWeekPolicy.addLifts(emptyList(), listOf(squat, bench)) { ids.incrementAndGet().toString() }
        val second = CustomWeekPolicy.addLifts(first, listOf(squat, bench)) { ids.incrementAndGet().toString() }
        assertEquals(listOf("squat", "bench"), first.map { it.exercise.id })
        assertEquals(first, second)
    }

    @Test
    fun moveSwapsNeighborsAndIgnoresTheEnds() {
        val lifts = listOf(lift("a", squat), lift("b", bench))
        assertEquals(listOf("b", "a"), CustomWeekPolicy.move(lifts, "a", 1).map { it.id })
        assertEquals(lifts, CustomWeekPolicy.move(lifts, "a", -1))
    }

    @Test
    fun mondayIsTheRoutineName() {
        assertEquals("Monday", CustomWeekPolicy.routineName(DayOfWeek.MONDAY))
    }

    @Test
    fun preferredDaysStayMarkedWithZeroLifts() {
        assertEquals(
            CustomWeekDayMark.PREFERRED,
            CustomWeekPolicy.dayMark(
                DayOfWeek.TUESDAY,
                filled = emptySet(),
                preferred = setOf(DayOfWeek.TUESDAY),
            ),
        )
        assertEquals(
            CustomWeekDayMark.FILLED,
            CustomWeekPolicy.dayMark(
                DayOfWeek.TUESDAY,
                filled = setOf(DayOfWeek.TUESDAY),
                preferred = setOf(DayOfWeek.TUESDAY),
            ),
        )
        assertEquals(
            CustomWeekDayMark.EMPTY,
            CustomWeekPolicy.dayMark(DayOfWeek.WEDNESDAY, filled = emptySet(), preferred = setOf(DayOfWeek.TUESDAY)),
        )
    }

    @Test
    fun selectedDayStartsAtWeekStartUnlessAPreferredDayExists() {
        assertEquals(
            DayOfWeek.SUNDAY,
            CustomWeekPolicy.initialSelectedDay(DayOfWeek.SUNDAY, preferred = emptySet()),
        )
        assertEquals(
            DayOfWeek.TUESDAY,
            CustomWeekPolicy.initialSelectedDay(
                DayOfWeek.SUNDAY,
                preferred = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            ),
        )
    }

    private fun lift(id: String, exercise: Exercise) = CustomWeekLift(
        id = id,
        exercise = exercise,
        targetSets = 3,
        targetReps = 5,
        restSeconds = 150,
    )
}
