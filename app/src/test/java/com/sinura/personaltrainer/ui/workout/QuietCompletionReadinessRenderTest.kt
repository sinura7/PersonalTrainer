package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestSetInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Completion describes saved plan progress, not the editable draft beside it. The real last
 * planned Log is followed by manual numeric/effort touches: those new values are not saved by
 * the completion helper, Next, or Add another set. Numeric confirmation retains FloorTestKit's
 * documented Robolectric keypad seam; this is not Android IME or owner-phone evidence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class QuietCompletionReadinessRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking {
            deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
            deps.preferencesRepository.markRestBatteryHintShown()
        }
    }

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun finishReadinessDoesNotCallNewNumbersSavedAndManualExtraPreservesThem() {
        checkCompletedPlanWithNewDraft(withNextLift = false)
    }

    @Test
    fun nextReadinessDoesNotCallNewNumbersSavedAndProgressionWaitsForItsTap() {
        checkCompletedPlanWithNewDraft(withNextLift = true)
    }

    private fun checkCompletedPlanWithNewDraft(withNextLift: Boolean) {
        val vm = openLegExtension(
            deps = deps,
            viewModels = viewModels,
            loggedSets = listOf(TestSetInput(weightKg = FLOOR_KG70, reps = 10, rpe = 8)),
            withNextLift = withNextLift,
            targetSets = 2,
        )
        compose.showWorkoutScreen(vm)
        reveal(WorkoutTestTags.rpeChoice(8)).assertIsEnabled().performClick()
        compose.awaitThat("the final planned set is ready", vm.primaryAction::value) {
            vm.primaryAction.value.kind == WorkoutPrimaryKind.LOG_SET && vm.primaryAction.value.enabled
        }
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed().assertIsEnabled().performClick()

        val completedKind = if (withNextLift) WorkoutPrimaryKind.NEXT_EXERCISE else WorkoutPrimaryKind.FINISH
        compose.awaitThat("the real Log completes the prescribed plan", vm.uiState::value) {
            vm.uiState.value.session?.sets?.size == 2 && !vm.uiState.value.entryLocked &&
                vm.primaryAction.value.kind == completedKind
        }
        val savedPlan = stored(vm)
        assertEquals(2, savedPlan.sets.size)
        assertFalse(savedPlan.isFinished)
        assertEquals(FLOOR_LIFT_ID, vm.uiState.value.selectedExerciseId)

        reveal(WorkoutTestTags.WEIGHT_STEPPER)
        compose.withKeypad(compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER), "87.5")
        reveal(WorkoutTestTags.REPS_STEPPER)
        compose.withKeypad(compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER), "12")
        reveal(WorkoutTestTags.rpeChoice(9)).assertIsEnabled().performClick()
        compose.awaitThat("the different actual entry is still only a draft", vm.uiState::value) {
            vm.uiState.value.draft.weightKg == 39.7 &&
                vm.uiState.value.draft.reps == 12 && vm.uiState.value.draft.rpe == 9
        }
        val manualDraft = vm.uiState.value.draft
        assertTrue(vm.uiState.value.draftDirty)
        assertFalse(vm.extraSetRequested.value)
        assertEquals(completedKind, vm.primaryAction.value.kind)
        assertEquals("typing after the plan cannot write or finish", savedPlan, stored(vm))
        assertEquals("typing cannot automatically advance", FLOOR_LIFT_ID, vm.uiState.value.selectedExerciseId)
        reveal(WorkoutTestTags.LOG_READINESS).assert(
            hasText(
                if (withNextLift) "Planned sets complete. Continue when you’re ready."
                else "Planned sets complete. Finish when you’re ready.",
            ),
        )
        assertFalse("readiness cannot describe unsaved numbers as saved", manualDraft == ActiveExerciseDraft())

        if (withNextLift) {
            compose.onNodeWithTag(WorkoutTestTags.NEXT).assertIsDisplayed().assertIsEnabled().performClick()
            compose.awaitThat("only the explicit Next tap changes lifts", vm.uiState::value) {
                vm.uiState.value.selectedExerciseId == FLOOR_NEXT_LIFT_ID && FLOOR_LIFT_READY(vm.uiState.value)
            }
            assertEquals("Next cannot save the pending entry", savedPlan, stored(vm))
        } else {
            compose.onNodeWithTag(WorkoutTestTags.DOCK_FINISH).assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertIsDisplayed().assertIsEnabled().performClick()
            compose.awaitThat("manual extra arms the existing draft", vm.primaryAction::value) {
                vm.extraSetRequested.value && vm.primaryAction.value.kind == WorkoutPrimaryKind.LOG_SET &&
                    vm.primaryAction.value.enabled
            }
            assertEquals(manualDraft, vm.uiState.value.draft)
            assertEquals("manual extra cannot save or finish", savedPlan, stored(vm))
            compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed().assertIsEnabled()
                .assert(hasText("87.5 lb × 12 · RPE 9"))
        }
    }

    private fun reveal(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun stored(vm: ActiveWorkoutViewModel) = checkNotNull(runBlocking {
        deps.workoutRepository.getSession(checkNotNull(vm.uiState.value.session).id)
    })
}
