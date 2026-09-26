package com.sinura.personaltrainer.ui.navigation

import android.app.Application
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit UI-1: a reminder tap switched to Home whatever was open, which popped a live workout to
 * reach a start that would be refused. With a session live the tap now moves nothing, is used up
 * here rather than by Home, and says why.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderHandoffHostTest {
    @get:Rule val compose = createComposeRule()

    private var start by mutableStateOf<String?>(null)
    private var review by mutableStateOf<String?>(null)
    private var live by mutableStateOf(false)
    private val tabs = mutableListOf<String>()

    private fun host(restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            ReminderHandoffHost(
                openStartId = start,
                openReviewId = review,
                sessionLive = live,
                goToTab = { tabs += it },
                onStartConsumed = { start = null },
                onReviewConsumed = { review = null },
            )
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
    }

    @Test
    fun aStartTappedDuringALiveSessionStaysWhereItIsAndSaysWhy() {
        live = true
        host()

        start = "occ-1"
        compose.waitForIdle()

        assertTrue("the live workout must not be left for Home", tabs.isEmpty())
        assertNull("the tap is taken here, so Home never starts it", start)
        compose.onNodeWithText(ReminderCopy.LIVE_TITLE).assertIsDisplayed()
        compose.onNodeWithText(ReminderCopy.LIVE_START_BODY).assertIsDisplayed()

        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).performClick()
        compose.onNodeWithText(ReminderCopy.LIVE_TITLE).assertDoesNotExist()
        assertTrue(tabs.isEmpty())
    }

    @Test
    fun aBodyTapDuringALiveSessionStaysWhereItIsAndSaysWhy() {
        live = true
        host()

        review = "occ-1"
        compose.waitForIdle()

        assertTrue(tabs.isEmpty())
        assertNull(review)
        compose.onNodeWithText(ReminderCopy.LIVE_REVIEW_BODY).assertIsDisplayed()
    }

    @Test
    fun aTapWithNoSessionLiveGoesHomeAndLeavesTheTapForHome() {
        host()

        start = "occ-1"
        compose.waitForIdle()

        assertEquals(listOf(Route.Home.path), tabs)
        assertEquals("Home consumes it when it starts", "occ-1", start)
        compose.onNodeWithText(ReminderCopy.LIVE_TITLE).assertDoesNotExist()
    }

    /** A cold start reads "no session" until the database answers; a tap still waiting is held. */
    @Test
    fun aTapNotYetTakenWhenTheLiveSessionIsKnownIsHeld() {
        host()
        start = "occ-1"
        compose.waitForIdle()

        live = true
        compose.waitForIdle()

        assertNull(start)
        compose.onNodeWithText(ReminderCopy.LIVE_START_BODY).assertIsDisplayed()
    }

    @Test
    fun theExplanationSurvivesTheActivityBeingRecreated() {
        val restoration = StateRestorationTester(compose)
        live = true
        host(restoration)
        start = "occ-1"
        compose.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(ReminderCopy.LIVE_START_BODY).assertIsDisplayed()
        assertTrue(tabs.isEmpty())
    }
}
