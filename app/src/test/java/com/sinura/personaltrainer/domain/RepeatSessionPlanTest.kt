package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatSessionPlanTest {
    private val at = 1_700_000_000_000L

    private fun planned(
        exerciseId: String,
        order: Int,
        targetSets: Int = 3,
        targetReps: Int = 5,
        restSeconds: Int = 90,
    ) = SessionExercise(
        id = "se-$exerciseId",
        sessionId = "s1",
        exercise = Exercise(exerciseId, exerciseId, "Chest", "", false),
        sortOrder = order,
        targetSets = targetSets,
        targetReps = targetReps,
        targetWeightKg = 100.0,
        restSeconds = restSeconds,
    )

    private fun logged(exerciseId: String, reps: Int, offset: Long, warmup: Boolean = false) =
        set(
            id = "set-$exerciseId-$offset",
            sessionId = "s1",
            exerciseId = exerciseId,
            name = exerciseId,
            weightKg = 100.0,
            reps = reps,
            warmup = warmup,
            at = at + offset,
        )

    @Test
    fun preservesExerciseOrder() {
        val source = session(
            id = "s1",
            finishedAt = at + 3_600_000,
            sets = emptyList(),
            exercises = listOf(planned("bench", 1), planned("squat", 0)),
            date = at,
        )
        assertEquals(listOf("squat", "bench"), RepeatSessionPlan.from(source).map { it.exerciseId })
    }

    @Test
    fun targetSetsFromActualWorkingSetsExcludingWarmups() {
        val source = session(
            id = "s1",
            finishedAt = at + 3_600_000,
            sets = listOf(
                logged("bench", reps = 5, offset = 0, warmup = true),
                logged("bench", reps = 5, offset = 1_000),
                logged("bench", reps = 4, offset = 2_000),
            ),
            exercises = listOf(planned("bench", 0, targetSets = 3)),
            date = at,
        )
        assertEquals(2, RepeatSessionPlan.from(source).single().targetSets)
    }

    @Test
    fun plannedButUnloggedExerciseKeepsPlannedTargets() {
        val source = session(
            id = "s1",
            finishedAt = at + 3_600_000,
            sets = emptyList(),
            exercises = listOf(planned("bench", 0, targetSets = 4, targetReps = 8)),
            date = at,
        )
        val item = RepeatSessionPlan.from(source).single()
        assertEquals(4, item.targetSets)
        assertEquals(8, item.targetReps)
    }

    @Test
    fun targetRepsFromLastWorkingSetWithFallbacks() {
        val source = session(
            id = "s1",
            finishedAt = at + 3_600_000,
            sets = listOf(
                logged("bench", reps = 8, offset = 1_000),
                logged("bench", reps = 6, offset = 2_000),
            ),
            exercises = listOf(planned("bench", 0, targetReps = 5)),
            date = at,
        )
        assertEquals(6, RepeatSessionPlan.from(source).single().targetReps)
    }

    @Test
    fun freeWorkoutDerivesExercisesFromSetsInCompletionOrder() {
        val source = session(
            id = "s1",
            finishedAt = at + 3_600_000,
            sets = listOf(
                logged("row", reps = 10, offset = 5_000),
                logged("curl", reps = 12, offset = 1_000),
                logged("row", reps = 9, offset = 6_000),
            ),
            exercises = emptyList(),
            date = at,
        )
        val plan = RepeatSessionPlan.from(source)
        assertEquals(listOf("curl", "row"), plan.map { it.exerciseId })
        assertEquals(2, plan.first { it.exerciseId == "row" }.targetSets)
        // No plan to fall back on, so rest takes the default rather than zero.
        assertEquals(90, plan.first().restSeconds)
    }

    @Test
    fun neverEmitsWeightsOrSets() {
        val source = session(
            id = "s1",
            finishedAt = at + 3_600_000,
            sets = listOf(logged("bench", reps = 5, offset = 1_000)),
            exercises = listOf(planned("bench", 0)),
            date = at,
        )
        val item = RepeatSessionPlan.from(source).single()
        // RepeatItem carries structure only: there is nowhere for a weight or a logged set
        // to hide, and that is the contract the progression hint depends on.
        assertTrue(item.targetSets >= 1)
        assertEquals(90, item.restSeconds)
    }
}
