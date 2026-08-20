package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSessionSelectionTest {
    @Test
    fun emptySessionHasNoLifts() {
        val session = session(exercises = emptyList(), sets = emptyList())
        assertFalse(session.hasLifts())
        assertNull(session.resolveSelectedExerciseId("stale"))
    }

    @Test
    fun neverTreatsStaleSelectionAsEmpty() {
        val squat = exercise("squat", "Barbell Back Squat")
        val bench = exercise("bench", "Bench")
        val session = session(
            exercises = listOf(sessionExercise(squat), sessionExercise(bench)),
            sets = listOf(setLog(squat.id, completedAt = 2L), setLog(bench.id, completedAt = 1L)),
        )
        assertTrue(session.hasLifts())
        assertEquals(squat.id, session.resolveSelectedExerciseId(squat.id))
        assertEquals(squat.id, session.resolveSelectedExerciseId("missing-id"))
        assertEquals(squat.id, session.resolveSelectedExerciseId(null))
    }

    @Test
    fun prefersFirstExerciseWithSetsWhenPreferredMissing() {
        val squat = exercise("squat", "Squat")
        val bench = exercise("bench", "Bench")
        val session = session(
            exercises = listOf(sessionExercise(squat), sessionExercise(bench)),
            sets = listOf(setLog(bench.id, completedAt = 10L)),
        )
        assertEquals(bench.id, session.resolveSelectedExerciseId("gone"))
        assertEquals(bench.id, session.resolveSelectedExerciseId(null))
    }

    @Test
    fun fallsBackToLoggedSetsWhenExerciseRowsMissing() {
        val session = session(
            exercises = emptyList(),
            sets = listOf(setLog("squat", completedAt = 5L)),
        )
        assertTrue(session.hasLifts())
        assertEquals("squat", session.resolveSelectedExerciseId(null))
    }

    private fun exercise(id: String, name: String) = Exercise(
        id = id,
        name = name,
        muscleGroup = "Quads",
        notes = "",
        isCustom = false,
    )

    private fun sessionExercise(exercise: Exercise) = SessionExercise(
        id = "se-${exercise.id}",
        sessionId = "s1",
        exercise = exercise,
        sortOrder = 0,
        targetSets = 3,
        targetReps = 5,
        targetWeightKg = 100.0,
        restSeconds = 90,
    )

    private fun setLog(exerciseId: String, completedAt: Long) = SetLog(
        id = "set-$exerciseId-$completedAt",
        sessionId = "s1",
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        setNumber = 1,
        weightKg = 0.0,
        reps = 5,
        rpe = null,
        isWarmup = true,
        completedAt = completedAt,
    )

    private fun session(
        exercises: List<SessionExercise>,
        sets: List<SetLog>,
    ) = WorkoutSession(
        id = "s1",
        routineId = null,
        routineName = "Free workout",
        date = 1L,
        notes = "",
        durationMinutes = 0,
        startedAt = 1L,
        finishedAt = null,
        exercises = exercises,
        sets = sets,
    )
}
