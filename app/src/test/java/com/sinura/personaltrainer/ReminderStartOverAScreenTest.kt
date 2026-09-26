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
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.LiveBarCopy
import com.sinura.personaltrainer.domain.LiveBarKind
import com.sinura.personaltrainer.domain.ReminderCopy
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.reminder.ReminderNotifications
import com.sinura.personaltrainer.testutil.forgetFirstApplication
import com.sinura.personaltrainer.ui.activity.ComposerTags
import com.sinura.personaltrainer.ui.components.ConfirmActionTags
import com.sinura.personaltrainer.ui.navigation.LiveSessionBarTestTags
import com.sinura.personaltrainer.ui.navigation.Route
import com.sinura.personaltrainer.ui.summary.SummaryTags
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * Audit X6, R4, through the real activity and app shell. With nothing live, a reminder's Start
 * reaches Home, and Home starts the session, but only while Home is drawn. Tapped with a screen
 * left open over Home (the workout summary most of all), switching to Home's tab brought that
 * screen back: the Start went nowhere, then started the session by itself once the lifter left
 * that screen, maybe much later.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class, qualifiers = "w360dp-h800dp-xhdpi")
class ReminderStartOverAScreenTest {
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
        forgetFirstApplication()
    }

    /** Just finished a workout, on its summary: the planned session opens, at once. */
    @Test
    fun aStartTappedOnTheWorkoutSummaryOpensThePlannedSession() {
        finishAWorkoutFromTheBar()
        val planned = plannedEarlierToday(ScheduleModality.STRENGTH)
        ReminderNotifications.show(app, planned, DELIVERY, "Push")

        tapStart(planned)

        awaitTag(WorkoutTestTags.CONTENT)
        assertEquals("the planned session opened", planned.id, followed())
        assertFalse("its Snooze, Move and Skip must leave the shade", reminderShown(planned.id))
    }

    /** Two screens over Home: the finished session's page, opened from its summary. */
    @Test
    fun aStartTappedOnTheFinishedSessionsPageOpensThePlannedSession() {
        finishAWorkoutFromTheBar()
        compose.onNodeWithTag(SummaryTags.OPEN_SESSION).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) {
            compose.onAllNodes(hasTestTag(SummaryTags.DONE)).fetchSemanticsNodes().isEmpty()
        }
        val planned = plannedEarlierToday(ScheduleModality.STRENGTH)
        ReminderNotifications.show(app, planned, DELIVERY, "Push")

        tapStart(planned)

        awaitTag(WorkoutTestTags.CONTENT)
        assertEquals("the planned session opened", planned.id, followed())
    }

    /**
     * An activity being logged asks before it is left, so it is not closed for the lifter: the
     * Start is held there with a word of why, and nothing starts when the composer is left.
     */
    @Test
    fun aStartTappedWhileLoggingAnActivityStaysAndNothingStartsLater() {
        val mixed = plannedEarlierToday(modality = ScheduleModality.MIXED)
        val strength = plannedEarlierToday(modality = ScheduleModality.STRENGTH, minute = 1)
        launch(ReminderNotifications.startLaunchIntent(app, mixed.id, MIXED_DELIVERY))
        awaitTag(ComposerTags.SAVE)
        ReminderNotifications.show(app, strength, DELIVERY, "Push")

        tapStart(strength)

        awaitText(ReminderCopy.EDIT_START_BODY)
        compose.onNodeWithText(ReminderCopy.EDIT_TITLE).assertIsDisplayed()
        assertTrue("the reminder stays in the shade, unused", reminderShown(strength.id))
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).performClick()
        compose.onNodeWithTag(ComposerTags.SAVE).assertIsDisplayed()

        compose.onNodeWithTag(ComposerTags.CANCEL).performClick()
        awaitTag(PLAN_TAB)
        assertNeverShown(WorkoutTestTags.CONTENT)
        assertNull("no session started by itself", runBlocking { app.container.workoutRepository.getInProgress() })
        assertTrue("the reminder is still there to use", reminderShown(strength.id))
    }

    /** A real planned day of [modality], at midnight so that no reminder is scheduled for it. */
    private fun plannedEarlierToday(modality: ScheduleModality, minute: Int = 0): ScheduleOccurrence = runBlocking {
        val planner = app.container.plannerRepository
        val today = JvmTime.captureNow().localEpochDay
        val rule = planner.addTimedRule(
            weekday = Weekday.fromEpochDay(today),
            hour = 0,
            minute = minute,
            modality = modality,
        )
        planner.ensureWeek(CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY))
            .single { it.ruleId == rule.id && it.localEpochDay == today }
    }

    /** A workout with one set, finished from the live bar on Home: its summary is over Home. */
    private fun finishAWorkoutFromTheBar() {
        runBlocking {
            val container = app.container
            val session = (container.workoutRepository.startFreeWorkoutSafely("Legs") as StartSessionOutcome.Started).session
            val lift = container.exerciseRepository.createCustom(name = "Probe squat", muscleGroup = "Quads")
            container.workoutRepository.logSet(
                sessionId = session.id,
                exerciseId = (lift as SaveExerciseResult.Saved).exercise.id,
                weightKg = 60.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
            )
        }
        launch(Intent(app, MainActivity::class.java))
        awaitTag(LiveSessionBarTestTags.ROOT)
        awaitTag(ACTIONS)
        compose.onNodeWithTag(ACTIONS).performClick()
        compose.onNodeWithText(LiveBarCopy.finish(LiveBarKind.WORKOUT)).performClick()
        awaitTag(SummaryTags.DONE)
        assertNull("nothing is live on the summary", runBlocking { app.container.workoutRepository.getInProgress() })
    }

    /** The planned day the live workout follows, or null. */
    private fun followed(): String? = runBlocking {
        val live = app.container.workoutRepository.getInProgress() ?: return@runBlocking null
        PendingOccurrence.followedBy(app.container, live.id)
    }

    /**
     * The app, opened by [intent]. A dialog window can outlive a case's activity and still be
     * found by the next case's queries, so each case checks it starts with nothing on screen.
     */
    private fun launch(intent: Intent) {
        val leftover = runCatching { compose.onAllNodes(isRoot()).fetchSemanticsNodes() }.getOrNull()
        check(leftover.isNullOrEmpty()) { "an earlier case is still on screen" }
        controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
    }

    /** The reminder's Start, delivered to the running activity as Android delivers it. */
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

    /** [tag] must not come within [NEVER_MS]: what would show it runs off the main thread. */
    private fun assertNeverShown(tag: String) {
        val came = runCatching {
            compose.waitUntil(timeoutMillis = NEVER_MS) {
                compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        assertFalse("$tag came up", came)
    }

    private fun reminderShown(occurrenceId: String): Boolean =
        app.getSystemService(NotificationManager::class.java)
            .activeNotifications.any { it.id == occurrenceId.hashCode() }

    private companion object {
        const val DELIVERY = "rem-occ-start-over-a-screen"
        const val MIXED_DELIVERY = "rem-occ-mixed-start-over-a-screen"
        const val ACTIONS = "live-session-actions"
        const val WAIT_MS = 20_000L
        const val NEVER_MS = 2_000L
        val PLAN_TAB = "navigation-${Route.Routines.path}"
    }
}
