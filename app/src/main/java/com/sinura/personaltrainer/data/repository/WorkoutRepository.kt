package com.sinura.personaltrainer.data.repository

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.backup.RestoreJournal
import com.sinura.personaltrainer.data.local.AppRoomDatabase
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toExerciseSetEntry
import com.sinura.personaltrainer.data.mapper.toHistoryStills
import com.sinura.personaltrainer.data.mapper.toRecordSet
import com.sinura.personaltrainer.data.mapper.toSummary
import com.sinura.personaltrainer.data.local.dao.FinishedWorkingSetRow
import com.sinura.personaltrainer.data.local.entity.ExerciseRecordPriorsRow
import com.sinura.personaltrainer.data.local.entity.FinishedWorkGeneration
import com.sinura.personaltrainer.domain.RecordSet
import com.sinura.personaltrainer.data.local.entity.SessionSummaryRow
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseHistoryBuilder
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.ExerciseSetEntry
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.FinishedSessionEdits
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.IncrementTable
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.PersonalRecords
import com.sinura.personaltrainer.domain.RecordsCalculator
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.ProgressionBasis
import com.sinura.personaltrainer.domain.ProgressionCalculator
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RepeatSessionPlan
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.domain.LighterWeekModifier
import com.sinura.personaltrainer.domain.RpeModifier
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.domain.SessionActivity
import com.sinura.personaltrainer.domain.SessionEditRules
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.IdPort
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkingSetCandidate
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.util.IdFactory
import com.sinura.personaltrainer.util.JvmTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

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
    data class Unavailable(val message: String) : StartSessionOutcome
}

class WorkoutRepository(
    private val database: AppRoomDatabase,
    private val workoutDao: WorkoutDao,
    private val dbMaintenance: DbMaintenance? = null,
    /**
     * True while a restore journal is in a phase that is about to replace Room. From
     * `room` on the training data is final and a start is allowed; see
     * [RestoreJournal.blocksStart].
     */
    private val restoreBlocksStart: () -> Boolean = { false },
    /**
     * The clock every write here stamps from, and the source of every id it mints.
     *
     * Defaulted in the data layer because naming [JvmTime] is this layer's job — `domain/`
     * used to carry the same default and so could not be compiled without the Android-backed
     * adapter behind it. Injected rather than called directly so a test can decide what "now"
     * is: fourteen sites in this file reached for `time.nowMillis()` and
     * `UUID.randomUUID()` on their own, which is why nothing about a logged set's stamp or a
     * session's id was reproducible.
     */
    private val time: TimePort = JvmTime,
    private val ids: IdPort = IdFactory.Uuid,
) {
    private suspend fun <T> serialized(block: suspend () -> T): T =
        dbMaintenance?.withMaintenanceLock(block) ?: block()

    private fun refuseIfRestoreOpen(): StartSessionOutcome.Unavailable? =
        if (restoreBlocksStart()) {
            StartSessionOutcome.Unavailable(RestoreJournal.INTERRUPTED)
        } else {
            null
        }

    private suspend fun hasLiveActivity(): Boolean {
        val temper = database as? TemperDatabase ?: return false
        return temper.activityDao().getLive() != null
    }

    private suspend fun refuseIfOtherLive(): StartSessionOutcome.Unavailable? =
        if (hasLiveActivity()) {
            StartSessionOutcome.Unavailable("One live activity at a time.")
        } else {
            null
        }
    fun observeHistoryHealth(): Flow<DataHealth<List<WorkoutSession>>> =
        workoutDao.observeFinishedSessions().map { list -> list.map { it.toDomain() } }
            .observeHealth("workout history")

    fun observeHistory(): Flow<List<WorkoutSession>> =
        observeHistoryHealth().presentValues()

    /**
     * Light history rows. [SessionSummary.localEpochDay] is derived from
     * [SessionSummary.date] in the device default zone at read time. That is
     * the documented leftover until a freeze-legal ADR-011 column exists on
     * `workout_sessions`. Do not "fix" it by switching to [SessionSummary.finishedAt].
     *
     * Gated on [com.sinura.personaltrainer.data.local.entity.FinishedWorkGeneration]:
     * logging a set on an in-progress session must not re-aggregate every
     * finished session.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSessionSummaries(): Flow<List<SessionSummary>> =
        workoutDao.observeFinishedWorkGeneration()
            .distinctUntilChanged()
            .mapLatest {
                val stills = workoutDao.sessionStills().toHistoryStills()
                workoutDao.sessionSummaries().map { row ->
                    row.toDomainSummary(stills[row.id].orEmpty())
                }
            }

    fun observeSessionSummariesHealth(): Flow<DataHealth<List<SessionSummary>>> =
        observeSessionSummaries().observeHealth("workout history")

    fun observeLastLogged(): Flow<Map<String, Long>> =
        workoutDao.observeLastLogged().map { rows ->
            rows.associate { it.exerciseId to it.lastLoggedAt }
        }

    /**
     * Recency for insights: finished sessions only, gated so a live set does
     * not re-aggregate every lift. The picker keeps [observeLastLogged].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFinishedLastLogged(): Flow<Map<String, Long>> =
        workoutDao.observeFinishedWorkGeneration()
            .distinctUntilChanged()
            .mapLatest {
                workoutDao.finishedLastLogged().associate { it.exerciseId to it.lastLoggedAt }
            }
            .distinctUntilChanged()

    fun observeBestWorkingWeights(): Flow<Map<String, Double>> =
        workoutDao.observeBestWorkingWeights().map { rows ->
            rows.associate { it.exerciseId to it.bestKg }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFinishedSince(minDateMs: Long): Flow<List<WorkoutSession>> =
        workoutDao.observeFinishedWorkGeneration()
            .distinctUntilChanged()
            .mapLatest {
                workoutDao.getFinishedSessionsSince(minDateMs).map { it.toDomain() }
            }

    suspend fun sessionsBetween(minDateMs: Long, maxDateMs: Long): List<WorkoutSession> =
        workoutDao.getFinishedSessionsBetween(minDateMs, maxDateMs).map { it.toDomain() }

    /**
     * The finished-work fingerprint as an opaque revision token.
     *
     * Moves when a finished session is added or removed, when a finished working set is
     * logged, deleted or restored, and — through the volume and rep sums — when one is
     * edited, which changes neither a count nor a timestamp. A screen that keys a recompute
     * on this recomputes exactly when finished work changed. History used to key its horizon
     * readout on list size and newest session id, and its past-block reviews on the last
     * weigh-in, so a corrected weight in last week's session never reached either.
     *
     * A string rather than the entity so callers outside the data layer do not learn the
     * fingerprint's shape; equality is the whole contract.
     */
    fun observeFinishedWorkRevision(): Flow<String> =
        workoutDao.observeFinishedWorkGeneration()
            .distinctUntilChanged()
            .map { it.revisionToken() }

    /**
     * Every finished working set with its lift's name and class, for the lifetime records
     * list. Gated on the same fingerprint, so a live set does not re-read the whole log; a
     * renamed lift shows its old name here until finished work next changes, which is the
     * trade the summaries already make.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeRecordSetsHealth(): Flow<DataHealth<List<RecordSet>>> =
        workoutDao.observeFinishedWorkGeneration()
            .distinctUntilChanged()
            .mapLatest {
                workoutDao.finishedWorkingSetRecords().mapNotNull { it.toRecordSet() }
            }
            .observeHealth("standing records")

    private fun FinishedWorkGeneration.revisionToken(): String =
        listOf(
            finishedSessionCount,
            durationSum,
            lastFinishedAt ?: 0L,
            finishedWorkingSetCount,
            lastFinishedSetAt ?: 0L,
            finishedWorkingVolumeKg,
            finishedWorkingRepCount,
        ).joinToString("|")

    fun observeSession(id: String): Flow<WorkoutSession?> =
        workoutDao.observeSession(id).map { it?.toDomain() }
            .observeHealth("the active session")
            .presentValues()

    /**
     * The session row as [DataHealth], so session detail can tell a thrown
     * read from a row that is not there. [observeSession] still swallows
     * [DataHealth.Unavailable] for callers that only want present values.
     */
    fun observeSessionHealth(id: String): Flow<DataHealth<WorkoutSession?>> =
        workoutDao.observeSession(id).map { it?.toDomain() }
            .observeHealth("this session")

    fun observeInProgress(): Flow<WorkoutSession?> =
        workoutDao.observeInProgressSession().map { it?.toSummary() }
            .observeHealth("the in-progress session")
            .presentValues()

    suspend fun getInProgress(): WorkoutSession? =
        workoutDao.getInProgressSession()?.toSummary()

    /** Live set counters for the session bar; degrades to zeroes rather than throwing. */
    fun observeSessionActivity(sessionId: String): Flow<SessionActivity> =
        workoutDao.observeSessionActivity(sessionId)
            .map { SessionActivity(it.totalSets, it.workingSets, it.lastCompletedAt) }
            .observeHealth("session activity")
            .presentValues()

    suspend fun getSession(id: String): WorkoutSession? = workoutDao.getSession(id)?.toDomain()

    suspend fun startRoutine(routine: Routine): WorkoutSession =
        when (val outcome = startRoutineSafely(routine)) {
            is StartSessionOutcome.Started -> outcome.session
            is StartSessionOutcome.Blocked -> outcome.inProgress
            is StartSessionOutcome.Unavailable -> error(outcome.message)
        }

    suspend fun startRoutineSafely(routine: Routine): StartSessionOutcome {
        return try {
            serialized {
            refuseIfRestoreOpen()?.let { return@serialized it }
            refuseIfOtherLive()?.let { return@serialized it }
            val now = time.nowMillis()
            val session = WorkoutSessionEntity(
                id = ids.newId(),
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
                    id = ids.newId(),
                    sessionId = session.id,
                    exerciseId = item.exercise.id,
                    sortOrder = index,
                    targetSets = item.targetSets,
                    targetReps = item.targetReps,
                    targetWeightKg = item.targetWeightKg,
                    restSeconds = item.restSeconds,
                )
            }
            startInserted(session, exercises)
            }
        } catch (thrown: kotlinx.coroutines.CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            AppLog.e(TAG, "startRoutineSafely failed closed", thrown)
            StartSessionOutcome.Unavailable(DataHealthCopy.START_UNAVAILABLE)
        }
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
            is StartSessionOutcome.Unavailable -> error(outcome.message)
        }

    suspend fun startFreeWorkoutSafely(focusTitle: String? = null): StartSessionOutcome {
        return try {
            serialized {
            refuseIfRestoreOpen()?.let { return@serialized it }
            refuseIfOtherLive()?.let { return@serialized it }
            val now = time.nowMillis()
            val focus = focusTitle?.trim().orEmpty()
            val session = WorkoutSessionEntity(
                id = ids.newId(),
                routineId = null,
                routineName = focus.ifBlank { "Free workout" },
                date = now,
                notes = if (focus.isBlank()) "" else "Suggested focus: $focus",
                durationMinutes = 0,
                startedAt = now,
                finishedAt = null,
            )
            startInserted(session, emptyList())
            }
        } catch (thrown: kotlinx.coroutines.CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            AppLog.e(TAG, "startFreeWorkoutSafely failed closed", thrown)
            StartSessionOutcome.Unavailable(DataHealthCopy.START_UNAVAILABLE)
        }
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

        val now = time.nowMillis()
        val newId = ids.newId()
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
                id = ids.newId(),
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

        val inserted = serialized {
            if (restoreBlocksStart()) null
            else insertSessionIfIdle(session, exercises)
        } ?: return RepeatOutcome.Failed(
            if (restoreBlocksStart()) RestoreJournal.INTERRUPTED
            else "One live activity at a time.",
        )
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
    ): SessionInsert? {
        return database.withTransaction {
            workoutDao.getInProgressSession()?.id?.let {
                return@withTransaction SessionInsert(it, inserted = false)
            }
            if (hasLiveActivity()) return@withTransaction null
            workoutDao.upsertSession(session)
            if (exercises.isNotEmpty()) {
                workoutDao.insertSessionExercises(exercises)
            }
            SessionInsert(session.id, inserted = true)
        }
    }

    private suspend fun startInserted(
        session: WorkoutSessionEntity,
        exercises: List<SessionExerciseEntity>,
    ): StartSessionOutcome {
        val insert = insertSessionIfIdle(session, exercises)
            ?: return StartSessionOutcome.Unavailable("One live activity at a time.")
        return materializeStart(insert)
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
        database.withTransaction {
            val current = workoutDao.getSession(sessionId) ?: return@withTransaction
            if (current.session.finishedAt != null) return@withTransaction
            if (current.exercises.any { it.exercise.id == exercise.id }) return@withTransaction
            val nextOrder = workoutDao.maxSessionExerciseOrder(sessionId) + 1
            workoutDao.upsertSessionExercise(
                SessionExerciseEntity(
                    id = ids.newId(),
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
                    id = ids.newId(),
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
        // Count-then-insert must be one Room transaction. Two overlapping logSet calls
        // (or a log overlapping a delete/renumber) used to both read the same count and
        // write the same setNumber — duplicate numbers under concurrency or process death.
        val entity = database.withTransaction {
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
            val completedAt = time.nowMillis()
            val row = SetLogEntity(
                id = ids.newId(),
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = nextNumber,
                weightKg = safeWeight,
                reps = safeReps,
                rpe = rpe,
                isWarmup = isWarmup,
                completedAt = completedAt,
            )
            workoutDao.insertSet(row)
            row
        }
        return LoggedSet(
            setId = entity.id,
            records = if (entity.isWarmup) {
                // A warm-up is preparation, not work. It is excluded from volume, from the
                // heat map and from records, and announcing one as a PR would be a lie.
                emptySet()
            } else {
                recordsBrokenBy(
                    exerciseId = exerciseId,
                    sessionId = sessionId,
                    weightKg = entity.weightKg,
                    reps = entity.reps,
                    completedAt = entity.completedAt,
                    setNumber = entity.setNumber,
                )
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
        // Delete and renumber are one write. A committed delete with a half-applied
        // renumber left a gap that the next logSet would then share a number with.
        return database.withTransaction {
            val deleted = workoutDao.getSet(setId) ?: return@withTransaction null
            workoutDao.deleteSet(setId)
            renumber(deleted.sessionId, deleted.exerciseId)
            DeletedSet(
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
    }

    /**
     * Puts a deleted set back with its original id and timestamp, then renumbers.
     *
     * Restoring the original `completedAt` is what makes undo a true reversal: a set
     * re-inserted with a fresh timestamp would land on today's body map and could re-date a
     * record. No-ops if the session has since gone.
     */
    suspend fun restoreSet(set: DeletedSet) {
        database.withTransaction {
            if (workoutDao.getSessionRow(set.sessionId) == null) return@withTransaction
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
        database.withTransaction {
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
                    id = ids.newId(),
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
    }

    /**
     * Deletes a finished session and everything under it.
     *
     * Separate from [discardSession] on purpose: discard belongs to the live-workout use
     * case and is guarded by the single-caller rule, while this is a deliberate act on
     * history. The check keeps the two from overlapping.
     */
    suspend fun deleteFinishedSession(sessionId: String) {
        val deleted = workoutDao.deleteFinishedSessionRow(sessionId)
        if (deleted > 0) return
        val current = workoutDao.getSessionRow(sessionId) ?: return
        check(current.finishedAt != null) {
            "Only finished sessions can be deleted; discard owns in-progress."
        }
    }

    /**
     * Writes `notes` and nothing else, on finished and in-progress sessions alike.
     *
     * Notes carry no timestamp, record or heat semantics, so unlike the set fields there is
     * nothing downstream for a late edit to disturb — and a typo in what you wrote about a
     * session is the same class of permanent annoyance the set edits exist to fix.
     */
    suspend fun updateSessionNotes(sessionId: String, notes: String) {
        // One column, written where it sits. This read the whole row and wrote it back, so a
        // Finish that landed between the read and the write was undone by a row that still
        // remembered the session as running — see WorkoutDao.updateSessionNotes. A row that is
        // no longer there matches nothing and the statement is a no-op, which is what the
        // early return did.
        workoutDao.updateSessionNotes(id = sessionId, notes = notes.trim())
    }

    suspend fun finishSession(sessionId: String, notes: String) {
        // One targeted UPDATE, for the reason WorkoutDao.updateSessionNotes gives: reading the
        // whole row and writing it all back makes every column a hostage to whatever wrote in
        // between. Only startedAt is read, and it never changes once the session exists.
        val startedAt = workoutDao.sessionStartedAt(sessionId)
            ?: error("This workout is no longer available.")
        val finishedAt = time.nowMillis()
        val duration = TimeUnit.MILLISECONDS.toMinutes(finishedAt - startedAt)
            .toInt()
            .coerceAtLeast(1)
        workoutDao.finishSession(
            id = sessionId,
            notes = notes.trim(),
            durationMinutes = duration,
            finishedAt = finishedAt,
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

    private val loadClassByExercise = mutableMapOf<String, LoadClass>()

    /**
     * How a lift is measured, read from the library.
     *
     * Needed by everything that summarises a lift's history: reps for a push-up, kilograms for
     * a bench. A missing row falls back to loaded, the safer wrong answer — see [LoadClass.of].
     */
    private suspend fun loadClassOf(exerciseId: String): LoadClass {
        loadClassByExercise[exerciseId]?.let { return it }
        val resolved = LoadClass.of(
            database.exerciseDao().getById(exerciseId)?.loadType?.let(LoadType::fromStorage),
        )
        loadClassByExercise[exerciseId] = resolved
        return resolved
    }

    /**
     * Last finished sessions that contain this lift, newest first.
     *
     * One batched read shared by the hint, the RPE window, and last
     * performance so a lift switch is not ten small queries.
     */
    private suspend fun lastFinishedWork(
        exerciseId: String,
        excludeSessionId: String,
        limit: Int = RpeModifier.RPE_HOLD_SESSIONS,
    ): List<List<FinishedWorkingSetRow>> {
        val rows = workoutDao.finishedWorkingSetsForExercises(listOf(exerciseId))
            .filter { it.sessionId != excludeSessionId }
        return rows.groupBy { it.sessionId }
            .entries
            .sortedByDescending { (_, sets) -> sets.first().sessionFinishedAt }
            .take(limit)
            .map { it.value }
    }

    /**
     * Deletes an in-progress session and everything under it.
     *
     * The delete itself is `WHERE finishedAt IS NULL`, so a Finish that
     * commits first leaves history in place. A stale Active Workout
     * screen must not delete logged work. [deleteFinishedSession] is the
     * deliberate act on history, behind its own confirm.
     */
    suspend fun discardSession(sessionId: String) {
        workoutDao.deleteInProgressSession(sessionId)
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
        val sessions = lastFinishedWork(exerciseId, excludeSessionId)
        val lastSessionSets = sessions.firstOrNull() ?: return null
        val loadClass = LoadClass.of(loadType)
        val topSet = ProgressionBasis.topWorkingSet(
            lastSessionSets.map { WorkingSetCandidate(it.weightKg, it.reps, it.completedAt) },
            loadClass.weightMeaning,
        ) ?: return null
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
            displayStep = IncrementTable.displayStep(loadType ?: LoadType.EXTERNAL, unit),
            loadType = loadType,
            unit = unit,
        )
        // Hitting the target reps at RPE 9 and hitting them at RPE 6 are the same event to the
        // calculator, and only one of them means "ready for more".
        val afterRpe = RpeModifier.apply(
            hint,
            sessions.map { sets -> rpeOfTopSet(sets, loadClass) },
        )
        return LighterWeekModifier.apply(afterRpe, lighterWeek)
    }

    /**
     * Every finished working set of one exercise, as the history builder wants it.
     *
     * Degrades to an empty history rather than throwing into the collector, the same way the
     * other observed reads do.
     */
    fun observeExerciseSets(exerciseId: String): Flow<List<ExerciseSetEntry>> =
        workoutDao.observeFinishedWorkingSets(exerciseId)
            .map { rows -> rows.map { row -> row.toExerciseSetEntry(kind = HistoryKind.WORKOUT) } }
            .observeHealth("the history for this exercise")
            .presentValues()

    /**
     * What this lift looked like the last time it was trained, for the values shown beside the
     * inputs while logging. Excludes the session being logged right now.
     */
    suspend fun lastPerformance(
        exerciseId: String,
        excludeSessionId: String = "",
    ): ExerciseSessionSummary? {
        val lastSession = lastFinishedWork(exerciseId, excludeSessionId, limit = 1)
            .firstOrNull()
            ?: return null
        val entries = lastSession.map { set ->
            ExerciseSetEntry(
                record = ExerciseSetRecord(
                    setId = set.setId,
                    sessionId = set.sessionId,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    completedAt = set.completedAt,
                ),
                sessionName = set.sessionName,
                sessionPerformedAtMs = set.sessionDate,
            )
        }
        if (entries.isEmpty()) return null
        return ExerciseHistoryBuilder
            .fromEntries(exerciseId, entries, loadClassOf(exerciseId), time)
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
        setNumber: Int,
    ): Set<PersonalRecordKind> {
        val row = workoutDao.recordPriorsBefore(
            exerciseId = exerciseId,
            sessionId = sessionId,
            weightKg = weightKg,
            completedAt = completedAt,
            setNumber = setNumber,
        )
        return RecordsCalculator.detect(
            candidate = ExerciseSetRecord(
                setId = "",
                sessionId = "",
                weightKg = weightKg,
                reps = reps,
                completedAt = completedAt,
            ),
            priors = row.toPriors(),
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
    suspend fun historyBefore(sessionId: String, exerciseIds: Collection<String>): Map<String, List<ExerciseSetRecord>> {
        val ids = exerciseIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        val rows = workoutDao.finishedWorkingSetsForExercises(ids)
        return ids.associateWith { exerciseId ->
            rows.asSequence()
                .filter { it.exerciseId == exerciseId && it.sessionId != sessionId }
                .map { row ->
                    ExerciseSetRecord(
                        setId = row.setId,
                        sessionId = row.sessionId,
                        weightKg = row.weightKg,
                        reps = row.reps,
                        completedAt = row.completedAt,
                    )
                }
                .toList()
        }
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

    suspend fun readyForProgression(
        routines: List<Routine>,
        unit: WeightUnit,
        lighterWeek: Boolean = false,
    ): List<ProgressionHint> {
        val items = linkedMapOf<String, RoutineExercise>()
        routines.forEach { routine ->
            routine.exercises.forEach { item ->
                items.putIfAbsent(item.exercise.id, item)
            }
        }
        if (items.isEmpty()) return emptyList()
        val rows = workoutDao.finishedWorkingSetsForExercises(items.keys.toList())
        val byExercise = rows.groupBy { it.exerciseId }
        val hints = mutableListOf<ProgressionHint>()
        items.values.forEach { item ->
            val sessionsNewestFirst = byExercise[item.exercise.id]
                .orEmpty()
                .groupBy { it.sessionId }
                .entries
                .sortedByDescending { (_, sets) -> sets.first().sessionFinishedAt }
            if (sessionsNewestFirst.isEmpty()) return@forEach
            val loadClass = LoadClass.of(item.exercise.loadType)
            val lastSessionSets = sessionsNewestFirst.first().value
            val topSet = ProgressionBasis.topWorkingSet(
                lastSessionSets.map { WorkingSetCandidate(it.weightKg, it.reps, it.completedAt) },
                loadClass.weightMeaning,
            ) ?: return@forEach
            val hint = ProgressionCalculator.hint(
                exerciseId = item.exercise.id,
                exerciseName = item.exercise.name,
                lastWeightKg = topSet.weightKg,
                lastWorkingReps = topSet.reps,
                targetReps = item.targetReps,
                displayStep = IncrementTable.displayStep(item.exercise.loadType, unit),
                loadType = item.exercise.loadType,
                unit = unit,
            )
            val recentRpes = sessionsNewestFirst.take(RpeModifier.RPE_HOLD_SESSIONS).map { (_, sets) ->
                rpeOfTopSet(sets, loadClass)
            }
            // The RPE rule downgrades a grinding lift to HOLD, which drops it out of
            // this list automatically — "ready to progress" must not name a lift the
            // in-workout strip is simultaneously telling you to hold.
            val adjusted = LighterWeekModifier.apply(
                RpeModifier.apply(hint, recentRpes),
                lighterWeek,
            )
            if (adjusted.action == ProgressionAction.INCREASE) {
                hints += adjusted
            }
        }
        return hints
    }

    private fun rpeOfTopSet(
        sets: List<FinishedWorkingSetRow>,
        loadClass: LoadClass,
    ): Int? {
        val top = ProgressionBasis.topWorkingSet(
            sets.map { WorkingSetCandidate(it.weightKg, it.reps, it.completedAt) },
            loadClass.weightMeaning,
        ) ?: return null
        return sets.firstOrNull {
            it.weightKg == top.weightKg && it.reps == top.reps && it.completedAt == top.completedAt
        }?.rpe
    }

    private fun SessionSummaryRow.toDomainSummary(
        stills: List<Exercise> = emptyList(),
    ): SessionSummary = SessionSummary(
        id = id,
        routineId = routineId,
        routineName = routineName,
        date = date,
        finishedAt = finishedAt,
        durationMinutes = durationMinutes,
        workingSets = workingSets,
        volumeKg = volumeKg,
        localEpochDay = com.sinura.personaltrainer.util.JvmTime.civilDate(date).epochDay,
        stills = stills,
    )

    private fun ExerciseRecordPriorsRow.toPriors(): PersonalRecords.RecordPriors =
        PersonalRecords.RecordPriors(
            priorSetCount = priorSetCount,
            maxWeightKg = maxWeightKg,
            maxReps = maxReps,
            maxRepsAtCandidateWeight = maxRepsAtWeight,
            maxEstimatedOneRepMaxKg = maxEstimatedOneRepMaxKg,
            maxRepsAtEqualOrMoreAssistance = maxRepsAtEqualOrMoreAssistance,
        )
}

private const val TAG = "PT/WorkoutRepository"
