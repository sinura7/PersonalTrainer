package com.sinura.personaltrainer.data.sync

import com.sinura.personaltrainer.data.local.dao.SyncDao
import com.sinura.personaltrainer.domain.AccountAuthPort
import com.sinura.personaltrainer.domain.SyncStatus
import com.sinura.personaltrainer.domain.SyncStatusPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SyncCoordinator(
    private val auth: AccountAuthPort,
    private val syncDao: SyncDao,
    private val engine: SyncEngine,
    private val scheduler: SyncScheduler,
    private val authoring: SyncAuthoring? = null,
    /**
     * [com.sinura.personaltrainer.domain.AccountSyncGate.SYNC_PAUSED], read once by the
     * container. While true no pass reaches [engine]; the upload queue keeps filling.
     */
    private val paused: Boolean = false,
) : SyncStatusPort {
    override val status: Flow<SyncStatus> = combine(
        auth.session,
        syncDao.observePendingCount(),
        syncDao.observeMetadata(),
    ) { session, pending, metadata ->
        SyncStatus(
            active = auth.configured && session != null,
            pendingCount = pending,
            lastSuccessAtMs = metadata?.lastSuccessAtMs,
            lastError = metadata?.lastError,
            paused = paused,
        )
    }

    override fun requestSync() {
        if (paused) return
        scheduler.enqueueOneShot()
    }

    override suspend fun abandonOutboxOnSignOut() {
        syncDao.clearOutbox()
        val previous = syncDao.getMetadata()?.lastSuccessAtMs
        syncDao.upsertMetadata(
            com.sinura.personaltrainer.data.local.entity.SyncMetadataEntity(
                lastSuccessAtMs = previous,
                lastError = null,
            ),
        )
    }

    /**
     * One push-then-pull pass, or nothing while [paused]. A pass WorkManager queued before the
     * pause shipped still runs this, so the guard lives here and not only in the scheduler;
     * it reports success so WorkManager drops the job instead of retrying it.
     */
    suspend fun runPass(userId: String): Result<Unit> {
        if (paused) return Result.success(Unit)
        return engine.run(userId)
    }

    override suspend fun bootstrapAfterSignIn() {
        authoring?.bootstrapLocalSnapshot()
    }
}
