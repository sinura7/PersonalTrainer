package com.sinura.personaltrainer

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.data.repository.prefs.REST_ALERTS_ASKED
import com.sinura.personaltrainer.domain.RestNotificationCopy
import com.sinura.personaltrainer.testutil.forgetFirstApplication
import com.sinura.personaltrainer.timer.RestTimerService
import com.sinura.personaltrainer.ui.workout.RestFloorTags
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Audit RT-2, through the real activity and app shell, with notifications off (Android 13 and
 * later). The "Rest alerts" sentence said it asked once, but its answer lived in the screen that
 * drew it: every workout opened and every rest page put it up again, for as long as
 * notifications stayed off, and after two refusals its Continue brought up nothing at all.
 * Answered once, Continue or Not now, it stays down on this phone; the compact row on the
 * workout and the rest page stays the way back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class, qualifiers = "w360dp-h800dp-xhdpi")
class RestAlertsAskedOnceTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var app: PersonalTrainerApp
    private var controller: ActivityController<MainActivity>? = null

    @Before
    fun setUp() {
        forgetFirstApplication()
        app = ApplicationProvider.getApplicationContext()
        // Notifications stay off, and the first-open walk has already had its turn, so the only
        // question on screen is the rest sentence.
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications(on = false)
        app.markColdStartIntroShown()
        runBlocking {
            app.container.preferencesRepository.setOnboardingComplete(true)
            app.container.preferencesRepository.setLaunchPermissionsAsked(true)
        }
        forgetTheAnswer()
    }

    @After
    fun tearDown() {
        app.container.restTimerController.stop()
        controller?.pause()?.stop()?.destroy()
        compose.waitForIdle()
        // The settings file is one per process: the next case, and the next class, start unasked.
        forgetTheAnswer()
        // So a later class in this process does not build its ViewModels over this app either.
        forgetFirstApplication()
    }

    private fun forgetTheAnswer() = runBlocking {
        app.container.preferencesRepository.accountSyncSettingsStore.data.edit { it.remove(REST_ALERTS_ASKED) }
    }

    @Test
    fun notNowOnTheWorkoutIsNotAskedAgainOnTheNextWorkoutOrTheRestPage() {
        val live = startAWorkout()
        launch(floorTap(live))
        awaitText(RestNotificationCopy.SENTENCE)
        compose.onNodeWithText(RestNotificationCopy.NOT_NOW).performClick()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertDoesNotExist()
        awaitAsked()

        leaveTheWorkout()
        controller!!.newIntent(floorTap(live))
        awaitTag(WorkoutTestTags.CONTENT)
        assertNeverShown(RestNotificationCopy.SENTENCE)

        app.container.restTimerController.start(90, live)
        openTheRestPage()
        assertNeverShown(RestNotificationCopy.SENTENCE)
        compose.onNodeWithText(RestNotificationCopy.RECOVERY_TITLE, useUnmergedTree = true).assertExists()
    }

    @Test
    fun notNowIsNotAskedAgainWhenTheAppOpensAgain() {
        val live = startAWorkout()
        launch(floorTap(live))
        awaitText(RestNotificationCopy.SENTENCE)
        compose.onNodeWithText(RestNotificationCopy.NOT_NOW).performClick()
        awaitAsked()

        closeTheApp()
        launch(floorTap(live))
        awaitTag(WorkoutTestTags.CONTENT)
        assertNeverShown(RestNotificationCopy.SENTENCE)
    }

    @Test
    fun continueCountsAsTheAnswerToo() {
        val live = startAWorkout()
        launch(floorTap(live))
        awaitText(RestNotificationCopy.SENTENCE)
        compose.onNodeWithText(RestNotificationCopy.CONTINUE).performClick()
        awaitAsked()

        closeTheApp()
        launch(floorTap(live))
        awaitTag(WorkoutTestTags.CONTENT)
        assertNeverShown(RestNotificationCopy.SENTENCE)
    }

    /**
     * The rest page opened a moment after Not now, while the answer is still being written (the
     * settings file busy with another write): it does not ask, then or once the write lands.
     */
    @Test
    fun theRestPageDoesNotAskWhileTheAnswerIsStillBeingSaved() {
        val live = startAWorkout()
        launch(floorTap(live))
        awaitText(RestNotificationCopy.SENTENCE)
        holdTheSettingsFile()
        try {
            compose.onNodeWithText(RestNotificationCopy.NOT_NOW).performClick()
            app.container.restTimerController.start(90, live)
            openTheRestPage()
            assertNeverShown(RestNotificationCopy.SENTENCE)
        } finally {
            letTheSettingsFileGo()
        }
        awaitAsked()
        compose.waitForIdle()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertDoesNotExist()
    }

    /** The app closed while the answer is still being written: the answer is kept all the same. */
    @Test
    fun anAnswerStillBeingSavedWhenTheAppClosesIsKept() {
        val live = startAWorkout()
        launch(floorTap(live))
        awaitText(RestNotificationCopy.SENTENCE)
        holdTheSettingsFile()
        try {
            compose.onNodeWithText(RestNotificationCopy.NOT_NOW).performClick()
            closeTheApp()
        } finally {
            letTheSettingsFileGo()
        }
        awaitAsked()

        launch(floorTap(live))
        awaitTag(WorkoutTestTags.CONTENT)
        assertNeverShown(RestNotificationCopy.SENTENCE)
    }

    /** Leaving without answering is not an answer: the next workout asks. */
    @Test
    fun aSentenceLeftUnansweredIsAskedAgain() {
        val live = startAWorkout()
        launch(floorTap(live))
        awaitText(RestNotificationCopy.SENTENCE)

        closeTheApp()
        assertFalse(runBlocking { app.container.preferencesRepository.restAlertsAsked.first() })
        launch(floorTap(live))
        awaitText(RestNotificationCopy.SENTENCE)
        compose.onNodeWithText(RestNotificationCopy.NOT_NOW).performClick()
    }

    @Test
    fun withNotificationsOnNothingIsAskedOrRecorded() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications(on = true)
        val live = startAWorkout()
        launch(floorTap(live))
        awaitTag(WorkoutTestTags.CONTENT)
        assertNeverShown(RestNotificationCopy.SENTENCE)
        assertFalse(
            "notifications turned off later still get the one sentence",
            runBlocking { app.container.preferencesRepository.restAlertsAsked.first() },
        )
    }

    private var release: CountDownLatch? = null
    private var holder: Thread? = null

    /** Another write holds the settings file (a slow disk): writes queue behind it. */
    private fun holdTheSettingsFile() {
        val holding = CountDownLatch(1)
        val gate = CountDownLatch(1)
        release = gate
        holder = thread(name = "settings-holder") {
            runBlocking {
                app.container.preferencesRepository.accountSyncSettingsStore.data.edit {
                    holding.countDown()
                    gate.await(WAIT_MS, TimeUnit.MILLISECONDS)
                }
            }
        }
        check(holding.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "the holder never got the settings file" }
    }

    private fun letTheSettingsFileGo() {
        release?.countDown()
        holder?.join(WAIT_MS)
        release = null
        holder = null
    }

    /** As a phone reports them: off when the permission is refused, on when it is given. */
    private fun notifications(on: Boolean) {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(on)
    }

    /** A workout with one lift, so the workout shows its rest clock. */
    private fun startAWorkout(): String = runBlocking {
        val workouts = app.container.workoutRepository
        val session = (workouts.startFreeWorkoutSafely("Legs") as StartSessionOutcome.Started).session
        val squat = when (val made = app.container.exerciseRepository.createCustom("Rest alerts squat", "Quads")) {
            is SaveExerciseResult.Saved -> made.exercise
            is SaveExerciseResult.DuplicateName -> made.existing
            SaveExerciseResult.MissingMuscle -> error("the squat's muscle was refused")
        }
        workouts.addExerciseToSession(
            sessionId = session.id,
            exercise = squat,
            targetSets = 3,
            targetReps = 5,
            targetWeightKg = 60.0,
            restSeconds = 90,
        )
        session.id
    }

    /** The rest notification's tap, which opens the workout over whatever is showing. */
    private fun floorTap(sessionId: String): Intent =
        Intent(app, MainActivity::class.java).putExtra(RestTimerService.EXTRA_SESSION_ID, sessionId)

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

    /** The activity goes, its screens' remembered state with it; the saved settings stay. */
    private fun closeTheApp() {
        controller!!.pause().stop().destroy()
        controller = null
        compose.waitForIdle()
    }

    /** Back from the workout, so the next tap opens a new one. */
    private fun leaveTheWorkout() {
        val activity: ComponentActivity = controller!!.get()
        activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = WAIT_MS) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.CONTENT)).fetchSemanticsNodes().isEmpty()
        }
    }

    /** The running rest's clock on the workout opens the rest page. */
    private fun openTheRestPage() {
        compose.waitUntil(timeoutMillis = WAIT_MS) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.COMPANION_CLOCK)).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(WorkoutTestTags.REST_BAR)))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        val clock = compose.onAllNodes(hasTestTag(WorkoutTestTags.COMPANION_CLOCK))
        if (clock.fetchSemanticsNodes().isNotEmpty()) {
            clock[0].performClick()
        } else {
            compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(WorkoutTestTags.REST_BAR)))[0]
                .performClick()
        }
        awaitTag(RestFloorTags.ROOT)
    }

    /**
     * The answer is written by the app's settings, and DataStore runs the write's change on the
     * caller's thread, the main one. Here the main thread runs only when a test lets it, so the
     * wait lets it run; on a phone it always does.
     */
    private fun awaitAsked() {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (!runBlocking { app.container.preferencesRepository.restAlertsAsked.first() }) {
            check(System.currentTimeMillis() < deadline) { "the answer was never saved" }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
        }
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

    /** [text] must not come within [NEVER_MS]: the sentence goes up from an effect, after a read. */
    private fun assertNeverShown(text: String) {
        val came = runCatching {
            compose.waitUntil(timeoutMillis = NEVER_MS) {
                compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        assertFalse("\"$text\" came up again", came)
    }

    private companion object {
        const val WAIT_MS = 20_000L
        const val NEVER_MS = 2_000L
    }
}
