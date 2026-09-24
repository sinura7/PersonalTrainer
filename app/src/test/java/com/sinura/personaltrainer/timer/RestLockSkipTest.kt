package com.sinura.personaltrainer.timer

import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The lock glance's Skip names the rest the glance drew, as the notification's does (W2b-3,
 * owner decision of 24 September 2026). It used to stop whatever ran when the tap was read:
 * a rest that ran out as Skip was tapped lost its "rest done" (the gold "Back to the bar" never
 * showed and the done card left the shade), and a newer rest the glance had not caught up with
 * was ended.
 *
 * Composed for real through [RestLockActivity] and the app's controller. A tap is the Skip as
 * drawn: its click action is taken while the glance shows the rest, the store then moves on
 * (a finish, the next set's rest, a ±15), and the action runs before the glance redraws.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class, qualifiers = "w360dp-h800dp-xhdpi")
class RestLockSkipTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var app: PersonalTrainerApp
    private var scenario: ActivityScenario<RestLockActivity>? = null
    private lateinit var glance: RestLockActivity

    private val rest get() = app.container.restTimerController
    private val store get() = app.container.restTimerStore

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        RestTimerCompletion.reset()
        rest.stop(fromService = true)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        scenario?.close()
        RestTimerCompletion.reset()
        rest.stop(fromService = true)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun aSkipTappedAsTheRestRanOutKeepsItDone() {
        rest.start(90, "session-1")
        val shown = store.current()
        openGlance()
        val skip = drawnSkip()

        finish(shown)
        skip()
        compose.waitForIdle()

        assertEquals("still done, not skipped", shown.timerId, rest.lastCompletedTimerId.value)
        assertNotNull(
            "the done card stays in the shade",
            app.getSystemService(NotificationManager::class.java)
                .activeNotifications.firstOrNull { it.id == RestTimerNotifications.DONE_ID },
        )
        assertFalse("the glance stays to say so", glance.isFinishing)
        compose.onNodeWithTag(RestLockTags.BACK_TO_BAR).assertIsDisplayed()
    }

    @Test
    fun aSkipForTheRestItDrewLeavesANewerRestRunning() {
        rest.start(90, "session-1")
        openGlance()
        val skip = drawnSkip()

        rest.start(120, "session-1")
        val next = store.current().timerId
        skip()
        compose.waitForIdle()

        assertTrue("the newer rest keeps running", store.current().running)
        assertEquals(next, store.current().timerId)
        assertFalse("the glance moves to it", glance.isFinishing)
        compose.onNodeWithTag(RestLockTags.SKIP).assertIsDisplayed()
    }

    @Test
    fun aSkipOnTheRestItShowsEndsItAndClosesTheGlance() {
        rest.start(90, "session-1")
        openGlance()

        compose.onNodeWithTag(RestLockTags.SKIP).performClick()

        assertFalse("the rest the glance shows ends", store.current().running)
        assertNull("a skip is not a finish", rest.lastCompletedTimerId.value)
        assertTrue("the glance closes once its rest is skipped", glance.isFinishing)
    }

    @Test
    fun aSkipDrawnJustBeforeAPlusFifteenStillEndsTheRest() {
        // To the owner a ±15 is the same rest, under a new id.
        rest.start(90, "session-1")
        openGlance()
        val skip = drawnSkip()

        rest.adjust(15)
        skip()

        assertFalse("the +15 of the rest shown ends", store.current().running)
        assertTrue("the glance closes once its rest is skipped", glance.isFinishing)
    }

    /** The glance over the lock screen, as the running card's full-screen intent opens it. */
    private fun openGlance() {
        scenario = ActivityScenario.launch<RestLockActivity>(Intent(app, RestLockActivity::class.java))
            .also { it.onActivity { activity -> glance = activity } }
        compose.onNodeWithTag(RestLockTags.SKIP).assertIsDisplayed()
    }

    /** The Skip as the glance drew it; running it later is a tap the glance has not redrawn for. */
    private fun drawnSkip(): () -> Unit {
        val click = compose.onNodeWithTag(RestLockTags.SKIP).fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action
        return { checkNotNull(click) { "the drawn Skip has no click" }.invoke() }
    }

    /** The alarm's finish for [shown]: "rest done" is published, and the store empties. */
    private fun finish(shown: RestTimerSnapshot) {
        runBlocking {
            assertTrue(
                RestTimerCompletion.completeOnce(
                    context = app,
                    incomingTimerId = shown.timerId,
                    expectedTimerId = shown.timerId,
                    deadlineElapsedRealtime = shown.endsAtElapsedRealtime,
                    sessionId = shown.sessionId,
                    nowElapsedRealtime = shown.endsAtElapsedRealtime + 1L,
                    playCue = false,
                ),
            )
        }
    }
}
