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
        compose.onNodeWithTag(HomeTags.TODAY_RESUME).assertIsDisplayed()
            .assertTextContains("Resume workout", substring = true)
            .assertTextContains("Workout in progress", substring = true)
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
}
