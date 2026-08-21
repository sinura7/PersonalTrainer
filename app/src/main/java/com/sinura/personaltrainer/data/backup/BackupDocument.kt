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
    /**
     * v2 additions. Defaulted to empty so a v1 file — which has neither array — decodes into a
     * complete document rather than failing, and so every existing construction site keeps
     * compiling. `decode` fills [exerciseMuscles] for a v1 document by deriving it.
     */
    val exerciseMuscles: List<BackupExerciseMuscle> = emptyList(),
    val scheduleSlots: List<BackupScheduleSlot> = emptyList(),
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

/**
 * The four v2 fields are nullable even though the columns behind them are not.
 *
 * Gson does not run Kotlin constructors, so a JSON null lands in a non-null Kotlin field as
 * null anyway and blows up at the first read — the type says one thing and the object holds
 * another. Declaring them nullable makes that state expressible, and `decode` normalizes every
 * one of them in a single step before anything else sees the document.
 *
 * `nameKey` is deliberately NOT in the document: it is derived from the name, and a stored copy
 * is one more thing that can arrive stale. Restore recomputes it.
 */
data class BackupExercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val notes: String,
    val isCustom: Boolean,
    val equipment: String? = null,
    val loadType: String? = null,
    val movementKey: String? = null,
    val imageKey: String? = null,
)

data class BackupExerciseMuscle(
    val exerciseId: String,
    val muscleKey: String,
    val weight: Double,
)

data class BackupScheduleSlot(
    val id: String,
    val position: Int,
    val routineId: String?,
    val focusKind: String?,
    val anchorDay: Int?,
    val createdAt: Long,
    val updatedAt: Long,
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
