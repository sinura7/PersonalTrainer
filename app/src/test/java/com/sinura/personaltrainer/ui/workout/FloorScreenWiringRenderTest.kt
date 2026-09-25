package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.FloorStepper
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.IncrementTable
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.PlateMath
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetOrdinalCopy
import com.sinura.personaltrainer.domain.SetRowCopy
import com.sinura.personaltrainer.domain.UnloadedLoad
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightDraftSource
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import java.time.Duration
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The floor's wiring, proved by tapping it: ActiveWorkoutScreen through the real
 * ViewModel, Room and timer graph, composed as WorkoutFloorRenderTest composes it, with
 * native graphics so the numerals and the header take their real height.
 *
 * The screen used to be held by lines of its own source — `onOpenSwitcher = {
 * liftSwitcherOpen = true }`, `onWeightKgChange = viewModel::setWeight`, `plated = … ==
 * EquipmentType.BARBELL`, `showAddSet = WorkoutAdvance.cardOffersAnotherSet(`, the
 * `item(key = …)` order. Those lines are where W1a rebuilds the header, the entry and the
 * two "Add set" controls, and a renamed lambda would have failed them while a broken tap
 * passed. Here each wire is a tap and the state it reaches. Two guards ride along because
 * they are the floor's own regressions: a save must leave the numerals where they were (the
 * log loop once scrolled to the history), and the floor never advances without a tap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class FloorScreenWiringRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private val openedExercises = mutableListOf<String>()

    /**
     * The ViewModel's clock. Its delays wait on this scheduler, which the screen's test clock
     * never moves, so a test about time passing has to advance both.
     */
    private val viewModelClock = TestCoroutineScheduler()

    @Before
    fun setUp() {
        // The ViewModel's scope runs inline on the test thread, as in WorkoutFloorRenderTest.
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler = viewModelClock))
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.LBS) }
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
    @Config(qualifiers = "w360dp-h1600dp-xhdpi")
    fun theLoopReadsIdentityStatsEntryEffortThenHistory() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(2))
        show(vm, heightDp = 1600)
        compose.waitUntil(timeoutMillis = WAIT_MS) { exists(WorkoutTestTags.SET_HISTORY) }
        // The Next-set card sits between effort and history when the coach offers one. Whether
        // it does is the coach card's own rule (T1b), so its place is checked only when shown.
        val order = listOf(
            WorkoutTestTags.CURRENT_LIFT,
            WorkoutTestTags.STATS_ROW,
            WorkoutTestTags.SET_ENTRY,
            WorkoutTestTags.RPE_TRACK,
        ) + listOf(WorkoutTestTags.NEXT_SET, WorkoutTestTags.NEXT_SET_COMPACT).filter { exists(it) } +
            WorkoutTestTags.SET_HISTORY
        val tops = order.map { compose.onNodeWithTag(it).getBoundsInRoot().top }
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        assertEquals(tops.size, tops.toSet().size)
        // The identity names the set about to be logged, from the saved rows.
        compose.onNodeWithTag(WorkoutTestTags.SET_CONTEXT, useUnmergedTree = true)
            .assert(hasText(SetOrdinalCopy.draftLine(isWarmup = false, warmupLogged = 0, workingLogged = 2, targetSets = 3)))
        // Where the lift sits in the session is on the switch, in words a lifter can see.
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).assert(hasText(CurrentLiftCopy.switchLabel(1, 1)))
    }

    @Test
    fun theLiftSwitchOpensTheSessionSwitcherAndARowSwitchesLift() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1), withNextLift = true)
        show(vm)
        // The switcher opens from the visible "Lift 1 of 2" control (D09). The identity is
        // words now: a tap on the name opens nothing.
        compose.onNodeWithTag(WorkoutTestTags.liftCard(LEG_EXTENSION)).performClick()
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCHER).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).assert(hasText(CurrentLiftCopy.switchLabel(1, 2))).performClick()
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCHER).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.liftSwitcherRow(NEXT_LIFT)).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.selectedExerciseId == NEXT_LIFT }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCHER).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.liftCard(NEXT_LIFT)).assertIsDisplayed()
    }

    @Test
    fun switchingLiftFromTheOverflowBringsTheNewIdentityAndEntryBackIntoView() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(2), withNextLift = true)
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.SET_HISTORY))
        compose.onNodeWithTag(WorkoutTestTags.CURRENT_LIFT).assertIsNotDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.LIFT_OPTIONS).performClick()
        compose.onNodeWithText(CurrentLiftCopy.SWITCH).performClick()
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCHER).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.liftSwitcherRow(NEXT_LIFT)).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.selectedExerciseId == NEXT_LIFT }
        compose.waitForIdle()
        // A lift switch lands on the new lift's identity with its numerals, not on a list
        // offset that belonged to the lift it left.
        compose.onNodeWithTag(WorkoutTestTags.liftCard(NEXT_LIFT)).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).assertIsDisplayed()
    }

    @Test
    fun theSwitchersAddExerciseOpensThePicker() {
        val vm = openLegExtension(deps, viewModels, loggedSets = emptyList())
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).performClick()
        compose.onNodeWithTag(WorkoutTestTags.SWITCHER_ADD_LIFT).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.showExercisePicker }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCHER).assertDoesNotExist()
    }

    @Test
    fun detailsOpensTheLiftsOwnScreen() {
        val vm = openLegExtension(deps, viewModels, loggedSets = emptyList())
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.DETAILS).performClick()
        assertEquals(listOf(LEG_EXTENSION), openedExercises)
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCHER).assertDoesNotExist()
    }

    @Test
    fun theLiftMenuAlsoOpensDetails() {
        // The picture is not the only way in: someone reading the ⋮ menu finds it by name.
        val vm = openLegExtension(deps, viewModels, loggedSets = emptyList())
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.LIFT_OPTIONS).performClick()
        compose.onNodeWithText(CurrentLiftCopy.DETAILS_SPOKEN).performClick()
        assertEquals(listOf(LEG_EXTENSION), openedExercises)
        compose.onNodeWithText(CurrentLiftCopy.DETAILS_SPOKEN).assertDoesNotExist()
    }

    @Test
    fun theSetTypeToggleDrivesTheDraftTheCommitAndTheWarmupRamp() {
        val vm = openBackSquat()
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.isWarmup }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).assertIsSelected()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assert(hasContentDescription("Log warm-up", substring = true))
        compose.onNodeWithTag(WorkoutTestTags.SET_CONTEXT, useUnmergedTree = true)
            .assert(hasText(SetOrdinalCopy.draftLine(isWarmup = true, warmupLogged = 0, workingLogged = 0, targetSets = 3)))
        // The ramp sits under the numerals and a preset only fills the draft.
        val ramp = compose.onNodeWithTag(WorkoutTestTags.WARMUP_RAMP).assertIsDisplayed().getBoundsInRoot()
        assertTrue(ramp.top >= compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).getBoundsInRoot().bottom)
        val preset = compose.onAllNodes(
            hasClickAction() and hasText("Use ", substring = true) and hasAnyAncestor(hasTestTag(WorkoutTestTags.WARMUP_RAMP)),
        ).onFirst()
        val label = preset.mergedTexts().first()
        preset.performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { label == "Use ${vm.uiState.value.draft.weightKg.toWeightLabel(WeightUnit.LBS)}" }
        compose.waitForIdle()
        // Asked of the database, not of the draft that just changed: nothing was logged.
        val stored = storedSession(vm)
        assertTrue("a preset fills the draft and logs nothing, was ${stored.sets}", stored.sets.isEmpty())
    }

    @Test
    fun thePlatesAndTheKeypadWriteTheDraftByTheBarbellsRule() {
        val vm = openBackSquat()
        show(vm)
        val start = vm.uiState.value.draft
        val step = IncrementTable.displayStep(LoadType.EXTERNAL, WeightUnit.LBS, EquipmentType.BARBELL)
            ?.let { WeightConverter.formatDisplayNumber(it) } ?: WeightUnit.LBS.stepLabel
        compose.onNodeWithContentDescription("Increase weight by $step lb").performClick()
        val stepped = FloorStepper.nextWeightKg(start.weightKg, WeightUnit.LBS, 1, LoadType.EXTERNAL, EquipmentType.BARBELL)
        compose.waitUntil(timeoutMillis = WAIT_MS) { abs(vm.uiState.value.draft.weightKg - stepped) < 0.001 }
        compose.onNodeWithContentDescription("Increase reps by 1").performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.reps == FloorStepper.nextReps(start.reps, 1) }
        compose.waitForIdle()
        // A barbell shows its plate loading under the weight.
        compose.onNodeWithText(checkNotNull(PlateMath.load(stepped, WeightUnit.LBS)).caption()).assertIsDisplayed()
        // Off the plan, the plan comes back as a one-tap fill.
        compose.onNodeWithTag(WorkoutTestTags.weightPreset(WeightDraftSource.PLAN)).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { abs(vm.uiState.value.draft.weightKg - SQUAT_KG) < 0.05 }
        // The keypad explains a barbell's weight and writes what was typed.
        compose.withKeypad(on = compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER), value = "185") {
            compose.onNodeWithText(SetCopy.weightKeypadHelper(LoadClass.LOADED, allowsZero = false), substring = true).assertExists()
        }
        compose.waitUntil(timeoutMillis = WAIT_MS) { abs(vm.uiState.value.draft.weightKg - WeightConverter.lbsToKg(185.0)) < 0.05 }
    }

    @Test
    fun aPinStackStepsByItsPinNotByThePlate() {
        val vm = openBackSquat()
        show(vm)
        selectAndAwaitItsDraft(vm, PULLDOWN) { it.draft.reps == 10 }
        // A selectorised stack moves a pin, 10 lb, where the squat's bar moves 5: the lift's
        // own load type reaches the plates' words and the draft, not the screen's default.
        assertEquals(IncrementTable.STACK_STEP_LBS, IncrementTable.displayStep(LoadType.STACK, WeightUnit.LBS, EquipmentType.CABLE))
        val pin = WeightConverter.formatDisplayNumber(IncrementTable.STACK_STEP_LBS)
        assertEquals(
            listOf("Decrease weight by $pin lb", "Increase weight by $pin lb", "Type a weight"),
            compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).customActionLabels(),
        )
        val start = vm.uiState.value.draft.weightKg
        compose.onNodeWithContentDescription("Increase weight by $pin lb").performClick()
        val stepped = FloorStepper.nextWeightKg(start, WeightUnit.LBS, 1, LoadType.STACK, EquipmentType.CABLE)
        compose.waitUntil(timeoutMillis = WAIT_MS) { abs(vm.uiState.value.draft.weightKg - stepped) < 0.001 }
        val moved = WeightConverter.toDisplayValue(stepped, WeightUnit.LBS) - WeightConverter.toDisplayValue(start, WeightUnit.LBS)
        assertEquals("one tap is one pin", IncrementTable.STACK_STEP_LBS, moved, 0.01)
    }

    @Test
    fun anEmptyHandsLungesKeypadSaysZeroIsAWeight() {
        val vm = openBackSquat()
        show(vm)
        selectAndAwaitItsDraft(vm, LUNGE) { it.draft.reps == 10 }
        assertTrue(UnloadedLoad.allowsZeroWorkingWeight(LoadType.EXTERNAL, EquipmentType.DUMBBELL, "lunge"))
        // Off zero first, so the typed 0 is a change the keypad has to accept and write.
        val step = IncrementTable.displayStep(LoadType.EXTERNAL, WeightUnit.LBS, EquipmentType.DUMBBELL)
            ?.let { WeightConverter.formatDisplayNumber(it) } ?: WeightUnit.LBS.stepLabel
        compose.onNodeWithContentDescription("Increase weight by $step lb").performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.weightKg > 0.0 }
        compose.waitForIdle()
        compose.withKeypad(on = compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER), value = "0") {
            compose.onNodeWithText(SetCopy.weightKeypadHelper(LoadClass.LOADED, allowsZero = true), substring = true).assertExists()
        }
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.weightKg == 0.0 }
    }

    @Test
    fun aHoldLiftStepsItsTimeIntoTheDraft() {
        val vm = openBackSquat()
        show(vm)
        selectAndAwaitItsDraft(vm, PLANK) { it.draft.reps == 0 && it.draft.durationSeconds != null }
        compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).assertDoesNotExist()
        val lift = checkNotNull(vm.uiState.value.session?.exercises?.first { it.exercise.id == PLANK })
        val before = vm.uiState.value.draft.durationSeconds ?: lift.targetSeconds ?: HoldWork.DEFAULT_SECONDS
        compose.onNodeWithTag(WorkoutTestTags.HOLD_STEPPER).assert(hasContentDescription("Time ${HoldWork.clock(before)}"))
        compose.onNodeWithContentDescription("Increase time by ${HoldWork.STEP_SECONDS} seconds").performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) {
            vm.uiState.value.draft.durationSeconds == FloorStepper.nextHoldSeconds(before, 1)
        }
    }

    @Test
    fun aSetChipsReviseOpensTheEditAndRevealsTheEntry() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(2))
        // Short enough that reaching the chips scrolls the numerals off screen.
        show(vm, heightDp = 600)
        val first = checkNotNull(vm.uiState.value.session?.sets?.minByOrNull { it.completedAt })
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.setChip(first.id)))
        compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).assertIsNotDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.setChip(first.id)).performClick()
        compose.onNodeWithText(SetRowCopy.revise(SetOrdinalCopy.working(1, 3))).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) {
            vm.uiState.value.editingSetId == first.id && !vm.uiState.value.entryLocked
        }
        compose.waitForIdle()
        // An edit deliberately reveals the entry it is editing.
        compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).assertIsDisplayed()
        // While editing, the history shows no "current" chip, and the identity says so.
        assertTrue(compose.onAllNodesWithTag(WorkoutTestTags.CURRENT_SET).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.CURRENT_LIFT))
        compose.onNodeWithTag(WorkoutTestTags.SET_CONTEXT, useUnmergedTree = true).assert(hasText("Editing saved set"))
    }

    @Test
    fun theHistorysEditOpensTheSavedSetsSheet() {
        // Both sets carry one completion time, as the seed's two back-to-back saves do on about
        // one hosted run in fifteen. The later set is still the latest; set 1 keeps its plain label.
        val sessionId = runBlocking {
            seedLegExtension(deps, loggedSets = sets(2)).also { id ->
                val dao = deps.database.workoutDao()
                val saved = dao.setsForExercise(id, FLOOR_LIFT_ID)
                check(saved.size == 2) { "the tie needs both seeded sets, found ${saved.size}" }
                saved.forEach { dao.updateSet(it.copy(completedAt = saved.first().completedAt)) }
            }
        }
        val vm = floorViewModel(deps = deps, sessionId = sessionId).also(viewModels::add)
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.VIEW_SETS))
        compose.onNodeWithTag(WorkoutTestTags.VIEW_SETS).performClick()
        compose.onNodeWithTag(WorkoutTestTags.SAVED_SETS_SHEET).assertIsDisplayed()
        compose.onNodeWithText("Working set 1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Working set 2 of 3 · Latest").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h1600dp-xhdpi")
    fun beforeThePlanIsMetNoAddSetIsOffered() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(2), withNextLift = true)
        show(vm, heightDp = 1600)
        // Two of three saved: the lifter's next act is the third set, so the dock does not
        // offer "Add another set" yet, and the saved-sets sheet does not offer one either.
        val saved = checkNotNull(vm.uiState.value.session).sets.map { it.id }
        assertEquals(2, saved.size)
        // The history is on screen through its last chip: the floor is composed, not pending.
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.SET_HISTORY))
        saved.forEach { compose.onNodeWithTag(WorkoutTestTags.setChip(it)).assertIsDisplayed() }
        compose.onNodeWithTag(WorkoutTestTags.CURRENT_SET).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.VIEW_SETS).performClick()
        compose.onNodeWithTag(WorkoutTestTags.SAVED_SETS_SHEET).assertIsDisplayed()
        assertSheetOffersNoAnotherSet(lastRow = "Working set 2 of 3")
    }

    @Test
    @Config(qualifiers = "w360dp-h1600dp-xhdpi")
    fun onceThePlanIsMetTheFloorOffersOneWayToAddASet() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(3), withNextLift = true)
        show(vm, heightDp = 1600)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.primaryAction.value.kind == WorkoutPrimaryKind.NEXT_EXERCISE }
        compose.waitForIdle()
        // One control asks for an extra set: the dock's "Add another set", beside Next
        // exercise and Finish (W1a). The history used to offer a second "Add set" chip. The
        // tall window composes the whole floor, so "not there" means not on the floor.
        compose.onNodeWithTag(WorkoutTestTags.SET_HISTORY).assertIsDisplayed()
        compose.onAllNodes(hasText("Add set") or hasText("Add another set"), useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.extraSetRequested.value }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
    }

    @Test
    fun onceThePlanIsMetTheDocksAddAnotherSetAsksForAnExtraSet() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(3), withNextLift = true)
        show(vm)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.primaryAction.value.kind == WorkoutPrimaryKind.NEXT_EXERCISE }
        compose.waitForIdle()
        // The floor's one extra-set control asks for it on the same lift.
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.extraSetRequested.value }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
        assertEquals(LEG_EXTENSION, vm.uiState.value.selectedExerciseId)
    }

    @Test
    fun theSavedSetsSheetsAddAnotherSetAsksForAnExtraSetAndCloses() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(3), withNextLift = true)
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.VIEW_SETS))
        compose.onNodeWithTag(WorkoutTestTags.VIEW_SETS).performClick()
        // The sheet covers the dock, so it keeps its own "Add another set" in the dock's
        // words: the same act, said the same way, wherever the lifter is looking.
        compose.onNode(hasText("Add another set") and hasAnyAncestor(hasTestTag(WorkoutTestTags.SAVED_SETS_SHEET)))
            .performScrollTo()
            .performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.extraSetRequested.value }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.SAVED_SETS_SHEET).assertDoesNotExist()
        // Asked once is enough: opened again, the sheet no longer offers it.
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.VIEW_SETS))
        compose.onNodeWithTag(WorkoutTestTags.VIEW_SETS).performClick()
        compose.onNodeWithTag(WorkoutTestTags.SAVED_SETS_SHEET).assertIsDisplayed()
        assertSheetOffersNoAnotherSet(lastRow = "Working set 3 of 3")
    }

    @Test
    fun savingASetKeepsTheNumeralsOnScreen() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(2))
        show(vm)
        val before = entryTop()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.session?.sets?.size == 3 }
        compose.waitForIdle()
        // Anchoring the loop on the set history is the bug LogLoopBringIntoView exists to
        // prevent. Somewhere on screen is not enough: after a save the next set's numerals are
        // exactly where the thumb left them.
        assertEquals("the numerals must not move when a set is saved", before, entryTop(), 1f)
    }

    @Test
    @Config(qualifiers = "w360dp-h1600dp-xhdpi")
    fun theJustSavedSetIsTheSavedChip() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(2))
        show(vm, heightDp = 1600)
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        val saved = "Saved · ${SetOrdinalCopy.working(3, 3)}"
        compose.waitUntil(timeoutMillis = WAIT_MS) {
            compose.onAllNodesWithText(saved, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // The chip itself carries the receipt, in words as well as the Volt ring.
        compose.onNode(hasContentDescription("${SetOrdinalCopy.working(3, 3)}, ", substring = true))
            .assert(hasContentDescription(", saved", substring = true))
    }

    @Test
    fun withThePlannedSetsDoneTheFloorWaitsForATapToAdvance() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(2), withNextLift = true)
        show(vm)
        // The last planned set is saved here, so everything a save sets off runs: the
        // receipt and its dwell on the screen, the settle and rest in the ViewModel. A loop
        // that jumped to the next lift once any of them ended would put the lifter on the
        // wrong card with a bar in their hands.
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.logReceipt.value != null }
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.primaryAction.value.kind == WorkoutPrimaryKind.NEXT_EXERCISE }
        // A minute on every clock the floor reads, since none of them moves the others: the
        // screen's, which runs the receipt's dwell; the ViewModel's scheduler; and the
        // device's elapsed time with the main thread's own timers.
        compose.mainClock.advanceTimeBy(ONE_MINUTE_MS)
        viewModelClock.advanceTimeBy(ONE_MINUTE_MS)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ONE_MINUTE_MS))
        compose.waitForIdle()
        assertNull("the receipt's dwell ran to its end", vm.logReceipt.value)
        assertEquals("time alone must not move the lifter", LEG_EXTENSION, vm.uiState.value.selectedExerciseId)
        compose.onNodeWithTag(WorkoutTestTags.NEXT).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.selectedExerciseId == NEXT_LIFT }
    }

    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** Where the numerals sit, in pixels, so "did not move" can be held to one pixel. */
    private fun entryTop(): Float =
        compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top

    /**
     * The open saved-sets sheet, scrolled to [lastRow] so its foot is composed, offers no
     * "Add another set".
     */
    private fun assertSheetOffersNoAnotherSet(lastRow: String) {
        val inSheet = hasAnyAncestor(hasTestTag(WorkoutTestTags.SAVED_SETS_SHEET))
        compose.onNode(hasText(lastRow, substring = true) and inSheet).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Add another set") and inSheet).assertDoesNotExist()
    }

    /** The session as the database holds it, read back rather than taken from the screen. */
    private fun storedSession(vm: ActiveWorkoutViewModel): WorkoutSession = runBlocking {
        val id = checkNotNull(vm.uiState.value.session).id
        withTimeout(WAIT_MS) { checkNotNull(deps.workoutRepository.observeSession(id).first { it != null }) }
    }

    private fun sets(count: Int) = (1..count).map { TestSetInput(weightKg = WeightConverter.lbsToKg(70.0), reps = 10, rpe = 8) }

    private fun show(vm: ActiveWorkoutViewModel, heightDp: Int = 800) {
        compose.showFloor {
            Box(modifier = Modifier.width(360.dp).height(heightDp.dp)) {
                ActiveWorkoutScreen(
                    onExit = {},
                    onFinished = {},
                    onOpenExercise = { openedExercises += it },
                    viewModel = vm,
                    restNotificationsEnabledOverride = true,
                )
            }
        }
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.loadState == SessionLoadState.FOUND }
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.session?.exercises?.isNotEmpty() == true }
        compose.waitForIdle()
    }

    /**
     * A barbell squat first, then an empty-hands dumbbell lunge, a plank and a pin-stack
     * pulldown: the lifts whose entry differs by equipment (plates), movement (zero is a
     * weight), kind (a hold has time, not reps) and load (a stack steps by its pin).
     */
    /**
     * Selects [exerciseId] and waits for its own draft: the lift's prefill reads the database on
     * its own thread and writes the draft when it lands, over anything tapped before then. A
     * lunge stepped off zero first went back to zero that way, and the wait for it gave up.
     * [landed] names something only this lift's draft holds (its planned reps, a hold's clock).
     */
    private fun selectAndAwaitItsDraft(
        vm: ActiveWorkoutViewModel,
        exerciseId: String,
        landed: (ActiveWorkoutUiState) -> Boolean,
    ) {
        vm.selectExercise(exerciseId)
        compose.waitUntil(timeoutMillis = WAIT_MS) {
            val state = vm.uiState.value
            state.selectedExerciseId == exerciseId &&
                state.liftReadiness == LiftEntryReadiness.READY &&
                !state.entryLocked &&
                landed(state)
        }
        compose.waitForIdle()
    }

    private fun openBackSquat(): ActiveWorkoutViewModel {
        val sessionId = runBlocking {
            deps.database.exerciseDao().insertAll(
                listOf(
                    entity(id = BACK_SQUAT, name = "Back Squat", equipment = EquipmentType.BARBELL, movementKey = null),
                    entity(id = LUNGE, name = "Walking Lunge", equipment = EquipmentType.DUMBBELL, movementKey = "lunge"),
                    entity(id = PLANK, name = "Plank", equipment = EquipmentType.BODYWEIGHT, movementKey = "plank"),
                    entity(id = PULLDOWN, name = "Lat Pulldown", equipment = EquipmentType.CABLE, movementKey = null, loadType = LoadType.STACK),
                ),
            )
            val seeded = seedTestWorkout(
                deps = deps,
                exerciseId = BACK_SQUAT,
                exerciseName = "Back Squat",
                routineName = "Lower A",
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = SQUAT_KG,
                restSeconds = 120,
            )
            listOf(LUNGE, PLANK).forEach { id ->
                val lift = checkNotNull(deps.exerciseRepository.getById(id))
                deps.workoutRepository.addExerciseToSession(seeded.session.id, lift, targetSets = 3, targetReps = 10, targetWeightKg = null, restSeconds = 60)
            }
            val pulldown = checkNotNull(deps.exerciseRepository.getById(PULLDOWN))
            deps.workoutRepository.addExerciseToSession(seeded.session.id, pulldown, targetSets = 3, targetReps = 10, targetWeightKg = PULLDOWN_KG, restSeconds = 90)
            seeded.session.id
        }
        return viewModel(sessionId)
    }

    private fun entity(
        id: String,
        name: String,
        equipment: EquipmentType,
        movementKey: String?,
        loadType: LoadType = LoadType.EXTERNAL,
    ) = ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = "Legs",
        notes = "",
        isCustom = false,
        equipment = equipment.name,
        loadType = loadType.name,
        movementKey = movementKey,
        nameKey = name.lowercase(),
    )

    private fun viewModel(sessionId: String) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
        container = deps,
        undoTimeout = { it.toLong() },
    ).also(viewModels::add)

    private companion object {
        const val WAIT_MS = 20_000L
        const val ONE_MINUTE_MS = 60_000L
        const val LEG_EXTENSION = "leg-extension"
        const val NEXT_LIFT = "romanian-deadlift"
        const val BACK_SQUAT = "back-squat"
        const val LUNGE = "walking-lunge"
        const val PLANK = "plank"
        const val PULLDOWN = "lat-pulldown"
        val SQUAT_KG = WeightConverter.lbsToKg(135.0)
        val PULLDOWN_KG = WeightConverter.lbsToKg(100.0)
    }
}
