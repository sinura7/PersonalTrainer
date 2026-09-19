package com.sinura.personaltrainer.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.DstGapPolicy
import com.sinura.personaltrainer.domain.DstOverlapChoice
import com.sinura.personaltrainer.domain.ReminderPreferences
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WorkoutAlarms
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.timer.exactAlarmSettingsIntent

/**
 * Per-day workout reminder alarms. Rest stays on the elapsed-realtime path.
 * Uses SCHEDULE_EXACT_ALARM when granted, honest inexact otherwise.
 */
object WorkoutAlarmScheduler {
    const val ACTION = "com.sinura.personaltrainer.WORKOUT_ALARM"
    const val EXTRA_WEEKDAY = "weekday"

    fun rebuild(context: Context, prefs: ReminderPreferences, time: TimePort) {
        val app = context.applicationContext
        Weekday.entries.forEach { day -> cancel(app, day) }
        if (prefs.optOut) return
        val nowMs = time.nowMillis()
        val zone = time.defaultZoneId()
        val today = time.civilDate(nowMs, zone)
        val nowMinutes = time.wallMinutesOfDay(nowMs, zone)
        prefs.sanitized().dayAlarms.forEach { (day, reminder) ->
            val epochDay = WorkoutAlarms.nextTriggerEpochDay(
                weekday = day,
                hour = reminder.hour,
                minute = reminder.minute,
                todayEpochDay = today.epochDay,
                nowMinutes = nowMinutes,
            )
            val captured = time.resolveLocal(
                CivilDateTime(CivilDate.fromEpochDay(epochDay), reminder.hour, reminder.minute),
                zone,
                overlap = DstOverlapChoice.EARLIER,
                gap = DstGapPolicy.SHIFT_FORWARD,
            )
            scheduleAt(app, day, captured.instantMillis)
        }
    }

    fun exactAlarmIntent(packageName: String): Intent? = exactAlarmSettingsIntent(packageName)

    private fun scheduleAt(context: Context, weekday: Weekday, triggerAtMs: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val operation = pending(context, weekday, create = true) ?: return
        try {
            val exact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    operation,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    operation,
                )
            }
        } catch (error: Exception) {
            AppLog.w(TAG, "Scheduling the workout alarm failed", error)
        }
    }

    private fun cancel(context: Context, weekday: Weekday) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val operation = pending(context, weekday, create = false) ?: return
        try {
            alarmManager.cancel(operation)
            operation.cancel()
        } catch (error: Exception) {
            AppLog.w(TAG, "Cancelling the workout alarm failed", error)
        }
    }

    private fun pending(context: Context, weekday: Weekday, create: Boolean): PendingIntent? {
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        val intent = Intent(context, WorkoutAlarmReceiver::class.java)
            .setAction(ACTION)
            .putExtra(EXTRA_WEEKDAY, weekday.name)
        return PendingIntent.getBroadcast(context, weekday.ordinal, intent, flags)
    }

    private const val TAG = "PT/WorkoutAlarm"
}
