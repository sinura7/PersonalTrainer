package com.sinura.personaltrainer.data.sync

import com.sinura.personaltrainer.domain.SyncEntityType

class FakeSyncRemote : SyncRemotePort {
    val upserts = mutableListOf<Pair<SyncEntityType, String>>()
    val tombstones = mutableListOf<Pair<SyncEntityType, String>>()
    private val store = mutableMapOf<SyncEntityType, MutableList<String>>()
    var failNextUpsert: Exception? = null
    /** When set, every upsert throws (keeps outbox rows for pull-only tests). */
    var persistUpsertFailure: Exception? = null
    /** Simulates PostgREST page caps in tests; default returns full result sets. */
    var pullPageSize: Int = Int.MAX_VALUE

    override suspend fun upsert(type: SyncEntityType, payloadJson: String) {
        persistUpsertFailure?.let { throw it }
        failNextUpsert?.let { error ->
            failNextUpsert = null
            throw error
        }
        upserts += type to payloadJson
        store.getOrPut(type) { mutableListOf() }.add(payloadJson)
    }

    override suspend fun tombstone(type: SyncEntityType, payloadJson: String) {
        tombstones += type to payloadJson
        upsert(type, payloadJson)
    }

    override suspend fun pullUpdatedSince(type: SyncEntityType, sinceUpdatedAtMs: Long): List<String> {
        return store[type].orEmpty()
            .filter { json ->
                val updated = decodeUpdatedAt(type, json)
                updated > sinceUpdatedAtMs
            }
            .sortedBy { decodeUpdatedAt(type, it) }
            .take(pullPageSize)
    }

    fun seed(type: SyncEntityType, json: String) {
        store.getOrPut(type) { mutableListOf() }.add(json)
    }

    private fun decodeUpdatedAt(type: SyncEntityType, json: String): Long = when (type) {
        SyncEntityType.ACTIVITY_SESSION ->
            decodeSync<RemoteActivitySessionRow>(json).updatedAtMs
        SyncEntityType.ACTIVITY_BLOCK ->
            decodeSync<RemoteActivityBlockRow>(json).updatedAtMs
        SyncEntityType.ACTIVITY_STRENGTH_SET ->
            decodeSync<RemoteStrengthSetRow>(json).updatedAtMs
        SyncEntityType.ACTIVITY_CARDIO_INTERVAL ->
            decodeSync<RemoteCardioIntervalRow>(json).updatedAtMs
        SyncEntityType.SCHEDULE_RULE ->
            decodeSync<RemoteScheduleRuleRow>(json).updatedAtMs
        SyncEntityType.SCHEDULE_OCCURRENCE ->
            decodeSync<RemoteScheduleOccurrenceRow>(json).updatedAtMs
        SyncEntityType.ROUTINE ->
            decodeSync<RemoteRoutineRow>(json).updatedAtMs
        SyncEntityType.ROUTINE_EXERCISE ->
            decodeSync<RemoteRoutineExerciseRow>(json).updatedAtMs
        SyncEntityType.ACTIVITY_TEMPLATE ->
            decodeSync<RemoteActivityTemplateRow>(json).updatedAtMs
    }
}
