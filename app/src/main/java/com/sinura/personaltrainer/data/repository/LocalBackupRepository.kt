package com.sinura.personaltrainer.data.repository

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.backup.BackupDocument
import com.sinura.personaltrainer.data.backup.BackupExercise
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.BackupPreferences
import com.sinura.personaltrainer.data.backup.BackupRoutine
import com.sinura.personaltrainer.data.backup.BackupRoutineExercise
import com.sinura.personaltrainer.data.backup.BackupSession
import com.sinura.personaltrainer.data.backup.BackupSessionExercise
import com.sinura.personaltrainer.data.backup.BackupSetLog
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.WeightUnit
import kotlinx.coroutines.flow.first

class LocalBackupRepository(
    private val database: TrainerDatabase,
    private val preferencesRepository: PreferencesRepository,
) {
    suspend fun createSnapshot(): BackupDocument {
        val exercises = database.exerciseDao().getAll()
        val routines = database.routineDao().getAllRoutines()
        val routineExercises = database.routineDao().getAllRoutineExercises()
        val sessions = database.workoutDao().getAllSessions()
        val sessionExercises = database.workoutDao().getAllSessionExercises()
        val sets = database.workoutDao().getAllSets()
        val unit = preferencesRepository.weightUnit.first()
        val schedule = preferencesRepository.schedulePreferences.first()
        val rest = preferencesRepository.restTimerPreferences.first()
        return BackupDocument(
            version = BackupJson.CURRENT_VERSION,
            app = BackupJson.APP_ID,
            exportedAt = BackupJson.nowIso(),
            preferences = BackupPreferences(
                weightUnit = unit.storageKey,
                trainingDaysPerWeek = schedule.trainingDaysPerWeek,
                splitStyle = schedule.splitStyle.storageKey,
                weekStart = schedule.weekStart.name,
                restSoundEnabled = rest.soundEnabled,
                restVibrationEnabled = rest.vibrationEnabled,
                defaultRestSeconds = rest.defaultRestSeconds,
            ),
            exercises = exercises.map {
                BackupExercise(it.id, it.name, it.muscleGroup, it.notes, it.isCustom)
            },
            routines = routines.map {
                BackupRoutine(it.id, it.name, it.notes, it.createdAt, it.updatedAt)
            },
            routineExercises = routineExercises.map {
                BackupRoutineExercise(
                    id = it.id,
                    routineId = it.routineId,
                    exerciseId = it.exerciseId,
                    sortOrder = it.sortOrder,
                    targetSets = it.targetSets,
                    targetReps = it.targetReps,
                    targetWeightKg = it.targetWeightKg,
                    restSeconds = it.restSeconds,
                )
            },
            sessions = sessions.map {
                BackupSession(
                    id = it.id,
                    routineId = it.routineId,
                    routineName = it.routineName,
                    date = it.date,
                    notes = it.notes,
                    durationMinutes = it.durationMinutes,
                    startedAt = it.startedAt,
                    finishedAt = it.finishedAt,
                )
            },
            sessionExercises = sessionExercises.map {
                BackupSessionExercise(
                    id = it.id,
                    sessionId = it.sessionId,
                    exerciseId = it.exerciseId,
                    sortOrder = it.sortOrder,
                    targetSets = it.targetSets,
                    targetReps = it.targetReps,
                    targetWeightKg = it.targetWeightKg,
                    restSeconds = it.restSeconds,
                )
            },
            setLogs = sets.map {
                BackupSetLog(
                    id = it.id,
                    sessionId = it.sessionId,
                    exerciseId = it.exerciseId,
                    setNumber = it.setNumber,
                    weightKg = it.weightKg,
                    reps = it.reps,
                    rpe = it.rpe,
                    isWarmup = it.isWarmup,
                    completedAt = it.completedAt,
                )
            },
        )
    }

    suspend fun replaceWith(document: BackupDocument) {
        if (document.version > BackupJson.CURRENT_VERSION) {
            throw BackupException("This backup was made with a newer app version and can’t be opened here.")
        }
        database.withTransaction {
            database.workoutDao().deleteAllSets()
            database.workoutDao().deleteAllSessionExercises()
            database.workoutDao().deleteAllSessions()
            database.routineDao().deleteAllRoutineExercises()
            database.routineDao().deleteAllRoutines()
            database.exerciseDao().deleteAll()

            if (document.exercises.isNotEmpty()) {
                database.exerciseDao().replaceAll(
                    document.exercises.map {
                        ExerciseEntity(it.id, it.name, it.muscleGroup, it.notes, it.isCustom)
                    },
                )
            }
            if (document.routines.isNotEmpty()) {
                database.routineDao().replaceRoutines(
                    document.routines.map {
                        RoutineEntity(it.id, it.name, it.notes, it.createdAt, it.updatedAt)
                    },
                )
            }
            if (document.routineExercises.isNotEmpty()) {
                database.routineDao().replaceRoutineExercises(
                    document.routineExercises.map {
                        RoutineExerciseEntity(
                            id = it.id,
                            routineId = it.routineId,
                            exerciseId = it.exerciseId,
                            sortOrder = it.sortOrder,
                            targetSets = it.targetSets,
                            targetReps = it.targetReps,
                            targetWeightKg = it.targetWeightKg,
                            restSeconds = it.restSeconds,
                        )
                    },
                )
            }
            if (document.sessions.isNotEmpty()) {
                database.workoutDao().replaceSessions(
                    document.sessions.map {
                        WorkoutSessionEntity(
                            id = it.id,
                            routineId = it.routineId,
                            routineName = it.routineName,
                            date = it.date,
                            notes = it.notes,
                            durationMinutes = it.durationMinutes,
                            startedAt = it.startedAt,
                            finishedAt = it.finishedAt,
                        )
                    },
                )
            }
            if (document.sessionExercises.isNotEmpty()) {
                database.workoutDao().insertSessionExercises(
                    document.sessionExercises.map {
                        SessionExerciseEntity(
                            id = it.id,
                            sessionId = it.sessionId,
                            exerciseId = it.exerciseId,
                            sortOrder = it.sortOrder,
                            targetSets = it.targetSets,
                            targetReps = it.targetReps,
                            targetWeightKg = it.targetWeightKg,
                            restSeconds = it.restSeconds,
                        )
                    },
                )
            }
            if (document.setLogs.isNotEmpty()) {
                database.workoutDao().replaceSets(
                    document.setLogs.map {
                        SetLogEntity(
                            id = it.id,
                            sessionId = it.sessionId,
                            exerciseId = it.exerciseId,
                            setNumber = it.setNumber,
                            weightKg = it.weightKg,
                            reps = it.reps,
                            rpe = it.rpe,
                            isWarmup = it.isWarmup,
                            completedAt = it.completedAt,
                        )
                    },
                )
            }
        }
        preferencesRepository.setWeightUnit(WeightUnit.fromStorage(document.preferences.weightUnit))
        preferencesRepository.setSchedulePreferences(
            SchedulePreferences(
                trainingDaysPerWeek = document.preferences.trainingDaysPerWeek,
                splitStyle = SplitStyle.fromStorage(document.preferences.splitStyle),
                weekStart = SchedulePreferences.weekStartFromStorage(document.preferences.weekStart),
            ),
        )
        preferencesRepository.setRestTimerPreferences(
            RestTimerPreferences(
                soundEnabled = document.preferences.restSoundEnabled,
                vibrationEnabled = document.preferences.restVibrationEnabled,
                defaultRestSeconds = document.preferences.defaultRestSeconds,
            ),
        )
    }
}
