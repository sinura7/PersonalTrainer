package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.domain.RestTimerMarkers
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.timer.RestTimerOverlayController
import com.sinura.personaltrainer.ui.components.EndWorkoutTags
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutFinishCooldownInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = WorkoutEntryFixture()

    @Before
    fun releaseExteriorFromPriorTests() {
        fixture.container.restTimerController.stop(fromService = true)
        RestTimerOverlayController.release()
    }

    @After
    fun cleanup() {
        RestTimerOverlayController.release()
        fixture.close()
    }

    private fun session() = runBlocking(Dispatchers.IO) {
        checkNotNull(fixture.container.workoutRepository.getSession(fixture.sessionId))
    }

    private fun awaitSets(count: Int) {
        compose.waitUntil(15_000) { session().sets.size == count && !fixture.vm.uiState.value.logging }
        compose.waitForIdle()
    }

    private fun pickEffort() {
        compose.runOnIdle { fixture.vm.setRpe(8) }
        compose.waitUntil(5_000) { fixture.vm.uiState.value.draft.rpe == 8 }
    }

    @Test
    fun finishStartsCooldownInTheStore() {
        fixture.seed(targetSets = 1)
        GoldenCapture.mountDevice(compose) {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG, LocalReducedMotion provides true) {
                PersonalTrainerTheme {
                    Scaffold(bottomBar = { Column {} }) { padding ->
                        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                            ActiveWorkoutScreen(
                                onExit = {},
                                onFinished = {},
                                viewModel = fixture.vm,
                                restNotificationsEnabledOverride = true,
                            )
                        }
                    }
                }
            }
        }
        compose.waitUntil(15_000) { fixture.vm.uiState.value.canLog && fixture.vm.uiState.value.draft.weightKg == fixture.expectedWeightKg }
        pickEffort()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitSets(1)
        compose.onNodeWithTag(WorkoutTestTags.DOCK_FINISH).assertIsDisplayed().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(EndWorkoutTags.SAVE), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(EndWorkoutTags.SAVE, useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.waitUntil(15_000) { session().isFinished }
        compose.waitUntil(15_000) {
            val snap = fixture.container.restTimerStore.current()
            snap.running && RestTimerMarkers.isCooldown(snap)
        }
        val snap = fixture.container.restTimerStore.current()
        assertTrue(snap.running)
        assertTrue(snap.sourceTag == RestTimerMarkers.COOLDOWN)
    }
}
