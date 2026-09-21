package com.sinura.personaltrainer.data.sync

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.dao.BodyweightDao
import com.sinura.personaltrainer.data.local.dao.CatalogDao
import com.sinura.personaltrainer.data.local.dao.ExerciseDao
import com.sinura.personaltrainer.data.local.dao.PlannerDao
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.dao.SyncDao
import com.sinura.personaltrainer.data.local.entity.SyncMetadataEntity
import com.sinura.personaltrainer.data.local.entity.SyncTableCursorEntity
import com.sinura.personaltrainer.domain.SyncChildRow
import com.sinura.personaltrainer.domain.SyncCopy
import com.sinura.personaltrainer.domain.SyncEntityType
import com.sinura.personaltrainer.domain.SyncEntityVersion
import com.sinura.personaltrainer.domain.SyncOutboxOperation
import com.sinura.personaltrainer.domain.SyncRevision
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CancellationException

class SyncEngine(
    private val database: TemperDatabase,
    private val syncDao: SyncDao,
    private val activityDao: ActivityDao,
    private val plannerDao: PlannerDao,
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    private val catalogDao: CatalogDao,
    private val bodyweightDao: BodyweightDao,
    private val remote: SyncRemotePort,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun run(userId: String): Result<Unit> {
        var pushError: Exception? = null
        var pullError: Exception? = null
        try {
            pushOutbox()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.w(TAG, "Sync push failed", error)
            pushError = error
        }
        try {
            pullAll()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.w(TAG, "Sync pull failed", error)
            pullError = error
        }
        val previousSuccess = syncDao.getMetadata()?.lastSuccessAtMs
        return when {
            pushError != null && pullError != null -> {
                val message = listOf(pushError!!.message, pullError!!.message)
                    .filterNot { it.isNullOrBlank() }
                    .joinToString(" ")
                recordFailure(previousSuccess, message.ifBlank { "Sync failed." })
                Result.failure(pushError!!)
            }
            pushError != null -> {
                recordFailure(previousSuccess, pushError!!.message)
                Result.failure(pushError!!)
            }
            pullError != null -> {
                recordFailure(previousSuccess, pullError!!.message)
                Result.failure(pullError!!)
            }
            else -> {
                syncDao.upsertMetadata(
                    SyncMetadataEntity(
                        lastSuccessAtMs = nowMillis(),
                        lastError = null,
                    ),
                )
                Result.success(Unit)
            }
        }
    }

    private suspend fun recordFailure(previousSuccess: Long?, rawMessage: String?) {
        syncDao.upsertMetadata(
            SyncMetadataEntity(
                lastSuccessAtMs = previousSuccess,
                lastError = SyncCopy.ownerFacingError(rawMessage),
            ),
        )
    }

    private suspend fun pushOutbox() {
        while (true) {
            val batch = syncDao.peekOutbox(BATCH_SIZE)
            if (batch.isEmpty()) return
            for (row in batch) {
                val type = SyncEntityType.valueOf(row.entityType)
                val payload = row.payloadJson ?: error("Outbox row ${row.id} missing payload")
                try {
                    when (SyncOutboxOperation.valueOf(row.operation)) {
                        SyncOutboxOperation.UPSERT -> remote.upsert(type, payload)
                        SyncOutboxOperation.DELETE -> remote.tombstone(type, payload)
                    }
                    syncDao.deleteOutbox(row.id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (pushError: Exception) {
                    syncDao.markOutboxAttempt(row.id, row.attempts + 1, pushError.message)
                    throw pushError
                }
            }
        }
    }

    private suspend fun pullAll() {
        pullTable(SyncEntityType.CUSTOM_EXERCISE, ::applyCustomExercise)
        pullTable(SyncEntityType.EXERCISE_MUSCLE, ::applyExerciseMuscle)
        pullTable(SyncEntityType.BODYWEIGHT_ENTRY, ::applyBodyweightEntry)
        pullTable(SyncEntityType.ACTIVITY_SESSION, ::applyActivitySession)
        pullTable(SyncEntityType.ACTIVITY_TEMPLATE, ::applyActivityTemplate)
        pullTable(SyncEntityType.ACTIVITY_BLOCK, ::applyActivityBlock)
        pullTable(SyncEntityType.ACTIVITY_STRENGTH_SET, ::applyStrengthSet)
        pullTable(SyncEntityType.ACTIVITY_CARDIO_INTERVAL, ::applyCardioInterval)
        pullTable(SyncEntityType.SCHEDULE_RULE, ::applyScheduleRule)
        pullTable(SyncEntityType.SCHEDULE_OCCURRENCE, ::applyScheduleOccurrence)
        pullTable(SyncEntityType.ROUTINE, ::applyRoutine)
        pullTable(SyncEntityType.ROUTINE_EXERCISE, ::applyRoutineExercise)
    }

    private suspend fun pullTable(
        type: SyncEntityType,
        apply: suspend (String) -> Long,
    ) {
        val initialCursor = syncDao.getCursor(type.remoteTable)?.lastPulledUpdatedAtMs ?: 0L
        var watermark = initialCursor
        while (true) {
            val rows = remote.pullUpdatedSince(type, watermark)
            if (rows.isEmpty()) break
            for (json in rows) {
                val appliedAt = apply(json)
                if (appliedAt > watermark) watermark = appliedAt
            }
            if (rows.size < SYNC_PULL_PAGE_SIZE) break
        }
        if (watermark > initialCursor) {
            syncDao.upsertCursor(
                SyncTableCursorEntity(tableName = type.remoteTable, lastPulledUpdatedAtMs = watermark),
            )
        }
    }

    private suspend fun hasPendingChild(type: SyncEntityType, entityId: String): Boolean =
        syncDao.hasPendingForEntity(type.name, entityId)

    private suspend fun activityBlockParentExists(remote: RemoteActivityBlockRow): Boolean {
        remote.sessionId?.let { return activityDao.getSessionRow(it) != null }
        remote.templateId?.let { return activityDao.getTemplateRow(it) != null }
        return false
    }

    private suspend fun applyActivitySession(json: String): Long {
        val remote = decodeSync<RemoteActivitySessionRow>(json)
        if (remote.deletedAtMs != null) {
            activityDao.deleteSession(remote.id)
            return remote.updatedAtMs
        }
        if (remote.status == "ACTIVE") return remote.updatedAtMs
        val local = activityDao.getSessionRow(remote.id)
        val localVersion = SyncEntityVersion(
            revision = local?.revision ?: -1L,
            updatedAtMs = local?.updatedAtMs ?: -1L,
        )
        val remoteVersion = SyncEntityVersion(remote.revision, remote.updatedAtMs)
        if (local != null && !SyncRevision.remoteWins(localVersion, remoteVersion)) {
            return remote.updatedAtMs
        }
        database.withTransaction {
            activityDao.deleteSession(remote.id)
            activityDao.insertSession(remote.toEntity())
        }
        return remote.updatedAtMs
    }

    private suspend fun applyActivityBlock(json: String): Long {
        val remote = decodeSync<RemoteActivityBlockRow>(json)
        val queued = hasPendingChild(SyncEntityType.ACTIVITY_BLOCK, remote.id)
        if (!SyncChildRow.remoteAppliesWhenNotLocallyQueued(queued)) {
            return remote.updatedAtMs
        }
        if (remote.deletedAtMs != null) {
            activityDao.deleteBlock(remote.id)
            return remote.updatedAtMs
        }
        if (!activityBlockParentExists(remote)) {
            return remote.updatedAtMs
        }
        activityDao.insertBlock(remote.toEntity())
        return remote.updatedAtMs
    }

    private suspend fun applyStrengthSet(json: String): Long {
        val remote = decodeSync<RemoteStrengthSetRow>(json)
        val queued = hasPendingChild(SyncEntityType.ACTIVITY_STRENGTH_SET, remote.id)
        if (!SyncChildRow.remoteAppliesWhenNotLocallyQueued(queued)) {
            return remote.updatedAtMs
        }
        if (remote.deletedAtMs != null) {
            activityDao.deleteStrengthSet(remote.id)
            return remote.updatedAtMs
        }
        if (activityDao.getBlock(remote.blockId) == null) {
            return remote.updatedAtMs
        }
        activityDao.insertStrengthSets(listOf(remote.toEntity()))
        return remote.updatedAtMs
    }

    private suspend fun applyCardioInterval(json: String): Long {
        val remote = decodeSync<RemoteCardioIntervalRow>(json)
        val queued = hasPendingChild(SyncEntityType.ACTIVITY_CARDIO_INTERVAL, remote.id)
        if (!SyncChildRow.remoteAppliesWhenNotLocallyQueued(queued)) {
            return remote.updatedAtMs
        }
        if (remote.deletedAtMs != null) {
            activityDao.deleteCardioInterval(remote.id)
            return remote.updatedAtMs
        }
        if (activityDao.getBlock(remote.blockId) == null) {
            return remote.updatedAtMs
        }
        activityDao.insertCardioIntervals(listOf(remote.toEntity()))
        return remote.updatedAtMs
    }

    private suspend fun applyScheduleRule(json: String): Long {
        val remote = decodeSync<RemoteScheduleRuleRow>(json)
        if (remote.deletedAtMs != null) {
            plannerDao.deleteRule(remote.id)
            return remote.updatedAtMs
        }
        val local = plannerDao.getRule(remote.id)
        val localVersion = SyncEntityVersion(0L, local?.updatedAtMs ?: -1L)
        val remoteVersion = SyncEntityVersion(remote.revision, remote.updatedAtMs)
        if (local != null && !SyncRevision.remoteWins(localVersion, remoteVersion)) {
            return remote.updatedAtMs
        }
        plannerDao.upsertRule(remote.toEntity())
        return remote.updatedAtMs
    }

    private suspend fun applyScheduleOccurrence(json: String): Long {
        val remote = decodeSync<RemoteScheduleOccurrenceRow>(json)
        if (remote.deletedAtMs != null) {
            plannerDao.deleteOccurrence(remote.id)
            return remote.updatedAtMs
        }
        val local = plannerDao.getOccurrence(remote.id)
        val localVersion = SyncEntityVersion(0L, local?.updatedAtMs ?: -1L)
        val remoteVersion = SyncEntityVersion(remote.revision, remote.updatedAtMs)
        if (local != null && !SyncRevision.remoteWins(localVersion, remoteVersion)) {
            return remote.updatedAtMs
        }
        plannerDao.upsertOccurrence(remote.toEntity())
        return remote.updatedAtMs
    }

    private suspend fun applyActivityTemplate(json: String): Long {
        val remote = decodeSync<RemoteActivityTemplateRow>(json)
        if (remote.deletedAtMs != null) {
            activityDao.deleteTemplate(remote.id)
            return remote.updatedAtMs
        }
        val local = activityDao.getTemplateRow(remote.id)
        val localVersion = SyncEntityVersion(0L, local?.updatedAtMs ?: -1L)
        val remoteVersion = SyncEntityVersion(remote.revision, remote.updatedAtMs)
        if (local != null && !SyncRevision.remoteWins(localVersion, remoteVersion)) {
            return remote.updatedAtMs
        }
        activityDao.upsertTemplate(remote.toEntity())
        return remote.updatedAtMs
    }

    private suspend fun applyRoutine(json: String): Long {
        val remote = decodeSync<RemoteRoutineRow>(json)
        if (remote.deletedAtMs != null) {
            routineDao.deleteRoutine(remote.id)
            return remote.updatedAtMs
        }
        val local = routineDao.getById(remote.id)?.routine
        val localVersion = SyncEntityVersion(0L, local?.updatedAt ?: -1L)
        val remoteVersion = SyncEntityVersion(remote.revision, remote.updatedAtMs)
        if (local != null && !SyncRevision.remoteWins(localVersion, remoteVersion)) {
            return remote.updatedAtMs
        }
        routineDao.upsertRoutine(remote.toEntity())
        return remote.updatedAtMs
    }

    private suspend fun applyRoutineExercise(json: String): Long {
        val remote = decodeSync<RemoteRoutineExerciseRow>(json)
        val queued = hasPendingChild(SyncEntityType.ROUTINE_EXERCISE, remote.id)
        if (!SyncChildRow.remoteAppliesWhenNotLocallyQueued(queued)) {
            return remote.updatedAtMs
        }
        if (remote.deletedAtMs != null) {
            routineDao.deleteRoutineExercise(remote.id)
            return remote.updatedAtMs
        }
        if (routineDao.getById(remote.routineId) == null) {
            return remote.updatedAtMs
        }
        routineDao.upsertRoutineExercise(remote.toEntity())
        return remote.updatedAtMs
    }

    private suspend fun applyCustomExercise(json: String): Long {
        val remote = decodeSync<RemoteCustomExerciseRow>(json)
        val queued = hasPendingChild(SyncEntityType.CUSTOM_EXERCISE, remote.id)
        if (!SyncChildRow.remoteAppliesWhenNotLocallyQueued(queued)) {
            return remote.updatedAtMs
        }
        if (remote.deletedAtMs != null) {
            exerciseDao.deleteCustom(remote.id)
            return remote.updatedAtMs
        }
        val local = exerciseDao.getById(remote.id)
        if (local != null && !local.isCustom) {
            return remote.updatedAtMs
        }
        val localVersion = SyncEntityVersion(0L, local?.updatedAtMs ?: -1L)
        val remoteVersion = SyncEntityVersion(remote.revision, remote.updatedAtMs)
        if (local != null && !SyncRevision.remoteWins(localVersion, remoteVersion)) {
            return remote.updatedAtMs
        }
        if (local == null) {
            exerciseDao.insert(remote.toEntity())
        } else {
            exerciseDao.update(remote.toEntity())
        }
        return remote.updatedAtMs
    }

    private suspend fun applyExerciseMuscle(json: String): Long {
        val remote = decodeSync<RemoteExerciseMuscleRow>(json)
        val entityId = syncExerciseMuscleEntityId(remote.exerciseId, remote.muscleKey)
        val queued = hasPendingChild(SyncEntityType.EXERCISE_MUSCLE, entityId)
        if (!SyncChildRow.remoteAppliesWhenNotLocallyQueued(queued)) {
            return remote.updatedAtMs
        }
        if (remote.deletedAtMs != null) {
            catalogDao.deleteCredit(remote.exerciseId, remote.muscleKey)
            return remote.updatedAtMs
        }
        val parent = exerciseDao.getById(remote.exerciseId)
        if (parent == null || !parent.isCustom) {
            return remote.updatedAtMs
        }
        catalogDao.insertCredits(listOf(remote.toEntity()))
        return remote.updatedAtMs
    }

    private suspend fun applyBodyweightEntry(json: String): Long {
        val remote = decodeSync<RemoteBodyweightEntryRow>(json)
        val entityId = syncBodyweightEntityId(remote.epochDay)
        val queued = hasPendingChild(SyncEntityType.BODYWEIGHT_ENTRY, entityId)
        if (!SyncChildRow.remoteAppliesWhenNotLocallyQueued(queued)) {
            return remote.updatedAtMs
        }
        if (remote.deletedAtMs != null) {
            bodyweightDao.deleteDay(remote.epochDay)
            return remote.updatedAtMs
        }
        val local = bodyweightDao.getAll().firstOrNull { it.epochDay == remote.epochDay }
        val localVersion = SyncEntityVersion(0L, local?.recordedAtMs ?: -1L)
        val remoteVersion = SyncEntityVersion(0L, remote.updatedAtMs)
        if (local != null && !SyncRevision.remoteWins(localVersion, remoteVersion)) {
            return remote.updatedAtMs
        }
        bodyweightDao.upsert(remote.toEntity())
        return remote.updatedAtMs
    }

    private companion object {
        const val TAG = "PT/Sync"
        const val BATCH_SIZE = 32
    }
}
