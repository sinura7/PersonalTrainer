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

/**
 * Rebuilds future WorkManager reminders after reboot or a zone change.
 * Past-due PENDING rows become STALE — no catch-up spam (ADR-012).
 */
class ReminderRebuildReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getAction() ?: return
        if (!ReminderRebuild.shouldHandle(action)) return
        val app = context.applicationContext as? PersonalTrainerApp ?: return
        val pending = goAsync()
        scope.launch {
            try {
                app.container.plannerRepository.rebuildReminders()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.w(TAG, "Reminder rebuild failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PT/ReminderRebuild"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
