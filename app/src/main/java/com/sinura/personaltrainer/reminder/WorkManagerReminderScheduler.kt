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

    /**
     * Cancels the scheduled job AND dismisses anything already in the shade.
     *
     * Cancelling the job alone left a notification that had already been posted sitting there
     * with live Start / Skip / Move buttons for a day that is now settled. Only MainActivity
     * dismissed it, and only on a Start launch — so the two destructive buttons outlived the
     * decision they were offering. The notification id is the occurrence id's hash, the same
     * one [ReminderNotifications.show] posts under, so this is exact rather than a sweep.
     */
    override fun cancelForOccurrence(occurrenceId: String) {
        WorkManager.getInstance(appContext).cancelUniqueWork(uniqueWorkName("rem-$occurrenceId"))
        ReminderNotifications.cancel(appContext, occurrenceId)
    }

    companion object {
        /** Unique work id. Two schedules for the same delivery replace, they do not stack. */
        internal fun uniqueWorkName(deliveryId: String): String = "reminder-$deliveryId"
    }
}
