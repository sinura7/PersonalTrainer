package com.sinura.personaltrainer.data.sync

import com.sinura.personaltrainer.domain.SyncEntityType

class FakeSyncRemote : SyncRemotePort {
    val upserts = mutableListOf<Pair<SyncEntityType, String>>()
    val tombstones = mutableListOf<Pair<SyncEntityType, String>>()
    private val store = mutableMapOf<SyncEntityType, MutableList<String>>()

    override suspend fun upsert(type: SyncEntityType, payloadJson: String) {
        upserts += type to payloadJson
        store.getOrPut(type) { mutableListOf() }.add(payloadJson)
    }

    override suspend fun tombstone(type: SyncEntityType, payloadJson: String) {
        tombstones += type to payloadJson
        upsert(type, payloadJson)
    }

    override suspend fun pullUpdatedSince(type: SyncEntityType, sinceUpdatedAtMs: Long): List<String> {
        return store[type].orEmpty().filter { json ->
            val updated = decodeUpdatedAt(type, json)
            updated > sinceUpdatedAtMs
        }
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
    }
}
