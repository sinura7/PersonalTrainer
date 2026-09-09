package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.testutil.awaitFirst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Custom week is a draft until Confirm. An empty week cannot be written, and
 * a preferred day without lifts is not a training day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CustomWeekViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: CustomWeekViewModel? = null
    private var keepAlive: Job? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
    }

    @After
    fun tearDown() {
        keepAlive?.cancel()
        keepAlive = null
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun emptyWeekCannotConfirmAndConfirmIsANoOp() = runBlocking {
        val vm = createViewModel()
        val state = vm.uiState.awaitFirst { true }
        assertFalse(state.canConfirm)
        assertEquals(0, state.trainingDays)

        vm.confirm()
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.finished.value)
        assertTrue(deps.routineRepository.observeAll().first().isEmpty())
        assertFalse(deps.preferencesRepository.onboardingComplete.first())
    }

    @Test
    fun addingALiftEnablesConfirmAndWritesThatDay() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.catalog.any { exercise -> exercise.id == squat.id } }

        vm.selectDay(Weekday.TUESDAY)
        vm.setPickerVisible(true)
        vm.togglePicked(squat)

        val staged = vm.uiState.awaitFirst { it.canConfirm }
        assertEquals(Weekday.TUESDAY, staged.selectedDay)
        assertEquals(1, staged.trainingDays)
        assertEquals(squat.id, staged.selectedLifts.single().exercise.id)
        // The sheet stays open on the day it is filling. The tap was the write.
        assertTrue(staged.showPicker)
        assertEquals(listOf(squat.id), staged.pickedIds)
        vm.setPickerVisible(false)

        vm.confirm()
        vm.finished.awaitFirst { it }
        val routines = deps.routineRepository.observeAll().first()
        assertEquals(listOf("Tuesday"), routines.map { it.name })
        assertEquals(squat.id, routines.single().exercises.single().exercise.id)
        assertTrue(deps.preferencesRepository.onboardingComplete.first())
    }

    @Test
    fun aSecondTapTakesTheLiftOffTheDay() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.catalog.size >= 2 }
        vm.setPickerVisible(true)
        vm.togglePicked(squat)
        vm.togglePicked(row)
        vm.uiState.awaitFirst { it.selectedLifts.size == 2 }

        vm.togglePicked(squat)

        val left = vm.uiState.awaitFirst { it.selectedLifts.size == 1 }
        assertEquals(listOf(row.id), left.selectedLifts.map { it.exercise.id })
        assertEquals(listOf(row.id), left.pickedIds)
    }

    @Test
    fun tapOrderIsTheOrderTheDayKeeps() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.catalog.size >= 2 }

        vm.selectDay(Weekday.THURSDAY)
        vm.togglePicked(row)
        vm.togglePicked(squat)

        assertEquals(
            listOf(row.id, squat.id),
            vm.uiState.awaitFirst { it.selectedLifts.size == 2 }.selectedLifts.map { it.exercise.id },
        )
        assertEquals(Weekday.THURSDAY, vm.uiState.value.selectedDay)
    }

    @Test
    fun closingThePickerKeepsEveryLiftAlreadyTapped() = runBlocking {
        // The defect this replaced: a tap outside the sheet emptied the cart, and the day
        // had to be built again from the first lift.
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.catalog.size >= 2 }
        vm.setPickerVisible(true)
        vm.onSearchQuery("squ")
        vm.togglePicked(squat)
        vm.togglePicked(row)
        vm.uiState.awaitFirst { it.selectedLifts.size == 2 }

        vm.setPickerVisible(false)

        val closed = vm.uiState.awaitFirst { !it.showPicker }
        assertEquals("", closed.searchQuery)
        assertEquals(listOf(squat.id, row.id), closed.selectedLifts.map { it.exercise.id })
        // Reopening starts where the day stands, numbered from 1.
        vm.setPickerVisible(true)
        assertEquals(listOf(squat.id, row.id), vm.uiState.awaitFirst { it.showPicker }.pickedIds)
    }

    @Test
    fun createAndSelectPutsTheNewLiftOnTheDay() = runBlocking {
        val vm = createViewModel()
        vm.setPickerVisible(true)
        vm.createAndSelect("Good morning", "Hamstrings")

        val staged = vm.uiState.awaitFirst { it.selectedLifts.size == 1 }
        assertEquals("Good morning", staged.selectedLifts.single().exercise.name)
        assertTrue(staged.selectedLifts.single().exercise.isCustom)
        // Creating a lift is one more tap in a list being built, so the sheet stays open.
        assertTrue(staged.showPicker)
    }

    @Test
    fun emptyPickerLeadsWithTheLiftLoggedMostRecently() = runBlocking {
        seedTestWorkout(
            deps,
            exerciseId = "zz-squat",
            exerciseName = "ZZ Squat",
            loggedSets = listOf(TestSetInput(weightKg = 100.0, reps = 5)),
            finish = true,
        )
        insertTestExercise(deps, "aa-bench", "AA Bench", muscleGroup = "Chest")
        val vm = createViewModel()
        val state = vm.uiState.awaitFirst {
            it.searchResults.size >= 2 && it.searchResults.first().name == "ZZ Squat"
        }
        assertEquals("ZZ Squat", state.searchResults.first().name)
    }

    @Test
    fun createAndSelectAppearsInPickerResultsBeforeTheCatalogCatchesUp() = runBlocking {
        val vm = createViewModel()
        vm.setPickerVisible(true)
        vm.createAndSelect("Good morning", "Hamstrings")
        val state = vm.uiState.awaitFirst { it.pickedIds.isNotEmpty() }
        assertTrue(state.searchResults.any { it.name == "Good morning" })
    }

    @Test
    fun moveRemoveAndStageTargetsStayInTheDraftUntilConfirm() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.catalog.size >= 2 }
        vm.togglePicked(squat)
        vm.togglePicked(row)
        val lifts = vm.uiState.awaitFirst { it.selectedLifts.size == 2 }.selectedLifts
        assertEquals(listOf(squat.id, row.id), lifts.map { it.exercise.id })
        val first = lifts.first()
        val second = lifts.last()

        vm.moveLift(second.id, -1)
        assertEquals(
            listOf(second.exercise.id, first.exercise.id),
            vm.uiState.awaitFirst { it.selectedLifts.firstOrNull()?.exercise?.id == second.exercise.id }
                .selectedLifts.map { it.exercise.id },
        )

        vm.stageTargets(second.id, sets = 4, reps = 6, rest = 150, weightKg = 80.0)
        val staged = vm.uiState.awaitFirst {
            it.selectedLifts.any { lift -> lift.id == second.id && lift.targetSets == 4 }
        }.selectedLifts.first { it.id == second.id }
        assertEquals(4, staged.targetSets)
        assertEquals(6, staged.targetReps)
        assertEquals(150, staged.restSeconds)
        assertEquals(80.0, staged.targetWeightKg)

        vm.removeLift(first.id)
        assertEquals(
            listOf(second.exercise.id),
            vm.uiState.awaitFirst { it.selectedLifts.size == 1 }.selectedLifts.map { it.exercise.id },
        )
        assertTrue(deps.routineRepository.observeAll().first().isEmpty())
    }

    @Test
    fun seedFromGuidedSelectsTheFirstPreferredDayAndHoldsTheWeightUnit() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.catalog.isNotEmpty() }

        vm.seedFromGuided(
            preferred = setOf(Weekday.WEDNESDAY, Weekday.FRIDAY),
            answers = null,
            unit = WeightUnit.KG,
        )
        val seeded = vm.uiState.awaitFirst {
            it.selectedDay == Weekday.WEDNESDAY &&
                it.preferredDays == setOf(Weekday.WEDNESDAY, Weekday.FRIDAY)
        }
        assertEquals(Weekday.WEDNESDAY, seeded.selectedDay)
        assertEquals(setOf(Weekday.WEDNESDAY, Weekday.FRIDAY), seeded.preferredDays)

        vm.togglePicked(squat)
        vm.confirm()
        vm.finished.awaitFirst { it }
        assertEquals(WeightUnit.KG, deps.preferencesRepository.weightUnit.first())
    }

    @Test
    fun createAndSelectDuplicateSurfacesTheSharedNameMessage() = runBlocking {
        insertTestExercise(deps, "existing", "Existing lift")
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.catalog.isNotEmpty() }

        vm.createAndSelect("Existing lift", "Back")
        assertEquals(
            "That name is already in your library",
            vm.uiState.awaitFirst { it.error == "That name is already in your library" }.error,
        )
        assertTrue(vm.uiState.value.pickedIds.isEmpty())
    }

    @Test
    fun createAndSelectBlankSurfacesTheNameError() = runBlocking {
        val vm = createViewModel()
        vm.createAndSelect("  ", "Back")
        assertEquals(
            SessionOrderCopy.LIFT_NAME_REQUIRED,
            vm.uiState.awaitFirst { it.error == SessionOrderCopy.LIFT_NAME_REQUIRED }.error,
        )
        assertTrue(deps.exerciseRepository.observeAll().first().isEmpty())
    }

    @Test
    fun createAndSelectAfterDismissDoesNotLeaveAGhostCart() = runBlocking {
        val vm = createViewModel()
        vm.setPickerVisible(true)
        vm.setPickerVisible(false)
        vm.createAndSelect("Good morning", "Hamstrings")

        deps.exerciseRepository.observeAll().first { list -> list.any { it.name == "Good morning" } }
        assertTrue(vm.uiState.value.pickedIds.isEmpty())
        assertTrue(vm.uiState.value.selectedLifts.isEmpty())
        assertFalse(vm.uiState.value.showPicker)
    }

    @Test
    fun dismissingThePickerClearsTheSearchAndNothingElse() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.catalog.isNotEmpty() }
        vm.setPickerVisible(true)
        vm.onSearchQuery("squ")
        vm.togglePicked(squat)

        vm.setPickerVisible(false)

        val state = vm.uiState.awaitFirst { !it.showPicker }
        assertEquals("", state.searchQuery)
        assertEquals(listOf(squat.id), state.pickedIds)
        assertEquals(squat.id, state.selectedLifts.single().exercise.id)
    }

    @Test
    fun processDeathRestoresDaysAndSelectedDay() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val handle = SavedStateHandle()
        val first = createViewModel(handle)
        first.uiState.awaitFirst { it.catalog.any { exercise -> exercise.id == squat.id } }
        first.selectDay(Weekday.TUESDAY)
        first.setPickerVisible(true)
        first.togglePicked(squat)
        first.uiState.awaitFirst { it.canConfirm && it.selectedDay == Weekday.TUESDAY }

        keepAlive?.cancel()
        keepAlive = null
        first.clearAndJoinForTest()
        viewModel = null

        val restored = createViewModel(handle)
        val state = restored.uiState.awaitFirst {
            it.selectedDay == Weekday.TUESDAY && it.selectedLifts.isNotEmpty()
        }
        assertEquals(Weekday.TUESDAY, state.selectedDay)
        assertEquals(squat.id, state.selectedLifts.single().exercise.id)
        assertEquals("Squat", state.selectedLifts.single().exercise.name)
        assertTrue(state.canConfirm)
    }

    private fun createViewModel(
        handle: SavedStateHandle = SavedStateHandle(),
    ): CustomWeekViewModel =
        CustomWeekViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            handle,
            deps,
        ).also { vm ->
            viewModel = vm
            keepAlive = CoroutineScope(dispatcher).launch { vm.uiState.collect { } }
        }
}
