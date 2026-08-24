package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exact APIs are legal below API 31, or on 31+ only when the grant is on.
 * The scheduler must not invent a third path.
 */
class ExactAlarmPolicyTest {
    @Test
    fun belowPermissionSdkAlwaysAttemptsExact() {
        for (sdk in listOf(26, 30)) {
            for (granted in listOf(true, false)) {
                assertEquals(
                    "sdk $sdk granted=$granted",
                    ExactAlarmAttempt.EXACT,
                    ExactAlarmPolicy.attempt(sdkInt = sdk, canScheduleExactAlarms = granted),
                )
            }
        }
    }

    @Test
    fun api31PlusFollowsTheGrant() {
        for (sdk in listOf(31, 34, 35)) {
            assertEquals(
                ExactAlarmAttempt.EXACT,
                ExactAlarmPolicy.attempt(sdkInt = sdk, canScheduleExactAlarms = true),
            )
            assertEquals(
                ExactAlarmAttempt.BEST_EFFORT,
                ExactAlarmPolicy.attempt(sdkInt = sdk, canScheduleExactAlarms = false),
            )
        }
    }
}
