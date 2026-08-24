package com.sinura.personaltrainer.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sinura.personaltrainer.MainActivity
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
                    ReminderNotifications.ACTION_START -> {
                        app.container.plannerRepository.markDeliveryStatus(
                            deliveryId,
                            ReminderDeliveryStatus.STARTED,
                        )
                        ReminderNotifications.cancel(context, occurrenceId)
                        val open = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(ReminderNotifications.EXTRA_OCCURRENCE_ID, occurrenceId)
                        }
                        context.startActivity(open)
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
