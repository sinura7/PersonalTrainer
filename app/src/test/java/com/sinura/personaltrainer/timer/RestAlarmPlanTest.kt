package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.domain.AlarmScheduleResult
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import org.junit.Assert.assertEquals
import org.junit.Test

class RestAlarmPlanTest {
    @Test
    fun missingManagerOrDeadlineOrIntentIsFail() {
        assertEquals(
            RestAlarmPlan.Action.FAIL,
            RestAlarmPlan.action(
                hasAlarmManager = false,
                remainingMs = 10_000L,
                attempt = ExactAlarmAttempt.EXACT,
                hasOperation = true,
            ),
        )
        assertEquals(
            RestAlarmPlan.Action.FAIL,
            RestAlarmPlan.action(
                hasAlarmManager = true,
                remainingMs = 0L,
                attempt = ExactAlarmAttempt.EXACT,
                hasOperation = true,
            ),
        )
        assertEquals(
            RestAlarmPlan.Action.FAIL,
            RestAlarmPlan.action(
                hasAlarmManager = true,
                remainingMs = -1L,
                attempt = ExactAlarmAttempt.EXACT,
                hasOperation = true,
            ),
        )
        assertEquals(
            RestAlarmPlan.Action.FAIL,
            RestAlarmPlan.action(
                hasAlarmManager = true,
                remainingMs = 10_000L,
                attempt = ExactAlarmAttempt.EXACT,
                hasOperation = false,
            ),
        )
    }

    @Test
    fun attemptChoosesExactOrInexact() {
        assertEquals(
            RestAlarmPlan.Action.EXACT,
            RestAlarmPlan.action(
                hasAlarmManager = true,
                remainingMs = 1L,
                attempt = ExactAlarmAttempt.EXACT,
                hasOperation = true,
            ),
        )
        assertEquals(
            RestAlarmPlan.Action.INEXACT,
            RestAlarmPlan.action(
                hasAlarmManager = true,
                remainingMs = 1L,
                attempt = ExactAlarmAttempt.BEST_EFFORT,
                hasOperation = true,
            ),
        )
    }

    @Test
    fun resultMapsSuccessAndFallback() {
        assertEquals(
            AlarmScheduleResult.FAILED,
            RestAlarmPlan.result(RestAlarmPlan.Action.FAIL, exactSucceeded = true, inexactSucceeded = true),
        )
        assertEquals(
            AlarmScheduleResult.EXACT,
            RestAlarmPlan.result(RestAlarmPlan.Action.EXACT, exactSucceeded = true, inexactSucceeded = false),
        )
        assertEquals(
            AlarmScheduleResult.BEST_EFFORT,
            RestAlarmPlan.result(RestAlarmPlan.Action.EXACT, exactSucceeded = false, inexactSucceeded = true),
        )
        assertEquals(
            AlarmScheduleResult.FAILED,
            RestAlarmPlan.result(RestAlarmPlan.Action.EXACT, exactSucceeded = false, inexactSucceeded = false),
        )
        assertEquals(
            AlarmScheduleResult.BEST_EFFORT,
            RestAlarmPlan.result(RestAlarmPlan.Action.INEXACT, exactSucceeded = false, inexactSucceeded = true),
        )
        assertEquals(
            AlarmScheduleResult.FAILED,
            RestAlarmPlan.result(RestAlarmPlan.Action.INEXACT, exactSucceeded = false, inexactSucceeded = false),
        )
    }
}
