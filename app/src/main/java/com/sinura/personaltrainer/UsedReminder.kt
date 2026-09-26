package com.sinura.personaltrainer

import android.content.Context
import com.sinura.personaltrainer.domain.ReminderDeliveryStatus
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.reminder.ReminderNotifications
import com.sinura.personaltrainer.util.runCatchingCancellable

/**
 * A reminder whose Start has gone through: its delivery marked started and its notification
 * dismissed, so its Snooze, Move and Skip cannot act on the session now running. A notification
 * action never dismisses its own notification, so this does. Used only once the Start has
 * opened something, never at the tap (audit UI-1). A failed mark is logged; what the Start
 * opened stands.
 */
object UsedReminder {
    private const val TAG = "PT/UsedReminder"

    suspend fun markStarted(context: Context, deps: AppDependencies, occurrenceId: String, deliveryId: String) {
        ReminderNotifications.cancel(context, occurrenceId)
        runCatchingCancellable {
            deps.plannerRepository.markDeliveryStatus(deliveryId, ReminderDeliveryStatus.STARTED)
        }.onFailure { thrown -> AppLog.w(TAG, "Marking a reminder delivery started failed", thrown) }
    }
}
