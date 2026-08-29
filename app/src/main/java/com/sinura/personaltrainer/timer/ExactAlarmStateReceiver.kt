package com.sinura.personaltrainer.timer

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sinura.personaltrainer.PersonalTrainerApp

/**
 * Re-arms the rest alarm when the SCHEDULE_EXACT_ALARM grant changes.
 *
 * Revoking the grant in Settings force-stops the app and cancels its exact
 * alarms; without this hook a mid-rest revoke left the countdown dead until
 * the next manual app open — no alert, no inexact fallback. The system sends
 * this broadcast on a grant (and wakes the app for it), which is the
 * documented recovery point: rehydrate the persisted rest and schedule with
 * whatever capability now holds. MainActivity.onResume stays the belt to
 * this brace.
 */
class ExactAlarmStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            return
        }
        val app = context.applicationContext as? PersonalTrainerApp ?: return
        val controller = app.container.restTimerController
        // Rehydrate first: after the force-stop this process is fresh and the
        // running rest exists only on disk. refreshAlarmCapability re-reads
        // the grant and re-schedules a live rest under it.
        controller.rehydrate()
        controller.refreshAlarmCapability()
    }
}
