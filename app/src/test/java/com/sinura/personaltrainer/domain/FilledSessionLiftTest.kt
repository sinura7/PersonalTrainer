package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FilledSessionLiftTest {
    @Test
    fun prescribedOrderKeepsUntouchedLifts() {
        val squat = sessionExercise("squat", "Squat", "Quads", sort = 0)
        val bench = sessionExercise("bench", "Bench", "Chest", sort = 1)
        val session = session(
            exercises = listOf(squat, bench),
            sets = listOf(set("squat", 0)),
        )
        val lifts = session.filledLifts()
        assertEquals(listOf("squat", "bench"), lifts.map { it.exercise.id })
        assertEquals(listOf(1, 2), lifts.map { it.number })
        assertEquals(1, lifts[0].workingLogged)
        assertTrue(lifts[1].sets.isEmpty())
        assertTrue(lifts[0].hasPrescription)
        assertEquals(3, lifts[0].prescriptions.single().row.targetSets)
    }

    @Test
    fun warmupsDoNotCountTowardTheFilledNumeral() {
        val squat = sessionExercise("squat", "Squat", "Quads")
        val session = session(
            exercises = listOf(squat),
            sets = listOf(set("squat", 0, warmup = true), set("squat", 1), set("squat", 2)),
        )
        val lift = session.filledLifts().single()
        assertEquals(2, lift.workingLogged)
        assertEquals("2/3", SessionOrderCopy.filledCount(lift.workingLogged, lift.prescriptions.single().row.targetSets))
    }

    @Test
    fun setOnlyHistorySynthesisesCardsInFirstSeenOrder() {
        val session = session(
            exercises = emptyList(),
            sets = listOf(set("row", 0, name = "Row"), set("squat", 0, name = "Squat")),
        )
        val lifts = session.filledLifts()
        assertEquals(listOf("row", "squat"), lifts.map { it.exercise.id })
        assertEquals("Row", lifts[0].exercise.name)
        assertFalse(lifts[0].hasPrescription)
        assertEquals(
            "1. Row. Recorded: 1 working set",
            SessionOrderCopy.filledSpoken(
                number = 1,
                name = "Row",
                muscleGroup = "",
                workingLogged = 1,
                targetSets = 0,
                targetReps = 0,
                restClock = null,
                load = null,
            ),
        )
    }

    @Test
    fun partialPrescriptionStillExposesEveryOriginalSavedSet() {
        val planned = sessionExercise("bench", "Bench", "Chest")
        val recorded = set("squat", 0, name = "Squat")
        val session = session(exercises = listOf(planned), sets = listOf(recorded))
        val beforePlans = session.exercises.toList()
        val beforeSets = session.sets.toList()

        val lifts = session.filledLifts()

        assertEquals(listOf("bench", "squat"), lifts.map { it.exercise.id })
        assertEquals(listOf(recorded.id), lifts.flatMap { it.sets }.map { it.id })
        assertSame(recorded, lifts.last().sets.single())
        assertFalse(lifts.last().hasPrescription)
        assertEquals(beforePlans, session.exercises)
        assertEquals(beforeSets, session.sets)
    }

    @Test
    fun repeatedPrescriptionDoesNotRepeatTheOriginalSavedSet() {
        val first = sessionExercise("squat", "Squat", "Quads")
        val second = first.copy(
            id = "se-squat-second",
            sortOrder = 1,
            targetSets = 2,
            targetReps = 8,
            targetWeightKg = 80.0,
            restSeconds = 120,
        )
        val recorded = set("squat", 0)
        val session = session(exercises = listOf(first, second), sets = listOf(recorded))
        val beforePlans = session.exercises.toList()
        val beforeSets = session.sets.toList()

        val lifts = session.filledLifts()

        assertEquals(listOf(recorded.id), lifts.flatMap { it.sets }.map { it.id })
        assertEquals(lifts.size, lifts.map { it.exercise.id }.distinct().size)
        assertSame(recorded, lifts.single().sets.single())
        assertEquals(beforePlans, session.exercises)
        assertEquals(beforeSets, session.sets)
    }

    @Test
    fun interleavedPrescriptionsKeepEveryOriginalPositionAndTarget() {
        val first = sessionExercise("squat", "Squat", "Quads")
        val between = sessionExercise("bench", "Bench", "Chest", sort = 0)
        val repeated = first.copy(
            id = "se-squat-second", sortOrder = 1, targetSets = 2, targetReps = 8,
            targetWeightKg = 80.0, restSeconds = 120, targetSeconds = 20, targetSecondsMax = 30,
        )
        val last = sessionExercise("curl", "Curl", "Biceps", sort = 2).copy(
            targetSets = 0, targetReps = 0, targetWeightKg = 0.0, restSeconds = 0,
        )
        val originalRows = listOf(first, between, repeated, last)
        val session = session(originalRows, emptyList())

        val lifts = session.filledLifts()

        assertEquals(listOf("squat", "bench", "curl"), lifts.map { it.exercise.id })
        assertEquals(listOf(1, 2, 4), lifts.map { it.number })
        assertEquals(listOf(1, 3), lifts[0].prescriptions.map { it.originalPosition })
        val reconstructed = lifts.flatMap { it.prescriptions }.sortedBy { it.originalPosition }
        assertEquals(listOf(1, 2, 3, 4), reconstructed.map { it.originalPosition })
        reconstructed.forEachIndexed { index, entry -> assertSame(originalRows[index], entry.row) }
        assertEquals(listOf(0, 0, 1, 2), reconstructed.map { it.row.sortOrder })
        assertEquals(listOf(3, 3, 2, 0), reconstructed.map { it.row.targetSets })
        assertEquals(listOf(5, 5, 8, 0), reconstructed.map { it.row.targetReps })
        assertEquals(listOf(100.0, 100.0, 80.0, 0.0), reconstructed.map { it.row.targetWeightKg })
        assertEquals(listOf(90, 90, 120, 0), reconstructed.map { it.row.restSeconds })
        assertEquals(listOf(null, null, 20, null), reconstructed.map { it.row.targetSeconds })
        assertEquals(listOf(null, null, 30, null), reconstructed.map { it.row.targetSecondsMax })
        assertFalse(lifts.last().hasPrescription)
        assertSame(last, lifts.last().prescriptions.single().row)
        assertEquals(originalRows, session.exercises)
        assertTrue(session.sets.isEmpty())
    }

    @Test
    fun equalValuedPrescriptionsWithDistinctRowIdsBothSurvive() {
        val first = sessionExercise("squat", "Squat", "Quads")
        val second = first.copy(id = "se-squat-equivalent")
        val lift = session(listOf(first, second), emptyList()).filledLifts().single()

        assertEquals(listOf(first.id, second.id), lift.prescriptions.map { it.row.id })
        assertEquals(listOf(1, 2), lift.prescriptions.map { it.originalPosition })
        assertSame(first, lift.prescriptions[0].row)
        assertSame(second, lift.prescriptions[1].row)
        assertTrue(lift.sets.isEmpty())
    }

    @Test
    fun orphanIdentityUsesOnlyItsExactSavedIdAndFirstNonblankName() {
        val basePlan = sessionExercise("planned", "Same name", "Chest")
        val planned = basePlan.copy(exercise = basePlan.exercise.copy(imageKey = "catalog-key"))
        val blankFirst = set("orphan-a", 0, name = " ")
        val namedLater = set("orphan-a", 1, name = "Same name")
        val sameNameOtherId = set("orphan-b", 0, name = "Same name")
        val blankOnly = set("orphan-c", 0, name = "")
        val originalSets = listOf(blankFirst, sameNameOtherId, namedLater, blankOnly)
        val session = session(listOf(planned), originalSets)

        val lifts = session.filledLifts()

        assertEquals(listOf("planned", "orphan-a", "orphan-b", "orphan-c"), lifts.map { it.exercise.id })
        assertEquals(listOf(1, 2, 3, 4), lifts.map { it.number })
        assertEquals(listOf("Same name", "Same name", "Same name", "Exercise"), lifts.map { it.exercise.name })
        assertSame(planned.exercise, lifts.first().exercise)
        lifts.drop(1).forEach { lift ->
            assertTrue(lift.prescriptions.isEmpty())
            assertFalse(lift.hasPrescription)
            assertTrue(lift.exercise.isCustom)
            assertNull(lift.exercise.imageKey)
            assertEquals("", lift.exercise.muscleGroup)
            assertEquals(EquipmentType.OTHER, lift.exercise.equipment)
            assertEquals(LoadClass.LOADED, session.loadClassOf(lift.exercise.id))
        }
        assertEquals(originalSets.groupingBy { it.id }.eachCount(), lifts.flatMap { it.sets }.groupingBy { it.id }.eachCount())
        assertSame(blankFirst, lifts[1].sets[0])
        assertSame(namedLater, lifts[1].sets[1])
        assertSame(sameNameOtherId, lifts[2].sets.single())
        assertSame(blankOnly, lifts[3].sets.single())
        assertEquals(listOf(planned), session.exercises)
        assertEquals(originalSets, session.sets)
    }

    @Test
    fun repeatedPlanKeepsOriginalSavedRowsWarmupsAndStableSetNumberTies() {
        val plan = sessionExercise("squat", "Squat", "Quads")
        val first = set("squat", 1).copy(id = "original-first", rpe = 8, completedAt = 99)
        val tied = first.copy(id = "original-tied", completedAt = 1, durationSeconds = 23)
        val warmup = set("squat", 5, warmup = true)
        val earlierNumber = set("squat", 0)
        val originalSets = listOf(first, warmup, tied, earlierNumber)
        val originalPlans = listOf(plan, plan.copy(id = "se-squat-second"))
        val session = session(originalPlans, originalSets)

        val lift = session.filledLifts().single()

        val expected = listOf(earlierNumber, first, tied, warmup)
        assertEquals(expected, lift.sets)
        expected.forEachIndexed { index, original -> assertSame(original, lift.sets[index]) }
        assertEquals(3, lift.workingLogged)
        assertEquals(originalSets.groupingBy { it.id }.eachCount(), lift.sets.groupingBy { it.id }.eachCount())
        assertEquals(session.workingSetCount(), lift.workingLogged)
        assertEquals(originalPlans, session.exercises)
        assertEquals(originalSets, session.sets)
    }

    @Test
    fun emptySessionDoesNotInventAnExerciseOrPrescription() {
        assertTrue(session(emptyList(), emptyList()).filledLifts().isEmpty())
    }

    private fun sessionExercise(
        id: String,
        name: String,
        muscle: String,
        sort: Int = 0,
    ) = SessionExercise(
        id = "se-$id",
        sessionId = "s1",
        exercise = Exercise(
            id = id,
            name = name,
            muscleGroup = muscle,
            notes = "",
            isCustom = false,
        ),
        sortOrder = sort,
        targetSets = 3,
        targetReps = 5,
        targetWeightKg = 100.0,
        restSeconds = 90,
    )

    private fun set(
        exerciseId: String,
        index: Int,
        warmup: Boolean = false,
        name: String = exerciseId,
    ) = SetLog(
        id = "set-$exerciseId-$index",
        sessionId = "s1",
        exerciseId = exerciseId,
        exerciseName = name,
        setNumber = index + 1,
        weightKg = 100.0,
        reps = 5,
        rpe = null,
        isWarmup = warmup,
        completedAt = index.toLong(),
    )

    private fun session(
        exercises: List<SessionExercise>,
        sets: List<SetLog>,
    ) = WorkoutSession(
        id = "s1",
        routineId = null,
        routineName = "Upper",
        date = 1L,
        notes = "",
        durationMinutes = 40,
        startedAt = 1L,
        finishedAt = 2L,
        exercises = exercises,
        sets = sets,
    )
}
