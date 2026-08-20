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
import com.sinura.personaltrainer.data.local.dao.ExerciseSetRow
import com.sinura.personaltrainer.domain.ExerciseHistoryBuilder
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.ExerciseSetEntry
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.PersonalRecords
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
            .orLogAndFallback("workout history", emptyList())

    fun observeSession(id: String): Flow<WorkoutSession?> =
        workoutDao.observeSession(id).map { it?.toDomain() }
            .orLogAndFallback("the active session", null)

    fun observeInProgress(): Flow<WorkoutSession?> =
        workoutDao.observeInProgressSession().map { it?.toSummary() }
            .orLogAndFallback("the in-progress session", null)

    suspend fun getInProgress(): WorkoutSession? =
        workoutDao.getInProgressSession()?.toSummary()

    suspend fun getSession(id: String): WorkoutSession? = workoutDao.getSession(id)?.toDomain()

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
    ): LoggedSet {
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
        val safeReps = reps.coerceAtLeast(1)
        val completedAt = System.currentTimeMillis()
        val entity = SetLogEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            exerciseId = exerciseId,
            setNumber = nextNumber,
            weightKg = safeWeight,
            reps = safeReps,
            rpe = rpe,
            isWarmup = isWarmup,
            completedAt = completedAt,
        )
        workoutDao.insertSet(entity)
        return LoggedSet(
            setId = entity.id,
            records = if (isWarmup) {
                // A warm-up is preparation, not work. It is excluded from volume, from the
                // heat map and from records, and announcing one as a PR would be a lie.
                emptySet()
            } else {
                recordsBrokenBy(exerciseId, sessionId, safeWeight, safeReps, completedAt)
            },
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

    /**
     * Every finished working set of one exercise, as the history builder wants it.
     *
     * Degrades to an empty history rather than throwing into the collector, the same way the
     * other observed reads do.
     */
    fun observeExerciseSets(exerciseId: String): Flow<List<ExerciseSetEntry>> =
        workoutDao.observeFinishedWorkingSets(exerciseId)
            .map { rows -> rows.map { it.toEntry() } }
            .orLogAndFallback("the history for this exercise", emptyList())

    /**
     * What this lift looked like the last time it was trained, for the values shown beside the
     * inputs while logging. Excludes the session being logged right now.
     */
    suspend fun lastPerformance(
        exerciseId: String,
        excludeSessionId: String = "",
    ): ExerciseSessionSummary? {
        val sessionId = workoutDao.lastFinishedSessionIdWithExercise(exerciseId, excludeSessionId)
            ?: return null
        val row = workoutDao.getSessionRow(sessionId) ?: return null
        // Reads one session, not the lift's whole history: this runs on every lift switch.
        val entries = workoutDao.workingSetsForExerciseInSession(sessionId, exerciseId)
            .map { set ->
                ExerciseSetEntry(
                    record = ExerciseSetRecord(
                        setId = set.id,
                        sessionId = set.sessionId,
                        weightKg = set.weightKg,
                        reps = set.reps,
                        completedAt = set.completedAt,
                    ),
                    sessionName = row.routineName,
                    sessionPerformedAtMs = row.date,
                )
            }
        if (entries.isEmpty()) return null
        return ExerciseHistoryBuilder.fromEntries(exerciseId, entries).sessions.firstOrNull()
    }

    /**
     * Which records a set breaks, judged against everything logged before it.
     *
     * "Before" is by completion time, not by id, so a set can never be its own prior history.
     *
     * The current session's own earlier sets count. They are not in the finished-history query
     * — that session has not finished — but a lifter who works up 100, then 105, then repeats
     * 102.5 has not just set a weight record, and saying so would be obviously wrong to the
     * person holding the phone.
     */
    suspend fun recordsBrokenBy(
        exerciseId: String,
        sessionId: String,
        weightKg: Double,
        reps: Int,
        completedAt: Long,
    ): Set<PersonalRecordKind> {
        val finished = workoutDao.finishedWorkingSets(exerciseId).map { it.toEntry().record }
        val thisSession = workoutDao.workingSetsForExerciseInSession(sessionId, exerciseId)
            .map { set ->
                ExerciseSetRecord(
                    setId = set.id,
                    sessionId = set.sessionId,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    completedAt = set.completedAt,
                )
            }
        val prior = (finished + thisSession).filter { it.completedAt < completedAt }
        return PersonalRecords.detect(
            candidate = ExerciseSetRecord(
                setId = "",
                sessionId = "",
                weightKg = weightKg,
                reps = reps,
                completedAt = completedAt,
            ),
            priorHistory = prior,
        )
    }

    /**
     * Every working set of each exercise in [sessionId], logged in any *other* session.
     *
     * Excluded by session id rather than by timestamp: the session is finished by the time
     * this runs, so its own sets are in the finished-history query, and a set cannot be part
     * of the history it is judged against.
     */
    suspend fun historyBefore(sessionId: String, exerciseIds: Collection<String>): Map<String, List<ExerciseSetRecord>> =
        exerciseIds.distinct().associateWith { exerciseId ->
            workoutDao.finishedWorkingSets(exerciseId)
                .asSequence()
                .filter { it.sessionId != sessionId }
                .map { it.toEntry().record }
                .toList()
        }

    /** The outcome of logging one set: what was written, and what it beat. */
    data class LoggedSet(
        val setId: String,
        val records: Set<PersonalRecordKind>,
    )

    private fun ExerciseSetRow.toEntry(): ExerciseSetEntry = ExerciseSetEntry(
        record = ExerciseSetRecord(
            setId = setId,
            sessionId = sessionId,
            weightKg = weightKg,
            reps = reps,
            completedAt = completedAt,
        ),
        sessionName = sessionName,
        sessionPerformedAtMs = sessionDate,
    )

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
