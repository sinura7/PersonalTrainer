package com.sinura.personaltrainer.ui.workout

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native buttons drive the durable completion journey; no automatic exercise advance. */
@RunWith(AndroidJUnit4::class)
class WorkoutCompletionJourneyInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = WorkoutEntryFixture()
    @After fun cleanup() = fixture.close()

    private fun mount() {
        GoldenCapture.mountDevice(compose) {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG, LocalReducedMotion provides true) {
                Scaffold(bottomBar = { Column {} }) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        ActiveWorkoutScreen(onExit = {}, onFinished = {}, viewModel = fixture.vm, restNotificationsEnabledOverride = true)
                    }
                }
            }
        }
        compose.waitUntil(15_000) { fixture.vm.uiState.value.canLog && fixture.vm.uiState.value.draft.weightKg == fixture.expectedWeightKg }
    }

    private fun session() = runBlocking(Dispatchers.IO) { checkNotNull(fixture.container.workoutRepository.getSession(fixture.sessionId)) }
    private fun awaitSets(count: Int) {
        compose.waitUntil(15_000) { session().sets.size == count && !fixture.vm.uiState.value.logging }
        // A new deliberate action is separated from the platform double-tap interval.
        SystemClock.sleep(android.view.ViewConfiguration.getDoubleTapTimeout().toLong() + 20)
    }

    @Test fun logExtraAdvanceAndFinishKeepTheCorrectExerciseAndTotals() {
        fixture.seed(targetSets = 1)
        val next = fixture.addNextExercise()
        mount()
        val first = fixture.vm.uiState.value.selectedExerciseId
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitSets(1)
        compose.onNodeWithTag(WorkoutTestTags.NEXT).assertIsDisplayed()
        assertEquals(first, fixture.vm.uiState.value.selectedExerciseId)
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).performClick()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitSets(2)
        assertTrue(session().sets.all { it.exerciseId == first })
        compose.onNodeWithTag(WorkoutTestTags.NEXT).performClick()
        compose.waitUntil(15_000) { fixture.vm.uiState.value.selectedExerciseId == next.id && fixture.vm.uiState.value.draft.weightKg == 40.0 }
        SystemClock.sleep(android.view.ViewConfiguration.getDoubleTapTimeout().toLong() + 20)
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitSets(3)
        compose.onNodeWithTag(WorkoutTestTags.DOCK_FINISH).assertIsDisplayed().performClick()
        assertFalse(session().isFinished)
        compose.onNodeWithText("Save as is").performClick()
        compose.waitUntil(15_000) { session().isFinished }
        assertEquals(3, session().sets.size)
        assertEquals(1, session().sets.count { it.exerciseId == next.id })
    }

    @Test fun deletingAndUndoingTheTargetSetRecomputeCompletionWithoutAddingASet() {
        fixture.seed(targetSets = 1)
        mount()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitSets(1)
        val original = session().sets.single()
        compose.onNodeWithTag("workout-view-sets").performScrollTo().performClick()
        compose.onNodeWithTag(WorkoutTestTags.setOptions(original.id)).performScrollTo().performClick()
        compose.onNodeWithText("Delete set").performClick()
        awaitSets(0)
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        awaitSets(1)
        compose.onNodeWithTag(WorkoutTestTags.DOCK_FINISH).assertIsDisplayed()
        assertEquals(original, session().sets.single())
    }

    @Test fun holdTargetDoesNotCommitUntilTheLogHoldActionIsTapped() {
        fixture.seed(exerciseId = "ex-plank", targetSets = 1)
        mount()
        compose.runOnIdle { fixture.vm.setHoldSeconds(com.sinura.personaltrainer.domain.HoldWork.MIN_SECONDS) }
        compose.onNodeWithText("Start hold").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        compose.waitUntil(15_000) { fixture.vm.holdTimer.value.targetReached }
        assertTrue(session().sets.isEmpty())
        assertEquals(WorkoutPrimaryKind.LOG_HOLD, fixture.vm.primaryAction.value.kind)
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitSets(1)
        assertTrue(checkNotNull(session().sets.single().durationSeconds) >= com.sinura.personaltrainer.domain.HoldWork.MIN_SECONDS)
        assertEquals(0, session().sets.single().reps)
        compose.onNodeWithTag(WorkoutTestTags.DOCK_FINISH).assertIsDisplayed()
    }
}
