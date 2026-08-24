package com.sinura.personaltrainer.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.sinura.personaltrainer.logging.AppLog

/**
 * The platform facts the rest scheduler is allowed to ask for.
 *
 * Tests substitute a recording fake. Production re-checks
 * [AlarmManager.canScheduleExactAlarms] immediately before the exact API so
 * lint can see the grant, and so a revoke between the policy read and the
 * call cannot sneak through.
 */
interface ExactAlarmCapability {
    val sdkInt: Int
    fun nowElapsedRealtime(): Long
    fun alarmManagerOrNull(): AlarmManager?
    fun canScheduleExactAlarms(): Boolean
    fun setExactElapsed(triggerAtElapsed: Long, operation: PendingIntent)
    fun setInexactElapsed(triggerAtElapsed: Long, operation: PendingIntent)
    fun cancel(operation: PendingIntent)
}

class AndroidExactAlarmCapability(
    context: Context,
    override val sdkInt: Int = Build.VERSION.SDK_INT,
) : ExactAlarmCapability {
    private val appContext = context.applicationContext

    override fun nowElapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun alarmManagerOrNull(): AlarmManager? =
        appContext.getSystemService(AlarmManager::class.java)

    override fun canScheduleExactAlarms(): Boolean {
        if (sdkInt < 31 || Build.VERSION.SDK_INT < 31) return true
        val alarmManager = alarmManagerOrNull() ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    override fun setExactElapsed(triggerAtElapsed: Long, operation: PendingIntent) {
        val alarmManager = alarmManagerOrNull()
            ?: throw IllegalStateException("AlarmManager is missing")
        // Lint must see Build.VERSION and canScheduleExactAlarms() next to the exact API.
        if (sdkInt < 31 || Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsed,
                operation,
            )
        } else {
            throw SecurityException("SCHEDULE_EXACT_ALARM is not granted")
        }
    }

    override fun setInexactElapsed(triggerAtElapsed: Long, operation: PendingIntent) {
        val alarmManager = alarmManagerOrNull()
            ?: throw IllegalStateException("AlarmManager is missing")
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtElapsed,
            operation,
        )
    }

    override fun cancel(operation: PendingIntent) {
        val alarmManager = alarmManagerOrNull() ?: return
        try {
            alarmManager.cancel(operation)
            operation.cancel()
        } catch (error: Exception) {
            AppLog.w(TAG, "Cancelling the rest alarm failed", error)
        }
    }

    private companion object {
        const val TAG = "PT/ExactAlarm"
    }
}

/** Settings screen for SCHEDULE_EXACT_ALARM. Null below API 31. */
fun exactAlarmSettingsIntent(packageName: String, sdkInt: Int = Build.VERSION.SDK_INT): Intent? {
    if (sdkInt < 31) return null
    return Intent(ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:$packageName")
    }
}

/** Inlined so minSdk 26 does not need Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM. */
private const val ACTION_REQUEST_SCHEDULE_EXACT_ALARM =
    "android.settings.REQUEST_SCHEDULE_EXACT_ALARM"
