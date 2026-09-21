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
        )
    }

    override fun requestSync() {
        scheduler.enqueueOneShot()
    }

    suspend fun runPass(userId: String): Result<Unit> = engine.run(userId)
}
