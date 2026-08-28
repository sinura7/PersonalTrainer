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
}
