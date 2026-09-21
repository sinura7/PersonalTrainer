package com.sinura.personaltrainer.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class WorkManagerSyncScheduler(
    context: Context,
) : SyncScheduler {
    private val appContext = context.applicationContext

    override fun enqueueOneShot() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    companion object {
        const val UNIQUE_NAME = "temper-account-sync"
    }
}
