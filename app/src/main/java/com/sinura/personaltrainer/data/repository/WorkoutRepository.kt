package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toSummary
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.ProgressionCalculator
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.WorkoutSession
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkoutRepository(
    private val workoutDao: WorkoutDao,
) {
    fun observeHistory(): Flow<List<WorkoutSession>> =
        workoutDao.observeFinishedSessions().map { list -> list.map { it.toDomain() } }

    fun observeSession(id: String): Flow<WorkoutSession?> =
        workoutDao.observeSession(id).map { it?.toDomain() }

    fun observeInProgress(): Flow<WorkoutSession?> =
        workoutDao.observeInProgressSession().map { it?.toSummary() }

    suspend fun getInProgress(): WorkoutSession? =
        workoutDao.getInProgressSession()?.toSummary()

    suspend fun startRoutine(routine: Routine): WorkoutSession {
        getInProgress()?.let { existing ->
            return workoutDao.getSession(existing.id)?.toDomain() ?: existing
        }
        val now = System.currentTimeMillis()
        val session = WorkoutSessionEntity(
            id = UUID.randomUUID().toString(),
            routineId = routine.id,
            routineName = routine.name,
            date = now,
            notes = "",
            durationMinutes = 0,
            startedAt = now,
            finishedAt = null,
        )
        workoutDao.upsertSession(session)
        workoutDao.insertSessionExercises(
            routine.exercises.mapIndexed { index, item ->
                SessionExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = session.id,
                    exerciseId = item.exercise.id,
                    sortOrder = index,
                    targetSets = item.targetSets,
                    targetReps = item.targetReps,
                    targetWeightKg = item.targetWeightKg,
                    restSeconds = item.restSeconds,
                )
            },
        )
        return workoutDao.getSession(session.id)?.toDomain()
            ?: error("Failed to start routine session")
    }

    suspend fun startFreeWorkout(): WorkoutSession {
        getInProgress()?.let { existing ->
            return workoutDao.getSession(existing.id)?.toDomain() ?: existing
        }
        val now = System.currentTimeMillis()
        val session = WorkoutSessionEntity(
            id = UUID.randomUUID().toString(),
            routineId = null,
            routineName = "Free workout",
            date = now,
            notes = "",
            durationMinutes = 0,
            startedAt = now,
            finishedAt = null,
        )
        workoutDao.upsertSession(session)
        return workoutDao.getSession(session.id)?.toDomain()
            ?: error("Failed to start free workout")
    }

    suspend fun addExerciseToSession(
        sessionId: String,
        exercise: Exercise,
        targetSets: Int = 3,
        targetReps: Int = 5,
        targetWeightKg: Double? = null,
        restSeconds: Int = 90,
    ) {
        val nextOrder = workoutDao.maxSessionExerciseOrder(sessionId) + 1
        workoutDao.upsertSessionExercise(
            SessionExerciseEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                exerciseId = exercise.id,
                sortOrder = nextOrder,
                targetSets = targetSets.coerceAtLeast(1),
                targetReps = targetReps.coerceAtLeast(1),
                targetWeightKg = targetWeightKg?.takeIf { it > 0.0 },
                restSeconds = restSeconds.coerceAtLeast(0),
            ),
        )
    }

    suspend fun removeExerciseFromSession(itemId: String) {
        workoutDao.deleteSessionExercise(itemId)
    }

    suspend fun logSet(
        sessionId: String,
        exerciseId: String,
        weightKg: Double,
        reps: Int,
        rpe: Int?,
        isWarmup: Boolean,
    ) {
        val current = workoutDao.getSession(sessionId) ?: return
        val nextNumber = current.sets.count { it.set.exerciseId == exerciseId } + 1
        workoutDao.insertSet(
            SetLogEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = nextNumber,
                weightKg = weightKg.coerceAtLeast(0.0),
                reps = reps.coerceAtLeast(0),
                rpe = rpe,
                isWarmup = isWarmup,
                completedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteSet(setId: String) {
        workoutDao.deleteSet(setId)
    }

    suspend fun finishSession(sessionId: String, notes: String) {
        val current = workoutDao.getSession(sessionId)?.session ?: return
        val finishedAt = System.currentTimeMillis()
        val duration = TimeUnit.MILLISECONDS.toMinutes(finishedAt - current.startedAt)
            .toInt()
            .coerceAtLeast(1)
        workoutDao.updateSession(
            current.copy(
                notes = notes.trim(),
                durationMinutes = duration,
                finishedAt = finishedAt,
            ),
        )
    }

    suspend fun discardSession(sessionId: String) {
        workoutDao.deleteSession(sessionId)
    }

    suspend fun progressionFor(
        exerciseId: String,
        exerciseName: String,
        targetReps: Int,
        excludeSessionId: String,
    ): ProgressionHint? {
        val last = workoutDao.lastWorkingSetExcluding(exerciseId, excludeSessionId) ?: return null
        val resolvedTarget = targetReps.takeIf { it > 0 }
            ?: workoutDao.lastTargetReps(exerciseId)
            ?: last.reps
        return ProgressionCalculator.hint(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            lastWeightKg = last.weightKg,
            lastWorkingReps = last.reps,
            targetReps = resolvedTarget,
        )
    }

    suspend fun readyForProgression(routines: List<Routine>): List<ProgressionHint> {
        val seen = linkedSetOf<String>()
        val hints = mutableListOf<ProgressionHint>()
        routines.forEach { routine ->
            routine.exercises.forEach { item ->
                if (seen.add(item.exercise.id)) {
                    val last = workoutDao.lastFinishedWorkingSet(item.exercise.id) ?: return@forEach
                    val hint = ProgressionCalculator.hint(
                        exerciseId = item.exercise.id,
                        exerciseName = item.exercise.name,
                        lastWeightKg = last.weightKg,
                        lastWorkingReps = last.reps,
                        targetReps = item.targetReps,
                    )
                    if (hint.action == ProgressionAction.INCREASE) {
                        hints += hint
                    }
                }
            }
        }
        return hints
    }
}
