package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutAdvanceTest {
    @Test
    fun prescribedSetsMustBeInBeforeComplete() {
        assertFalse(WorkoutAdvance.liftComplete(workingLogged = 2, targetSets = 3, wantAnother = false))
        assertTrue(WorkoutAdvance.liftComplete(workingLogged = 3, targetSets = 3, wantAnother = false))
        assertTrue(WorkoutAdvance.liftComplete(workingLogged = 5, targetSets = 3, wantAnother = false))
    }

    @Test
    fun extraSetAskKeepsTheLiftOpen() {
        assertFalse(WorkoutAdvance.liftComplete(workingLogged = 3, targetSets = 3, wantAnother = true))
        assertFalse(WorkoutAdvance.liftComplete(workingLogged = 4, targetSets = 3, wantAnother = true))
    }

    @Test
    fun freeLiftNeverAutoCompletes() {
        assertFalse(WorkoutAdvance.liftComplete(workingLogged = 0, targetSets = 0, wantAnother = false))
        assertFalse(WorkoutAdvance.liftComplete(workingLogged = 4, targetSets = 0, wantAnother = false))
        assertFalse(WorkoutAdvance.liftComplete(workingLogged = 1, targetSets = -1, wantAnother = false))
    }

    @Test
    fun nextIsTheFollowingIdInOrder() {
        val ids = listOf("squat", "bench", "row")
        assertEquals("bench", WorkoutAdvance.nextExerciseId(ids, "squat"))
        assertEquals("row", WorkoutAdvance.nextExerciseId(ids, "bench"))
        assertNull(WorkoutAdvance.nextExerciseId(ids, "row"))
    }

    @Test
    fun unknownOrBlankCurrentHasNoNext() {
        val ids = listOf("squat", "row")
        assertNull(WorkoutAdvance.nextExerciseId(ids, null))
        assertNull(WorkoutAdvance.nextExerciseId(ids, "deadlift"))
        assertNull(WorkoutAdvance.nextExerciseId(emptyList(), "squat"))
        assertNull(WorkoutAdvance.nextExerciseId(listOf("squat"), "squat"))
    }

    @Test
    fun nextUnfinishedWrapsToSkippedLiftsAndFinishesWhenEveryOtherLiftIsDone() {
        val squat = sessionExercise("squat", targetSets = 2)
        val bench = sessionExercise("bench", targetSets = 1)
        val skipped = WorkoutSession(
            id = "s1",
            routineId = null,
            routineName = null,
            date = 1L,
            notes = "",
            durationMinutes = 0,
            startedAt = 1L,
            finishedAt = null,
            exercises = listOf(squat, bench),
            sets = listOf(set("bench")),
        )
        assertEquals("squat", WorkoutAdvance.nextUnfinishedExerciseId(skipped, "bench"))
        assertEquals("squat", skipped.nextUnfinishedExerciseAfter("bench"))

        val done = skipped.copy(
            sets = listOf(set("squat"), set("squat", id = "s2"), set("bench")),
        )
        assertNull(WorkoutAdvance.nextUnfinishedExerciseId(done, "bench"))
        assertEquals("3 × 5", WorkoutAdvance.plannedWork(3, 5))
        assertEquals("", WorkoutAdvance.plannedWork(0, 5))
    }

    private fun sessionExercise(id: String, targetSets: Int) = SessionExercise(
        id = "se-$id",
        sessionId = "s1",
        exercise = Exercise(
            id = id,
            name = id,
            muscleGroup = "Legs",
            notes = "",
            isCustom = false,
        ),
        sortOrder = 0,
        targetSets = targetSets,
        targetReps = 5,
        targetWeightKg = 100.0,
        restSeconds = 90,
    )

    private fun set(exerciseId: String, id: String = "set-$exerciseId") = SetLog(
        id = id,
        sessionId = "s1",
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        setNumber = 1,
        weightKg = 100.0,
        reps = 5,
        rpe = null,
        isWarmup = false,
        completedAt = 1L,
    )
}
