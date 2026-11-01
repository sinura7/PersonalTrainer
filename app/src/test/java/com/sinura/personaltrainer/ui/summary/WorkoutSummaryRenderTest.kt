package com.sinura.personaltrainer.ui.summary

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSummary
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.sinura.personaltrainer.ui.units.DateCopy
import java.time.LocalDate

/** v4 V6 (blueprint 3.6, 4.7): the receipt's "Up next" line. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class WorkoutSummaryRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 11, 2).toEpochDay() // a Monday
    private val opened = mutableListOf<Long?>()

    @Test
    fun upNextNamesTheNextPlannedDay() {
        mount(SummaryUpNext(epochDay = today + 2, routineName = "Push"))
        compose.onNodeWithTag(SummaryTags.UP_NEXT).performScrollTo().assertIsDisplayed()
        val day = LocalDate.ofEpochDay(today + 2)
        compose.onNodeWithText("Up next: ${DateCopy.weekdayShort(day)} 4, Push", useUnmergedTree = true).assertExists()
    }

    @Test
    fun upNextTomorrowCopy() {
        mount(SummaryUpNext(epochDay = today + 1, routineName = "Legs"))
        compose.onNodeWithText("Up next: tomorrow, Legs").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noPlanSaysPlanYourWeek() {
        mount(SummaryUpNext(epochDay = null, routineName = null))
        compose.onNodeWithText("Up next: plan your week").performScrollTo().performClick()
        assertEquals(listOf<Long?>(null), opened)
    }

    @Test
    fun tapOpensPlanOnThatDay() {
        mount(SummaryUpNext(epochDay = today + 3, routineName = "Pull"))
        compose.onNodeWithTag(SummaryTags.UP_NEXT).performScrollTo().performClick()
        assertEquals(listOf<Long?>(today + 3), opened)
    }

    @Test
    fun aNextWeekDaySaysNextWeekAndOpensPlanWithoutAScroll() {
        mount(SummaryUpNext(epochDay = today + 1, routineName = "Legs", nextWeek = true))
        val day = LocalDate.ofEpochDay(today + 1)
        compose.onNodeWithText("Up next: ${DateCopy.weekdayShort(day)} next week, Legs")
            .performScrollTo().assertIsDisplayed().performClick()
        // Plan shows the current week only, so the tap must not ask it to find that day.
        assertEquals(listOf<Long?>(null), opened)
    }

    @Test
    fun hiddenUntilThePlanReadSettles() {
        mount(null)
        assertTrue(compose.onAllNodesWithTag(SummaryTags.UP_NEXT).fetchSemanticsNodes().isEmpty())
    }

    private fun mount(upNext: SummaryUpNext?) {
        val state = WorkoutSummaryUiState(
            sessionId = "s1",
            isLoading = false,
            savedConfirmed = true,
            summary = WorkoutSummary(sessionId = "s1", title = "Push", workingSets = 3, volumeKg = 900.0),
            upNext = upNext,
            todayEpochDay = today,
        )
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    WorkoutSummaryContent(
                        state = state,
                        unit = WeightUnit.KG,
                        onRetry = {},
                        onOpenSession = {},
                        onDone = {},
                        onOpenPlanDay = { opened += it },
                    )
                }
            }
        }
    }
}
