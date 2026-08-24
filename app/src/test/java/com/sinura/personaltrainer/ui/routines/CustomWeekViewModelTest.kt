package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.insertTestExercise
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun emptyWeekCannotConfirmAndConfirmIsANoOp() = runBlocking {
        val vm = createViewModel()
        val state = vm.uiState.first { true }
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
        vm.uiState.first { it.catalog.any { exercise -> exercise.id == squat.id } }

        vm.selectDay(DayOfWeek.TUESDAY)
        vm.setPickerVisible(true)
        vm.togglePendingAdd(squat)
        vm.confirmPendingAdd()

        val staged = vm.uiState.first { it.canConfirm }
        assertEquals(DayOfWeek.TUESDAY, staged.selectedDay)
        assertEquals(1, staged.trainingDays)
        assertEquals(squat.id, staged.selectedLifts.single().exercise.id)
        assertFalse(staged.showPicker)

        vm.confirm()
        eventually { true.takeIf { vm.finished.value } }
        val routines = deps.routineRepository.observeAll().first()
        assertEquals(listOf("Tuesday"), routines.map { it.name })
        assertEquals(squat.id, routines.single().exercises.single().exercise.id)
        assertTrue(deps.preferencesRepository.onboardingComplete.first())
    }

    @Test
    fun duplicateLiftOnTheSameDayIsIgnored() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        vm.uiState.first { it.catalog.isNotEmpty() }
        vm.togglePendingAdd(squat)
        vm.confirmPendingAdd()
        vm.uiState.first { it.selectedLifts.size == 1 }
        vm.setPickerVisible(true)
        vm.togglePendingAdd(squat)
        vm.confirmPendingAdd()

        assertEquals(1, vm.uiState.first { it.selectedLifts.size == 1 }.selectedLifts.size)
    }

    @Test
    fun moveRemoveAndStageTargetsStayInTheDraftUntilConfirm() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel()
        vm.uiState.first { it.catalog.size >= 2 }
        vm.togglePendingAdd(squat)
        vm.togglePendingAdd(row)
        vm.confirmPendingAdd()
        val lifts = vm.uiState.first { it.selectedLifts.size == 2 }.selectedLifts
        val first = lifts.first()
        val second = lifts.last()

        vm.moveLift(second.id, -1)
        assertEquals(
            listOf(second.exercise.id, first.exercise.id),
            vm.uiState.first { it.selectedLifts.firstOrNull()?.exercise?.id == second.exercise.id }
                .selectedLifts.map { it.exercise.id },
        )

        vm.stageTargets(second.id, sets = 4, reps = 6, rest = 150, weightKg = 80.0)
        val staged = vm.uiState.first {
            it.selectedLifts.any { lift -> lift.id == second.id && lift.targetSets == 4 }
        }.selectedLifts.first { it.id == second.id }
        assertEquals(4, staged.targetSets)
        assertEquals(6, staged.targetReps)
        assertEquals(150, staged.restSeconds)
        assertEquals(80.0, staged.targetWeightKg)

        vm.removeLift(first.id)
        assertEquals(
            listOf(second.exercise.id),
            vm.uiState.first { it.selectedLifts.size == 1 }.selectedLifts.map { it.exercise.id },
        )
        assertTrue(deps.routineRepository.observeAll().first().isEmpty())
    }

    @Test
    fun seedFromGuidedSelectsTheFirstPreferredDayAndHoldsTheWeightUnit() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        vm.uiState.first { it.catalog.isNotEmpty() }

        vm.seedFromGuided(
            preferred = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            answers = null,
            unit = WeightUnit.KG,
        )
        assertEquals(DayOfWeek.WEDNESDAY, vm.uiState.value.selectedDay)
        assertEquals(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), vm.uiState.value.preferredDays)

        vm.togglePendingAdd(squat)
        vm.confirmPendingAdd()
        vm.confirm()
        eventually { true.takeIf { vm.finished.value } }
        assertEquals(WeightUnit.KG, deps.preferencesRepository.weightUnit.first())
    }

    @Test
    fun createAndSelectDuplicateSurfacesTheSharedNameMessage() = runBlocking {
        insertTestExercise(deps, "existing", "Existing lift")
        val vm = createViewModel()
        vm.uiState.first { it.catalog.isNotEmpty() }

        vm.createAndSelect("Existing lift", "Back")
        assertEquals(
            "That name is already in your library",
            eventually { vm.uiState.value.error },
        )
        assertTrue(vm.uiState.value.pendingAddIds.isEmpty())
    }

    @Test
    fun dismissingThePickerClearsSearchAndPendingAdds() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        vm.uiState.first { it.catalog.isNotEmpty() }
        vm.setPickerVisible(true)
        vm.onSearchQuery("squ")
        vm.togglePendingAdd(squat)

        vm.setPickerVisible(false)

        val state = vm.uiState.value
        assertFalse(state.showPicker)
        assertEquals("", state.searchQuery)
        assertTrue(state.pendingAddIds.isEmpty())
    }

    private fun createViewModel(): CustomWeekViewModel =
        CustomWeekViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            deps,
        ).also { viewModel = it }

    private suspend fun <T : Any> eventually(block: suspend () -> T?): T =
        withTimeout(5_000) {
            while (true) {
                dispatcher.scheduler.runCurrent()
                block()?.let { return@withTimeout it }
                delay(10)
            }
            error("unreachable")
        }
}
