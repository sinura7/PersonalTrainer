package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseUsageTest {
    @Test
    fun unusedExerciseCanBeDeleted() {
        val usage = ExerciseUsage(routineCount = 0, historySetCount = 0, sessionCount = 0)
        assertFalse(usage.isReferenced)
    }

    @Test
    fun routineOrHistoryBlocksDelete() {
        assertTrue(ExerciseUsage(1, 0, 0).isReferenced)
        assertTrue(ExerciseUsage(0, 4, 0).isReferenced)
        assertTrue(ExerciseUsage(0, 0, 1).isReferenced)
    }

    @Test
    fun reasonListsWhatIsBlockingDelete() {
        assertEquals("1 routine", ExerciseUsage(1, 0, 0).reason())
        assertEquals("2 routines and logged workout history", ExerciseUsage(2, 3, 1).reason())
    }

    @Test
    fun muscleFiltersPreferCatalogOrder() {
        val exercises = listOf(
            Exercise("1", "Curl", "Biceps", "", true),
            Exercise("2", "Squat", "Quads", "", false),
            Exercise("3", "Neck", "Neck", "", true),
        )
        assertEquals(listOf("Quads", "Biceps", "Neck"), MuscleGroups.presentIn(exercises))
    }
}
