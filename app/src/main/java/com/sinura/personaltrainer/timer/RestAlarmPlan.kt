package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.domain.AlarmScheduleResult
import com.sinura.personaltrainer.domain.ExactAlarmAttempt

/**
 * The rest-wakeup decision, without AlarmManager.
 *
 * [RestTimerAlarmScheduler] is the Android adapter. This object is what
 * tests can prove on a plain JVM: exact is only attempted when the policy
 * says so, and a failed exact becomes an honest inexact result.
 */
object RestAlarmPlan {
    enum class Action {
        EXACT,
        INEXACT,
        FAIL,
    }

    fun action(
        hasAlarmManager: Boolean,
        remainingMs: Long,
        attempt: ExactAlarmAttempt,
        hasOperation: Boolean,
    ): Action {
        if (!hasAlarmManager || remainingMs <= 0L || !hasOperation) return Action.FAIL
        return when (attempt) {
            ExactAlarmAttempt.EXACT -> Action.EXACT
            ExactAlarmAttempt.BEST_EFFORT -> Action.INEXACT
        }
    }

    fun result(
        action: Action,
        exactSucceeded: Boolean,
        inexactSucceeded: Boolean,
    ): AlarmScheduleResult = when (action) {
        Action.FAIL -> AlarmScheduleResult.FAILED
        Action.EXACT -> when {
            exactSucceeded -> AlarmScheduleResult.EXACT
            inexactSucceeded -> AlarmScheduleResult.BEST_EFFORT
            else -> AlarmScheduleResult.FAILED
        }
        Action.INEXACT -> if (inexactSucceeded) {
            AlarmScheduleResult.BEST_EFFORT
        } else {
            AlarmScheduleResult.FAILED
        }
    }
}
