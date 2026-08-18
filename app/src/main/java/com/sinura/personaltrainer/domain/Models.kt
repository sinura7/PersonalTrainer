package com.sinura.personaltrainer.domain

data class Exercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val notes: String,
    val isCustom: Boolean,
)

data class RoutineExercise(
    val id: String,
    val routineId: String,
    val exercise: Exercise,
    val sortOrder: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double?,
    val restSeconds: Int,
)

data class Routine(
    val id: String,
    val name: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val exercises: List<RoutineExercise>,
)

data class SessionExercise(
    val id: String,
    val sessionId: String,
    val exercise: Exercise,
    val sortOrder: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double?,
    val restSeconds: Int,
)

data class SetLog(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    val isWarmup: Boolean,
    val completedAt: Long,
)

data class WorkoutSession(
    val id: String,
    val routineId: String?,
    val routineName: String?,
    val date: Long,
    val notes: String,
    val durationMinutes: Int,
    val startedAt: Long,
    val finishedAt: Long?,
    val exercises: List<SessionExercise>,
    val sets: List<SetLog>,
) {
    val isFinished: Boolean get() = finishedAt != null

    fun workingVolumeKg(): Double = sets
        .filterNot { it.isWarmup }
        .sumOf { it.weightKg * it.reps }

    fun setsFor(exerciseId: String): List<SetLog> =
        sets.filter { it.exerciseId == exerciseId }.sortedBy { it.setNumber }
}

data class ProgressionHint(
    val exerciseId: String,
    val exerciseName: String,
    val lastWeightKg: Double,
    val lastReps: Int,
    val targetReps: Int,
    val suggestedWeightKg: Double,
    val action: ProgressionAction,
)

enum class ProgressionAction {
    INCREASE,
    HOLD,
    DECREASE,
}
