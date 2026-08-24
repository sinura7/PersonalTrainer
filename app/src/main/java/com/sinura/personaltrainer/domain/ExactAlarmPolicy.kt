package com.sinura.personaltrainer.domain

/**
 * What the rest wakeup was actually scheduled as.
 *
 * Exact means `canScheduleExactAlarms` was true (or the SDK is older than the
 * permission) and the exact API succeeded. BestEffort is the honest inexact
 * fallback. Failed means nothing was armed.
 */
enum class AlarmScheduleResult {
    EXACT,
    BEST_EFFORT,
    FAILED,
}

/** Which API the policy will attempt, before the platform call. */
enum class ExactAlarmAttempt {
    EXACT,
    BEST_EFFORT,
}

/**
 * Rest uses [SCHEDULE_EXACT_ALARM], never [USE_EXACT_ALARM].
 *
 * API 31+ requires a grant. Below that the exact APIs do not need the
 * permission. The check lives here so the scheduler cannot invent a third path.
 */
object ExactAlarmPolicy {
    const val PERMISSION_SDK = 31

    fun attempt(sdkInt: Int, canScheduleExactAlarms: Boolean): ExactAlarmAttempt =
        if (sdkInt < PERMISSION_SDK || canScheduleExactAlarms) {
            ExactAlarmAttempt.EXACT
        } else {
            ExactAlarmAttempt.BEST_EFFORT
        }
}
