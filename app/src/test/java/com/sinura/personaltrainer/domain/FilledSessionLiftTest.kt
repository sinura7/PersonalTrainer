package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(3, lifts[0].targetSets)
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
        assertEquals("2/3", SessionOrderCopy.filledCount(lift.workingLogged, lift.targetSets))
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
            "1. Row. 1 set",
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
