package com.sinura.personaltrainer.domain

import kotlinx.coroutines.flow.Flow

data class SyncStatus(
    /** False when Supabase is not configured or the user is signed out. */
    val active: Boolean,
    val pendingCount: Int,
    val lastSuccessAtMs: Long?,
    val lastError: String?,
    /** True while [AccountSyncGate.SYNC_PAUSED] holds: edits queue, but no pass runs. */
    val paused: Boolean = false,
)

/**
 * Optional cloud sync when Temper Account is signed in. Training never depends on this.
 */
interface SyncStatusPort {
    val status: Flow<SyncStatus>
    fun requestSync()

    /**
     * Drops queued uploads for the signed-out account. Local gym-floor data is unchanged;
     * a later sign-in starts a fresh upload queue from new edits.
     */
    suspend fun abandonOutboxOnSignOut()

    /** Queues local custom lifts and weigh-ins after a successful sign-in. */
    suspend fun bootstrapAfterSignIn()
}

/** No-op for tests and unconfigured builds. */
object DisabledSyncStatusPort : SyncStatusPort {
    override val status: Flow<SyncStatus> = kotlinx.coroutines.flow.flowOf(
        SyncStatus(active = false, pendingCount = 0, lastSuccessAtMs = null, lastError = null),
    )
    override fun requestSync() = Unit
    override suspend fun abandonOutboxOnSignOut() = Unit
    override suspend fun bootstrapAfterSignIn() = Unit
}
