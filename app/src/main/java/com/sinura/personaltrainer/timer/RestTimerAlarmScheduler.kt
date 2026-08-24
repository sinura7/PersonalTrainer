package com.sinura.personaltrainer.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Schedules the one thing that must happen even with the phone asleep in a pocket: the
 * rest-complete alert.
 *
 * A foreground service does NOT hold the CPU awake. Once the screen is off and nothing else
 * holds a wakelock the kernel suspends, Handler callbacks stop being delivered, and the old
 * poll-only design simply missed the wall — the alert landed whenever the user next woke the
 * device. AlarmManager is the only mechanism that wakes the CPU on time.
 *
 * [AlarmManager.setAlarmClock] is used deliberately over setExactAndAllowWhileIdle:
 *  - it is exempt from Doze deferral AND from the Android 12+ SCHEDULE_EXACT_ALARM
 *    permission, so no runtime grant flow and nothing for the user to accidentally revoke;
 *  - it is not subject to the ~9-minute setExactAndAllowWhileIdle throttle;
 *  - the user-visible "alarm set" status-bar affordance is honest — a rest timer is an alarm.
 * It is wall-clock (RTC) based, so a mid-rest clock change is the one thing it cannot absorb;
 * the elapsedRealtime snapshot in the store stays authoritative for display, and the receiver
 * re-checks the real remaining time before alerting.
 */
class RestTimerAlarmScheduler(context: Context) {
    private val appContext = context.applicationContext

    fun schedule(endsAtElapsedRealtime: Long, sessionId: String?, timerId: String) {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val remainingMs = endsAtElapsedRealtime - SystemClock.elapsedRealtime()
        if (remainingMs <= 0L) return
        val triggerAtWallClock = System.currentTimeMillis() + remainingMs
        val operation = alarmIntent(sessionId, timerId) ?: return
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtWallClock, showIntent(sessionId)),
                operation,
            )
        } catch (_: Exception) {
            // Fall back to the next-best exact alarm rather than losing the alert entirely.
            try {
                // minSdk 26, so setExactAndAllowWhileIdle is always available.
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    endsAtElapsedRealtime,
                    operation,
                )
            } catch (_: Exception) {
                // Nothing more to try; the in-service tick remains as a screen-on backstop.
            }
        }
    }

    fun cancel() {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val operation = alarmIntent(sessionId = null, timerId = "") ?: return
        try {
            alarmManager.cancel(operation)
            operation.cancel()
        } catch (_: Exception) {
            // Already gone.
        }
    }

    private fun alarmIntent(sessionId: String?, timerId: String): PendingIntent? {
        val intent = Intent(appContext, RestTimerAlarmReceiver::class.java)
            .setAction(RestTimerAlarmReceiver.ACTION_REST_COMPLETE)
            .putExtra(RestTimerService.EXTRA_TIMER_ID, timerId)
            .apply { sessionId?.let { putExtra(RestTimerService.EXTRA_SESSION_ID, it) } }
        // FLAG_UPDATE_CURRENT keeps one canonical alarm: rescheduling replaces, never stacks.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return try {
            PendingIntent.getBroadcast(appContext, REQUEST_CODE, intent, flags)
        } catch (_: Exception) {
            null
        }
    }

    private fun showIntent(sessionId: String?): PendingIntent? {
        val intent = Intent(appContext, com.sinura.personaltrainer.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            sessionId?.let { putExtra(RestTimerService.EXTRA_SESSION_ID, it) }
        }
        return try {
            PendingIntent.getActivity(
                appContext,
                REQUEST_CODE_SHOW,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val REQUEST_CODE = 30
        const val REQUEST_CODE_SHOW = 31
    }
}
