package com.sinura.personaltrainer.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sinura.personaltrainer.PersonalTrainerApp
import kotlinx.coroutines.flow.first

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? PersonalTrainerApp ?: return Result.success()
        val coordinator = app.container.syncCoordinator
        val session = app.container.accountAuth.session.first() ?: return Result.success()
        if (!app.container.accountAuth.configured) return Result.success()
        return coordinator.runPass(session.userId).fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
