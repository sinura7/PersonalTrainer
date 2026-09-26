package com.sinura.personaltrainer.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.util.JvmTime

/**
 * Rereads the delivery row. Stale work is a no-op. Never schedules
 * an exact alarm (ADR-012).
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? PersonalTrainerApp ?: return Result.success()
        ReminderWork.runFor(
            deps = app.container,
            deliveryId = inputData.getString(KEY_DELIVERY_ID),
            now = JvmTime.captureNow(),
            notify = { occurrence, deliveryId, title ->
                ReminderNotifications.show(applicationContext, occurrence, deliveryId, title)
            },
        )
        return Result.success()
    }

    companion object {
        const val KEY_DELIVERY_ID = "delivery_id"
    }
}
