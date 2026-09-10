package com.sinura.personaltrainer.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.sinura.personaltrainer.data.backup.RestoreJournal
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toExerciseSetEntry
import com.sinura.personaltrainer.data.mapper.toRecordSet
import com.sinura.personaltrainer.data.mapper.toSummary
import com.sinura.personaltrainer.domain.ActivityBlock
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityQueries
import com.sinura.personaltrainer.domain.ActivityRules
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityTemplate
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.ExerciseSetEntry
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.IdPort
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.RecordSet
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ActivityRepository(
    private val database: TemperDatabase,
    private val dao: ActivityDao = database.activityDao(),
    private val dbMaintenance: DbMaintenance? = null,
    /**
     * Called with the occurrence id after a completion commits, so its reminders can be
     * cancelled and anything already in the shade dismissed.
     *
     * A callback rather than a [PlannerRepository] handle, for two reasons. The status write
     * has to stay inside the activity transaction — a completed session with a still-planned
     * day is the state this whole packet is about — while cancelling reminders reaches
     * WorkManager and the notification manager, which must not happen inside one. And the
     * dependency would otherwise run backwards through the container. Defaults to doing
     * nothing so a test can construct this repository with a database and no scheduler.
     */
    private val onOccurrenceCompleted: suspend (String) -> Unit = {},
    /**
     * Mirrors WorkoutRepository: a confirm that lands while a restore journal is in
     * `staged` or `wiping` would write into tables about to be replaced. Recovery on the
     * next launch resolves such a journal before any screen can reach here, so this is
     * the same belt-and-braces refusal strength starts already had.
     */
    private val restoreBlocksStart: () -> Boolean = { false },
) {
    private suspend fun <T> serialized(block: suspend () -> T): T =
        dbMaintenance?.withMaintenanceLock(block) ?: block()

    /**
     * The live row as [DataHealth], the contract the strength paths already have: a read
     * fault is logged and reported, never thrown into a collector. [observeLive] is the
     * values-only view of the same flow, so a failure stops it rather than crashing whoever
     * combined it.
     */
    fun observeLiveHealth(): Flow<DataHealth<ActivitySession?>> =
        dao.observeLive()
            .map { row -> row?.let { dao.getSessionGraph(it.id)?.toDomain() } }
            .observeHealth("the live activity")

    fun observeLive(): Flow<ActivitySession?> = observeLiveHealth().presentValues()

    suspend fun getLive(): ActivitySession? =
        dao.getLive()?.let { dao.getSessionGraph(it.id)?.toDomain() }

    suspend fun get(id: String): ActivitySession? =
        dao.getSessionGraph(id)?.toDomain()

    suspend fun all(): List<ActivitySession> =
        dao.getAllGraphs().map { it.toDomain() }

    fun observeCompleted(): Flow<List<ActivitySession>> =
        dao.observeCompletedGraphs().map { rows -> rows.map { it.toDomain() } }

    fun observeCompletedSummaries(): Flow<List<SessionSummary>> =
        dao.observeCompletedSummaries().map { rows -> rows.map { it.toSummary() } }

    fun observeCompletedSummariesHealth(): Flow<DataHealth<List<SessionSummary>>> =
        observeCompletedSummaries().observeHealth("activity history")

    /** Every completed strength set with the block's snapshot of its lift, for lifetime records. */
    fun observeRecordSetsHealth(): Flow<DataHealth<List<RecordSet>>> =
        dao.observeCompletedStrengthSetRecords()
            .map { rows -> rows.mapNotNull { it.toRecordSet() } }
            .observeHealth("activity records")

    /**
     * Finished working sets of one lift from the activity store. Exercise detail
     * unions this with the strength session query through
     * [CompletedTrainingRepository.observeExerciseSets].
     */
    fun observeExerciseSets(exerciseId: String): Flow<List<ExerciseSetEntry>> =
        dao.observeFinishedWorkingSets(exerciseId)
            .map { rows -> rows.map { row -> row.toExerciseSetEntry(kind = HistoryKind.ACTIVITY) } }
            .observeHealth("the activity history for this exercise")
            .presentValues()

    fun observeCompletedGraphsSince(minPerformedAtMs: Long): Flow<List<ActivitySession>> =
        dao.observeCompletedGraphsSince(minPerformedAtMs).map { rows -> rows.map { it.toDomain() } }

    suspend fun onLocalDate(localEpochDay: Long): List<ActivitySession> =
        dao.graphsOnLocalDate(localEpochDay).map { it.toDomain() }

    suspend fun completedOn(localEpochDay: Long): List<ActivitySession> =
        ActivityQueries.completedOn(onLocalDate(localEpochDay), localEpochDay)

    /**
     * The only durable write. Domain rules run inside the same
     * transaction as the insert so a second live start cannot land.
     * The maintenance lock is the same lock strength starts take, so a
     * cardio confirm and a routine start cannot both pass the idle check.
     */
    suspend fun confirm(
        draft: ActivityDraft,
        now: CapturedCivilTime,
        ids: IdPort,
        clock: TimePort,
    ): ActivityWrite = serialized {
        if (restoreBlocksStart()) return@serialized ActivityWrite.Rejected(RestoreJournal.INTERRUPTED)
        var completedOccurrenceId: String? = null
        val result = database.withTransaction {
            if ((draft.origin == ActivityOrigin.LIVE || draft.status == ActivityStatus.ACTIVE) &&
                database.workoutDao().getInProgressSession() != null
            ) {
                return@withTransaction ActivityWrite.Rejected("One live activity at a time.")
            }
            val existing = listOfNotNull(getLive())
            when (val write = ActivityRules.confirm(draft, existing, now, ids, clock)) {
                is ActivityWrite.Rejected -> write
                is ActivityWrite.Accepted -> {
                    try {
                        ActivityBackupIo.insertSession(dao, write.session)
                        if (write.session.status == ActivityStatus.COMPLETED) {
                            write.session.occurrenceId?.let { occId ->
                                completedOccurrenceId = occId
                                database.plannerDao().getOccurrence(occId)?.let { row ->
                                    database.plannerDao().upsertOccurrence(
                                        row.copy(
                                            status = OccurrenceStatus.DONE.name,
                                            completedActivityId = write.session.id,
                                            updatedAtMs = clock.nowMillis(),
                                        ),
                                    )
                                }
                            }
                        }
                        write
                    } catch (_: SQLiteConstraintException) {
                        ActivityWrite.Rejected("One live activity at a time.")
                    }
                }
            }
        }
        // After the commit, never inside it: this reaches WorkManager and the notification
        // manager. Only for a session this call actually completed, so a rejected write cannot
        // silently clear a reminder for a day that is still planned. It fires even when the
        // occurrence row has since been deleted — cancelling a job and dismissing a
        // notification that are not there costs nothing, and a stale notification for a
        // deleted day is exactly what this packet is about.
        afterCommit(completedOccurrenceId)
        result
    }

    /**
     * Runs the reminder cleanup after a commit, and never lets it change the answer.
     *
     * The activity is durable by the time this runs. A throw from the cleanup used to escape
     * the call and reach the screen as "Could not save that session. Try again." — with the
     * composer still holding its plan link, so the retry wrote a second completed activity for
     * the same day; live cardio's retry was told the session was already finished. Nothing is
     * queued: cancellation is idempotent, a delivery whose occurrence is no longer planned is
     * dropped at fire time by ReminderPolicy, and Skip and Move refuse a DONE row. Cancellation
     * of the caller is not a cleanup failure and still propagates.
     */
    private suspend fun afterCommit(occurrenceId: String?) {
        occurrenceId ?: return
        try {
            onOccurrenceCompleted(occurrenceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (thrown: Exception) {
            AppLog.w(TAG, "Reminder cleanup after an activity commit failed; the row is saved", thrown)
        }
    }

    suspend fun discard(sessionId: String) {
        serialized {
            database.withTransaction {
                val row = dao.getSessionRow(sessionId) ?: return@withTransaction
                if (row.status == "ACTIVE") dao.deleteSession(sessionId)
            }
        }
    }

    /**
     * Removes one completed activity and its blocks. The strength store has had this since
     * session repair landed; the activity store did not, which is one row of the
     * convergence matrix. No screen offers it yet — the instrumented screen passes use it
     * to take their fixtures back off a real database — and a planner day that pointed at
     * the row keeps its DONE status, exactly as deleting a finished strength session does.
     */
    suspend fun deleteCompleted(sessionId: String) {
        serialized {
            database.withTransaction {
                val row = dao.getSessionRow(sessionId) ?: return@withTransaction
                if (row.status == "COMPLETED") dao.deleteSession(sessionId)
            }
        }
    }

    /**
     * Completes a live session in place. Used by live cardio: the ACTIVE
     * row keeps its id so process-death recovery can find it, then this
     * writes the final blocks and clears [liveToken].
     */
    suspend fun completeLive(
        sessionId: String,
        now: CapturedCivilTime,
        clock: TimePort,
        blocks: List<ActivityBlock>? = null,
    ): ActivityWrite = serialized {
        var completedOccurrenceId: String? = null
        val result = database.withTransaction {
            val graph = dao.getSessionGraph(sessionId)
                ?: return@withTransaction ActivityWrite.Rejected("That session is gone.")
            val session = graph.toDomain()
            if (session.status != ActivityStatus.ACTIVE) {
                return@withTransaction ActivityWrite.Rejected("That session is already finished.")
            }
            val completed = session.copy(
                status = ActivityStatus.COMPLETED,
                blocks = blocks ?: session.blocks,
                performedEnd = now,
                updatedAtMs = clock.nowMillis(),
                revision = session.revision + 1,
            )
            dao.deleteSession(sessionId)
            ActivityBackupIo.insertSession(dao, completed)
            completed.occurrenceId?.let { occId ->
                completedOccurrenceId = occId
                database.plannerDao().getOccurrence(occId)?.let { row ->
                    database.plannerDao().upsertOccurrence(
                        row.copy(
                            status = OccurrenceStatus.DONE.name,
                            completedActivityId = completed.id,
                            updatedAtMs = clock.nowMillis(),
                        ),
                    )
                }
            }
            ActivityWrite.Accepted(completed)
        }
        afterCommit(completedOccurrenceId)
        result
    }

    suspend fun templates(): List<ActivityTemplate> =
        dao.getAllTemplateGraphs().map { it.toDomain() }

    suspend fun saveTemplate(template: ActivityTemplate, clock: TimePort) {
        database.withTransaction {
            dao.deleteTemplate(template.id)
            ActivityBackupIo.insertTemplate(dao, template, clock.nowMillis())
        }
    }

    private companion object {
        const val TAG = "PT/ActivityRepository"
    }
}
