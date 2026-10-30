package com.sinura.personaltrainer.ui.home

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Blueprint 01_today dock: the primary per state (A20, A27). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class TodayDockRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun plannedDayPrimaryIsStartWorkoutWithDayAndLiftCount() {
        var starts = 0
        compose.setContent {
            PersonalTrainerTheme {
                TodayPrimaryAction(todayStart = TodayDockStart(dayName = "Upper A", liftCount = 5, onStart = { starts++ }), onResume = null)
            }
        }
        compose.onNodeWithTag(HomeTags.TODAY_START).assertIsDisplayed()
            .assertTextContains("Start workout", substring = true)
            .assertTextContains("Upper A · 5 lifts", substring = true)
            .performClick()
        assertEquals(1, starts)
        compose.onNodeWithTag(HomeTags.TODAY_RESUME).assertDoesNotExist()
    }

    @Test
    fun liveSessionPrimaryIsResumeWorkout() {
        var resumed = 0
        compose.setContent {
            PersonalTrainerTheme {
                TodayPrimaryAction(todayStart = TodayDockStart(dayName = "Upper A", liftCount = 5, onStart = {}), onResume = { resumed++ })
            }
        }
        compose.onNodeWithTag(HomeTags.TODAY_HERO).assertTextContains("Workout in progress")
        compose.onNodeWithTag(HomeTags.TODAY_RESUME).assertIsDisplayed()
            .assertTextContains("Resume workout", substring = true)
            .performClick()
        assertEquals(1, resumed)
        compose.onNodeWithTag(HomeTags.TODAY_START).assertDoesNotExist()
    }

    @Test
    fun noPlannedStrengthDayLeavesNoTodayPrimary() {
        compose.setContent {
            PersonalTrainerTheme { TodayPrimaryAction(todayStart = null, onResume = null) }
        }
        compose.onNodeWithTag(HomeTags.TODAY_START).assertDoesNotExist()
        compose.onNodeWithTag(HomeTags.TODAY_RESUME).assertDoesNotExist()
    }

    @Test
    fun emptyStateShowsNoPlanYetAndBuildYourWeek() {
        var built = 0
        compose.setContent {
            PersonalTrainerTheme {
                TodayPrimaryAction(todayStart = null, onResume = null, onBuildWeek = { built++ })
            }
        }
        compose.onNodeWithTag(HomeTags.TODAY_HERO).assertTextContains("No plan yet")
        compose.onNodeWithTag(HomeTags.TODAY_BUILD_WEEK).assertIsDisplayed()
            .assertTextContains("Build your week", substring = true)
            .performClick()
        assertEquals(1, built)
    }

    @Test
    fun firstTimeStateCaptionsTheStartWithYourFirstWorkout() {
        compose.setContent {
            PersonalTrainerTheme {
                TodayPrimaryAction(
                    todayStart = TodayDockStart(dayName = "Upper A", liftCount = 5, onStart = {}, firstTime = true),
                    onResume = null,
                )
            }
        }
        compose.onNodeWithTag(HomeTags.TODAY_HERO).assertTextContains("Your first workout")
        compose.onNodeWithTag(HomeTags.TODAY_START).assertIsDisplayed()
    }

    @Test
    fun aReturningUserHasNoFirstWorkoutCaption() {
        compose.setContent {
            PersonalTrainerTheme {
                TodayPrimaryAction(todayStart = TodayDockStart(dayName = "Upper A", liftCount = 5, onStart = {}), onResume = null)
            }
        }
        compose.onNodeWithTag(HomeTags.TODAY_HERO).assertDoesNotExist()
    }

    @Test
    fun errorStateIsAnInlineCouldNotLoadTodayWithTryAgain() {
        var retries = 0
        compose.setContent {
            PersonalTrainerTheme {
                HomeReadRecovery(state = HomeUiState(readProblem = "Your plan could not be read."), onRetry = { retries++ })
            }
        }
        compose.onNodeWithTag(HomeTags.TODAY_ERROR).assertTextContains("Could not load today")
        compose.onNodeWithTag(HomeTags.RETRY).assertTextContains("Try again").performClick()
        assertEquals(1, retries)
    }
}
