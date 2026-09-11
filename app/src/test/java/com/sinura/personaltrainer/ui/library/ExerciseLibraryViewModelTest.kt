package com.sinura.personaltrainer.ui.library

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
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
    fun loadsCatalogAndGroupsWhenUnfiltered() = runBlocking {
        insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        insertTestExercise(deps, "row", "Row", muscleGroup = "Back")
        val vm = createViewModel()
        val state = vm.uiState.awaitFirst { !it.isLoading && it.exercises.size == 2 }
        assertTrue(state.grouped)
        assertTrue(state.families.isNotEmpty())
        assertEquals(2, state.visibleExercises.size)
    }

    @Test
    fun queryAndMuscleFilterNarrowAndClear() = runBlocking {
        insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        insertTestExercise(deps, "row", "Chest-supported row", muscleGroup = "Back")
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.exercises.size == 2 }

        vm.onQueryChange("row")
        val searched = vm.uiState.awaitFirst { it.query == "row" }
        assertEquals(listOf("Chest-supported row"), searched.visibleExercises.map { it.name })
        assertFalse(searched.grouped)

        vm.onQueryChange("")
        vm.onMuscleSelected(CanonicalMuscle.QUADRICEPS)
        val filtered = vm.uiState.awaitFirst { it.selectedMuscle == CanonicalMuscle.QUADRICEPS }
        assertEquals(listOf("Squat"), filtered.visibleExercises.map { it.name })

        vm.onMuscleSelected(CanonicalMuscle.QUADRICEPS)
        assertNull(vm.uiState.awaitFirst { it.selectedMuscle == null }.selectedMuscle)
    }

    @Test
    fun seedMuscleFromRouteAppliesOnce() = runBlocking {
        insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        vm.uiState.awaitFirst { !it.isLoading }
        vm.seedMuscleFromRoute(CanonicalMuscle.QUADRICEPS)
        assertEquals(
            CanonicalMuscle.QUADRICEPS,
            vm.uiState.awaitFirst { it.selectedMuscle == CanonicalMuscle.QUADRICEPS }.selectedMuscle,
        )
        vm.onMuscleSelected(null)
        vm.uiState.awaitFirst { it.selectedMuscle == null }
        vm.seedMuscleFromRoute(CanonicalMuscle.QUADRICEPS)
        assertNull(vm.uiState.value.selectedMuscle)
    }

    @Test
    fun saveEditorCreatesACustomAndRefusesABlankName() = runBlocking {
        val vm = createViewModel()
        vm.uiState.awaitFirst { !it.isLoading }
        vm.openCreate()
        vm.saveEditor()
        assertEquals(
            "Give this lift a name.",
            vm.uiState.awaitFirst { it.error == "Give this lift a name." }.error,
        )
        assertNotNull(vm.uiState.value.editor)

        vm.updateEditor(vm.uiState.value.editor!!.copy(name = "My row", muscleGroup = "Back"))
        vm.saveEditor()
        val created = deps.exerciseRepository.observeAll().first { list ->
            list.any { it.isCustom && it.name == "My row" }
        }.first { it.isCustom && it.name == "My row" }
        assertEquals("Back", created.muscleGroup)
        val saved = vm.uiState.awaitFirst { it.editor == null && it.message == "Created My row." }
        assertNull(saved.editor)
        assertEquals("Created My row.", saved.message)
    }

    @Test
    fun builtInCannotBeEditedOrDeleted() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.exercises.isNotEmpty() }

        vm.openEdit(squat)
        assertEquals(
            "Built-in lifts can’t be edited.",
            vm.uiState.awaitFirst { it.error == "Built-in lifts can’t be edited." }.error,
        )
        assertNull(vm.uiState.value.editor)

        vm.requestDelete(squat)
        assertEquals(
            "Built-in lifts can’t be deleted.",
            vm.uiState.awaitFirst { it.error == "Built-in lifts can’t be deleted." }.error,
        )
        assertNull(vm.uiState.value.pendingDelete)
        assertNull(vm.uiState.value.blockedDelete)
    }

    @Test
    fun unusedCustomOffersDeleteAndConfirmRemovesTheRow() = runBlocking {
        val custom = insertTestExercise(deps, "ex-custom-1", "My fly", muscleGroup = "Chest", isCustom = true)
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.exercises.any { it.id == custom.id } }

        vm.requestDelete(custom)
        assertEquals(
            custom.id,
            vm.uiState.awaitFirst { it.pendingDelete?.id == custom.id }.pendingDelete?.id,
        )
        vm.confirmDelete()
        deps.exerciseRepository.observeAll().first { list -> list.none { it.id == custom.id } }
        assertEquals(
            "Deleted My fly.",
            vm.uiState.awaitFirst { it.message == "Deleted My fly." }.message,
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
        vm.uiState.awaitFirst { it.exercises.any { it.id == custom.exercise.id } }

        vm.requestDelete(custom.exercise)
        val blocked = checkNotNull(vm.uiState.awaitFirst { it.blockedDelete != null }.blockedDelete)
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
        vm.uiState.awaitFirst { it.routines.isNotEmpty() }

        vm.openAddToRoutine(fixture.exercise)
        vm.addToRoutine(fixture.routine.id)
        assertEquals(
            "${fixture.exercise.name} is already in ${fixture.routine.name}.",
            vm.uiState.awaitFirst {
                it.message == "${fixture.exercise.name} is already in ${fixture.routine.name}."
            }.message,
        )
        assertNull(vm.uiState.value.addToRoutine)
        assertEquals(1, deps.routineRepository.getById(fixture.routine.id)?.exercises?.size)
    }

    @Test
    fun addToRoutineMissingRoutineSurfacesError() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.exercises.isNotEmpty() }
        vm.openAddToRoutine(squat)
        vm.addToRoutine("gone")
        assertEquals(
            "That routine is no longer available.",
            vm.uiState.awaitFirst { it.error == "That routine is no longer available." }.error,
        )
    }

    @Test
    fun addToRoutinePersistsDefaults() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val routine = deps.routineRepository.create("Upper")
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.routines.any { it.id == routine.id } }
        vm.openAddToRoutine(squat)
        vm.addToRoutine(routine.id)
        val saved = deps.routineRepository.observeAll().first { list ->
            list.any { it.id == routine.id && it.exercises.size == 1 }
        }.first { it.id == routine.id }
        assertEquals(squat.id, saved.exercises.single().exercise.id)
        assertTrue(saved.exercises.single().targetSets >= 1)
        assertEquals(
            "Added ${squat.name} to ${routine.name}.",
            vm.uiState.awaitFirst { it.message == "Added ${squat.name} to ${routine.name}." }.message,
        )
    }

    @Test
    fun keepBothNamesDismissesTheCollision() = runBlocking {
        insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val custom = insertTestExercise(deps, "ex-custom-squat", "Squat", muscleGroup = "Quads", isCustom = true)
        val vm = createViewModel()
        val flagged = vm.uiState.awaitFirst { it.needsAttention.any { it.id == custom.id } }
        assertTrue(flagged.needsAttention.any { it.id == custom.id })

        vm.keepBothNames(custom)
        vm.uiState.awaitFirst { it.needsAttention.none { it.id == custom.id } }
        assertTrue(deps.preferencesRepository.dismissedCollisionIds.first().contains(custom.id))
    }

    @Test
    fun saveEditorPersistsTheChosenLoadOnANewCustom() = runBlocking {
        val vm = createViewModel()
        vm.uiState.awaitFirst { !it.isLoading }
        vm.openCreate()
        val draft = checkNotNull(vm.uiState.awaitFirst { it.editor != null }.editor)
        vm.updateEditor(
            draft.copy(
                name = "My push-up",
                muscleGroup = "Chest",
                loadType = LoadType.BODYWEIGHT,
            ),
        )
        vm.saveEditor()
        val saved = deps.exerciseRepository.observeAll().first { list ->
            list.any { it.name == "My push-up" }
        }.first { it.name == "My push-up" }
        assertEquals(LoadType.BODYWEIGHT, saved.loadType)
        assertEquals(EquipmentType.BODYWEIGHT, saved.equipment)
        assertEquals(
            "Created My push-up.",
            vm.uiState.awaitFirst { it.message == "Created My push-up." }.message,
        )
        assertNull(vm.uiState.value.editor)
    }

    @Test
    fun saveEditorCanReclassifyAnExistingCustom() = runBlocking {
        val custom = (deps.exerciseRepository.createCustom("My dip", "Chest") as com.sinura.personaltrainer.data.repository.SaveExerciseResult.Saved)
            .exercise
        val vm = createViewModel()
        vm.uiState.awaitFirst { it.exercises.any { it.id == custom.id } }
        vm.openEdit(custom)
        val draft = checkNotNull(vm.uiState.awaitFirst { it.editor?.id == custom.id }.editor)
        assertEquals(LoadType.EXTERNAL, draft.loadType)
        vm.updateEditor(draft.copy(loadType = LoadType.ASSISTED))
        vm.saveEditor()
        val saved = checkNotNull(deps.exerciseRepository.getById(custom.id))
        assertEquals(LoadType.ASSISTED, saved.loadType)
        assertEquals(EquipmentType.MACHINE, saved.equipment)
        assertEquals(
            "Updated My dip.",
            vm.uiState.awaitFirst { it.message == "Updated My dip." }.message,
        )
    }

    @Test
    fun toggleFamilyExpandsAndCollapses() = runBlocking {
        insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel()
        val family = vm.uiState.awaitFirst { it.families.isNotEmpty() }.families.first()
        vm.toggleFamily(family.movementKey)
        assertTrue(
            vm.uiState.awaitFirst { it.expandedFamilies.contains(family.movementKey) }
                .expandedFamilies.contains(family.movementKey),
        )
        vm.toggleFamily(family.movementKey)
        assertFalse(
            vm.uiState.awaitFirst { !it.expandedFamilies.contains(family.movementKey) }
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
}
