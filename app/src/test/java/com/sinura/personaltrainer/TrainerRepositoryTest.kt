package com.sinura.personaltrainer

import com.sinura.personaltrainer.data.SampleData
import com.sinura.personaltrainer.data.TrainerRepository
import com.sinura.personaltrainer.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainerRepositoryTest {
    @Test
    fun completeWorkoutAddsHistoryAndCountsThisWeek() {
        val repository = TrainerRepository()
        val workout = SampleData.workouts.first()

        assertEquals(0, repository.completedThisWeekCount())
        repository.completeWorkout(workout)

        assertEquals(1, repository.history.value.size)
        assertEquals(workout.id, repository.history.value.first().workoutId)
        assertEquals(1, repository.completedThisWeekCount())
    }

    @Test
    fun workoutByIdReturnsNullForUnknownId() {
        val repository = TrainerRepository()
        assertNull(repository.workoutById("missing"))
        assertEquals("Full Body Strength", repository.workoutById("w-full-body")?.name)
    }

    @Test
    fun updateProfileReplacesCurrentProfile() {
        val repository = TrainerRepository()
        val updated = UserProfile(displayName = "Sam", goal = "Run more", weeklyWorkoutGoal = 4)
        repository.updateProfile(updated)
        assertEquals(updated, repository.profile.value)
        assertTrue(repository.workouts.value.isNotEmpty())
    }
}
