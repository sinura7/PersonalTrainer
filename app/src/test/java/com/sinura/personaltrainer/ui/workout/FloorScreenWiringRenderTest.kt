package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
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
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The floor's wiring, proved by tapping it: ActiveWorkoutScreen through the real
 * ViewModel, Room and timer graph, as WorkoutFloorRenderTest composes it.
 *
 * The screen used to be held by lines of its own source — `onOpenSwitcher = {
 * liftSwitcherOpen = true }`, `onWeightKgChange = viewModel::setWeight`, `plated = … ==
 * EquipmentType.BARBELL`, `showAddSet = WorkoutAdvance.cardOffersAnotherSet(`, the
 * `item(key = …)` order. Those lines are where W1a rebuilds the header, the entry and the
 * two "Add set" controls, and a renamed lambda would have failed them while a broken tap
 * passed. Here each wire is a tap and the state it reaches. Two guards ride along because
 * they are the floor's own regressions: a save must leave the numerals on screen (the log
 * loop once scrolled to the history), and the floor never advances without a tap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class FloorScreenWiringRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private val openedExercises = mutableListOf<String>()

    @Before
    fun setUp() {
        // The ViewModel's scope runs inline on the test thread, as in WorkoutFloorRenderTest.
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
        val vm = openLegExtension(loggedSets = sets(2))
        show(vm, heightDp = 1600)
        compose.waitUntil(timeoutMillis = WAIT_MS) { exists(WorkoutTestTags.SET_HISTORY) }
        assertTrue(
            "with two working sets saved the Next-set card sits between effort and history",
            exists(WorkoutTestTags.NEXT_SET) || exists(WorkoutTestTags.NEXT_SET_COMPACT),
        )
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
        compose.onNodeWithTag(WorkoutTestTags.liftCard(LEG_EXTENSION))
            .assert(hasContentDescription(CurrentLiftCopy.heroOrdinal(1, 1), substring = true))
    }

    @Test
    fun tappingTheIdentityOpensTheSessionSwitcherAndARowSwitchesLift() {
        val vm = openLegExtension(loggedSets = sets(1), withNextLift = true)
        show(vm)
        // W1a changes this: the switch moves from the whole identity to a visible
        // "Lift n of N" control; the switcher it opens stays the same.
        compose.onNodeWithTag(WorkoutTestTags.liftCard(LEG_EXTENSION)).performClick()
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCHER).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.liftSwitcherRow(NEXT_LIFT)).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.selectedExerciseId == NEXT_LIFT }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCHER).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.liftCard(NEXT_LIFT)).assertIsDisplayed()
    }

    @Test
    fun switchingLiftFromTheOverflowBringsTheNewIdentityAndEntryBackIntoView() {
        val vm = openLegExtension(loggedSets = sets(2), withNextLift = true)
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
        val vm = openLegExtension(loggedSets = emptyList())
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.liftCard(LEG_EXTENSION)).performClick()
        compose.onNodeWithTag(WorkoutTestTags.SWITCHER_ADD_LIFT).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.showExercisePicker }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCHER).assertDoesNotExist()
    }

    @Test
    fun detailsOpensTheLiftsOwnScreen() {
        val vm = openLegExtension(loggedSets = emptyList())
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.DETAILS).performClick()
        assertEquals(listOf(LEG_EXTENSION), openedExercises)
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCHER).assertDoesNotExist()
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
        val preset = compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("workout-warmup-preset-0")))
        val label = preset.mergedTexts().first()
        preset.performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { label == "Use ${vm.uiState.value.draft.weightKg.toWeightLabel(WeightUnit.LBS)}" }
        assertTrue(vm.uiState.value.session?.sets.orEmpty().isEmpty())
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
        compose.openKeypad { compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).performClick() }
        compose.onNodeWithText(SetCopy.weightKeypadHelper(LoadClass.LOADED, allowsZero = false), substring = true).assertExists()
        compose.confirmKeypad("185")
        compose.waitUntil(timeoutMillis = WAIT_MS) { abs(vm.uiState.value.draft.weightKg - WeightConverter.lbsToKg(185.0)) < 0.05 }
    }

    @Test
    fun anEmptyHandsLungesKeypadSaysZeroIsAWeight() {
        val vm = openBackSquat()
        show(vm)
        vm.selectExercise(LUNGE)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.selectedExerciseId == LUNGE }
        compose.waitForIdle()
        assertTrue(UnloadedLoad.allowsZeroWorkingWeight(LoadType.EXTERNAL, EquipmentType.DUMBBELL, "lunge"))
        compose.openKeypad { compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).performClick() }
        compose.onNodeWithText(SetCopy.weightKeypadHelper(LoadClass.LOADED, allowsZero = true), substring = true).assertExists()
        compose.confirmKeypad("0")
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.weightKg == 0.0 }
    }

    @Test
    fun aHoldLiftStepsItsTimeIntoTheDraft() {
        val vm = openBackSquat()
        show(vm)
        vm.selectExercise(PLANK)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.selectedExerciseId == PLANK }
        compose.waitForIdle()
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
        val vm = openLegExtension(loggedSets = sets(2))
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
        val vm = openLegExtension(loggedSets = sets(2))
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.VIEW_SETS))
        compose.onNodeWithTag(WorkoutTestTags.VIEW_SETS).performClick()
        compose.onNodeWithTag(WorkoutTestTags.SAVED_SETS_SHEET).assertIsDisplayed()
        compose.onNodeWithText("Working set 1 of 3").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h1600dp-xhdpi")
    fun onceThePlanIsMetTheHistorysAddSetAsksForAnExtraSet() {
        val vm = openLegExtension(loggedSets = sets(3), withNextLift = true)
        show(vm, heightDp = 1600)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.primaryAction.value.kind == WorkoutPrimaryKind.NEXT_EXERCISE }
        compose.waitForIdle()
        // W1a changes this: two controls ask for the same extra set today, the history's
        // "Add set" chip and the dock's "Add another set". W1a keeps one.
        compose.onNodeWithTag(WorkoutTestTags.ADD_SET).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.ADD_SET).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.extraSetRequested.value }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.ADD_SET).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
    }

    @Test
    fun onceThePlanIsMetTheDocksAddAnotherSetAsksForAnExtraSet() {
        val vm = openLegExtension(loggedSets = sets(3), withNextLift = true)
        show(vm)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.primaryAction.value.kind == WorkoutPrimaryKind.NEXT_EXERCISE }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.extraSetRequested.value }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
        assertEquals(LEG_EXTENSION, vm.uiState.value.selectedExerciseId)
    }

    @Test
    fun theSavedSetsSheetsAddAnotherSetAsksForAnExtraSetAndCloses() {
        val vm = openLegExtension(loggedSets = sets(3), withNextLift = true)
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.VIEW_SETS))
        compose.onNodeWithTag(WorkoutTestTags.VIEW_SETS).performClick()
        // The dock says the same words; this is the sheet's own button.
        compose.onNode(hasText("Add another set") and hasAnyAncestor(hasTestTag(WorkoutTestTags.SAVED_SETS_SHEET)))
            .performScrollTo()
            .performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.extraSetRequested.value }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.SAVED_SETS_SHEET).assertDoesNotExist()
    }

    @Test
    fun savingASetKeepsTheNumeralsOnScreen() {
        val vm = openLegExtension(loggedSets = sets(2))
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.session?.sets?.size == 3 }
        compose.waitForIdle()
        // Anchoring the loop on the set history is the bug LogLoopBringIntoView exists to
        // prevent: after a save the next set's numerals are still where the thumb is.
        compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h1600dp-xhdpi")
    fun theJustSavedSetIsTheSavedChip() {
        val vm = openLegExtension(loggedSets = sets(2))
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
        val vm = openLegExtension(loggedSets = sets(1), targetSets = 1, withNextLift = true)
        show(vm)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.primaryAction.value.kind == WorkoutPrimaryKind.NEXT_EXERCISE }
        compose.mainClock.advanceTimeBy(ONE_MINUTE_MS)
        compose.waitForIdle()
        assertEquals("time alone must not move the lifter", LEG_EXTENSION, vm.uiState.value.selectedExerciseId)
        compose.onNodeWithTag(WorkoutTestTags.NEXT).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.selectedExerciseId == NEXT_LIFT }
    }

    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun sets(count: Int) = (1..count).map { TestSetInput(weightKg = WeightConverter.lbsToKg(70.0), reps = 10, rpe = 8) }

    private fun show(vm: ActiveWorkoutViewModel, heightDp: Int = 800) {
        compose.showFloor {
            Box(Modifier.width(360.dp).height(heightDp.dp)) {
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

    private fun openLegExtension(
        loggedSets: List<TestSetInput>,
        targetSets: Int = 3,
        withNextLift: Boolean = false,
    ): ActiveWorkoutViewModel {
        val sessionId = runBlocking {
            val seeded = seedTestWorkout(
                deps = deps,
                exerciseId = LEG_EXTENSION,
                exerciseName = "Leg Extension",
                routineName = "Lower B",
                targetSets = targetSets,
                targetReps = 10,
                targetWeightKg = WeightConverter.lbsToKg(70.0),
                restSeconds = 120,
                loggedSets = loggedSets,
            )
            if (withNextLift) {
                val next = insertTestExercise(deps, id = NEXT_LIFT, name = "Romanian Deadlift", muscleGroup = "Hamstrings")
                deps.workoutRepository.addExerciseToSession(seeded.session.id, next, targetSets = 3, targetReps = 8, targetWeightKg = 40.0, restSeconds = 90)
            }
            seeded.session.id
        }
        return viewModel(sessionId)
    }

    /**
     * A barbell squat first, then an empty-hands dumbbell lunge and a plank: the three
     * lifts whose entry differs by equipment (plates and step), movement (zero is a weight)
     * and kind (a hold has time, not reps).
     */
    private fun openBackSquat(): ActiveWorkoutViewModel {
        val sessionId = runBlocking {
            deps.database.exerciseDao().insertAll(
                listOf(
                    entity(id = BACK_SQUAT, name = "Back Squat", equipment = EquipmentType.BARBELL, movementKey = null),
                    entity(id = LUNGE, name = "Walking Lunge", equipment = EquipmentType.DUMBBELL, movementKey = "lunge"),
                    entity(id = PLANK, name = "Plank", equipment = EquipmentType.BODYWEIGHT, movementKey = "plank"),
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
            seeded.session.id
        }
        return viewModel(sessionId)
    }

    private fun entity(id: String, name: String, equipment: EquipmentType, movementKey: String?) = ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = "Legs",
        notes = "",
        isCustom = false,
        equipment = equipment.name,
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
        val SQUAT_KG = WeightConverter.lbsToKg(135.0)
    }
}
