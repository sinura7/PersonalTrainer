package com.sinura.personaltrainer.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.mapper.toDomain
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
import com.sinura.personaltrainer.domain.IdPort
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.TimePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ActivityRepository(
    private val database: TemperDatabase,
    private val dao: ActivityDao = database.activityDao(),
    private val dbMaintenance: DbMaintenance? = null,
) {
    private suspend fun <T> serialized(block: suspend () -> T): T =
        dbMaintenance?.withMaintenanceLock(block) ?: block()

    fun observeLive(): Flow<ActivitySession?> =
        dao.observeLive().map { row ->
            row?.let { dao.getSessionGraph(it.id)?.toDomain() }
        }

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
        database.withTransaction {
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
                                database.plannerDao().getOccurrence(occId)?.let { row ->
                                    database.plannerDao().upsertOccurrence(
                                        row.copy(
                                            status = "DONE",
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
        database.withTransaction {
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
                database.plannerDao().getOccurrence(occId)?.let { row ->
                    database.plannerDao().upsertOccurrence(
                        row.copy(
                            status = "DONE",
                            completedActivityId = completed.id,
                            updatedAtMs = clock.nowMillis(),
                        ),
                    )
                }
            }
            ActivityWrite.Accepted(completed)
        }
    }

    suspend fun templates(): List<ActivityTemplate> =
        dao.getAllTemplateGraphs().map { it.toDomain() }

    suspend fun saveTemplate(template: ActivityTemplate, clock: TimePort) {
        database.withTransaction {
            dao.deleteTemplate(template.id)
            ActivityBackupIo.insertTemplate(dao, template, clock.nowMillis())
        }
    }
}
