package com.sinura.personaltrainer.data.sync

import com.sinura.personaltrainer.domain.SyncEntityType

/** Test seam for Supabase PostgREST. Production uses [SupabaseSyncRemote]. */
interface SyncRemotePort {
    suspend fun upsert(type: SyncEntityType, payloadJson: String)
    suspend fun tombstone(type: SyncEntityType, payloadJson: String)
    suspend fun pullUpdatedSince(type: SyncEntityType, sinceUpdatedAtMs: Long): List<String>
}
