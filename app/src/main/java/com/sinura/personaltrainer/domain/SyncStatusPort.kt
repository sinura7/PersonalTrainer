package com.sinura.personaltrainer.domain

import kotlinx.coroutines.flow.Flow

data class SyncStatus(
    /** False when Supabase is not configured or the user is signed out. */
    val active: Boolean,
    val pendingCount: Int,
    val lastSuccessAtMs: Long?,
    val lastError: String?,
)

/**
 * Optional cloud sync when Temper Account is signed in. Training never depends on this.
 */
interface SyncStatusPort {
    val status: Flow<SyncStatus>
    fun requestSync()
}

/** No-op for tests and unconfigured builds. */
object DisabledSyncStatusPort : SyncStatusPort {
    override val status: Flow<SyncStatus> = kotlinx.coroutines.flow.flowOf(
        SyncStatus(active = false, pendingCount = 0, lastSuccessAtMs = null, lastError = null),
    )
    override fun requestSync() = Unit
}
