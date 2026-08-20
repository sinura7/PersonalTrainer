package com.sinura.personaltrainer.data.backup

data class BackupDocument(
    val version: Int = BackupJson.CURRENT_VERSION,
    val app: String = BackupJson.APP_ID,
    val exportedAt: String,
    val preferences: BackupPreferences,
    val exercises: List<BackupExercise>,
    val routines: List<BackupRoutine>,
    val routineExercises: List<BackupRoutineExercise>,
    val sessions: List<BackupSession>,
    val sessionExercises: List<BackupSessionExercise>,
    val setLogs: List<BackupSetLog>,
)

data class BackupPreferences(
    val weightUnit: String,
    val trainingDaysPerWeek: Int = 4,
    val splitStyle: String = "auto",
    val weekStart: String = "MONDAY",
    val restSoundEnabled: Boolean = true,
    val restVibrationEnabled: Boolean = true,
    val defaultRestSeconds: Int = 90,
)

data class BackupExercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val notes: String,
    val isCustom: Boolean,
)

data class BackupRoutine(
    val id: String,
    val name: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupRoutineExercise(
    val id: String,
    val routineId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double?,
    val restSeconds: Int,
)

data class BackupSession(
    val id: String,
    val routineId: String?,
    val routineName: String?,
    val date: Long,
    val notes: String,
    val durationMinutes: Int,
    val startedAt: Long,
    val finishedAt: Long?,
)

data class BackupSessionExercise(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double?,
    val restSeconds: Int,
)

data class BackupSetLog(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    val isWarmup: Boolean,
    val completedAt: Long,
)

data class DriveBackupFile(
    val id: String,
    val name: String,
    val modifiedAtMillis: Long,
)

class BackupException(message: String) : Exception(message)
