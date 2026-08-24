package com.sinura.personaltrainer.data.repository

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toSummary
import com.sinura.personaltrainer.data.local.dao.ExerciseSetRow
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseHistoryBuilder
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.ExerciseSetEntry
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.FinishedSessionEdits
import com.sinura.personaltrainer.domain.IncrementTable
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.PersonalRecords
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.ProgressionBasis
import com.sinura.personaltrainer.domain.ProgressionCalculator
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RepeatSessionPlan
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.LighterWeekModifier
import com.sinura.personaltrainer.domain.RpeModifier
import com.sinura.personaltrainer.domain.SessionActivity
import com.sinura.personaltrainer.domain.SessionEditRules
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkingSetCandidate
import com.sinura.personaltrainer.domain.WorkoutSession
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface RepeatOutcome {
    data class Started(val sessionId: String) : RepeatOutcome

    /** A session is already running; the caller offers to resume or discard it explicitly. */
    data class Blocked(val inProgressSessionId: String, val inProgressName: String?) :
        RepeatOutcome

    data class Failed(val message: String) : RepeatOutcome
}

/** Transactional answer to "start this exact thing", never a silent resume. */
sealed interface StartSessionOutcome {
    data class Started(val session: WorkoutSession) : StartSessionOutcome
    data class Blocked(val inProgress: WorkoutSession) : StartSessionOutcome
}

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

    /** Live set counters for the session bar; degrades to zeroes rather than throwing. */
    fun observeSessionActivity(sessionId: String): Flow<SessionActivity> =
        workoutDao.observeSessionActivity(sessionId)
            .map { SessionActivity(it.totalSets, it.workingSets, it.lastCompletedAt) }
            .orLogAndFallback("session activity", SessionActivity(0, 0, null))

    suspend fun getSession(id: String): WorkoutSession? = workoutDao.getSession(id)?.toDomain()

    suspend fun startRoutine(routine: Routine): WorkoutSession =
        when (val outcome = startRoutineSafely(routine)) {
            is StartSessionOutcome.Started -> outcome.session
            is StartSessionOutcome.Blocked -> outcome.inProgress
        }

    suspend fun startRoutineSafely(routine: Routine): StartSessionOutcome {
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
        return materializeStart(insertSessionIfIdle(session, exercises))
    }

    private suspend fun materializeStart(result: SessionInsert): StartSessionOutcome {
        val loaded = workoutDao.getSession(result.sessionId)?.toDomain()
            ?: error("Could not start that workout.")
        return if (result.inserted) {
            StartSessionOutcome.Started(loaded)
        } else {
            StartSessionOutcome.Blocked(loaded)
        }
    }

    suspend fun startFreeWorkout(focusTitle: String? = null): WorkoutSession =
        when (val outcome = startFreeWorkoutSafely(focusTitle)) {
            is StartSessionOutcome.Started -> outcome.session
            is StartSessionOutcome.Blocked -> outcome.inProgress
        }

    suspend fun startFreeWorkoutSafely(focusTitle: String? = null): StartSessionOutcome {
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
        return materializeStart(insertSessionIfIdle(session, emptyList()))
    }

    /**
     * Starts a new session shaped like a finished one.
     *
     * Routed through the same single-in-progress transaction as every other start, and it
     * reports [RepeatOutcome.Blocked] rather than quietly handing back the session that is
     * already running — silently resuming a different workout than the one tapped is the
     * behaviour this app is trying to get rid of.
     */
    suspend fun repeatSession(sourceSessionId: String): RepeatOutcome {
        val source = workoutDao.getSession(sourceSessionId)?.toDomain()
            ?: return RepeatOutcome.Failed("That session is no longer available.")
        if (!source.isFinished) {
            return RepeatOutcome.Failed("That session is still in progress.")
        }

        val now = System.currentTimeMillis()
        val newId = UUID.randomUUID().toString()
        val session = WorkoutSessionEntity(
            id = newId,
            // Never carry a dangling foreign key: the routine may have been deleted since.
            routineId = source.routineId?.takeIf { routineExists(it) },
            routineName = source.routineName,
            date = now,
            notes = "",
            durationMinutes = 0,
            startedAt = now,
            finishedAt = null,
        )
        val exercises = RepeatSessionPlan.from(source).mapIndexed { index, item ->
            SessionExerciseEntity(
                id = UUID.randomUUID().toString(),
                sessionId = newId,
                exerciseId = item.exerciseId,
                sortOrder = index,
                targetSets = item.targetSets,
                targetReps = item.targetReps,
                // The progression hint owns weight; a copied one would only contradict it.
                targetWeightKg = null,
                restSeconds = item.restSeconds,
            )
        }

        val inserted = insertSessionIfIdle(session, exercises)
        if (!inserted.inserted) {
            val running = workoutDao.getSessionRow(inserted.sessionId)
            return RepeatOutcome.Blocked(inserted.sessionId, running?.routineName)
        }
        return RepeatOutcome.Started(newId)
    }

    private suspend fun routineExists(routineId: String): Boolean =
        database.routineDao().getById(routineId) != null

    private suspend fun insertSessionIfIdle(
        session: WorkoutSessionEntity,
        exercises: List<SessionExerciseEntity>,
    ): SessionInsert {
        return database.withTransaction {
            workoutDao.getInProgressSession()?.id?.let {
                return@withTransaction SessionInsert(it, inserted = false)
            }
            workoutDao.upsertSession(session)
            if (exercises.isNotEmpty()) {
                workoutDao.insertSessionExercises(exercises)
            }
            SessionInsert(session.id, inserted = true)
        }
    }

    private data class SessionInsert(val sessionId: String, val inserted: Boolean)

    /**
     * Adds a lift to a live session with targets suited to it.
     *
     * The defaults are gone on purpose. `targetSets = 3, targetReps = 5` as parameter defaults
     * meant every caller that omitted them silently got a barbell scheme, including the ones
     * adding a cable fly — and a default is invisible at the call site, so nobody saw it happen.
     * The caller now names what it wants, and [AddDefaults] is where "what it wants" is decided.
     */
    suspend fun addExerciseToSession(
        sessionId: String,
        exercise: Exercise,
        targetSets: Int,
        targetReps: Int,
        targetWeightKg: Double?,
        restSeconds: Int,
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

    /**
     * Takes a lift out of a live session, provided nothing has been logged against it.
     *
     * This method existed with no guards and no callers at all — a one-liner that would happily
     * delete a lift out from under sets that were already recorded against it. The guards are
     * in [SessionEditRules] so they are testable, and the whole thing runs in a transaction so
     * the check and the delete cannot be separated by a set landing between them.
     */
    suspend fun removeExerciseFromSession(sessionId: String, itemId: String) {
        database.withTransaction {
            val current = workoutDao.getSession(sessionId) ?: error(SessionEditRules.ITEM_MISSING)
            val item = current.exercises.firstOrNull { it.item.id == itemId }
            val refusal = SessionEditRules.refusalForRemove(
                sessionFinished = current.session.finishedAt != null,
                itemExists = item != null,
                loggedSetCount = current.sets.count { it.set.exerciseId == item?.item?.exerciseId },
            )
            if (refusal != null) error(refusal)
            workoutDao.deleteSessionExercise(itemId)
        }
    }

    /**
     * Exchanges one lift for another in the same position.
     *
     * The plan travels and the log does not: sortOrder, target sets, target reps and rest all
     * carry over so the session keeps its shape, while `targetWeightKg` is dropped because a
     * weight chosen for a different lift is meaningless on this one — the progression prefill
     * will suggest a real number instead.
     */
    suspend fun swapExerciseInSession(sessionId: String, itemId: String, replacement: Exercise) {
        database.withTransaction {
            val current = workoutDao.getSession(sessionId) ?: error(SessionEditRules.ITEM_MISSING)
            val item = current.exercises.firstOrNull { it.item.id == itemId }
            val refusal = SessionEditRules.refusalForSwap(
                sessionFinished = current.session.finishedAt != null,
                itemExists = item != null,
                loggedSetCount = current.sets.count { it.set.exerciseId == item?.item?.exerciseId },
                replacementAlreadyPresent = current.exercises.any {
                    it.item.exerciseId == replacement.id
                },
            )
            if (refusal != null) error(refusal)
            val existing = item!!.item
            workoutDao.deleteSessionExercise(itemId)
            workoutDao.upsertSessionExercise(
                SessionExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    exerciseId = replacement.id,
                    sortOrder = existing.sortOrder,
                    targetSets = existing.targetSets,
                    targetReps = existing.targetReps,
                    targetWeightKg = null,
                    restSeconds = existing.restSeconds,
                ),
            )
        }
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
        val violation = SetLogRules.validate(weightKg, reps, isWarmup, loadTypeOf(current, exerciseId))
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
        // Deliberately no finished-guard: correcting a mistyped weight in last week's
        // session is the point. The copy below touches neither completedAt nor setNumber,
        // so the set keeps the day it happened on and its place in the exercise — which is
        // what stops an edit from re-dating a personal record or heating the wrong week.
        if (reps < 1) error("Reps must be at least 1.")
        val violation = SetLogRules.validate(
            weightKg,
            reps,
            isWarmup,
            loadTypeOf(workoutDao.getSession(current.sessionId), current.exerciseId),
        )
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

    /**
     * Deletes a set and renumbers the ones after it, returning what was removed so the
     * caller can offer an undo. Deleting is now immediate rather than dialog-guarded: a
     * confirm on every delete taxes the common case to prevent the rare one, and an undo
     * costs nothing until it is needed.
     */
    suspend fun deleteSet(setId: String): DeletedSet? {
        val deleted = workoutDao.getSet(setId) ?: return null
        workoutDao.deleteSet(setId)
        renumber(deleted.sessionId, deleted.exerciseId)
        return DeletedSet(
            setId = deleted.id,
            sessionId = deleted.sessionId,
            exerciseId = deleted.exerciseId,
            setNumber = deleted.setNumber,
            weightKg = deleted.weightKg,
            reps = deleted.reps,
            rpe = deleted.rpe,
            isWarmup = deleted.isWarmup,
            completedAt = deleted.completedAt,
        )
    }

    /**
     * Puts a deleted set back with its original id and timestamp, then renumbers.
     *
     * Restoring the original `completedAt` is what makes undo a true reversal: a set
     * re-inserted with a fresh timestamp would land on today's body map and could re-date a
     * record. No-ops if the session has since gone.
     */
    suspend fun restoreSet(set: DeletedSet) {
        if (workoutDao.getSessionRow(set.sessionId) == null) return
        workoutDao.insertSet(
            SetLogEntity(
                id = set.setId,
                sessionId = set.sessionId,
                exerciseId = set.exerciseId,
                setNumber = set.setNumber,
                weightKg = set.weightKg,
                reps = set.reps,
                rpe = set.rpe,
                isWarmup = set.isWarmup,
                completedAt = set.completedAt,
            ),
        )
        renumber(set.sessionId, set.exerciseId)
    }

    private suspend fun renumber(sessionId: String, exerciseId: String) {
        workoutDao.setsForExercise(sessionId, exerciseId)
            .forEachIndexed { index, set ->
                val nextNumber = index + 1
                if (set.setNumber != nextNumber) {
                    workoutDao.updateSet(set.copy(setNumber = nextNumber))
                }
            }
    }

    /**
     * Adds a set to a session that is already finished.
     *
     * Deliberately not [logSet]: the live-logging path detects personal records and drives
     * the rest timer, neither of which belongs to an edit made days later. The timestamp
     * comes from [FinishedSessionEdits] so the set lands inside the session's own lifetime.
     */
    suspend fun addSetToFinishedSession(
        sessionId: String,
        exerciseId: String,
        weightKg: Double,
        reps: Int,
        rpe: Int?,
        isWarmup: Boolean,
    ) {
        val current = workoutDao.getSession(sessionId)
            ?: error("This workout is no longer available.")
        val session = current.session
        val finishedAt = session.finishedAt ?: error("This workout is still in progress.")
        if (reps < 1) error("Reps must be at least 1.")
        val violation = SetLogRules.validate(weightKg, reps, isWarmup, loadTypeOf(current, exerciseId))
        if (violation != null) error(violation)
        val safeWeight = if (weightKg.isFinite()) weightKg.coerceAtLeast(0.0) else 0.0
        val nextNumber = current.sets.count { it.set.exerciseId == exerciseId } + 1
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
                completedAt = FinishedSessionEdits.timestampForAddedSet(
                    startedAt = session.startedAt,
                    finishedAt = finishedAt,
                    lastCompletedAt = current.sets.maxOfOrNull { it.set.completedAt },
                ),
            ),
        )
    }

    /**
     * Deletes a finished session and everything under it.
     *
     * Separate from [discardSession] on purpose: discard belongs to the live-workout use
     * case and is guarded by the single-caller rule, while this is a deliberate act on
     * history. The check keeps the two from overlapping.
     */
    suspend fun deleteFinishedSession(sessionId: String) {
        val current = workoutDao.getSessionRow(sessionId) ?: return
        check(current.finishedAt != null) {
            "Only finished sessions can be deleted; discard owns in-progress."
        }
        workoutDao.deleteSession(sessionId)
    }

    /**
     * Writes `notes` and nothing else, on finished and in-progress sessions alike.
     *
     * Notes carry no timestamp, record or heat semantics, so unlike the set fields there is
     * nothing downstream for a late edit to disturb — and a typo in what you wrote about a
     * session is the same class of permanent annoyance the set edits exist to fix.
     */
    suspend fun updateSessionNotes(sessionId: String, notes: String) {
        val current = workoutDao.getSession(sessionId)?.session ?: return
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

    /**
     * How a lift in this session is loaded, for the zero-weight rule.
     *
     * Read off the session relation the caller already loaded rather than a fresh query: the
     * lift is right there, and a second round trip to learn something already in hand is how a
     * validation check becomes a reason not to validate. Null when the session or the lift is
     * gone, which [SetLogRules] treats as externally loaded — the stricter reading.
     */
    private fun loadTypeOf(session: SessionWithDetails?, exerciseId: String): LoadType? =
        session?.exercises
            ?.firstOrNull { it.item.exerciseId == exerciseId }
            ?.let { LoadType.fromStorage(it.exercise.loadType) }

    /**
     * How a lift is measured, read from the library.
     *
     * Needed by everything that summarises a lift's history: reps for a push-up, kilograms for
     * a bench. A missing row falls back to loaded, the safer wrong answer — see [LoadClass.of].
     */
    private suspend fun loadClassOf(exerciseId: String): LoadClass =
        LoadClass.of(
            database.exerciseDao().getById(exerciseId)?.loadType?.let(LoadType::fromStorage),
        )

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
        loadType: LoadType?,
        unit: WeightUnit,
        lighterWeek: Boolean = false,
    ): ProgressionHint? {
        val topSet = topSetOfLastSession(exerciseId, excludeSessionId) ?: return null
        val resolvedTarget = targetReps.takeIf { it > 0 }
            ?: workoutDao.lastTargetReps(exerciseId)
            ?: topSet.reps
        val hint = ProgressionCalculator.hint(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            lastWeightKg = topSet.weightKg,
            lastWorkingReps = topSet.reps,
            targetReps = resolvedTarget,
            // The lift decides the size of the jump and the unit decides its shape. An unknown
            // load type — a custom, or a row from a backup this build predates — is treated as
            // loadable, because refusing to suggest anything is worse than suggesting 2.5 kg.
            stepKg = IncrementTable.stepKg(loadType ?: LoadType.EXTERNAL, unit),
            loadType = loadType,
        )
        // Hitting the target reps at RPE 9 and hitting them at RPE 6 are the same event to the
        // calculator, and only one of them means "ready for more".
        val afterRpe = RpeModifier.apply(hint, recentTopSetRpes(exerciseId, excludeSessionId))
        return LighterWeekModifier.apply(afterRpe, lighterWeek)
    }

    /**
     * The top set's RPE for each of the last few finished sessions containing this lift,
     * newest first. Null entries mean "not recorded", which the rule treats as unknown rather
     * than as easy.
     */
    private suspend fun recentTopSetRpes(
        exerciseId: String,
        excludeSessionId: String,
    ): List<Int?> {
        val sessionIds = workoutDao.lastFinishedSessionIdsWithExercise(
            exerciseId = exerciseId,
            excludeSessionId = excludeSessionId,
            limit = RpeModifier.RPE_HOLD_SESSIONS,
        )
        return sessionIds.map { sessionId ->
            val sets = workoutDao.workingSetsForExerciseInSession(sessionId, exerciseId)
            val top = ProgressionBasis.topWorkingSet(
                sets.map { WorkingSetCandidate(it.weightKg, it.reps, it.completedAt) },
            ) ?: return@map null
            sets.firstOrNull {
                it.weightKg == top.weightKg && it.reps == top.reps && it.completedAt == top.completedAt
            }?.rpe
        }
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
        return ExerciseHistoryBuilder
            .fromEntries(exerciseId, entries, loadClassOf(exerciseId))
            .sessions
            .firstOrNull()
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
            loadClass = loadClassOf(exerciseId),
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

    /** Everything needed to put a deleted set back exactly as it was. */
    data class DeletedSet(
        val setId: String,
        val sessionId: String,
        val exerciseId: String,
        val setNumber: Int,
        val weightKg: Double,
        val reps: Int,
        val rpe: Int?,
        val isWarmup: Boolean,
        val completedAt: Long,
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

    suspend fun readyForProgression(
        routines: List<Routine>,
        unit: WeightUnit,
        lighterWeek: Boolean = false,
    ): List<ProgressionHint> {
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
                        stepKg = IncrementTable.stepKg(item.exercise.loadType, unit),
                        loadType = item.exercise.loadType,
                    )
                    // The RPE rule downgrades a grinding lift to HOLD, which drops it out of
                    // this list automatically — "ready to progress" must not name a lift the
                    // in-workout strip is simultaneously telling you to hold.
                    val adjusted = LighterWeekModifier.apply(
                        RpeModifier.apply(
                            hint,
                            recentTopSetRpes(item.exercise.id, excludeSessionId = ""),
                        ),
                        lighterWeek,
                    )
                    if (adjusted.action == ProgressionAction.INCREASE) {
                        hints += adjusted
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
