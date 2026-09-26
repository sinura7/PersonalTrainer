package com.sinura.personaltrainer

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.reminder.ReminderNotifications
import com.sinura.personaltrainer.testutil.forgetFirstApplication
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Audit X6, R4, through the real app, notification and receiver. A planned day's reminder kept
 * its Snooze, Move and Skip while that day's session ran, unless the session had been opened
 * from the reminder itself: Move moved the day being trained away and left a copy on a later
 * day, Skip skipped it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class)
class PlannedDayReminderWhileTrainingTest {
    private lateinit var app: PersonalTrainerApp

    @Before
    fun setUp() {
        forgetFirstApplication()
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        forgetFirstApplication()
    }

    /** From any screen, a planned day's session opening takes its reminder off the phone. */
    @Test
    fun thePlannedSessionOpeningTakesItsReminderOffThePhone() {
        val planned = plannedEarlierToday()
        ReminderNotifications.show(app, planned, DELIVERY, "Push")
        assertTrue("the reminder was not posted", reminderShown(planned.id))

        runBlocking { PendingOccurrence.bindForSession(app.container, planned.id, startAWorkout()) }

        assertFalse("its Snooze, Move and Skip must leave the shade", reminderShown(planned.id))
    }

    /** A Move tapped on a reminder still on screen while its day is trained leaves the day. */
    @Test
    fun moveOnTheReminderOfADayBeingTrainedLeavesTheDay() {
        val planned = plannedEarlierToday()
        runBlocking { PendingOccurrence.bindForSession(app.container, planned.id, startAWorkout()) }
        // Posted after the session opened, as a reminder due just then was before R4.
        ReminderNotifications.show(app, planned, DELIVERY, "Push")

        tap("Move", planned)

        assertEquals(OccurrenceStatus.PLANNED, status(planned))
        assertEquals(
            "no copy of the day was made for later",
            listOf(planned.id),
            runBlocking {
                app.container.plannerRepository
                    .occurrencesBetween(planned.localEpochDay, planned.localEpochDay + 14)
                    .filter { it.ruleId == planned.ruleId }
                    .map { it.id }
            },
        )
    }

    @Test
    fun skipOnTheReminderOfADayBeingTrainedLeavesTheDay() {
        val planned = plannedEarlierToday()
        runBlocking { PendingOccurrence.bindForSession(app.container, planned.id, startAWorkout()) }
        ReminderNotifications.show(app, planned, DELIVERY, "Push")

        tap("Skip", planned)

        assertEquals(OccurrenceStatus.PLANNED, status(planned))
    }

    /** Another planned day's Move still moves that day: only the day being trained is held. */
    @Test
    fun moveOnAnotherDaysReminderStillMovesIt() {
        val trained = plannedEarlierToday()
        val other = plannedEarlierToday(minute = 1)
        runBlocking { PendingOccurrence.bindForSession(app.container, trained.id, startAWorkout()) }
        ReminderNotifications.show(app, other, "rem-${other.id}", "Pull")

        tap("Move", other)

        assertEquals(OccurrenceStatus.MOVED, status(other))
    }

    /**
     * Taps [action] on [planned]'s notification, then waits for the receiver: until the day is
     * written, or its reminder is taken down (its last step). A day that is moved goes on to
     * schedule its copy's reminder, which this test's app cannot (no WorkManager): the receiver
     * logs that and stops before the reminder goes, so the day's own status is the sign then.
     */
    private fun tap(action: String, planned: ScheduleOccurrence) {
        val shown = app.getSystemService(NotificationManager::class.java)
            .activeNotifications.single { it.id == planned.id.hashCode() }
        val button: PendingIntent = shown.notification.actions.single { it.title == action }.actionIntent
        button.send()
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (reminderShown(planned.id) && status(planned) == OccurrenceStatus.PLANNED &&
            System.currentTimeMillis() < deadline
        ) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue(
            "the receiver did not finish",
            !reminderShown(planned.id) || status(planned) != OccurrenceStatus.PLANNED,
        )
    }

    private fun status(planned: ScheduleOccurrence): OccurrenceStatus? =
        runBlocking { app.container.plannerRepository.getOccurrence(planned.id)?.status }

    /** A real planned day, at midnight so that no reminder is scheduled for it. */
    private fun plannedEarlierToday(minute: Int = 0): ScheduleOccurrence = runBlocking {
        val planner = app.container.plannerRepository
        val today = JvmTime.captureNow().localEpochDay
        val rule = planner.addTimedRule(
            weekday = Weekday.fromEpochDay(today),
            hour = 0,
            minute = minute,
            modality = ScheduleModality.STRENGTH,
        )
        planner.ensureWeek(CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY))
            .single { it.ruleId == rule.id && it.localEpochDay == today }
    }

    private suspend fun startAWorkout(): String =
        (app.container.workoutRepository.startFreeWorkoutSafely("Legs") as StartSessionOutcome.Started).session.id

    private fun reminderShown(occurrenceId: String): Boolean =
        app.getSystemService(NotificationManager::class.java)
            .activeNotifications.any { it.id == occurrenceId.hashCode() }

    private companion object {
        const val DELIVERY = "rem-occ-while-training"
        const val WAIT_MS = 10_000L
    }
}
