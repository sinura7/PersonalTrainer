package com.sinura.personaltrainer.ui.plan

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

/** Blueprint 4.2 (A28): one render check per Plan week state. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class PlanWeekStatesRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyShowsNoWeekYetAndBuildTheWeek() {
        var built = 0
        compose.setContent { PersonalTrainerTheme { PlanWeekEmpty(onBuildWeek = { built++ }) } }
        compose.onNodeWithText("No week yet").assertIsDisplayed()
        compose.onNodeWithTag(PlanWeekTags.BUILD_WEEK).assertIsDisplayed().performClick()
        assertEquals(1, built)
        assertTrue(PlanWeekState.isEmpty(PlanUiState(isLoading = false)))
    }

    @Test
    fun loadingKeepsTheHeaderAndDrawsSkeletonRows() {
        compose.setContent {
            PersonalTrainerTheme { PlanWeekLoading(onOpenRoutines = {}, onOpenStartSheet = {}) }
        }
        compose.onNodeWithTag(PlanWeekTags.LOADING).assertExists()
        compose.onNodeWithTag(PlanTags.TITLE, useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(com.sinura.personaltrainer.ui.components.ScreenSkeletonTags.ROOT).assertExists()
    }

    @Test
    fun errorIsAPanelWithTheMessageAndTryAgain() {
        var retried = 0
        compose.setContent {
            PersonalTrainerTheme { PlanWeekError(message = "Couldn't load the week.", onRetry = { retried++ }) }
        }
        compose.onNodeWithText("Couldn't load the week.").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithTag(PlanWeekTags.RETRY).performClick()
        assertEquals(1, retried)
    }

    @Test
    fun firstTimeLabelsTheSuggestedWeek() {
        compose.setContent { PersonalTrainerTheme { PlanSuggestedCaption() } }
        compose.onNodeWithText("Suggested. Change anything.").assertIsDisplayed()
    }

    @Test
    fun resumeLinksToTheLiveSession() {
        var opened = 0
        compose.setContent { PersonalTrainerTheme { PlanLiveRow(onOpen = { opened++ }) } }
        compose.onNodeWithText("Workout in progress").assertIsDisplayed()
        compose.onNodeWithTag(PlanWeekTags.LIVE_ROW).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun processDeathStartsLoadingNotEmptyUntilTheDatabaseAnswers() {
        // A recreated PlanViewModel starts from PlanUiState() and reloads from the DB; until it
        // does, the screen must show Loading, never the No week yet panel.
        assertTrue(PlanUiState().isLoading)
        assertFalse(PlanWeekState.isEmpty(PlanUiState()))
    }
}
