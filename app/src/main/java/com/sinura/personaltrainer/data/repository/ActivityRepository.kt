package com.sinura.personaltrainer.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityQueries
import com.sinura.personaltrainer.domain.ActivityRules
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.ActivityTemplate
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.IdPort
import com.sinura.personaltrainer.domain.TimePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ActivityRepository(
    private val database: TemperDatabase,
    private val dao: ActivityDao = database.activityDao(),
) {
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

    suspend fun onLocalDate(localEpochDay: Long): List<ActivitySession> =
        ActivityQueries.onLocalDate(all(), localEpochDay)

    suspend fun completedOn(localEpochDay: Long): List<ActivitySession> =
        ActivityQueries.completedOn(all(), localEpochDay)

    /**
     * The only durable write. Domain rules run inside the same
     * transaction as the insert so a second live start cannot land.
     */
    suspend fun confirm(
        draft: ActivityDraft,
        now: CapturedCivilTime,
        ids: IdPort,
        clock: TimePort,
    ): ActivityWrite {
        return database.withTransaction {
            val existing = dao.getAllGraphs().map { it.toDomain() }
            when (val write = ActivityRules.confirm(draft, existing, now, ids, clock)) {
                is ActivityWrite.Rejected -> write
                is ActivityWrite.Accepted -> {
                    try {
                        ActivityBackupIo.insertSession(dao, write.session)
                        write
                    } catch (_: SQLiteConstraintException) {
                        ActivityWrite.Rejected("One live activity at a time.")
                    }
                }
            }
        }
    }

    suspend fun discard(sessionId: String) {
        database.withTransaction {
            val row = dao.getSessionRow(sessionId) ?: return@withTransaction
            if (row.status == "ACTIVE") dao.deleteSession(sessionId)
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
