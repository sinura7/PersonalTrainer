package com.sinura.personaltrainer

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.PowerManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ReminderCopy
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.reminder.ReminderNotifications
import com.sinura.personaltrainer.testutil.forgetFirstApplication
import com.sinura.personaltrainer.timer.RestTimerService
import com.sinura.personaltrainer.ui.components.ConfirmActionTags
import com.sinura.personaltrainer.ui.navigation.LiveSessionBarTestTags
import com.sinura.personaltrainer.ui.navigation.Route
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Audit UI-1, through the real activity and app shell. A reminder's Start tapped during a live
 * workout switched to Home: a workout opened from another tab was swapped for Home, behind the
 * live bar. The reminder was also marked started and dismissed at the tap, although the start
 * was then refused because that workout was live.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class, qualifiers = "w360dp-h800dp-xhdpi")
class ReminderTapDuringAWorkoutTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var app: PersonalTrainerApp
    private var controller: ActivityController<MainActivity>? = null

    @Before
    fun setUp() {
        forgetFirstApplication()
        app = ApplicationProvider.getApplicationContext()
        // Everything the first-open permission walk asks for, so it puts no dialog up.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
        app.markColdStartIntroShown()
        runBlocking { app.container.preferencesRepository.setOnboardingComplete(true) }
    }

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        // So a later class in this process does not build its ViewModels over this app either.
        forgetFirstApplication()
    }

    @Test
    fun aStartTappedOnAWorkoutOpenedFromPlanLeavesTheWorkoutOnScreen() {
        val live = startAWorkout()
        val planned = remindOfAPlannedSession()
        launch(Intent(app, MainActivity::class.java))
        awaitTag(PLAN_TAB)
        compose.onNodeWithTag(PLAN_TAB).performClick()
        compose.waitForIdle()
        openTheFloor(live)

        tapStart(planned)

        awaitText(ReminderCopy.LIVE_TITLE)
        closeTheExplanation()
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).assertIsDisplayed()
        assertTrue("the reminder stays in the shade, unused", reminderShown(planned.id))
    }

    @Test
    fun aStartTappedOnTheWorkoutFloorLeavesTheReminderUnused() {
        val live = startAWorkout()
        val planned = remindOfAPlannedSession()
        launch(restTap(live))
        awaitTag(WorkoutTestTags.CONTENT)

        tapStart(planned)

        awaitText(ReminderCopy.LIVE_START_BODY)
        assertTrue("the reminder stays in the shade, unused", reminderShown(planned.id))
        closeTheExplanation()
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).assertIsDisplayed()
    }

    /**
     * On the Home tab Home would take the tap too, and its own start would answer over the held
     * one with "A workout is already in progress". Home starts off the main thread, so one idle
     * is not enough to see it: this waits for it, and it must not come.
     */
    @Test
    fun aStartTappedOnHomeDuringAWorkoutIsAnsweredOnce() {
        startAWorkout()
        val planned = plannedEarlierToday()
        ReminderNotifications.show(app, planned, DELIVERY, "Push")
        launch(Intent(app, MainActivity::class.java))
        awaitTag(LiveSessionBarTestTags.ROOT)

        tapStart(planned)

        awaitText(ReminderCopy.LIVE_START_BODY)
        assertNeverShown(HOME_IN_PROGRESS)
        assertTrue("the reminder stays in the shade, unused", reminderShown(planned.id))
        closeTheExplanation()
    }

    /**
     * The app closed with a workout live, then opened by the Start itself. The live state reads
     * "nothing live" until the database answers, and Home, drawn first, took the tap.
     */
    @Test
    fun aStartThatOpensTheAppDuringAWorkoutIsHeldTheSameWay() {
        startAWorkout()
        val planned = plannedEarlierToday()
        ReminderNotifications.show(app, planned, DELIVERY, "Push")

        launch(ReminderNotifications.startLaunchIntent(app, planned.id, DELIVERY))

        awaitText(ReminderCopy.LIVE_START_BODY)
        assertNeverShown(HOME_IN_PROGRESS)
        assertTrue("the reminder stays in the shade, unused", reminderShown(planned.id))
        closeTheExplanation()
    }

    /**
     * The planned day started early, from Home or Plan: its own reminder can come in mid-session.
     * Its Start opens that session and uses the reminder, so its Move and Skip leave the shade.
     */
    @Test
    fun aStartForTheSessionYouAreInOpensItAndUsesTheReminder() {
        val live = startAWorkout()
        val planned = plannedEarlierToday()
        runBlocking { PendingOccurrence.bindForSession(app.container, planned.id, live) }
        ReminderNotifications.show(app, planned, DELIVERY, "Push")
        launch(Intent(app, MainActivity::class.java))
        awaitTag(PLAN_TAB)
        compose.onNodeWithTag(PLAN_TAB).performClick()
        compose.waitForIdle()

        tapStart(planned)

        awaitTag(WorkoutTestTags.CONTENT)
        compose.onNodeWithText(ReminderCopy.LIVE_TITLE).assertDoesNotExist()
        assertFalse("its Snooze, Move and Skip must leave the shade", reminderShown(planned.id))
    }

    /**
     * Nothing live: the Start reaches Home and opens the session, and only then is the reminder
     * used up. It is dismissed only if its delivery came through from the tap to Home.
     */
    @Test
    fun aStartWithNothingLiveOpensTheSessionAndThenClearsTheReminder() {
        val planned = plannedEarlierToday()
        ReminderNotifications.show(app, planned, DELIVERY, "Push")
        launch(Intent(app, MainActivity::class.java))
        awaitTag(PLAN_TAB)

        tapStart(planned)
        awaitTag(WorkoutTestTags.CONTENT)

        assertNotNull("the planned session opened", runBlocking { app.container.workoutRepository.getInProgress() })
        assertFalse("its Snooze, Move and Skip must leave the shade", reminderShown(planned.id))
    }

    /** The same through the intent that opens the app, which hands the delivery over in onCreate. */
    @Test
    fun aStartThatOpensTheAppWithNothingLiveOpensTheSessionAndThenClearsTheReminder() {
        val planned = plannedEarlierToday()
        ReminderNotifications.show(app, planned, DELIVERY, "Push")

        launch(ReminderNotifications.startLaunchIntent(app, planned.id, DELIVERY))
        awaitTag(WorkoutTestTags.CONTENT)

        assertNotNull("the planned session opened", runBlocking { app.container.workoutRepository.getInProgress() })
        assertFalse("its Snooze, Move and Skip must leave the shade", reminderShown(planned.id))
    }

    /** A real planned day, at midnight so that no reminder is scheduled for it. */
    private fun plannedEarlierToday(): ScheduleOccurrence = runBlocking {
        val planner = app.container.plannerRepository
        val today = JvmTime.captureNow().localEpochDay
        val rule = planner.addTimedRule(
            weekday = Weekday.fromEpochDay(today),
            hour = 0,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
        )
        planner.ensureWeek(CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY))
            .single { it.ruleId == rule.id && it.localEpochDay == today }
    }

    private fun startAWorkout(): String = runBlocking {
        (app.container.workoutRepository.startFreeWorkoutSafely("Legs") as StartSessionOutcome.Started).session.id
    }

    /** A plan reminder in the shade, as the worker posts it. */
    private fun remindOfAPlannedSession(): ScheduleOccurrence {
        val now = JvmTime.captureNow()
        val planned = ScheduleOccurrence(
            id = "occ-reminder-tap",
            ruleId = "rule-reminder-tap",
            status = OccurrenceStatus.PLANNED,
            captured = now,
            hour = 18,
            minute = 0,
            createdAtMs = now.instantMillis,
            updatedAtMs = now.instantMillis,
        )
        ReminderNotifications.show(app, planned, DELIVERY, "Push")
        check(reminderShown(planned.id)) { "the reminder was not posted" }
        return planned
    }

    /**
     * The app, opened by [intent]. A dialog window can outlive a case's activity and still be
     * found by the next case's queries, so each case checks it starts with nothing on screen.
     */
    private fun launch(intent: Intent) {
        // With nothing on screen the query throws "No compose hierarchies found": the clean state.
        val leftover = runCatching { compose.onAllNodes(isRoot()).fetchSemanticsNodes() }.getOrNull()
        check(leftover.isNullOrEmpty()) { "an earlier case is still on screen" }
        controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
    }

    /** OK on the held tap's explanation; a case leaves no dialog up for the next. */
    private fun closeTheExplanation() {
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).performClick()
        compose.onNodeWithText(ReminderCopy.LIVE_TITLE).assertDoesNotExist()
    }

    /** The rest notification's tap, which opens the floor over whatever tab is showing. */
    private fun restTap(sessionId: String): Intent =
        Intent(app, MainActivity::class.java).putExtra(RestTimerService.EXTRA_SESSION_ID, sessionId)

    private fun openTheFloor(sessionId: String) {
        controller!!.newIntent(restTap(sessionId))
        awaitTag(WorkoutTestTags.CONTENT)
    }

    /**
     * The reminder's Start, delivered to the running activity as Android delivers it. Where it
     * goes is read from the database first, off the main thread: wait on what it shows.
     */
    private fun tapStart(planned: ScheduleOccurrence) {
        controller!!.newIntent(ReminderNotifications.startLaunchIntent(app, planned.id, DELIVERY))
        compose.waitForIdle()
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(timeoutMillis = WAIT_MS) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = WAIT_MS) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** [text] must not come within [NEVER_MS]: what would show it runs off the main thread. */
    private fun assertNeverShown(text: String) {
        val came = runCatching {
            compose.waitUntil(timeoutMillis = NEVER_MS) {
                compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        assertFalse("\"$text\" came up", came)
    }

    private fun reminderShown(occurrenceId: String): Boolean =
        app.getSystemService(NotificationManager::class.java)
            .activeNotifications.any { it.id == occurrenceId.hashCode() }

    private companion object {
        const val DELIVERY = "rem-occ-reminder-tap"
        const val WAIT_MS = 20_000L
        const val NEVER_MS = 2_000L

        /** Home's own answer to a start while a workout is live (ResumeOrDiscardDialog). */
        const val HOME_IN_PROGRESS = "A workout is already in progress"
        val PLAN_TAB = "navigation-${Route.Routines.path}"
    }
}
