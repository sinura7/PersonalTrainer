package com.sinura.personaltrainer.reminder

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.entity.ReminderDeliveryEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleOccurrenceEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ReminderDeliveryStatus
import com.sinura.personaltrainer.domain.ReminderPreferences
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reminder Start/Snooze/Move/Skip, reboot rebuild, and the worker's
 * reread-then-maybe-notify path. A defect here is silent on the phone:
 * the tap dismisses and nothing is written, or a stale worker fires a
 * catch-up notification after a reboot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderReceiversAndWorkerTest {
    private lateinit var deps: FakeAppDependencies
    private val cancelled = mutableListOf<String>()

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        cancelled.clear()
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun startRecordsStartedAndCancelsWithoutOpeningTheApp() = runBlocking {
        seed(occurrenceStatus = OccurrenceStatus.PLANNED, deliveryStatus = ReminderDeliveryStatus.PENDING)
        ReminderActionApply.apply(
            action = ReminderNotifications.ACTION_START,
            occurrenceId = OCCURRENCE,
            deliveryId = DELIVERY,
            planner = deps.plannerRepository,
            cancelNotification = { cancelled.add(it) },
        )
        assertEquals(ReminderDeliveryStatus.STARTED, deps.plannerRepository.getDelivery(DELIVERY)?.status)
        assertEquals(OccurrenceStatus.PLANNED, deps.plannerRepository.getOccurrence(OCCURRENCE)?.status)
        assertEquals(listOf(OCCURRENCE), cancelled)
    }

    @Test
    fun snoozeMarksTheOldDeliveryAndSchedulesANewPendingOne() = runBlocking {
        seed(occurrenceStatus = OccurrenceStatus.PLANNED, deliveryStatus = ReminderDeliveryStatus.PENDING)
        ReminderActionApply.apply(
            action = ReminderNotifications.ACTION_SNOOZE,
            occurrenceId = OCCURRENCE,
            deliveryId = DELIVERY,
            planner = deps.plannerRepository,
            cancelNotification = { cancelled.add(it) },
        )
        assertEquals(ReminderDeliveryStatus.SNOOZED, deps.plannerRepository.getDelivery(DELIVERY)?.status)
        val remaining = deps.database.plannerDao().getDeliveriesForOccurrence(OCCURRENCE)
        assertTrue(remaining.any { it.status == ReminderDeliveryStatus.PENDING.name && it.id != DELIVERY })
        assertEquals(listOf(OCCURRENCE), cancelled)
    }

    @Test
    fun moveMarksTheDayMovedAndLeavesAPlannedCopyForward() = runBlocking {
        seed(occurrenceStatus = OccurrenceStatus.PLANNED, deliveryStatus = ReminderDeliveryStatus.PENDING)
        ReminderActionApply.apply(
            action = ReminderNotifications.ACTION_MOVE,
            occurrenceId = OCCURRENCE,
            deliveryId = DELIVERY,
            planner = deps.plannerRepository,
            cancelNotification = { cancelled.add(it) },
        )
        assertEquals(ReminderDeliveryStatus.MOVED, deps.plannerRepository.getDelivery(DELIVERY)?.status)
        assertEquals(OccurrenceStatus.MOVED, deps.plannerRepository.getOccurrence(OCCURRENCE)?.status)
        val later = deps.database.plannerDao().getOccurrencesForRule(RULE)
        assertTrue(later.any { it.status == OccurrenceStatus.PLANNED.name && it.id != OCCURRENCE })
        assertEquals(listOf(OCCURRENCE), cancelled)
    }

    @Test
    fun skipMarksTheOccurrenceSkipped() = runBlocking {
        seed(occurrenceStatus = OccurrenceStatus.PLANNED, deliveryStatus = ReminderDeliveryStatus.PENDING)
        ReminderActionApply.apply(
            action = ReminderNotifications.ACTION_SKIP,
            occurrenceId = OCCURRENCE,
            deliveryId = DELIVERY,
            planner = deps.plannerRepository,
            cancelNotification = { cancelled.add(it) },
        )
        assertEquals(ReminderDeliveryStatus.SKIPPED, deps.plannerRepository.getDelivery(DELIVERY)?.status)
        assertEquals(OccurrenceStatus.SKIPPED, deps.plannerRepository.getOccurrence(OCCURRENCE)?.status)
        assertEquals(listOf(OCCURRENCE), cancelled)
    }

    @Test
    fun unknownActionIsANoOp() = runBlocking {
        seed(occurrenceStatus = OccurrenceStatus.PLANNED, deliveryStatus = ReminderDeliveryStatus.PENDING)
        ReminderActionApply.apply(
            action = "com.sinura.personaltrainer.REMINDER_OPEN",
            occurrenceId = OCCURRENCE,
            deliveryId = DELIVERY,
            planner = deps.plannerRepository,
            cancelNotification = { cancelled.add(it) },
        )
        assertEquals(ReminderDeliveryStatus.PENDING, deps.plannerRepository.getDelivery(DELIVERY)?.status)
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun actionReceiverWithoutTheAppClassIsANoOp() {
        val receiver = ReminderActionReceiver()
        val intent = Intent(ReminderNotifications.ACTION_START)
            .putExtra(ReminderNotifications.EXTRA_OCCURRENCE_ID, OCCURRENCE)
            .putExtra(ReminderNotifications.EXTRA_DELIVERY_ID, DELIVERY)
        receiver.onReceive(ApplicationProvider.getApplicationContext(), intent)
    }

    @Test
    fun rebuildOnlyHandlesBootTimezoneAndTimeChanged() {
        assertTrue(ReminderRebuild.shouldHandle(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(ReminderRebuild.shouldHandle(Intent.ACTION_TIMEZONE_CHANGED))
        assertTrue(ReminderRebuild.shouldHandle(Intent.ACTION_TIME_CHANGED))
        assertFalse(ReminderRebuild.shouldHandle(Intent.ACTION_DATE_CHANGED))
        assertFalse(ReminderRebuild.shouldHandle(null))
        assertFalse(ReminderRebuild.shouldHandle("android.intent.action.MY_PACKAGE_REPLACED"))
    }

    @Test
    fun rebuildMarksPastDuePendingDeliveriesStale() = runBlocking {
        seed(
            occurrenceStatus = OccurrenceStatus.PLANNED,
            deliveryStatus = ReminderDeliveryStatus.PENDING,
            scheduledAtMs = NOW.instantMillis - 60_000L,
        )
        deps.plannerRepository.rebuildReminders(nowMs = NOW.instantMillis)
        assertEquals(ReminderDeliveryStatus.STALE, deps.plannerRepository.getDelivery(DELIVERY)?.status)
    }

    @Test
    fun workerDoesNotNotifyAStaleOrMissingDelivery() = runBlocking {
        val shown = mutableListOf<String>()
        seed(
            occurrenceStatus = OccurrenceStatus.DONE,
            deliveryStatus = ReminderDeliveryStatus.PENDING,
            scheduledAtMs = NOW.instantMillis - 1_000L,
        )
        ReminderWork.run(
            deliveryId = DELIVERY,
            planner = deps.plannerRepository,
            prefs = ReminderPreferences.DEFAULT,
            now = NOW,
            notify = { occurrence, _, _ -> shown.add(occurrence.id) },
        )
        assertTrue(shown.isEmpty())
        assertEquals(ReminderDeliveryStatus.STALE, deps.plannerRepository.getDelivery(DELIVERY)?.status)

        ReminderWork.run(
            deliveryId = "missing",
            planner = deps.plannerRepository,
            prefs = ReminderPreferences.DEFAULT,
            now = NOW,
            notify = { occurrence, _, _ -> shown.add(occurrence.id) },
        )
        ReminderWork.run(
            deliveryId = null,
            planner = deps.plannerRepository,
            prefs = ReminderPreferences.DEFAULT,
            now = NOW,
            notify = { occurrence, _, _ -> shown.add(occurrence.id) },
        )
        assertTrue(shown.isEmpty())
    }

    @Test
    fun workerNotifiesADuePlannedDeliveryAndMarksItDelivered() = runBlocking {
        val shown = mutableListOf<Pair<String, String>>()
        seed(
            occurrenceStatus = OccurrenceStatus.PLANNED,
            deliveryStatus = ReminderDeliveryStatus.PENDING,
            scheduledAtMs = NOW.instantMillis - 1_000L,
        )
        ReminderWork.run(
            deliveryId = DELIVERY,
            planner = deps.plannerRepository,
            prefs = ReminderPreferences.DEFAULT,
            now = NOW,
            notify = { occurrence: ScheduleOccurrence, deliveryId, title ->
                shown.add(occurrence.id to deliveryId)
                assertEquals("Strength", title)
            },
        )
        assertEquals(listOf(OCCURRENCE to DELIVERY), shown)
        assertEquals(ReminderDeliveryStatus.DELIVERED, deps.plannerRepository.getDelivery(DELIVERY)?.status)
    }

    @Test
    fun uniqueWorkNameIsReminderDashDeliveryId() {
        assertEquals("reminder-rem-occ-1", WorkManagerReminderScheduler.uniqueWorkName("rem-occ-1"))
        assertEquals(
            WorkManagerReminderScheduler.uniqueWorkName("rem-$OCCURRENCE"),
            WorkManagerReminderScheduler.uniqueWorkName("rem-$OCCURRENCE"),
        )
    }

    private suspend fun seed(
        occurrenceStatus: OccurrenceStatus,
        deliveryStatus: ReminderDeliveryStatus,
        scheduledAtMs: Long = NOW.instantMillis - 1_000L,
    ) {
        deps.database.plannerDao().upsertRule(
            ScheduleRuleEntity(
                id = RULE,
                weekday = 5,
                hour = 12,
                minute = 0,
                modality = "STRENGTH",
                zonePolicy = "FOLLOW_DEVICE",
                fixedZoneId = null,
                routineId = null,
                templateId = null,
                focusKind = null,
                reminderOffsetMinutes = 0,
                enabled = 1,
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
        )
        deps.database.plannerDao().upsertOccurrence(
            ScheduleOccurrenceEntity(
                id = OCCURRENCE,
                ruleId = RULE,
                status = occurrenceStatus.name,
                instantMs = NOW.instantMillis,
                zoneId = NOW.zoneId,
                offsetSeconds = NOW.offsetSeconds,
                localEpochDay = NOW.localEpochDay,
                hour = 12,
                minute = 0,
                completedActivityId = null,
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
        )
        deps.database.plannerDao().upsertDelivery(
            ReminderDeliveryEntity(
                id = DELIVERY,
                occurrenceId = OCCURRENCE,
                scheduledAtMs = scheduledAtMs,
                status = deliveryStatus.name,
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
        )
    }

    private companion object {
        const val RULE = "rule-1"
        const val OCCURRENCE = "occ-1"
        const val DELIVERY = "rem-occ-1"
        val NOW = JvmTime.resolveLocal(
            CivilDateTime(CivilDate(2026, 8, 21), hour = 12, minute = 0),
            "UTC",
        )
    }
}
