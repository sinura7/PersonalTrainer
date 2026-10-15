package com.sinura.personaltrainer.data.sync

import com.sinura.personaltrainer.domain.SyncEntityType

/** Test seam for Supabase PostgREST. Production uses [SupabaseSyncRemote]. */
interface SyncRemotePort {
    suspend fun upsert(type: SyncEntityType, payloadJson: String)
    suspend fun tombstone(type: SyncEntityType, payloadJson: String)
    suspend fun pullUpdatedSince(type: SyncEntityType, sinceUpdatedAtMs: Long): List<String>

    /** Rows whose [column] equals [value]. Used to read one weigh-in day before uploading it. */
    suspend fun fetchEq(type: SyncEntityType, column: String, value: String): List<String>
}
