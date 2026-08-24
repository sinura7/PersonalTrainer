package com.sinura.personaltrainer.timer

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.AlarmScheduleResult
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-lane facts for P2.2. This environment's emulator is API 29, so the
 * typed outcome must be Exact. API 31/34/35 grant/deny/Doze is an owner
 * physical check, not this lane.
 */
@RunWith(AndroidJUnit4::class)
class ExactAlarmCapabilityInstrumentedTest {
    @Test
    fun apiBelow31SchedulesExact() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as PersonalTrainerApp
        val timer = app.container.restTimerController
        try {
            timer.start(60, "instrumented-exact-alarm")
            assertEquals(AlarmScheduleResult.EXACT, timer.lastAlarmSchedule.value)
            assertEquals(ExactAlarmAttempt.EXACT, timer.exactAlarmAttempt.value)
        } finally {
            timer.stop()
        }
    }

    @Test
    fun manifestDeclaresScheduleExactAlarmAndNotUseExactAlarm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requested = info.requestedPermissions?.toSet().orEmpty()
        assertTrue(requested.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        assertFalse(requested.contains("android.permission.USE_EXACT_ALARM"))
    }
}
