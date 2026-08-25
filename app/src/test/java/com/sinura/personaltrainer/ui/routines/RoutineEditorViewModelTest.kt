package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.relation.RoutineWithExercises
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
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
 * The editor writes every structural edit through Room. Name and notes wait
 * for leave. An empty stub created this session must not survive the exit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RoutineEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: RoutineEditorViewModel? = null

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
    fun newRoutineOpensEditingWithoutARow() = runBlocking {
        val vm = createViewModel("new")
        val state = vm.uiState.first { !it.isLoading }
        assertFalse(state.missing)
        assertNull(state.routine)
        assertNull(deps.routineRepository.observeAll().first().singleOrNull())
    }

    @Test
    fun existingRoutineLoadsNameExercisesAndNotes() = runBlocking {
        val fixture = seedTestWorkout(deps, notes = "")
        deps.workoutRepository.discardSession(fixture.session.id)
        deps.routineRepository.updateDetails(fixture.routine.id, fixture.routine.name, "keep these")

        val vm = createViewModel(fixture.routine.id)
        val state = vm.uiState.first { !it.isLoading && it.routine != null }

        assertEquals(fixture.routine.id, state.routine?.id)
        assertEquals(fixture.routine.name, state.name)
        assertEquals("keep these", state.notes)
        assertEquals(1, state.routine?.exercises?.size)
    }

    @Test
    fun missingRoutineResolvesMissingWithUserMessage() = runBlocking {
        val vm = createViewModel("gone")
        val state = vm.uiState.first { it.missing && it.error != null }
        assertEquals("This routine is no longer available.", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun existingRoutineHydrationFailureBecomesErrorNotStuckLoading() = runBlocking {
        // N8: when the opening read of an existing routine throws, the editor used to sit on a
        // spinner forever. It must now resolve to an explicit, non-loading error state.
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val gate = FailureGate(shouldFail = true)
        val vm = createViewModel(fixture.routine.id, container = failingHydration(gate))

        val state = vm.uiState.first { it.failed }
        assertFalse(state.isLoading)
        assertFalse(state.missing)
    }

    @Test
    fun retryAfterHydrationFailureLoadsTheRoutine() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val gate = FailureGate(shouldFail = true)
        val vm = createViewModel(fixture.routine.id, container = failingHydration(gate))
        vm.uiState.first { it.failed }

        gate.shouldFail = false
        vm.retryHydration()

        val recovered = vm.uiState.first { !it.failed && !it.isLoading && it.routine != null }
        assertFalse(recovered.missing)
        assertEquals(fixture.routine.id, recovered.routine?.id)
        assertEquals(fixture.routine.name, recovered.name)
    }

    @Test
    fun addExerciseCreatesTheStubAndPersistsTheLift() = runBlocking {
        val exercise = insertTestExercise(deps, "row", "Chest-supported row")
        val vm = createViewModel("new")
        vm.uiState.first { !it.isLoading }
        vm.onNameChange("Pull")
        vm.addExercise(exercise, targetSets = 4, targetReps = 8, targetWeightKg = null, restSeconds = 90)

        val saved = awaitRoutine { it.exercises.size == 1 }
        assertEquals("Pull", saved.name)
        assertEquals(exercise.id, saved.exercises.single().exercise.id)
        assertEquals(4, saved.exercises.single().targetSets)
        assertEquals(8, saved.exercises.single().targetReps)
    }

    @Test
    fun addingTheSameLiftTwiceSurfacesTheUserMessage() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val vm = createViewModel(fixture.routine.id)
        vm.uiState.first { it.routine != null }

        vm.addExercise(fixture.exercise, 3, 5, 100.0, 90)

        assertEquals(
            "${fixture.exercise.name} is already in this routine.",
            eventually { vm.uiState.value.error },
        )
        assertEquals(1, deps.routineRepository.getById(fixture.routine.id)?.exercises?.size)
    }

    @Test
    fun leaveDiscardsAnEmptyStubCreatedThisSession() = runBlocking {
        val exercise = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.uiState.first { !it.isLoading }
        vm.addExercise(exercise, 3, 8, null, 90)
        val created = awaitRoutine { it.exercises.size == 1 }
        vm.removeExercise(created.exercises.single().id)
        awaitRoutine { it.exercises.isEmpty() }

        vm.leave()
        eventually { true.takeIf { vm.exitRequested.value } }
        assertTrue(deps.routineRepository.observeAll().first().isEmpty())
        vm.onExitHandled()
        assertFalse(vm.exitRequested.value)
    }

    @Test
    fun leavePersistsRenamedNotesOnAnExistingRoutine() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val vm = createViewModel(fixture.routine.id)
        vm.uiState.first { it.routine != null && it.name == fixture.routine.name }

        vm.onNameChange("Lower strength")
        vm.onNotesChange("tempo on the last set")
        vm.leave()

        eventually { true.takeIf { vm.exitRequested.value } }
        val saved = checkNotNull(deps.routineRepository.getById(fixture.routine.id))
        assertEquals("Lower strength", saved.name)
        assertEquals("tempo on the last set", saved.notes)
    }

    @Test
    fun stagedTargetsCommitWhenTheyDifferAndRejectZeroSets() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5)
        deps.workoutRepository.discardSession(fixture.session.id)
        val itemId = fixture.routine.exercises.single().id
        val vm = createViewModel(fixture.routine.id)
        vm.uiState.first { it.routine != null }

        vm.stageTargets(itemId, targetSets = 4, targetReps = 6, targetWeightKg = 110.0, restSeconds = 120)
        vm.commitTargets(itemId)
        val written = awaitRoutine { it.exercises.single().targetSets == 4 }
        assertEquals(6, written.exercises.single().targetReps)
        assertEquals(110.0, written.exercises.single().targetWeightKg)
        assertEquals(120, written.exercises.single().restSeconds)

        vm.stageTargets(itemId, targetSets = 0, targetReps = 6, targetWeightKg = 110.0, restSeconds = 120)
        vm.commitTargets(itemId)
        assertEquals("Sets and reps must be at least 1.", eventually { vm.uiState.value.error })
        assertEquals(4, deps.routineRepository.getById(fixture.routine.id)!!.exercises.single().targetSets)
    }

    @Test
    fun removeAndMovePersistOrder() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val row = insertTestExercise(deps, "row", "Row")
        deps.routineRepository.addExercise(fixture.routine.id, row, 3, 8, null, 90)
        val vm = createViewModel(fixture.routine.id)
        val loaded = vm.uiState.first { it.routine?.exercises?.size == 2 }.routine!!
        val first = loaded.exercises.first()
        val second = loaded.exercises.last()

        vm.moveExercise(second.id, -1)
        val swapped = awaitRoutine { it.exercises.first().id == second.id }
        assertEquals(listOf(second.exercise.id, first.exercise.id), swapped.exercises.map { it.exercise.id })

        vm.removeExercise(second.id)
        val remaining = awaitRoutine { it.exercises.size == 1 }
        assertEquals(first.exercise.id, remaining.exercises.single().exercise.id)
    }

    @Test
    fun swapKeepsPositionAndTargets() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5, targetWeightKg = 100.0)
        deps.workoutRepository.discardSession(fixture.session.id)
        val replacement = insertTestExercise(deps, "front-squat", "Front squat", muscleGroup = "Quads")
        val vm = createViewModel(fixture.routine.id)
        val item = vm.uiState.first { it.routine?.exercises?.size == 1 }.routine!!.exercises.single()

        vm.requestSwap(item.id)
        vm.uiState.first { it.swapItemId == item.id }
        vm.swapExercise(replacement)

        val saved = vm.uiState.first {
            it.routine?.exercises?.singleOrNull()?.exercise?.id == replacement.id
        }.routine!!
        assertEquals(3, saved.exercises.single().targetSets)
        assertEquals(5, saved.exercises.single().targetReps)
        assertNull(vm.uiState.value.swapItemId)
        val stored = checkNotNull(deps.routineRepository.getById(fixture.routine.id))
        assertEquals(replacement.id, stored.exercises.single().exercise.id)
        assertEquals(3, stored.exercises.single().targetSets)
        assertEquals(5, stored.exercises.single().targetReps)
    }

    @Test
    fun createAndSelectBlankOrDuplicateSurfacesErrorsWithoutWriting() = runBlocking {
        insertTestExercise(deps, "existing", "Existing lift")
        val vm = createViewModel("new")
        vm.uiState.first { !it.isLoading }

        vm.createAndSelect("  ", "Back")
        assertEquals("Exercise name is required.", eventually { vm.uiState.value.error })

        vm.createAndSelect("Existing lift", "Back")
        assertEquals(
            "That name is already in your library",
            vm.uiState.first { it.error == "That name is already in your library" }.error,
        )
        assertTrue(deps.exerciseRepository.observeAll().first().none { it.isCustom })
    }

    @Test
    fun confirmPendingAddWritesSelectedLiftsAndClosesThePicker() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.uiState.first { it.catalog.isNotEmpty() }

        vm.setPickerVisible(true)
        vm.togglePendingAdd(squat)
        vm.togglePendingAdd(row)
        vm.togglePendingAdd(row)
        assertEquals(listOf(squat.id), vm.uiState.first { it.pendingAddIds == listOf(squat.id) }.pendingAddIds)
        vm.togglePendingAdd(row)
        vm.uiState.first { it.pendingAddIds == listOf(squat.id, row.id) }
        vm.confirmPendingAdd()

        val saved = awaitRoutine { it.exercises.size == 2 }
        assertEquals(listOf(squat.id, row.id), saved.exercises.map { it.exercise.id })
        val closed = vm.uiState.first { !it.showExercisePicker && it.pendingAddIds.isEmpty() }
        assertFalse(closed.showExercisePicker)
        assertTrue(closed.pendingAddIds.isEmpty())
    }

    @Test
    fun confirmPendingAddWritesLiftsInReverseTapOrder() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.uiState.first { it.catalog.isNotEmpty() }

        vm.togglePendingAdd(row)
        vm.togglePendingAdd(squat)
        vm.confirmPendingAdd()

        val saved = awaitRoutine { it.exercises.size == 2 }
        assertEquals(listOf(row.id, squat.id), saved.exercises.map { it.exercise.id })
    }

    @Test
    fun confirmPendingAddDoesNotDuplicateOnASecondTap() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.uiState.first { it.catalog.isNotEmpty() }
        vm.togglePendingAdd(squat)
        vm.togglePendingAdd(row)
        vm.confirmPendingAdd()
        vm.confirmPendingAdd()

        val saved = awaitRoutine { it.exercises.size == 2 }
        assertEquals(listOf(squat.id, row.id), saved.exercises.map { it.exercise.id })
    }

    @Test
    fun confirmPendingAddKeepsTheCartWhenALiftIsMissingFromTheCatalog() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val vm = createViewModel("new")
        vm.uiState.first { it.catalog.isNotEmpty() }
        vm.setPickerVisible(true)
        vm.togglePendingAdd(squat.copy(id = "ghost", name = "Ghost"))
        vm.confirmPendingAdd()

        val state = vm.uiState.first { it.error != null }
        assertTrue(state.showExercisePicker)
        assertEquals(listOf("ghost"), state.pendingAddIds)
        assertTrue(deps.routineRepository.observeAll().first().isEmpty())
    }

    @Test
    fun confirmPendingAddSkipsLiftsAlreadyOnTheRoutine() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.uiState.first { it.catalog.isNotEmpty() }
        vm.togglePendingAdd(squat)
        vm.confirmPendingAdd()
        awaitRoutine { it.exercises.size == 1 }

        vm.setPickerVisible(true)
        vm.togglePendingAdd(squat)
        vm.togglePendingAdd(row)
        vm.confirmPendingAdd()

        val saved = awaitRoutine { it.exercises.size == 2 }
        assertEquals(listOf(squat.id, row.id), saved.exercises.map { it.exercise.id })
    }

    @Test
    fun confirmPendingAddWritesALiftCreatedInThePicker() = runBlocking {
        val vm = createViewModel("new")
        vm.uiState.first { !it.isLoading }
        vm.setPickerVisible(true)
        vm.createAndSelect("Good morning", "Hamstrings")
        vm.uiState.first { it.pendingAddIds.isNotEmpty() }
        vm.confirmPendingAdd()

        val saved = awaitRoutine { it.exercises.size == 1 }
        assertEquals("Good morning", saved.exercises.single().exercise.name)
        assertTrue(saved.exercises.single().exercise.isCustom)
        assertFalse(vm.uiState.value.showExercisePicker)
    }

    private fun createViewModel(
        routineId: String,
        container: AppDependencies = deps,
    ): RoutineEditorViewModel =
        RoutineEditorViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            savedStateHandle = SavedStateHandle(mapOf("routineId" to routineId)),
            container = container,
        ).also { viewModel = it }

    /**
     * A copy of the graph whose routine reads throw on demand, so the editor's hydration failure
     * branch can be exercised. Only [RoutineDao.getById] — the opening read — is made to fail;
     * everything else runs against the real in-memory database.
     */
    private fun failingHydration(gate: FailureGate): AppDependencies {
        val repo = RoutineRepository(FailingGetByIdDao(deps.database.routineDao(), gate))
        return object : AppDependencies by deps {
            override val routineRepository: RoutineRepository = repo
        }
    }

    private class FailureGate(var shouldFail: Boolean)

    private class FailingGetByIdDao(
        private val delegate: RoutineDao,
        private val gate: FailureGate,
    ) : RoutineDao by delegate {
        override suspend fun getById(id: String): RoutineWithExercises? {
            if (gate.shouldFail) error("boom: Room could not read routine $id")
            return delegate.getById(id)
        }
    }

    private suspend fun awaitRoutine(predicate: (Routine) -> Boolean): Routine =
        eventually {
            deps.routineRepository.observeAll().first().singleOrNull()?.takeIf(predicate)
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
