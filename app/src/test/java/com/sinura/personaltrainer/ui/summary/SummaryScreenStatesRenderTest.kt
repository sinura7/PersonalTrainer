package com.sinura.personaltrainer.ui.summary

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.SessionHighlight
import com.sinura.personaltrainer.domain.SummaryCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSummary
import com.sinura.personaltrainer.ui.components.ScreenSkeletonTags
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Blueprint 14_summary — loading, error, empty, first-time, and receipt (A40). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class SummaryScreenStatesRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingShowsSkeleton() {
        mount(
            WorkoutSummaryUiState(sessionId = "s1", isLoading = true),
        )
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag(ScreenSkeletonTags.ROOT))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(ScreenSkeletonTags.ROOT).assertIsDisplayed()
    }

    @Test
    fun errorFailedReadShowsRetry() {
        mount(WorkoutSummaryUiState(sessionId = "s1", isLoading = false, failed = true))
        compose.onNodeWithText(SummaryCopy.UNAVAILABLE_TITLE).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.RETRY).assertIsDisplayed()
    }

    @Test
    fun emptyNoWorkingSets() {
        mount(
            WorkoutSummaryUiState(
                sessionId = "s1",
                isLoading = false,
                savedConfirmed = true,
                summary = WorkoutSummary(sessionId = "s1", title = "Legs"),
            ),
        )
        compose.onNodeWithText(SummaryCopy.SAVED_NO_WORK_TITLE).assertIsDisplayed()
    }

    @Test
    fun bodyweightRepsTileUsesSpokenLabel() {
        mount(
            WorkoutSummaryUiState(
                sessionId = "bw",
                isLoading = false,
                savedConfirmed = true,
                summary = WorkoutSummary(
                    sessionId = "bw",
                    title = "Bodyweight",
                    workingSets = 1,
                    bodyweightReps = 35,
                ),
            ),
        )
        compose.onNodeWithTag(SummaryTags.BODYWEIGHT_REPS).assertIsDisplayed()
        compose.onNodeWithContentDescription("Bodyweight reps 35").assertIsDisplayed()
    }

    @Test
    fun firstWorkoutCaptionAndStatTiles() {
        mount(
            WorkoutSummaryUiState(
                sessionId = "s1",
                isLoading = false,
                savedConfirmed = true,
                firstWorkoutSaved = true,
                summary = WorkoutSummary(
                    sessionId = "s1",
                    title = "Push",
                    workingSets = 6,
                    volumeKg = 1200.0,
                    highlights = listOf(
                        SessionHighlight(
                            exerciseId = "ex1",
                            exerciseName = "Bench",
                            topSet = null,
                            workingSets = 3,
                            volumeKg = 1200.0,
                            records = setOf(PersonalRecordKind.WEIGHT),
                        ),
                    ),
                ),
            ),
        )
        compose.onNodeWithText("WORKOUT SAVED").assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.FIRST_WORKOUT).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.WORKING_SETS).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.HEADLINE).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.PR_COUNT).assertIsDisplayed()
    }

    private fun mount(state: WorkoutSummaryUiState) {
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalWeightUnit provides WeightUnit.KG,
                    LocalReducedMotion provides true,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        WorkoutSummaryContent(
                            state = state,
                            unit = WeightUnit.KG,
                            onRetry = {},
                            onOpenSession = {},
                            onDone = {},
                        )
                    }
                }
            }
        }
    }
}
