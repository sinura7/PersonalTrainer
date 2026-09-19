package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutProgressTest {
    @Test
    fun countsLiftsAndWorkingSetsAcrossTheSession() {
        val session = session(
            lifts = listOf(lift(id = "squat", targetSets = 3), lift(id = "bench", targetSets = 3), lift(id = "row", targetSets = 2)),
            sets = listOf(
                set(exerciseId = "squat", number = 1), set(exerciseId = "squat", number = 2), set(exerciseId = "squat", number = 3),
                set(exerciseId = "bench", number = 4), set(exerciseId = "bench", number = 5, warmup = true),
            ),
        )
        val progress = WorkoutProgressCalculator.of(session = session, selectedExerciseId = "bench")
        assertEquals(2, progress.exerciseNumber)
        assertEquals(3, progress.exerciseCount)
        assertEquals(1, progress.exercisesDone)
        assertEquals(4, progress.setsDone)
        assertEquals(8, progress.setsPlanned)
        assertEquals("Exercise 2 of 3 · 4 of 8 sets", WorkoutProgressCalculator.headline(progress))
        assertEquals("Exercise 2 of 3 · 4 of 8 sets. 1 exercise complete", WorkoutProgressCalculator.spoken(progress))
        assertEquals(
            listOf(ProgressSegmentState.DONE, ProgressSegmentState.CURRENT, ProgressSegmentState.UPCOMING),
            progress.segments.map { it.state },
        )
        assertEquals(1f, progress.segments[0].fraction)
        assertEquals(1f / 3f, progress.segments[1].fraction, 0.001f)
        assertEquals(0f, progress.segments[2].fraction)
    }

    @Test
    fun extraSetsGrowThePlannedCountInsteadOfOverflowingIt() {
        val session = session(
            lifts = listOf(lift(id = "squat", targetSets = 3)),
            sets = (1..5).map { set(exerciseId = "squat", number = it) },
        )
        val progress = WorkoutProgressCalculator.of(session = session, selectedExerciseId = "squat")
        assertEquals(5, progress.setsDone)
        assertEquals(5, progress.setsPlanned)
        assertEquals("Exercise 1 of 1 · 5 of 5 sets", WorkoutProgressCalculator.headline(progress))
        assertEquals(ProgressSegmentState.CURRENT, progress.segments.single().state)
        assertEquals(1f, progress.segments.single().fraction)
    }

    @Test
    fun freeLiftsNeverCountAsDoneAndReadAsLogged() {
        val session = session(
            lifts = listOf(lift(id = "curl", targetSets = 0), lift(id = "dip", targetSets = 0)),
            sets = listOf(set(exerciseId = "curl", number = 1), set(exerciseId = "curl", number = 2)),
        )
        val progress = WorkoutProgressCalculator.of(session = session, selectedExerciseId = "dip")
        assertEquals(0, progress.exercisesDone)
        assertEquals(0, progress.setsPlanned)
        assertEquals("Exercise 2 of 2 · 2 sets logged", WorkoutProgressCalculator.headline(progress))
        assertEquals(1f, progress.segments[0].fraction)
        assertEquals(ProgressSegmentState.UPCOMING, progress.segments[0].state)
        val single = WorkoutProgressCalculator.of(session = session.copy(sets = listOf(set(exerciseId = "curl", number = 1))), selectedExerciseId = "dip")
        assertEquals("Exercise 2 of 2 · 1 set logged", WorkoutProgressCalculator.headline(single))
        val fresh = WorkoutProgressCalculator.of(session = session.copy(sets = emptyList()), selectedExerciseId = "curl")
        assertEquals("Exercise 1 of 2 · No sets yet", WorkoutProgressCalculator.headline(fresh))
    }

    @Test
    fun emptyOrMissingSessionIsEmpty() {
        assertEquals(WorkoutProgress.EMPTY, WorkoutProgressCalculator.of(session = null, selectedExerciseId = "squat"))
        val empty = session(lifts = emptyList(), sets = emptyList())
        assertEquals(WorkoutProgress.EMPTY, WorkoutProgressCalculator.of(session = empty, selectedExerciseId = null))
        assertEquals("", WorkoutProgressCalculator.headline(WorkoutProgress.EMPTY))
        assertEquals("", WorkoutProgressCalculator.spoken(WorkoutProgress.EMPTY))
    }

    @Test
    fun staleSelectionResolvesLikeTheSessionDoes() {
        val session = session(
            lifts = listOf(lift(id = "squat", targetSets = 3), lift(id = "bench", targetSets = 3)),
            sets = listOf(set(exerciseId = "bench", number = 1)),
        )
        val progress = WorkoutProgressCalculator.of(session = session, selectedExerciseId = "deadlift")
        // resolveSelectedExerciseId prefers the first lift that has sets.
        assertEquals(2, progress.exerciseNumber)
        assertTrue(progress.segments[1].state == ProgressSegmentState.CURRENT)
    }

    private fun session(lifts: List<SessionExercise>, sets: List<SetLog>) = WorkoutSession(
        id = "s1",
        routineId = null,
        routineName = "Lower B",
        date = 1L,
        notes = "",
        durationMinutes = 0,
        startedAt = 1L,
        finishedAt = null,
        exercises = lifts,
        sets = sets,
    )

    private fun lift(id: String, targetSets: Int) = SessionExercise(
        id = "se-$id",
        sessionId = "s1",
        exercise = Exercise(id = id, name = id, muscleGroup = "Legs", notes = "", isCustom = false),
        sortOrder = 0,
        targetSets = targetSets,
        targetReps = 8,
        targetWeightKg = 60.0,
        restSeconds = 90,
    )

    private fun set(exerciseId: String, number: Int, warmup: Boolean = false) = SetLog(
        id = "set-$exerciseId-$number",
        sessionId = "s1",
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        setNumber = number,
        weightKg = 60.0,
        reps = 8,
        rpe = null,
        isWarmup = warmup,
        completedAt = number.toLong(),
    )
}
