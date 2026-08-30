package com.sinura.personaltrainer.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sinura.personaltrainer.domain.ReminderDelivery
import com.sinura.personaltrainer.domain.ReminderScheduler
import java.util.concurrent.TimeUnit

class WorkManagerReminderScheduler(
    context: Context,
) : ReminderScheduler {
    private val appContext = context.applicationContext

    override fun schedule(delivery: ReminderDelivery) {
        val delay = (delivery.scheduledAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf(ReminderWorker.KEY_DELIVERY_ID to delivery.id))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            uniqueWorkName(delivery.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel(deliveryId: String) {
        WorkManager.getInstance(appContext).cancelUniqueWork(uniqueWorkName(deliveryId))
    }

    override fun cancelForOccurrence(occurrenceId: String) {
        WorkManager.getInstance(appContext).cancelUniqueWork(uniqueWorkName("rem-$occurrenceId"))
    }

    companion object {
        /** Unique work id. Two schedules for the same delivery replace, they do not stack. */
        internal fun uniqueWorkName(deliveryId: String): String = "reminder-$deliveryId"
    }
}
