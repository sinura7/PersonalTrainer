package com.sinura.personaltrainer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a lift is in a session: planned, or logged (N2). The workout screen waits for a session
 * row that holds the lift it is loading, and a lift can be selected from its logged sets alone
 * ([WorkoutSession.resolveSelectedExerciseId]), so a lift with sets and no plan must count, or that
 * wait would never end.
 */
class WorkoutSessionHasLiftTest {
    /** H1. */
    @Test
    fun aLiftIsInTheSessionWhenItIsPlannedOrHasSets() {
        val session = session(
            exercises = listOf(planned("squat")),
            sets = listOf(setLog(exerciseId = "curl", completedAt = 1L)),
        )
        assertTrue("a planned lift is in the session", session.hasLift("squat"))
        assertTrue("a lift with sets and no plan is in the session", session.hasLift("curl"))
        assertFalse("a lift neither planned nor logged is not", session.hasLift("press"))
    }

    /** H2. */
    @Test
    fun theLiftASessionWithSetsButNoPlanSelectsIsInIt() {
        val session = session(
            exercises = emptyList(),
            sets = listOf(setLog(exerciseId = "curl", completedAt = 1L), setLog(exerciseId = "row", completedAt = 2L)),
        )
        val selected = checkNotNull(session.resolveSelectedExerciseId(null)) { "a session with sets selects a lift" }
        assertTrue("the lift the session selects from its sets alone ($selected) is in it", session.hasLift(selected))
    }

    private fun planned(id: String) = SessionExercise(
        id = "se-$id",
        sessionId = "s1",
        exercise = Exercise(id = id, name = id, muscleGroup = "Quads", notes = "", isCustom = false),
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
        weightKg = 20.0,
        reps = 10,
        rpe = null,
        isWarmup = false,
        completedAt = completedAt,
    )

    private fun session(exercises: List<SessionExercise>, sets: List<SetLog>) = WorkoutSession(
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
