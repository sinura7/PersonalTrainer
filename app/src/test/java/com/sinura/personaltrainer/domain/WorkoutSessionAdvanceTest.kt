package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule behind the log loop moving on. The loop used to stay on a lift forever, because
 * [WorkoutSession.resolveSelectedExerciseId] returns a valid preferred id unchanged and
 * nothing else ever wrote the selection; finishing the last prescribed set of a lift left the
 * lifter on a card with nothing to do.
 */
class WorkoutSessionAdvanceTest {
    @Test
    fun targetIsMetOnlyByWorkingSets() {
        val squat = sessionExercise("squat", targetSets = 3)
        val warm = session(listOf(squat), List(3) { set("squat", it, warmup = true) })
        assertFalse("warm-ups must not meet a target", warm.isTargetMet("squat"))

        val working = session(listOf(squat), List(3) { set("squat", it) })
        assertTrue(working.isTargetMet("squat"))
        assertEquals(3, working.workingSetsFor("squat"))
    }

    @Test
    fun aLiftWithNoPrescriptionIsNeverFinished() {
        // An ad-hoc lift added mid-session has nothing to be measured against. Calling it done
        // would march the loop off it after a single set.
        val adhoc = sessionExercise("curl", targetSets = 0)
        val s = session(listOf(adhoc), listOf(set("curl", 0)))
        assertFalse(s.isTargetMet("curl"))
    }

    @Test
    fun advancesToTheNextLiftThatStillOwesSets() {
        val s = session(
            listOf(
                sessionExercise("squat", targetSets = 1),
                sessionExercise("bench", targetSets = 2),
                sessionExercise("row", targetSets = 2),
            ),
            listOf(set("squat", 0), set("bench", 1), set("bench", 2)),
        )
        // bench is finished, so squat hands over to row rather than to the next in the list.
        assertEquals("row", s.nextUnfinishedExerciseAfter("squat"))
    }

    @Test
    fun wrapsBackToALiftSkippedEarlier() {
        // Skipping forward earlier in the session leaves work owed behind the cursor. Stopping
        // at the end of the list would strand it with no way back but a manual tap.
        val s = session(
            listOf(
                sessionExercise("squat", targetSets = 2),
                sessionExercise("bench", targetSets = 1),
            ),
            listOf(set("bench", 0)),
        )
        assertEquals("squat", s.nextUnfinishedExerciseAfter("bench"))
    }

    @Test
    fun offersNothingWhenEveryOtherLiftIsDone() {
        val s = session(
            listOf(
                sessionExercise("squat", targetSets = 1),
                sessionExercise("bench", targetSets = 1),
            ),
            listOf(set("squat", 0), set("bench", 1)),
        )
        // Advancing onto a finished lift is worse than not advancing at all.
        assertNull(s.nextUnfinishedExerciseAfter("squat"))
    }

    @Test
    fun offersNothingForALiftThatIsNotInTheSession() {
        val s = session(listOf(sessionExercise("squat", targetSets = 1)), emptyList())
        assertNull(s.nextUnfinishedExerciseAfter("ghost"))
        assertNull(s.nextUnfinishedExerciseAfter("squat"))
    }

    private fun sessionExercise(id: String, targetSets: Int) = SessionExercise(
        id = "se-$id",
        sessionId = "s1",
        exercise = Exercise(
            id = id,
            name = id.replaceFirstChar { it.uppercase() },
            muscleGroup = "Quads",
            notes = "",
            isCustom = false,
        ),
        sortOrder = 0,
        targetSets = targetSets,
        targetReps = 5,
        targetWeightKg = 100.0,
        restSeconds = 90,
    )

    private fun set(exerciseId: String, index: Int, warmup: Boolean = false) = SetLog(
        id = "set-$exerciseId-$index",
        sessionId = "s1",
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        setNumber = index + 1,
        weightKg = 100.0,
        reps = 5,
        rpe = null,
        isWarmup = warmup,
        completedAt = index.toLong(),
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
