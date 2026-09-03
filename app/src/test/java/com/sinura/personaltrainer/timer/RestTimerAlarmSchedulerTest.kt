package com.sinura.personaltrainer.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.AlarmScheduleResult
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The scheduler's typed outcome is proven against a recording capability.
 * ShadowAlarmManager is not the contract.
 */
@RunWith(RobolectricTestRunner::class)
class RestTimerAlarmSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    @Test
    fun grantedUsesOnlyTheExactApi() {
        val capability = RecordingExactAlarmCapability(
            sdkInt = 35,
            canExact = true,
            alarmManager = alarmManager,
        )
        val result = RestTimerAlarmScheduler(context, capability)
            .schedule(10_000L, "session-1", "timer-1")

        assertEquals(AlarmScheduleResult.EXACT, result)
        assertEquals(listOf(10_000L), capability.exactTriggers)
        assertTrue(capability.inexactTriggers.isEmpty())
    }

    @Test
    fun deniedOn31PlusUsesOnlyTheInexactApi() {
        val capability = RecordingExactAlarmCapability(
            sdkInt = 35,
            canExact = false,
            alarmManager = alarmManager,
        )
        val result = RestTimerAlarmScheduler(context, capability)
            .schedule(10_000L, "session-1", "timer-1")

        assertEquals(AlarmScheduleResult.BEST_EFFORT, result)
        assertTrue(capability.exactTriggers.isEmpty())
        assertEquals(listOf(10_000L), capability.inexactTriggers)
    }

    @Test
    fun below31AttemptsExactEvenWhenTheGrantFlagIsFalse() {
        val capability = RecordingExactAlarmCapability(
            sdkInt = 30,
            canExact = false,
            alarmManager = alarmManager,
        )
        val result = RestTimerAlarmScheduler(context, capability)
            .schedule(10_000L, "session-1", "timer-1")

        assertEquals(AlarmScheduleResult.EXACT, result)
        assertEquals(listOf(10_000L), capability.exactTriggers)
        assertTrue(capability.inexactTriggers.isEmpty())
    }

    @Test
    fun exactThrowFallsBackToInexact() {
        val capability = RecordingExactAlarmCapability(
            sdkInt = 35,
            canExact = true,
            alarmManager = alarmManager,
            exactError = SecurityException("revoked mid-call"),
        )
        val result = RestTimerAlarmScheduler(context, capability)
            .schedule(10_000L, "session-1", "timer-1")

        assertEquals(AlarmScheduleResult.BEST_EFFORT, result)
        assertEquals(listOf(10_000L), capability.exactTriggers)
        assertEquals(listOf(10_000L), capability.inexactTriggers)
    }

    @Test
    fun bothApisThrowingIsFailed() {
        val capability = RecordingExactAlarmCapability(
            sdkInt = 35,
            canExact = true,
            alarmManager = alarmManager,
            exactError = SecurityException("exact"),
            inexactError = RuntimeException("inexact"),
        )
        val result = RestTimerAlarmScheduler(context, capability)
            .schedule(10_000L, "session-1", "timer-1")

        assertEquals(AlarmScheduleResult.FAILED, result)
    }

    @Test
    fun remainingZeroOrNegativeDoesNotArm() {
        val capability = RecordingExactAlarmCapability(
            sdkInt = 35,
            canExact = true,
            alarmManager = alarmManager,
            nowElapsed = 5_000L,
        )
        val scheduler = RestTimerAlarmScheduler(context, capability)

        assertEquals(AlarmScheduleResult.FAILED, scheduler.schedule(5_000L, "s", "t"))
        assertEquals(AlarmScheduleResult.FAILED, scheduler.schedule(4_999L, "s", "t"))
        assertTrue(capability.exactTriggers.isEmpty())
        assertTrue(capability.inexactTriggers.isEmpty())
    }

    @Test
    fun missingAlarmManagerIsFailed() {
        val capability = RecordingExactAlarmCapability(
            sdkInt = 35,
            canExact = true,
            alarmManager = null,
        )
        val result = RestTimerAlarmScheduler(context, capability)
            .schedule(10_000L, "session-1", "timer-1")

        assertEquals(AlarmScheduleResult.FAILED, result)
        assertTrue(capability.exactTriggers.isEmpty())
        assertTrue(capability.inexactTriggers.isEmpty())
    }

    @Test
    fun cancelWithoutAPriorScheduleDoesNotCreateAnAlarm() {
        val capability = RecordingExactAlarmCapability(
            sdkInt = 35,
            canExact = true,
            alarmManager = alarmManager,
        )
        RestTimerAlarmScheduler(
            context,
            capability,
            existingAlarm = { null },
        ).cancel()
        assertEquals(0, capability.cancelCount)
    }

    @Test
    fun cancelGoesThroughTheCapability() {
        val capability = RecordingExactAlarmCapability(
            sdkInt = 35,
            canExact = true,
            alarmManager = alarmManager,
        )
        val scheduler = RestTimerAlarmScheduler(context, capability)
        scheduler.schedule(10_000L, "session-1", "timer-1")
        scheduler.cancel()
        assertEquals(1, capability.cancelCount)
    }

    @Test
    fun refreshAfterGrantReschedulesALiveTimerAsExact() {
        val capability = RecordingExactAlarmCapability(
            sdkInt = 35,
            canExact = false,
            alarmManager = alarmManager,
        )
        val store = RestTimerStore()
        val controller = RestTimerController(
            context = context,
            store = store,
            alarms = RestTimerAlarmScheduler(context, capability),
        )
        try {
            controller.start(90, "session-1")

            assertEquals(ExactAlarmAttempt.BEST_EFFORT, controller.exactAlarmAttempt.value)
            assertEquals(AlarmScheduleResult.BEST_EFFORT, controller.lastAlarmSchedule.value)
            assertTrue(capability.exactTriggers.isEmpty())
            assertEquals(1, capability.inexactTriggers.size)

            capability.canExact = true
            controller.refreshAlarmCapability()

            assertEquals(ExactAlarmAttempt.EXACT, controller.exactAlarmAttempt.value)
            assertEquals(AlarmScheduleResult.EXACT, controller.lastAlarmSchedule.value)
            assertEquals(1, capability.exactTriggers.size)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun settingsIntentIsNullBelow31AndTargetsThePackageOn31() {
        assertNull(exactAlarmSettingsIntent("com.sinura.personaltrainer", sdkInt = 30))
        val intent = exactAlarmSettingsIntent("com.sinura.personaltrainer", sdkInt = 31)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent?.action)
        assertEquals("package:com.sinura.personaltrainer", intent?.data.toString())
    }

    @Test
    fun androidCapabilityAllowsExactBelow31() {
        val capability = AndroidExactAlarmCapability(context, sdkInt = 30)
        assertTrue(capability.canScheduleExactAlarms())
    }

    private class RecordingExactAlarmCapability(
        override val sdkInt: Int,
        var canExact: Boolean,
        private val alarmManager: AlarmManager?,
        private val nowElapsed: Long = 1_000L,
        private val exactError: Exception? = null,
        private val inexactError: Exception? = null,
    ) : ExactAlarmCapability {
        val exactTriggers = mutableListOf<Long>()
        val inexactTriggers = mutableListOf<Long>()
        var cancelCount = 0

        override fun nowElapsedRealtime(): Long = nowElapsed
        override fun alarmManagerOrNull(): AlarmManager? = alarmManager
        override fun canScheduleExactAlarms(): Boolean = canExact

        override fun setExactElapsed(triggerAtElapsed: Long, operation: PendingIntent) {
            exactTriggers += triggerAtElapsed
            exactError?.let { throw it }
        }

        override fun setInexactElapsed(triggerAtElapsed: Long, operation: PendingIntent) {
            inexactTriggers += triggerAtElapsed
            inexactError?.let { throw it }
        }

        override fun cancel(operation: PendingIntent) {
            cancelCount++
        }
    }
}
