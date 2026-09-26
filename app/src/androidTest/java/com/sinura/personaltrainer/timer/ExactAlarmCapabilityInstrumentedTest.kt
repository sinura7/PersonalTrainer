package com.sinura.personaltrainer.timer

import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
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
    @SdkSuppress(maxSdkVersion = 30)
    fun apiBelow31SchedulesExact() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as PersonalTrainerApp
        val timer = app.container.restTimerController
        try {
            timer.start(60, "instrumented-exact-alarm")
            // start() arms the wakeup on the IO scope after the rest row lands on disk, and
            // the flow starts at FAILED. Asserting straight after start() read that initial
            // value on the hosted emulator; a fast dev box happened to win the race.
            //
            // Wait on THIS start's own row rather than on the flow leaving FAILED: a value
            // left over from an earlier test in the same process would satisfy that and
            // report an arm that never happened. A persisted row precedes the
            // arm; it does not prove that the following scheduling call finished.
            val timerId = app.container.restTimerStore.current().timerId
            awaitTrue("rest row for $timerId reached disk") {
                app.container.restTimerStatePersistence.load()?.timerId == timerId
            }
            // This is a platform-capability check, so it asks the scheduler itself, for this
            // persisted, current rest: a prior outcome cannot satisfy it. The controller re-arms
            // through its write queue (row first, audit RT-4), so its own outcome lands later;
            // that sequencing has its unit coverage in RestTimerControllerTest.
            val rest = app.container.restTimerStore.current()
            val scheduler = RestTimerAlarmScheduler(context.applicationContext)
            assertEquals(
                AlarmScheduleResult.EXACT,
                scheduler.schedule(
                    endsAtElapsedRealtime = rest.endsAtElapsedRealtime,
                    sessionId = rest.sessionId,
                    timerId = rest.timerId,
                ),
            )
            assertEquals(ExactAlarmAttempt.EXACT, scheduler.currentAttempt())
        } finally {
            timer.stop()
        }
    }

    private fun awaitTrue(what: String, timeoutMs: Long = 15_000, ready: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (ready()) return
            Thread.sleep(50)
        }
        throw AssertionError("$what did not happen within ${timeoutMs}ms")
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
