package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.domain.Weekday
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
        assertFalse(CustomWeekPolicy.canConfirm(mapOf(Weekday.MONDAY to emptyList())))
        assertTrue(
            CustomWeekPolicy.canConfirm(
                mapOf(Weekday.MONDAY to listOf(lift("1", squat))),
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
        assertEquals("Monday", CustomWeekPolicy.routineName(Weekday.MONDAY))
    }

    @Test
    fun preferredDaysStayMarkedWithZeroLifts() {
        assertEquals(
            CustomWeekDayMark.PREFERRED,
            CustomWeekPolicy.dayMark(
                Weekday.TUESDAY,
                filled = emptySet(),
                preferred = setOf(Weekday.TUESDAY),
            ),
        )
        assertEquals(
            CustomWeekDayMark.FILLED,
            CustomWeekPolicy.dayMark(
                Weekday.TUESDAY,
                filled = setOf(Weekday.TUESDAY),
                preferred = setOf(Weekday.TUESDAY),
            ),
        )
        assertEquals(
            CustomWeekDayMark.EMPTY,
            CustomWeekPolicy.dayMark(Weekday.WEDNESDAY, filled = emptySet(), preferred = setOf(Weekday.TUESDAY)),
        )
    }

    @Test
    fun selectedDayStartsAtWeekStartUnlessAPreferredDayExists() {
        assertEquals(
            Weekday.SUNDAY,
            CustomWeekPolicy.initialSelectedDay(Weekday.SUNDAY, preferred = emptySet()),
        )
        assertEquals(
            Weekday.TUESDAY,
            CustomWeekPolicy.initialSelectedDay(
                Weekday.SUNDAY,
                preferred = setOf(Weekday.TUESDAY, Weekday.THURSDAY),
            ),
        )
    }

    @Test
    fun restDaysAreTheUnfilledWeekdays() {
        assertEquals(7, CustomWeekPolicy.restDayCount(emptyMap()))
        assertEquals(0, CustomWeekPolicy.filledDayCount(emptyMap()))
        assertFalse(CustomWeekPolicy.isFullWeek(emptyMap()))
        assertFalse(CustomWeekPolicy.isFullWeek(daysPerWeek = 6))
        assertTrue(CustomWeekPolicy.isFullWeek(daysPerWeek = 7))

        val six = filledWeek(6)
        assertEquals(6, CustomWeekPolicy.filledDayCount(six))
        assertEquals(1, CustomWeekPolicy.restDayCount(six))
        assertFalse(CustomWeekPolicy.isFullWeek(six))
        assertEquals("Use this week · 6 training", CustomWeekPolicy.confirmCta(6))
        assertEquals("1 rest", CustomWeekPolicy.restCaption(1))

        val seven = filledWeek(7)
        assertEquals(0, CustomWeekPolicy.restDayCount(seven))
        assertTrue(CustomWeekPolicy.isFullWeek(seven))
        assertEquals("Use this week · 7 training", CustomWeekPolicy.confirmCta(7))
        assertEquals(null, CustomWeekPolicy.restCaption(0))
    }

    @Test
    fun updateTargetsWritesLoad() {
        val lifts = listOf(lift("a", squat))
        val updated = CustomWeekPolicy.updateTargets(
            lifts,
            itemId = "a",
            sets = 5,
            reps = 5,
            restSeconds = 180,
            weightKg = 80.0,
        )
        assertEquals(80.0, updated.single().targetWeightKg!!, 0.001)
        assertEquals(5, updated.single().targetSets)
        val cleared = CustomWeekPolicy.updateTargets(
            updated,
            itemId = "a",
            sets = null,
            reps = null,
            restSeconds = null,
            weightKg = null,
        )
        assertEquals(null, cleared.single().targetWeightKg)
        assertEquals(5, cleared.single().targetSets)

        val zeroSets = CustomWeekPolicy.updateTargets(
            updated,
            itemId = "a",
            sets = 0,
            reps = 0,
            restSeconds = null,
            weightKg = 80.0,
        )
        assertEquals(5, zeroSets.single().targetSets)
        assertEquals(5, zeroSets.single().targetReps)
        assertEquals(180, zeroSets.single().restSeconds)
    }

    private fun filledWeek(count: Int): Map<Weekday, List<CustomWeekLift>> =
        Weekday.entries.take(count).associateWith { day -> listOf(lift(day.name, squat)) }

    private fun lift(id: String, exercise: Exercise) = CustomWeekLift(
        id = id,
        exercise = exercise,
        targetSets = 3,
        targetReps = 5,
        restSeconds = 150,
    )
}
