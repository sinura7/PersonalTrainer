package com.sinura.personaltrainer.data.repository

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toSummary
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.ProgressionBasis
import com.sinura.personaltrainer.domain.ProgressionCalculator
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.WorkingSetCandidate
import com.sinura.personaltrainer.domain.WorkoutSession
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkoutRepository(
    private val database: TrainerDatabase,
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
        val exercises = routine.exercises.mapIndexed { index, item ->
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
        }
        val sessionId = insertSessionIfIdle(session, exercises)
        return workoutDao.getSession(sessionId)?.toDomain()
            ?: error("Could not start that workout.")
    }

    suspend fun startFreeWorkout(focusTitle: String? = null): WorkoutSession {
        val now = System.currentTimeMillis()
        val focus = focusTitle?.trim().orEmpty()
        val session = WorkoutSessionEntity(
            id = UUID.randomUUID().toString(),
            routineId = null,
            routineName = focus.ifBlank { "Free workout" },
            date = now,
            notes = if (focus.isBlank()) "" else "Suggested focus: $focus",
            durationMinutes = 0,
            startedAt = now,
            finishedAt = null,
        )
        val sessionId = insertSessionIfIdle(session, emptyList())
        return workoutDao.getSession(sessionId)?.toDomain()
            ?: error("Could not start that workout.")
    }

    private suspend fun insertSessionIfIdle(
        session: WorkoutSessionEntity,
        exercises: List<SessionExerciseEntity>,
    ): String {
        return database.withTransaction {
            workoutDao.getInProgressSession()?.id?.let { return@withTransaction it }
            workoutDao.upsertSession(session)
            if (exercises.isNotEmpty()) {
                workoutDao.insertSessionExercises(exercises)
            }
            session.id
        }
    }

    suspend fun addExerciseToSession(
        sessionId: String,
        exercise: Exercise,
        targetSets: Int = 3,
        targetReps: Int = 5,
        targetWeightKg: Double? = null,
        restSeconds: Int = 90,
    ) {
        val current = workoutDao.getSession(sessionId) ?: return
        if (current.session.finishedAt != null) return
        if (current.exercises.any { it.exercise.id == exercise.id }) return
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
        val current = workoutDao.getSession(sessionId)
            ?: error("This workout is no longer available.")
        if (current.session.finishedAt != null) {
            error("This workout is already finished.")
        }
        if (reps < 1) error("Reps must be at least 1.")
        val violation = SetLogRules.validate(weightKg, reps, isWarmup)
        if (violation != null) error(violation)
        val nextNumber = current.sets.count { it.set.exerciseId == exerciseId } + 1
        val safeWeight = if (weightKg.isFinite()) weightKg.coerceAtLeast(0.0) else 0.0
        workoutDao.insertSet(
            SetLogEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = nextNumber,
                weightKg = safeWeight,
                reps = reps.coerceAtLeast(1),
                rpe = rpe,
                isWarmup = isWarmup,
                completedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateSet(
        setId: String,
        weightKg: Double,
        reps: Int,
        rpe: Int?,
        isWarmup: Boolean,
    ) {
        val current = workoutDao.getSet(setId) ?: error("That set is no longer available.")
        val session = workoutDao.getSession(current.sessionId)
        if (session?.session?.finishedAt != null) {
            error("This workout is already finished.")
        }
        if (reps < 1) error("Reps must be at least 1.")
        val violation = SetLogRules.validate(weightKg, reps, isWarmup)
        if (violation != null) error(violation)
        val safeWeight = if (weightKg.isFinite()) weightKg.coerceAtLeast(0.0) else current.weightKg
        workoutDao.updateSet(
            current.copy(
                weightKg = safeWeight,
                reps = reps.coerceAtLeast(1),
                rpe = rpe,
                isWarmup = isWarmup,
            ),
        )
    }

    suspend fun deleteSet(setId: String) {
        val deleted = workoutDao.getSet(setId) ?: return
        workoutDao.deleteSet(setId)
        workoutDao.setsForExercise(deleted.sessionId, deleted.exerciseId)
            .forEachIndexed { index, set ->
                val nextNumber = index + 1
                if (set.setNumber != nextNumber) {
                    workoutDao.updateSet(set.copy(setNumber = nextNumber))
                }
            }
    }

    suspend fun updateSessionNotes(sessionId: String, notes: String) {
        val current = workoutDao.getSession(sessionId)?.session ?: return
        if (current.finishedAt != null) return
        workoutDao.updateSession(current.copy(notes = notes.trim()))
    }

    suspend fun finishSession(sessionId: String, notes: String) {
        val current = workoutDao.getSession(sessionId)?.session
            ?: error("This workout is no longer available.")
        if (current.finishedAt != null) return
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

    /**
     * The progression hint for one exercise, judged on the TOP set of the last finished
     * session containing it — see [ProgressionBasis] for the rule and the back-off-set bug it
     * fixes. Every progression surface goes through here or [readyForProgression]; there is no
     * other path to a suggested weight.
     */
    suspend fun progressionFor(
        exerciseId: String,
        exerciseName: String,
        targetReps: Int,
        excludeSessionId: String,
    ): ProgressionHint? {
        val topSet = topSetOfLastSession(exerciseId, excludeSessionId) ?: return null
        val resolvedTarget = targetReps.takeIf { it > 0 }
            ?: workoutDao.lastTargetReps(exerciseId)
            ?: topSet.reps
        return ProgressionCalculator.hint(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            lastWeightKg = topSet.weightKg,
            lastWorkingReps = topSet.reps,
            targetReps = resolvedTarget,
        )
    }

    suspend fun readyForProgression(routines: List<Routine>): List<ProgressionHint> {
        val seen = linkedSetOf<String>()
        val hints = mutableListOf<ProgressionHint>()
        routines.forEach { routine ->
            routine.exercises.forEach { item ->
                if (seen.add(item.exercise.id)) {
                    val topSet = topSetOfLastSession(item.exercise.id) ?: return@forEach
                    val hint = ProgressionCalculator.hint(
                        exerciseId = item.exercise.id,
                        exerciseName = item.exercise.name,
                        lastWeightKg = topSet.weightKg,
                        lastWorkingReps = topSet.reps,
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

    /**
     * Finds the last finished session containing [exerciseId], then applies the pure top-set
     * rule to its working sets. Two small queries rather than one clever one: the selection
     * stays in testable Kotlin instead of SQL nobody can unit-test on the JVM.
     *
     * @param excludeSessionId a session to skip (the one being logged right now); empty
     * string excludes nothing.
     */
    private suspend fun topSetOfLastSession(
        exerciseId: String,
        excludeSessionId: String = "",
    ): WorkingSetCandidate? {
        val sessionId = workoutDao.lastFinishedSessionIdWithExercise(exerciseId, excludeSessionId)
            ?: return null
        val candidates = workoutDao.workingSetsForExerciseInSession(sessionId, exerciseId)
            .map { WorkingSetCandidate(it.weightKg, it.reps, it.completedAt) }
        return ProgressionBasis.topWorkingSet(candidates)
    }
}
