package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Log-or-Next decision, now that it is a rule rather than four lines of a composable.
 *
 * `ActiveWorkoutScreen` counted the selected lift's working sets, asked whether the lift was
 * complete, mapped every lift to an id, asked for the next one, and combined the answers with
 * whether a set was being edited — all in a function whose job is to draw.
 */
class WorkoutAdvanceSelectionTest {
    @Test
    fun theSelectedLiftsWorkingSetsAreCountedAndWarmupsAreNot() {
        val state = WorkoutAdvance.forSelection(
            session = session(
                sets = listOf(
                    set("a", SQUAT, warmup = true),
                    set("b", SQUAT),
                    set("c", SQUAT),
                    set("d", BENCH),
                ),
            ),
            selectedExerciseId = SQUAT,
            wantAnother = false,
            editing = false,
        )
        assertEquals(2, state.workingLogged)
    }

    @Test
    fun prescribedSetsInMeansCompleteAndNextIsTheLiftAfterIt() {
        val state = WorkoutAdvance.forSelection(
            session = session(sets = listOf(set("a", SQUAT), set("b", SQUAT), set("c", SQUAT))),
            selectedExerciseId = SQUAT,
            wantAnother = false,
            editing = false,
        )
        assertTrue(state.liftComplete)
        assertEquals(BENCH, state.nextExerciseId)
        assertTrue(state.showNext)
    }

    @Test
    fun askingForAnotherSetKeepsLogTheVolt() {
        val state = WorkoutAdvance.forSelection(
            session = session(sets = listOf(set("a", SQUAT), set("b", SQUAT), set("c", SQUAT))),
            selectedExerciseId = SQUAT,
            wantAnother = true,
            editing = false,
        )
        assertFalse(state.liftComplete)
        assertFalse(state.showNext)
    }

    @Test
    fun editingASetHidesNextEvenWhenTheLiftIsDone() {
        val state = WorkoutAdvance.forSelection(
            session = session(sets = listOf(set("a", SQUAT), set("b", SQUAT), set("c", SQUAT))),
            selectedExerciseId = SQUAT,
            wantAnother = false,
            editing = true,
        )
        assertTrue(state.liftComplete)
        assertFalse(state.showNext)
    }

    @Test
    fun theLastLiftHasNoNextAndNoSessionHasNothingAtAll() {
        val onLast = WorkoutAdvance.forSelection(
            session = session(sets = listOf(set("a", BENCH), set("b", BENCH), set("c", BENCH))),
            selectedExerciseId = BENCH,
            wantAnother = false,
            editing = false,
        )
        assertTrue(onLast.liftComplete)
        assertNull(onLast.nextExerciseId)
        assertFalse(onLast.showNext)

        val empty = WorkoutAdvance.forSelection(null, SQUAT, wantAnother = false, editing = false)
        assertEquals(0, empty.workingLogged)
        assertFalse(empty.liftComplete)
        assertNull(empty.nextExerciseId)
    }

    @Test
    fun aCardOffersAnotherSetOnlyOnceItsPrescribedSetsAreIn() {
        val two = listOf(set("a", SQUAT), set("b", SQUAT))
        assertFalse(WorkoutAdvance.cardOffersAnotherSet(two, targetSets = 3))
        assertTrue(WorkoutAdvance.cardOffersAnotherSet(two, targetSets = 2))
        // A free lift is never auto-complete, so it never grows an Add set row.
        assertFalse(WorkoutAdvance.cardOffersAnotherSet(two, targetSets = 0))
        // Warm-ups do not count towards the prescription.
        assertFalse(
            WorkoutAdvance.cardOffersAnotherSet(
                listOf(set("a", SQUAT, warmup = true), set("b", SQUAT)),
                targetSets = 2,
            ),
        )
    }

    @Test
    fun theLatestSetIsTheOneCompletedLastNotTheOneAddedLast() {
        val sets = listOf(
            set("first", SQUAT, completedAt = 300L),
            set("second", SQUAT, completedAt = 100L),
            set("third", SQUAT, completedAt = 200L),
        )
        assertEquals("first", WorkoutAdvance.latestSetId(sets))
        assertNull(WorkoutAdvance.latestSetId(emptyList()))
    }

    private fun session(sets: List<SetLog>): WorkoutSession = WorkoutSession(
        id = "s1",
        routineId = null,
        routineName = null,
        date = STAMP,
        notes = "",
        durationMinutes = 0,
        startedAt = STAMP,
        finishedAt = null,
        exercises = listOf(sessionExercise(SQUAT, "Squat", 3), sessionExercise(BENCH, "Bench", 3)),
        sets = sets,
    )

    private fun sessionExercise(id: String, name: String, targetSets: Int) = SessionExercise(
        id = "se-$id",
        sessionId = "s1",
        exercise = Exercise(
            id = id,
            name = name,
            muscleGroup = "Legs",
            notes = "",
            isCustom = false,
        ),
        sortOrder = 0,
        targetSets = targetSets,
        targetReps = 5,
        targetWeightKg = null,
        restSeconds = 120,
    )

    private fun set(
        id: String,
        exerciseId: String,
        warmup: Boolean = false,
        completedAt: Long = STAMP,
    ) = SetLog(
        id = id,
        sessionId = "s1",
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        setNumber = 1,
        weightKg = 100.0,
        reps = 5,
        rpe = null,
        isWarmup = warmup,
        completedAt = completedAt,
    )

    private companion object {
        const val SQUAT = "ex-squat"
        const val BENCH = "ex-bench"
        const val STAMP = 1_700_000_000_000L
    }
}

/** The wording two screens used to keep their own, drifted copies of. */
class PersonalRecordCopyTest {
    @Test
    fun repsOutranksEverythingSoABodyweightLiftIsNeverToldAboutItsWeight() {
        val kinds = setOf(
            PersonalRecordKind.REPS,
            PersonalRecordKind.WEIGHT,
            PersonalRecordKind.ESTIMATED_ONE_REP_MAX,
            PersonalRecordKind.REPS_AT_WEIGHT,
        )
        assertEquals("Most reps ever", PersonalRecordCopy.headline(kinds))
    }

    @Test
    fun theRestOfTheOrderIsHeaviestThenTheEstimateThenRepsAtWeight() {
        assertEquals(
            "Heaviest ever",
            PersonalRecordCopy.headline(
                setOf(PersonalRecordKind.WEIGHT, PersonalRecordKind.ESTIMATED_ONE_REP_MAX),
            ),
        )
        assertEquals(
            "Best estimated 1RM",
            PersonalRecordCopy.headline(
                setOf(PersonalRecordKind.ESTIMATED_ONE_REP_MAX, PersonalRecordKind.REPS_AT_WEIGHT),
            ),
        )
        assertEquals(
            "Most reps at that weight",
            PersonalRecordCopy.headline(setOf(PersonalRecordKind.REPS_AT_WEIGHT)),
        )
    }

    @Test
    fun everyKindHasACelebrationAndTheEstimateReadsTheSameOnBothScreens() {
        PersonalRecordKind.entries.forEach { kind ->
            assertTrue(kind.name, PersonalRecordCopy.celebration(kind).isNotBlank())
        }
        // The live banner said "Strongest set ever" where the summary said "Best estimated 1RM"
        // for the same record. One source, one sentence.
        assertEquals(
            PersonalRecordCopy.celebration(PersonalRecordKind.ESTIMATED_ONE_REP_MAX),
            PersonalRecordCopy.headline(setOf(PersonalRecordKind.ESTIMATED_ONE_REP_MAX)),
        )
    }
}
