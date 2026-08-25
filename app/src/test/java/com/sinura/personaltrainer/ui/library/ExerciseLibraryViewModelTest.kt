package com.sinura.personaltrainer.ui.library

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Library filters, editor, and delete must stay honest: built-ins cannot be
 * edited or deleted, and a used custom cannot disappear behind a confirm.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExerciseLibraryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: ExerciseLibraryViewModel? = null
    private var keepAlive: Job? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
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
    fun loadsCatalogAndGroupsWhenUnfiltered() = runBlocking {
        insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        insertTestExercise(deps, "row", "Row", muscleGroup = "Back")
        val vm = createViewModel()
        val state = vm.uiState.first { !it.isLoading && it.exercises.size == 2 }
        assertTrue(state.grouped)
        assertTrue(state.families.isNotEmpty())
        assertEquals(2, state.visibleExercises.size)
    }

    @Test
    fun queryAndMuscleFilterNarrowAndClear() = runBlocking {
        insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        insertTestExercise(deps, "row", "Chest-supported row", muscleGroup = "Back")
        val vm = createViewModel()
        vm.uiState.first { it.exercises.size == 2 }

        vm.onQueryChange("row")
        val searched = vm.uiState.first { it.query == "row" }
        assertEquals(listOf("Chest-supported row"), searched.visibleExercises.map { it.name })
        assertFalse(searched.grouped)

        vm.onQueryChange("")
        vm.onMuscleSelected(CanonicalMuscle.QUADRICEPS)
        val filtered = vm.uiState.first { it.selectedMuscle == CanonicalMuscle.QUADRICEPS }
        assertEquals(listOf("Squat"), filtered.visibleExercises.map { it.name })

        vm.onMuscleSelected(CanonicalMuscle.QUADRICEPS)
        assertNull(vm.uiState.first { it.selectedMuscle == null }.selectedMuscle)
    }

    @Test
    fun seedMuscleFromRouteAppliesOnce() = runBlocking {
        insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        vm.uiState.first { !it.isLoading }
        vm.seedMuscleFromRoute(CanonicalMuscle.QUADRICEPS)
        assertEquals(
            CanonicalMuscle.QUADRICEPS,
            eventually { vm.uiState.value.selectedMuscle },
        )
        vm.onMuscleSelected(null)
        vm.uiState.first { it.selectedMuscle == null }
        vm.seedMuscleFromRoute(CanonicalMuscle.QUADRICEPS)
        assertNull(vm.uiState.value.selectedMuscle)
    }

    @Test
    fun saveEditorCreatesACustomAndRefusesABlankName() = runBlocking {
        val vm = createViewModel()
        vm.uiState.first { !it.isLoading }
        vm.openCreate()
        vm.saveEditor()
        assertEquals("Give this exercise a name.", eventually { vm.uiState.value.error })
        assertNotNull(vm.uiState.value.editor)

        vm.updateEditor(vm.uiState.value.editor!!.copy(name = "My row", muscleGroup = "Back"))
        vm.saveEditor()
        val created = eventually {
            deps.exerciseRepository.observeAll().first().firstOrNull { it.isCustom && it.name == "My row" }
        }
        assertEquals("Back", created.muscleGroup)
        val saved = vm.uiState.first { it.editor == null && it.message == "Created My row." }
        assertNull(saved.editor)
        assertEquals("Created My row.", saved.message)
    }

    @Test
    fun builtInCannotBeEditedOrDeleted() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        vm.uiState.first { it.exercises.isNotEmpty() }

        vm.openEdit(squat)
        assertEquals("Built-in exercises can’t be edited.", eventually { vm.uiState.value.error })
        assertNull(vm.uiState.value.editor)

        vm.requestDelete(squat)
        assertEquals("Built-in exercises can’t be deleted.", eventually { vm.uiState.value.error })
        assertNull(vm.uiState.value.pendingDelete)
        assertNull(vm.uiState.value.blockedDelete)
    }

    @Test
    fun unusedCustomOffersDeleteAndConfirmRemovesTheRow() = runBlocking {
        val custom = insertTestExercise(deps, "ex-custom-1", "My fly", muscleGroup = "Chest", isCustom = true)
        val vm = createViewModel()
        vm.uiState.first { it.exercises.any { it.id == custom.id } }

        vm.requestDelete(custom)
        assertEquals(custom.id, eventually { vm.uiState.value.pendingDelete?.id })
        vm.confirmDelete()
        eventually { true.takeIf { deps.exerciseRepository.getById(custom.id) == null } }
        assertEquals(
            "Deleted My fly.",
            eventually { vm.uiState.value.message.takeIf { it == "Deleted My fly." } },
        )
        assertNull(vm.uiState.value.pendingDelete)
    }

    @Test
    fun usedCustomBlocksDeleteInsteadOfOfferingConfirm() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val custom = deps.exerciseRepository.createCustom("My squat", "Quads")
        check(custom is com.sinura.personaltrainer.data.repository.SaveExerciseResult.Saved)
        deps.routineRepository.addExercise(fixture.routine.id, custom.exercise, 3, 5, null, 90)
        val vm = createViewModel()
        vm.uiState.first { it.exercises.any { it.id == custom.exercise.id } }

        vm.requestDelete(custom.exercise)
        val blocked = eventually { vm.uiState.value.blockedDelete }
        assertEquals(custom.exercise.id, blocked.first.id)
        assertTrue(blocked.second.isReferenced)
        assertNull(vm.uiState.value.pendingDelete)
        assertNotNull(deps.exerciseRepository.getById(custom.exercise.id))
    }

    @Test
    fun addToRoutineAlreadyPresentMessagesWithoutWritingASecondRow() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val vm = createViewModel()
        vm.uiState.first { it.routines.isNotEmpty() }

        vm.openAddToRoutine(fixture.exercise)
        vm.addToRoutine(fixture.routine.id)
        assertEquals(
            "${fixture.exercise.name} is already in ${fixture.routine.name}.",
            eventually { vm.uiState.value.message },
        )
        assertNull(vm.uiState.value.addToRoutine)
        assertEquals(1, deps.routineRepository.getById(fixture.routine.id)?.exercises?.size)
    }

    @Test
    fun addToRoutineMissingRoutineSurfacesError() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        vm.uiState.first { it.exercises.isNotEmpty() }
        vm.openAddToRoutine(squat)
        vm.addToRoutine("gone")
        assertEquals("That routine is no longer available.", eventually { vm.uiState.value.error })
    }

    @Test
    fun addToRoutinePersistsDefaults() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val routine = deps.routineRepository.create("Upper")
        val vm = createViewModel()
        vm.uiState.first { it.routines.any { it.id == routine.id } }
        vm.openAddToRoutine(squat)
        vm.addToRoutine(routine.id)
        val saved = eventually {
            deps.routineRepository.getById(routine.id)?.takeIf { it.exercises.size == 1 }
        }
        assertEquals(squat.id, saved.exercises.single().exercise.id)
        assertTrue(saved.exercises.single().targetSets >= 1)
        assertEquals(
            "Added ${squat.name} to ${routine.name}.",
            eventually { vm.uiState.value.message.takeIf { it == "Added ${squat.name} to ${routine.name}." } },
        )
    }

    @Test
    fun keepBothNamesDismissesTheCollision() = runBlocking {
        insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val custom = insertTestExercise(deps, "ex-custom-squat", "Squat", muscleGroup = "Quads", isCustom = true)
        val vm = createViewModel()
        val flagged = vm.uiState.first { it.needsAttention.any { it.id == custom.id } }
        assertTrue(flagged.needsAttention.any { it.id == custom.id })

        vm.keepBothNames(custom)
        vm.uiState.first { it.needsAttention.none { it.id == custom.id } }
        assertTrue(deps.preferencesRepository.dismissedCollisionIds.first().contains(custom.id))
    }

    @Test
    fun toggleFamilyExpandsAndCollapses() = runBlocking {
        insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        val family = vm.uiState.first { it.families.isNotEmpty() }.families.first()
        vm.toggleFamily(family.movementKey)
        assertTrue(
            vm.uiState.first { it.expandedFamilies.contains(family.movementKey) }
                .expandedFamilies.contains(family.movementKey),
        )
        vm.toggleFamily(family.movementKey)
        assertFalse(
            vm.uiState.first { !it.expandedFamilies.contains(family.movementKey) }
                .expandedFamilies.contains(family.movementKey),
        )
    }

    private fun createViewModel(): ExerciseLibraryViewModel =
        ExerciseLibraryViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            deps,
        ).also { vm ->
            viewModel = vm
            keepAlive = CoroutineScope(dispatcher).launch { vm.uiState.collect { } }
        }

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
