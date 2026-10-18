package com.sinura.personaltrainer.ui.workout

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.domain.CooldownCopy
import com.sinura.personaltrainer.domain.RestTimerMarkers
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.summary.SummaryTags
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryContent
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryUiState
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.domain.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutCooldownJourneyInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = WorkoutEntryFixture()

    @After fun cleanup() = fixture.close()

    @Test
    fun finishStartsCooldownSkipEndsItAndBackDoesNotReopenWorkout() {
        fixture.seed(targetSets = 1)
        GoldenCapture.mountDevice(compose) {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG, LocalReducedMotion provides true) {
                Scaffold { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        ActiveWorkoutScreen(onExit = {}, onFinished = {}, viewModel = fixture.vm, restNotificationsEnabledOverride = true)
                    }
                }
            }
        }
        compose.waitUntil(15_000) { fixture.vm.uiState.value.canLog }
        compose.runOnIdle { fixture.vm.setRpe(8) }
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        compose.waitUntil(15_000) { runBlocking(Dispatchers.IO) { fixture.container.workoutRepository.getSession(fixture.sessionId)?.sets?.size == 1 } }
        SystemClock.sleep(400)
        compose.onNodeWithTag(WorkoutTestTags.DOCK_FINISH).performClick()
        compose.onNodeWithText("Save as is").performClick()
        compose.waitUntil(15_000) {
            runBlocking(Dispatchers.IO) { fixture.container.workoutRepository.getSession(fixture.sessionId)?.isFinished == true }
        }
        val snap = fixture.container.restTimerStore.current()
        assertTrue(snap.running)
        assertTrue(snap.sourceTag == RestTimerMarkers.COOLDOWN)
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG, LocalReducedMotion provides true) {
                    WorkoutSummaryContent(
                        state = WorkoutSummaryUiState(isLoading = false, sessionId = fixture.sessionId, savedConfirmed = true,
                            summary = WorkoutSummary(sessionId = fixture.sessionId, title = "Test", workingSets = 1)),
                        unit = WeightUnit.KG,
                        onRetry = {},
                        onOpenSession = {},
                        onDone = {},
                        onSkipCooldown = {
                            val id = fixture.container.restTimerController.snapshot.value.timerId
                            fixture.container.restTimerController.skipIfShown(id)
                        },
                        restSnapshot = fixture.container.restTimerController.snapshot,
                        restRemaining = fixture.container.restTimerController.remainingSeconds,
                    )
                }
            }
        }
        compose.onNodeWithText(CooldownCopy.KICKER).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.COOLDOWN_SKIP).performClick()
        compose.waitUntil(5_000) { !fixture.container.restTimerStore.current().running }
        assertFalse(fixture.container.restTimerStore.current().running)
    }
}
