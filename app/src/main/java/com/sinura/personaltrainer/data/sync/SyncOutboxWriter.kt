package com.sinura.personaltrainer.data.sync

import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.dao.SyncDao
import com.sinura.personaltrainer.data.local.entity.ScheduleOccurrenceEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import com.sinura.personaltrainer.data.local.entity.SyncOutboxEntity
import com.sinura.personaltrainer.domain.SyncEntityType
import com.sinura.personaltrainer.domain.SyncOutboxOperation
import java.util.UUID

/**
 * Enqueues durable sync work. Called after local commits; never throws into UI paths.
 */
class SyncOutboxWriter(
    private val syncDao: SyncDao,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun enqueueCompletedSession(activityDao: ActivityDao, sessionId: String, userId: String) {
        val graph = activityDao.getSessionGraph(sessionId) ?: return
        val session = graph.session
        if (session.status == "ACTIVE") return
        val sessionRow = session.toRemote(userId)
        enqueue(SyncEntityType.ACTIVITY_SESSION, session.id, SyncOutboxOperation.UPSERT, encodeSync(sessionRow))
        val updatedAtMs = session.updatedAtMs
        graph.blocks.forEach { blockGraph ->
            val block = blockGraph.block
            enqueue(
                SyncEntityType.ACTIVITY_BLOCK,
                block.id,
                SyncOutboxOperation.UPSERT,
                encodeSync(block.toRemote(userId, updatedAtMs)),
            )
            blockGraph.strengthSets.forEach { set ->
                enqueue(
                    SyncEntityType.ACTIVITY_STRENGTH_SET,
                    set.id,
                    SyncOutboxOperation.UPSERT,
                    encodeSync(set.toRemote(userId, set.completedAtMs)),
                )
            }
            blockGraph.cardioIntervals.forEach { interval ->
                enqueue(
                    SyncEntityType.ACTIVITY_CARDIO_INTERVAL,
                    interval.id,
                    SyncOutboxOperation.UPSERT,
                    encodeSync(interval.toRemote(userId, updatedAtMs)),
                )
            }
        }
    }

    suspend fun enqueueSessionDelete(
        userId: String,
        sessionId: String,
        revision: Long,
        updatedAtMs: Long,
        deletedAtMs: Long,
    ) {
        val tombstone = RemoteActivitySessionRow(
            id = sessionId,
            userId = userId,
            status = "COMPLETED",
            origin = "",
            source = "",
            title = "",
            notes = "",
            performedStartInstantMs = 0L,
            performedStartZoneId = "UTC",
            performedStartOffsetSeconds = 0,
            performedStartLocalEpochDay = 0L,
            performedEndInstantMs = null,
            performedEndZoneId = null,
            performedEndOffsetSeconds = null,
            performedEndLocalEpochDay = null,
            templateId = null,
            occurrenceId = null,
            createdAtMs = deletedAtMs,
            updatedAtMs = updatedAtMs,
            revision = revision,
            deletedAtMs = deletedAtMs,
        )
        enqueue(
            SyncEntityType.ACTIVITY_SESSION,
            sessionId,
            SyncOutboxOperation.DELETE,
            encodeSync(tombstone),
        )
    }

    suspend fun enqueueScheduleRules(userId: String, rules: List<ScheduleRuleEntity>) {
        rules.forEach { rule ->
            enqueue(
                SyncEntityType.SCHEDULE_RULE,
                rule.id,
                SyncOutboxOperation.UPSERT,
                encodeSync(rule.toRemote(userId)),
            )
        }
    }

    suspend fun enqueueScheduleOccurrences(userId: String, rows: List<ScheduleOccurrenceEntity>) {
        rows.forEach { row ->
            enqueue(
                SyncEntityType.SCHEDULE_OCCURRENCE,
                row.id,
                SyncOutboxOperation.UPSERT,
                encodeSync(row.toRemote(userId)),
            )
        }
    }

    suspend fun enqueueRuleDelete(userId: String, rule: ScheduleRuleEntity, deletedAtMs: Long) {
        enqueue(
            SyncEntityType.SCHEDULE_RULE,
            rule.id,
            SyncOutboxOperation.DELETE,
            encodeSync(rule.toRemote(userId, deletedAtMs = deletedAtMs)),
        )
    }

    suspend fun enqueueOccurrenceDelete(userId: String, row: ScheduleOccurrenceEntity, deletedAtMs: Long) {
        enqueue(
            SyncEntityType.SCHEDULE_OCCURRENCE,
            row.id,
            SyncOutboxOperation.DELETE,
            encodeSync(row.toRemote(userId, deletedAtMs = deletedAtMs)),
        )
    }

    private suspend fun enqueue(
        type: SyncEntityType,
        entityId: String,
        operation: SyncOutboxOperation,
        payloadJson: String,
    ) {
        syncDao.deletePendingForEntity(type.name, entityId)
        syncDao.insertOutbox(
            SyncOutboxEntity(
                id = UUID.randomUUID().toString(),
                entityType = type.name,
                entityId = entityId,
                operation = operation.name,
                payloadJson = payloadJson,
                createdAtMs = nowMillis(),
                attempts = 0,
                lastError = null,
            ),
        )
    }
}
