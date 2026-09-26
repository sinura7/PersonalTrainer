package com.sinura.personaltrainer.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.ReminderCopy
import com.sinura.personaltrainer.ui.components.ConfirmActionTags
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit UI-1: a reminder tap switched to Home whatever was open, which put Home in place of a
 * live workout to reach a start that would be refused, and the reminder was used up at the tap.
 * The tap is now decided from the database as it arrives: Home is handed it only when nothing is
 * live, another session holds it with a word of why, and the tapped day's own session opens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderHandoffHostTest {
    @get:Rule val compose = createComposeRule()

    private var start by mutableStateOf<String?>(null)
    private var delivery by mutableStateOf<String?>(null)
    private var review by mutableStateOf<String?>(null)
    private var verdict: suspend (String) -> TapVerdict = { TapVerdict.NothingLive }
    private var forHome: HomeTap? = null
    private val tabs = mutableListOf<String>()
    private val opened = mutableListOf<Pair<String, Boolean>>()
    private val used = mutableListOf<Pair<String, String>>()

    private fun host(restoration: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            forHome = ReminderHandoffHost(
                openStartId = start,
                openDeliveryId = delivery,
                openReviewId = review,
                verdict = { verdict(it) },
                goToTab = { tabs += it },
                openLive = { sessionId, cardio -> opened += sessionId to cardio },
                useReminder = { occurrenceId, deliveryId -> used += occurrenceId to deliveryId },
                onStartConsumed = {
                    start = null
                    delivery = null
                },
                onReviewConsumed = { review = null },
            )
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
    }

    private fun tapStart() {
        start = "occ-1"
        delivery = "rem-occ-1"
        compose.waitForIdle()
    }

    @Test
    fun aStartTappedWhileAnotherSessionIsLiveStaysWhereItIsAndSaysWhy() {
        verdict = { TapVerdict.OtherSessionLive }
        host()

        tapStart()

        assertTrue("the live workout must not be left for Home", tabs.isEmpty())
        assertNull("Home is never handed it", forHome)
        assertNull("the tap is taken here", start)
        assertTrue("its reminder is not used", used.isEmpty())
        compose.onNodeWithText(ReminderCopy.LIVE_TITLE).assertIsDisplayed()
        compose.onNodeWithText(ReminderCopy.LIVE_START_BODY).assertIsDisplayed()

        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).performClick()
        compose.onNodeWithText(ReminderCopy.LIVE_TITLE).assertDoesNotExist()
        assertTrue(tabs.isEmpty())
    }

    @Test
    fun aBodyTapWhileAnotherSessionIsLiveStaysWhereItIsAndSaysWhy() {
        verdict = { TapVerdict.OtherSessionLive }
        host()

        review = "occ-1"
        compose.waitForIdle()

        assertTrue(tabs.isEmpty())
        assertNull(review)
        compose.onNodeWithText(ReminderCopy.LIVE_REVIEW_BODY).assertIsDisplayed()
    }

    @Test
    fun withNothingLiveTheTapGoesToHomeWhichIsHandedIt() {
        host()

        tapStart()

        assertEquals(listOf(Route.Home.path), tabs)
        assertEquals(HomeTap(startId = "occ-1", deliveryId = "rem-occ-1", reviewId = null), forHome)
        assertEquals("Home consumes it when it starts", "occ-1", start)
        compose.onNodeWithText(ReminderCopy.LIVE_TITLE).assertDoesNotExist()
    }

    /** On a cold start the database answers late; until it does, nothing moves and Home waits. */
    @Test
    fun untilTheDatabaseAnswersHomeIsNotHandedTheTap() {
        val answer = CompletableDeferred<TapVerdict>()
        verdict = { answer.await() }
        host()

        tapStart()
        assertNull(forHome)
        assertTrue(tabs.isEmpty())

        answer.complete(TapVerdict.OtherSessionLive)
        compose.waitForIdle()
        assertNull("held, never handed to Home", forHome)
        compose.onNodeWithText(ReminderCopy.LIVE_START_BODY).assertIsDisplayed()
    }

    /** Its own planned day, started early or from Home: Start opens it and uses the reminder. */
    @Test
    fun aStartForTheLiveSessionsOwnDayOpensItAndUsesTheReminder() {
        verdict = { TapVerdict.ThisSessionLive(sessionId = "session-1", cardio = false) }
        host()

        tapStart()

        assertEquals(listOf("session-1" to false), opened)
        assertEquals(listOf("occ-1" to "rem-occ-1"), used)
        assertNull(start)
        assertTrue(tabs.isEmpty())
        compose.onNodeWithText(ReminderCopy.LIVE_TITLE).assertDoesNotExist()
    }

    @Test
    fun aBodyTapForTheLiveSessionsOwnDayOpensItAndUsesNothing() {
        verdict = { TapVerdict.ThisSessionLive(sessionId = "cardio-1", cardio = true) }
        host()

        review = "occ-1"
        compose.waitForIdle()

        assertEquals(listOf("cardio-1" to true), opened)
        assertTrue("a body tap never marks a reminder started", used.isEmpty())
        assertNull(review)
    }

    @Test
    fun theExplanationSurvivesTheActivityBeingRecreated() {
        val restoration = StateRestorationTester(compose)
        verdict = { TapVerdict.OtherSessionLive }
        host(restoration)
        tapStart()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(ReminderCopy.LIVE_START_BODY).assertIsDisplayed()
        assertTrue(tabs.isEmpty())
    }
}
