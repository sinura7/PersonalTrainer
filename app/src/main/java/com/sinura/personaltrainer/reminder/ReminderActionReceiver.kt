package com.sinura.personaltrainer.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PersonalTrainerApp ?: return
        val occurrenceId = intent.getStringExtra(ReminderNotifications.EXTRA_OCCURRENCE_ID) ?: return
        val deliveryId = intent.getStringExtra(ReminderNotifications.EXTRA_DELIVERY_ID) ?: return
        val pending = goAsync()
        // A scope per broadcast, cancelled when that broadcast's work is done. This used to be
        // a `companion object val`: one CoroutineScope for the life of the process, owned by
        // nothing, cancelled never — and shared by every test in the suite that touched this
        // receiver. goAsync() already defines exactly how long the work may live, so the scope
        // matches it. RestTimerAlarmReceiver has always done it this way.
        val scope = CoroutineScope(SupervisorJob() + app.container.ioDispatcher)
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
                scope.cancel()
            }
        }
    }

    private companion object {
        const val TAG = "PT/ReminderAction"
    }
}
