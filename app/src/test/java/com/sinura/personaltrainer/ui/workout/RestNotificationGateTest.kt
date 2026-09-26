package com.sinura.personaltrainer.ui.workout

import android.Manifest
import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestNotificationCopy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/**
 * The "Rest alerts" sentence reads its answer from the app shell ([LocalRestAlertsAsk]), not
 * from the screen that draws it (audit RT-2). Notifications are off here (Android 15) unless a
 * case turns them on.
 */
@RunWith(RobolectricTestRunner::class)
class RestNotificationGateTest {
    @get:Rule val compose = createComposeRule()

    private var asked by mutableStateOf<Boolean?>(false)
    private var screen by mutableIntStateOf(0)
    private var marks = 0

    /** A screen that asks, drawn afresh each time [screen] changes, as a new back-stack entry is. */
    private fun showScreens(provided: Boolean = true) {
        compose.setContent {
            val ask = RestAlertsAsk(asked = asked, markAsked = { marks++; asked = true })
            if (provided) {
                CompositionLocalProvider(LocalRestAlertsAsk provides ask) {
                    key(screen) { rememberRestNotificationsEnabled() }
                }
            } else {
                key(screen) { rememberRestNotificationsEnabled() }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun notNowIsTheAnswerAndTheNextScreenDoesNotAsk() {
        showScreens()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertExists()
        compose.onNodeWithText(RestNotificationCopy.NOT_NOW).performClick()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertDoesNotExist()
        assertEquals(1, marks)

        screen++
        compose.waitForIdle()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertDoesNotExist()
        assertEquals(1, marks)
    }

    @Test
    fun continueIsTheAnswerToo() {
        showScreens()
        compose.onNodeWithText(RestNotificationCopy.CONTINUE).performClick()
        assertEquals(1, marks)

        screen++
        compose.waitForIdle()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertDoesNotExist()
    }

    /** Back, like Not now, is an answer. */
    @Test
    fun backIsTheAnswerToo() {
        showScreens()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertExists()
        ShadowDialog.getLatestDialog().onBackPressed()
        compose.waitForIdle()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertDoesNotExist()
        assertEquals(1, marks)
    }

    /** A screen left with the sentence up has not been answered: the next one asks. */
    @Test
    fun leavingWithTheSentenceUpIsNotAnAnswer() {
        showScreens()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertExists()

        screen++
        compose.waitForIdle()
        assertEquals(0, marks)
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertExists()
    }

    /** Until the saved answer is read the sentence waits; it goes up once it reads "not asked". */
    @Test
    fun theSentenceWaitsForTheSavedAnswer() {
        asked = null
        showScreens()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertDoesNotExist()

        asked = false
        compose.waitForIdle()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertExists()
    }

    /** Answered on another screen while this one had the sentence up: it goes down. */
    @Test
    fun aSentenceStillUpGoesDownWhenTheAnswerArrives() {
        showScreens()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertExists()

        asked = true
        compose.waitForIdle()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertDoesNotExist()
        assertEquals(0, marks)
    }

    /** A screen kept on the back stack with the sentence up does not bring it back once answered. */
    @Test
    fun aKeptScreenDoesNotBringTheSentenceBackOnceAnswered() {
        compose.setContent {
            val kept = rememberSaveableStateHolder()
            val ask = RestAlertsAsk(asked = asked, markAsked = { marks++; asked = true })
            CompositionLocalProvider(LocalRestAlertsAsk provides ask) {
                kept.SaveableStateProvider(screen) { rememberRestNotificationsEnabled() }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertExists()
        screen = 1
        compose.waitForIdle()
        compose.onNodeWithText(RestNotificationCopy.NOT_NOW).performClick()

        screen = 0
        compose.waitForIdle()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertDoesNotExist()
        assertEquals(1, marks)
    }

    @Test
    fun anAnswerAlreadySavedIsNotAskedAgain() {
        asked = true
        showScreens()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertDoesNotExist()
        assertEquals(0, marks)
    }

    /** A screen drawn without the app shell (a test, a preview) never asks. */
    @Test
    fun withoutTheShellNothingIsAsked() {
        showScreens(provided = false)
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertDoesNotExist()
    }

    @Test
    fun withNotificationsAllowedNothingIsAskedOrRecorded() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        showScreens()
        compose.onNodeWithText(RestNotificationCopy.SENTENCE).assertDoesNotExist()
        assertEquals(0, marks)
    }
}
