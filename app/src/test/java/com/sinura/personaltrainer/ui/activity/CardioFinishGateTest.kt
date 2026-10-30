package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A24 / D12: cardio Finish is enabled only once the clock reaches one minute. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class CardioFinishGateTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun gateOpensAtSixtySeconds() {
        assertFalse(CardioFinishGate.isOpen(elapsedSeconds = 0))
        assertFalse(CardioFinishGate.isOpen(elapsedSeconds = 59))
        assertTrue(CardioFinishGate.isOpen(elapsedSeconds = 60))
    }

    @Test
    fun finishWaitsWithItsReasonThenOpensOnTheMinute() {
        val elapsed = mutableLongStateOf(59L)
        var finishes = 0
        compose.setContent {
            PersonalTrainerTheme {
                CardioActionDock(
                    elapsedSeconds = elapsed.longValue, typeName = "Run", finishing = false,
                    onFinish = { finishes++ }, onLeaveRunning = {}, onDiscard = {},
                )
            }
        }
        compose.onNodeWithTag(CardioTags.FINISH).assertIsNotEnabled()
            .assertTextContains("Finish Run", substring = true)
            .assertTextContains("Available after 1 min", substring = true)
        assertEquals(0, finishes)
        elapsed.longValue = 60L
        compose.waitForIdle()
        compose.onNodeWithTag(CardioTags.FINISH).assertIsEnabled()
            .assertTextContains("Finish Run", substring = true)
            .performClick()
        assertEquals(1, finishes)
    }
}
