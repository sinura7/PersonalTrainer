package com.sinura.personaltrainer.data

enum class Difficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}

enum class WorkoutCategory {
    STRENGTH,
    CARDIO,
    MOBILITY,
    FULL_BODY,
}

data class Exercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    val instructions: List<String>,
)

data class WorkoutSet(
    val exercise: Exercise,
    val sets: Int,
    val reps: String,
    val restSeconds: Int,
)

data class Workout(
    val id: String,
    val name: String,
    val description: String,
    val durationMinutes: Int,
    val difficulty: Difficulty,
    val category: WorkoutCategory,
    val items: List<WorkoutSet>,
)

data class CompletedSession(
    val id: String,
    val workoutId: String,
    val workoutName: String,
    val completedAtMillis: Long,
    val durationMinutes: Int,
)

data class UserProfile(
    val displayName: String,
    val goal: String,
    val weeklyWorkoutGoal: Int,
)
