package com.sinura.personaltrainer.ui.settings

import com.sinura.personaltrainer.domain.SyncStatus
import com.sinura.personaltrainer.domain.SyncStatusPort
import kotlinx.coroutines.flow.MutableStateFlow

class RecordingSyncStatusPort : SyncStatusPort {
    val statusFlow = MutableStateFlow(
        SyncStatus(active = true, pendingCount = 0, lastSuccessAtMs = null, lastError = null),
    )
    var abandonOutboxCalls = 0
    var requestSyncCalls = 0

    override val status = statusFlow

    override fun requestSync() {
        requestSyncCalls++
    }

    override suspend fun abandonOutboxOnSignOut() {
        abandonOutboxCalls++
    }

    override suspend fun bootstrapAfterSignIn() = Unit
}
