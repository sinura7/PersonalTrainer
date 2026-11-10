package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EarlyFinishTest {
    @Test
    fun finishedEarlyCountsSkippedLifts() {
        val session = WorkoutSession(
            id = "s1",
            routineId = "r1",
            routineName = "Push",
            date = 0L,
            notes = "",
            durationMinutes = 10,
            startedAt = 0L,
            finishedAt = 1L,
            exercises = listOf(
                sessionExercise("ex-a", "Bench"),
                sessionExercise("ex-b", "Row"),
            ),
            sets = listOf(
                SetLog("1", "s1", "ex-a", "Bench", 1, 60.0, 8, null, false, 0L),
            ),
        )
        val completion = EarlyFinish.liftCompletion(session)
        assertTrue(completion.finishedEarly)
        assertEquals("1 of 2 lifts", completion.progressLabel())
        assertEquals(listOf("Row"), completion.skippedLiftNames)
    }

    private fun sessionExercise(id: String, name: String) = SessionExercise(
        id = "se-$id",
        sessionId = "s1",
        exercise = Exercise(id = id, name = name, muscleGroup = "chest", notes = "", isCustom = false),
        sortOrder = 0,
        targetSets = 3,
        targetReps = 8,
        targetWeightKg = 60.0,
        restSeconds = 90,
    )
}
