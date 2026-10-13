package com.sinura.personaltrainer.ui.summary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.SummaryCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSummary
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Native receipt branches; repository fault/Retry inventories are separately verified on JVM. */
class P07ReceiptStatesInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingUnreadableSavedComputationFailureAndNoWorkHaveDistinctReceipts() {
        var state by mutableStateOf(WorkoutSummaryUiState(isLoading = false, missing = true))
        var retries = 0
        var done = 0
        var opened: String? = null
        compose.setContent {
            PersonalTrainerTheme {
                WorkoutSummaryContent(state = state, unit = WeightUnit.KG,
                    onRetry = { retries++ }, onOpenSession = { opened = it }, onDone = { done++ })
            }
        }
        compose.onNodeWithText(SummaryCopy.MISSING_TITLE).assertIsDisplayed()
        compose.onNodeWithText(SummaryCopy.MISSING_BODY).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.RETRY).assertDoesNotExist()
        capture("missing")
        compose.onNodeWithTag(SummaryTags.DONE).performClick()
        assertEquals(1, done)

        compose.runOnIdle { state = WorkoutSummaryUiState(isLoading = false, failed = true) }
        compose.onNodeWithText(SummaryCopy.UNAVAILABLE_TITLE).assertIsDisplayed()
        compose.onNodeWithText(SummaryCopy.UNAVAILABLE_BODY).assertIsDisplayed()
        compose.onNodeWithText(SummaryCopy.SAVED_NO_WORK_TITLE).assertDoesNotExist()
        compose.onNodeWithText(SummaryCopy.OPEN_SESSION).assertDoesNotExist()
        capture("unreadable")
        compose.onNodeWithTag(SummaryTags.RETRY).performClick()
        assertEquals(1, retries)

        compose.runOnIdle {
            state = WorkoutSummaryUiState(isLoading = false, failed = true, savedConfirmed = true, sessionId = "p07-saved-owner")
        }
        compose.onNodeWithText(SummaryCopy.SAVED_SUMMARY_UNAVAILABLE_TITLE).assertIsDisplayed()
        compose.onNodeWithText(SummaryCopy.SAVED_SUMMARY_UNAVAILABLE_BODY).assertIsDisplayed()
        capture("saved-computation-unavailable")
        compose.onNodeWithText(SummaryCopy.OPEN_SESSION).performClick()
        assertEquals("p07-saved-owner", opened)
        compose.onNodeWithTag(SummaryTags.RETRY).performClick()
        assertEquals(2, retries)

        compose.runOnIdle { state = WorkoutSummaryUiState(isLoading = false, savedConfirmed = true) }
        compose.onNodeWithText(SummaryCopy.SAVED_NO_WORK_TITLE).assertIsDisplayed()
        compose.onNodeWithText(SummaryCopy.SAVED_NO_WORK_BODY).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.RETRY).assertDoesNotExist()
        capture("saved-no-work")
    }

    @Test fun bodyweightSummaryHeadlinesItsRecordedRepsWithoutAnEmptyLoadMeasure() {
        val summary = WorkoutSummary(sessionId = "p07-bodyweight", title = "Bodyweight strength",
            performedAtMs = 1767268800000L, durationMinutes = 24, workingSets = 1, bodyweightReps = 35)
        compose.setContent {
            PersonalTrainerTheme {
                WorkoutSummaryContent(state = WorkoutSummaryUiState(isLoading = false, savedConfirmed = true,
                    sessionId = summary.sessionId, summary = summary), unit = WeightUnit.LBS,
                    onRetry = {}, onOpenSession = {}, onDone = {})
            }
        }
        compose.onNodeWithContentDescription("Bodyweight reps 35").assertIsDisplayed()
        compose.onNodeWithText(SummaryCopy.TOTAL_VOLUME).assertDoesNotExist()
        compose.onNodeWithText("0 lb").assertDoesNotExist()
        compose.onNodeWithText("0 kg").assertDoesNotExist()
        capture("bodyweight-reps")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        NativeArtifacts.write("notes-summary-$name", compose.onRoot().captureToImage().asAndroidBitmap())
    }
}
