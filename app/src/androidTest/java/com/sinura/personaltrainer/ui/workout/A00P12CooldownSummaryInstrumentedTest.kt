package com.sinura.personaltrainer.ui.workout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.RestTimerMarkers
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.timer.RestTimerOverlayController
import com.sinura.personaltrainer.ui.summary.SummaryTags
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryScreen
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryViewModel
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.workout.FinishOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class A00P12CooldownSummaryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val fixture = WorkoutEntryFixture()
    private val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()

    @Before
    fun releaseExteriorFromPriorTests() {
        fixture.container.restTimerController.stop(fromService = true)
        RestTimerOverlayController.release()
    }

    @After fun cleanup() {
        RestTimerOverlayController.release()
        fixture.close()
    }

    @Test
    fun summaryScreenSkipAndBackUseDoneNotHistory() {
        fixture.seed(targetSets = 1, startViewModel = false)
        fixture.logOneWorkingSet()
        runBlocking(Dispatchers.IO) {
            val outcome = fixture.container.finishWorkout(fixture.sessionId)
            check(outcome is FinishOutcome.Finished)
        }
        var done = false
        val summaryVm = WorkoutSummaryViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to fixture.sessionId)),
            container = fixture.container,
        )
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalWeightUnit provides WeightUnit.KG,
                    LocalReducedMotion provides true,
                ) {
                    WorkoutSummaryScreen(
                        onDone = { done = true },
                        onOpenSession = { error("Summary Back must not reopen the workout") },
                        viewModel = summaryVm,
                    )
                }
            }
        }
        compose.waitUntil(15_000) {
            fixture.container.restTimerStore.current().running &&
                RestTimerMarkers.isCooldown(fixture.container.restTimerStore.current())
        }
        compose.waitUntil(15_000) {
            !summaryVm.uiState.value.isLoading && summaryVm.uiState.value.summary.workingSets > 0
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(SummaryTags.COOLDOWN), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot(useUnmergedTree = true).printToLog("SUMMARY_COOLDOWN")
        compose.onNodeWithTag(SummaryTags.COOLDOWN, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(SummaryTags.COOLDOWN, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.COOLDOWN_SKIP, useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { !fixture.container.restTimerStore.current().running }
        assertFalse(fixture.container.restTimerStore.current().running)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { done }
        assertTrue(done)
    }
}
