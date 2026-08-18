package com.sinura.personaltrainer

import androidx.lifecycle.ViewModel
import com.sinura.personaltrainer.data.TrainerRepository
import com.sinura.personaltrainer.data.TrainerStore
import com.sinura.personaltrainer.data.UserProfile
import com.sinura.personaltrainer.data.Workout
import kotlinx.coroutines.flow.StateFlow

class TrainerViewModel(
    private val repository: TrainerRepository = TrainerStore.repository,
) : ViewModel() {
    val workouts: StateFlow<List<Workout>> = repository.workouts
    val history = repository.history
    val profile: StateFlow<UserProfile> = repository.profile

    fun workoutById(id: String): Workout? = repository.workoutById(id)

    fun completeWorkout(workout: Workout) {
        repository.completeWorkout(workout)
    }

    fun updateProfile(profile: UserProfile) {
        repository.updateProfile(profile)
    }

    fun completedThisWeek(): Int = repository.completedThisWeekCount()
}
