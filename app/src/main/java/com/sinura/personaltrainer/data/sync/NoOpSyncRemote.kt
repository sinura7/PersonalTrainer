package com.sinura.personaltrainer.data.sync

import com.sinura.personaltrainer.domain.SyncEntityType

/** Used when Supabase is not configured. Sync never runs. */
object NoOpSyncRemote : SyncRemotePort {
    override suspend fun upsert(type: SyncEntityType, payloadJson: String) =
        error("Sync is not configured")

    override suspend fun tombstone(type: SyncEntityType, payloadJson: String) =
        error("Sync is not configured")

    override suspend fun pullUpdatedSince(type: SyncEntityType, sinceUpdatedAtMs: Long): List<String> =
        emptyList()
}
