package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Blueprint screen 06 states (A32): loading, empty, first time (also the failed-read state), last. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class WorkoutPreviewStatesRenderTest {
    @get:Rule val compose = createComposeRule()

    private var begins = 0
    private var adds = 0

    private fun lift(name: String, hint: PreviewHint?) = PreviewLift(
        name = name, sets = 3, reps = 8, seconds = null, startWeightKg = 40.0, hint = hint, warmup = false,
    )

    private fun show(lifts: List<PreviewLift>, loading: Boolean = false) {
        val model = WorkoutPreviewModel(title = "Upper A", minutes = 40, lifts = lifts, hintsLoading = loading)
        compose.setContent {
            PersonalTrainerTheme {
                WorkoutPreviewContent(
                    model = model,
                    unit = WeightUnit.KG,
                    onBegin = { begins++ },
                    onBack = {},
                    onReorder = {},
                    onAddLift = { adds++ },
                )
            }
        }
    }

    @Test
    fun loadingHintsShowTheSkeletonAndKeepBeginAvailable() {
        show(lifts = listOf(lift(name = "Bench Press", hint = null)), loading = true)
        // The skeleton holds back briefly so a fast read never flashes it.
        compose.mainClock.advanceTimeBy(milliseconds = 2_000)
        compose.onNodeWithTag(WorkoutPreviewTags.LOADING).assertExists()
        compose.onNodeWithTag(WorkoutPreviewTags.BEGIN).performClick()
        assertEquals(1, begins)
    }

    @Test
    fun emptyRoutineOffersAddALiftInsteadOfBegin() {
        show(lifts = emptyList())
        compose.onNodeWithTag(WorkoutPreviewTags.BEGIN).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutPreviewTags.ADD_LIFT).assertIsDisplayed().performClick()
        assertEquals(1, adds)
    }

    @Test
    fun noHistoryOrAFailedReadReadsFirstTime() {
        show(lifts = listOf(lift(name = "Bench Press", hint = PreviewHint.FirstTime), lift(name = "Row", hint = PreviewHint.FirstTime)))
        assertEquals(2, compose.onAllNodesWithText("First time", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun knownLiftReadsItsLastWeight() {
        show(lifts = listOf(lift(name = "Bench Press", hint = PreviewHint.Last(weightKg = 60.0))))
        compose.onNodeWithText("Last 60", substring = true).assertExists()
    }

    @Test
    fun failedHintReadBuildsFirstTimeNotLoading() {
        val model = WorkoutPreview.build(title = "Upper A", routine = null, minutes = 0, lastWeightKg = emptyMap(), unit = WeightUnit.KG)
        assertEquals(false, model.hintsLoading)
    }
}
