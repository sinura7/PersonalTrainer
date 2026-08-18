package com.sinura.personaltrainer.data

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TrainerRepository(
    initialWorkouts: List<Workout> = SampleData.workouts,
    initialProfile: UserProfile = SampleData.defaultProfile,
) {
    private val _workouts = MutableStateFlow(initialWorkouts)
    val workouts: StateFlow<List<Workout>> = _workouts.asStateFlow()

    private val _history = MutableStateFlow<List<CompletedSession>>(emptyList())
    val history: StateFlow<List<CompletedSession>> = _history.asStateFlow()

    private val _profile = MutableStateFlow(initialProfile)
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    fun workoutById(id: String): Workout? = _workouts.value.firstOrNull { it.id == id }

    fun completeWorkout(workout: Workout) {
        val session = CompletedSession(
            id = UUID.randomUUID().toString(),
            workoutId = workout.id,
            workoutName = workout.name,
            completedAtMillis = System.currentTimeMillis(),
            durationMinutes = workout.durationMinutes,
        )
        _history.update { listOf(session) + it }
    }

    fun updateProfile(profile: UserProfile) {
        _profile.value = profile
    }

    fun completedThisWeekCount(nowMillis: Long = System.currentTimeMillis()): Int {
        val weekMs = 7L * 24L * 60L * 60L * 1000L
        return _history.value.count { nowMillis - it.completedAtMillis <= weekMs }
    }
}
