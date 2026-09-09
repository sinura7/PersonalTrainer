package com.sinura.personaltrainer.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CancellationException
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
                // Start records the tap and clears the notification. It must not
                // call startActivity: since API 31 the system drops that from a
                // receiver. New notifications use an activity PendingIntent
                // (ReminderNotifications.startApp); this path is only for taps
                // still pointing here from an older build.
                ReminderActionApply.apply(
                    action = intent.action,
                    occurrenceId = occurrenceId,
                    deliveryId = deliveryId,
                    planner = app.container.plannerRepository,
                    cancelNotification = { id -> ReminderNotifications.cancel(context, id) },
                )
            } catch (error: CancellationException) {
                throw error
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
