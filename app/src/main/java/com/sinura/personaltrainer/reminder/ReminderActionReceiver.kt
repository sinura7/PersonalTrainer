package com.sinura.personaltrainer.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.ReminderDeliveryStatus
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PersonalTrainerApp ?: return
        val occurrenceId = intent.getStringExtra(ReminderNotifications.EXTRA_OCCURRENCE_ID) ?: return
        val deliveryId = intent.getStringExtra(ReminderNotifications.EXTRA_DELIVERY_ID) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    // Kept only for notifications posted by builds whose Start action still
                    // pointed here. Since API 31 a receiver cannot launch an activity from
                    // a notification action — the system drops the startActivity silently —
                    // so new notifications carry an activity PendingIntent instead
                    // (ReminderNotifications.startApp). This branch records the tap and
                    // clears the notification; it must not pretend to open the app.
                    ReminderNotifications.ACTION_START -> {
                        app.container.plannerRepository.markDeliveryStatus(
                            deliveryId,
                            ReminderDeliveryStatus.STARTED,
                        )
                        ReminderNotifications.cancel(context, occurrenceId)
                    }
                    ReminderNotifications.ACTION_SNOOZE -> {
                        app.container.plannerRepository.snoozeDelivery(deliveryId)
                        ReminderNotifications.cancel(context, occurrenceId)
                    }
                    ReminderNotifications.ACTION_MOVE -> {
                        app.container.plannerRepository.markDeliveryStatus(
                            deliveryId,
                            ReminderDeliveryStatus.MOVED,
                        )
                        app.container.plannerRepository.moveOccurrenceForward(occurrenceId)
                        ReminderNotifications.cancel(context, occurrenceId)
                    }
                    ReminderNotifications.ACTION_SKIP -> {
                        app.container.plannerRepository.markDeliveryStatus(
                            deliveryId,
                            ReminderDeliveryStatus.SKIPPED,
                        )
                        app.container.plannerRepository.skipOccurrence(occurrenceId)
                        ReminderNotifications.cancel(context, occurrenceId)
                    }
                }
            } catch (error: Exception) {
                AppLog.w(TAG, "Reminder action failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PT/ReminderAction"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
