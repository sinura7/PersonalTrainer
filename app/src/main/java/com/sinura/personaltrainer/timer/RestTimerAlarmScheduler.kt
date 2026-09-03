package com.sinura.personaltrainer.timer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sinura.personaltrainer.domain.AlarmScheduleResult
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.ExactAlarmPolicy
import com.sinura.personaltrainer.logging.AppLog

/**
 * Arms the rest-complete wakeup.
 *
 * Exact APIs run only when [ExactAlarmPolicy] says they may. A denied grant
 * takes the inexact path and reports [AlarmScheduleResult.BEST_EFFORT] — the
 * UI must not call that "reliable". Failures are logged, not swallowed.
 *
 * Elapsed-realtime, not RTC: a mid-rest wall-clock change must not move the
 * deadline the claim ledger is already keyed on.
 */
class RestTimerAlarmScheduler(
    context: Context,
    private val capability: ExactAlarmCapability = AndroidExactAlarmCapability(context),
    existingAlarm: (() -> PendingIntent?)? = null,
) {
    private val appContext = context.applicationContext
    private val existingAlarm: () -> PendingIntent? = existingAlarm ?: {
        PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            Intent(appContext, RestTimerAlarmReceiver::class.java)
                .setAction(RestTimerAlarmReceiver.ACTION_REST_COMPLETE),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun currentAttempt(): ExactAlarmAttempt =
        ExactAlarmPolicy.attempt(
            sdkInt = capability.sdkInt,
            canScheduleExactAlarms = capability.canScheduleExactAlarms(),
        )

    fun schedule(
        endsAtElapsedRealtime: Long,
        sessionId: String?,
        timerId: String,
    ): AlarmScheduleResult {
        val remainingMs = endsAtElapsedRealtime - capability.nowElapsedRealtime()
        val operation = alarmIntent(sessionId, timerId)
        val action = RestAlarmPlan.action(
            hasAlarmManager = capability.alarmManagerOrNull() != null,
            remainingMs = remainingMs,
            attempt = currentAttempt(),
            hasOperation = operation != null,
        )
        if (action == RestAlarmPlan.Action.FAIL) {
            if (capability.alarmManagerOrNull() == null) {
                AppLog.w(TAG, "No AlarmManager; rest wakeup cannot be armed")
            } else if (operation == null) {
                AppLog.w(TAG, "Could not build the rest PendingIntent")
            }
            return AlarmScheduleResult.FAILED
        }
        val pending = operation ?: return AlarmScheduleResult.FAILED
        return when (action) {
            RestAlarmPlan.Action.EXACT -> try {
                capability.setExactElapsed(endsAtElapsedRealtime, pending)
                RestAlarmPlan.result(
                    action = action,
                    exactSucceeded = true,
                    inexactSucceeded = false,
                )
            } catch (error: Exception) {
                AppLog.w(TAG, "Exact rest alarm failed; trying inexact", error)
                armInexact(endsAtElapsedRealtime, pending)
            }
            RestAlarmPlan.Action.INEXACT -> armInexact(endsAtElapsedRealtime, pending)
            RestAlarmPlan.Action.FAIL -> AlarmScheduleResult.FAILED
        }
    }

    fun cancel() {
        val pending = try {
            existingAlarm()
        } catch (error: Exception) {
            AppLog.w(TAG, "PendingIntent lookup for rest-alarm cancel failed", error)
            null
        } ?: return
        capability.cancel(pending)
    }

    private fun armInexact(
        endsAtElapsedRealtime: Long,
        operation: PendingIntent,
    ): AlarmScheduleResult = try {
        capability.setInexactElapsed(endsAtElapsedRealtime, operation)
        RestAlarmPlan.result(
            action = RestAlarmPlan.Action.INEXACT,
            exactSucceeded = false,
            inexactSucceeded = true,
        )
    } catch (error: Exception) {
        AppLog.w(TAG, "Inexact rest alarm failed", error)
        RestAlarmPlan.result(
            action = RestAlarmPlan.Action.INEXACT,
            exactSucceeded = false,
            inexactSucceeded = false,
        )
    }

    private fun alarmIntent(sessionId: String?, timerId: String): PendingIntent? {
        val intent = Intent(appContext, RestTimerAlarmReceiver::class.java)
            .setAction(RestTimerAlarmReceiver.ACTION_REST_COMPLETE)
            .putExtra(RestTimerService.EXTRA_TIMER_ID, timerId)
            .apply { sessionId?.let { putExtra(RestTimerService.EXTRA_SESSION_ID, it) } }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return try {
            PendingIntent.getBroadcast(appContext, REQUEST_CODE, intent, flags)
        } catch (error: Exception) {
            AppLog.w(TAG, "PendingIntent for the rest alarm failed", error)
            null
        }
    }

    private companion object {
        const val TAG = "PT/RestAlarm"
        const val REQUEST_CODE = 30
    }
}
