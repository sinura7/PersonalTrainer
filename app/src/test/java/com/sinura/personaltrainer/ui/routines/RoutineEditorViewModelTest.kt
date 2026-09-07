package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.relation.RoutineWithExercises
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineSaveCopy
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
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
            vm.uiState.first { it.error == "${fixture.exercise.name} is already in this routine." }.error,
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
        vm.exitRequested.first { it }
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

        vm.exitRequested.first { it }
        val saved = checkNotNull(deps.routineRepository.getById(fixture.routine.id))
        assertEquals("Lower strength", saved.name)
        assertEquals("tempo on the last set", saved.notes)
    }

    @Test
    fun saveAndLeaveKeepsACreatedRoutineWithLiftsAndPersistsTheName() = runBlocking {
        val exercise = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.uiState.first { !it.isLoading }
        vm.onNameChange("Push")
        vm.addExercise(exercise, 3, 8, null, 90)
        val created = awaitRoutine { it.exercises.size == 1 }

        vm.saveAndLeave()
        vm.exitRequested.first { it }
        val saved = checkNotNull(deps.routineRepository.getById(created.id))
        assertEquals("Push", saved.name)
        assertEquals(1, saved.exercises.size)
    }

    @Test
    fun saveAndLeaveWithoutLiftsStaysOnTheEditor() = runBlocking {
        val vm = createViewModel("new")
        vm.uiState.first { !it.isLoading }
        vm.saveAndLeave()
        assertFalse(vm.exitRequested.value)
        assertEquals(
            SessionOrderCopy.NEED_A_LIFT,
            vm.uiState.first { it.error == SessionOrderCopy.NEED_A_LIFT }.error,
        )
        assertTrue(deps.routineRepository.observeAll().first().isEmpty())
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
        assertEquals(
            "Sets and reps must be at least 1.",
            vm.uiState.first { it.error == "Sets and reps must be at least 1." }.error,
        )
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
        assertEquals(
            SessionOrderCopy.LIFT_NAME_REQUIRED,
            vm.uiState.first { it.error == SessionOrderCopy.LIFT_NAME_REQUIRED }.error,
        )

        vm.createAndSelect("Existing lift", "Back")
        assertEquals(
            "That name is already in your library",
            vm.uiState.first { it.error == "That name is already in your library" }.error,
        )
        assertTrue(deps.exerciseRepository.observeAll().first().none { it.isCustom })
    }

    @Test
    fun createAndSelectAfterDismissDoesNotLeaveAGhostCart() = runBlocking {
        val vm = createViewModel("new")
        vm.uiState.first { !it.isLoading }
        vm.setPickerVisible(true)
        vm.setPickerVisible(false)
        vm.createAndSelect("Good morning", "Hamstrings")

        deps.exerciseRepository.observeAll().first { list -> list.any { it.name == "Good morning" } }
        assertTrue(vm.uiState.value.pendingAddIds.isEmpty())
        assertFalse(vm.uiState.value.showExercisePicker)
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
        // Both lifts, not merely one. confirmPendingAdd resolves every selected id against
        // uiState.catalog, and LiftCart.planConfirm returns blocked and writes nothing if one
        // is missing (LiftCart:66-68) — leaving the routine empty and the size == 2 wait below
        // unable to come true. isNotEmpty() is satisfied by the first of the two emissions.
        vm.uiState.first { it.catalog.size >= 2 }

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
        // Both lifts — see confirmPendingAddWritesLiftsInReverseTapOrder above.
        vm.uiState.first { it.catalog.size >= 2 }
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
        // Both lifts, not merely one. confirmPendingAdd resolves the selected ids against
        // uiState.catalog (:463-469), and LiftCart.planConfirm (:66-68) returns blocked --
        // writing nothing at all, not even the ids it could resolve -- as soon as one is
        // missing. Catch the emission carrying only squat and the row toggled below is
        // unresolvable, the second confirm writes nothing, and the size == 2 wait at the
        // end can never come true. Two other tests in this file were tightened for exactly
        // this in b29aade; this one was missed and it failed on trunk.
        vm.uiState.first { it.catalog.size >= 2 }
        vm.togglePendingAdd(squat)
        vm.confirmPendingAdd()
        awaitRoutine { it.exercises.size == 1 }
        // Two conditions, because two things must be true before the second confirm is even
        // legal. confirmPendingAdd dedups against routineFlow.value
        // (RoutineEditorViewModel:470) -- the view model's own copy, not the repository that
        // awaitRoutine polls -- so the routine has to have landed there. And :460 returns
        // immediately while confirmInFlight is set, which the finally at :516 clears only
        // after the write returns; Room can emit the saved routine before that runs. Wait on
        // the routine alone and the second confirm is a no-op against a view model still
        // refusing confirms, which is how this failed on trunk. addingLifts is that flag.
        vm.uiState.first { it.routine?.exercises?.size == 1 && !it.addingLifts }

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
        val vm = createViewModel("new")
        val state = vm.uiState.first {
            it.searchResults.size >= 2 && it.searchResults.first().name == "ZZ Squat"
        }
        assertEquals("ZZ Squat", state.searchResults.first().name)
    }

    @Test
    fun createAndSelectAppearsInPickerResultsBeforeTheCatalogCatchesUp() = runBlocking {
        val vm = createViewModel("new")
        vm.uiState.first { !it.isLoading }
        vm.setPickerVisible(true)
        vm.createAndSelect("Good morning", "Hamstrings")
        val state = vm.uiState.first { it.pendingAddIds.isNotEmpty() }
        assertTrue(state.searchResults.any { it.name == "Good morning" })
    }

    @Test
    fun stagedLoadDoesNotSurviveASwapWhenLeaving() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5, targetWeightKg = 100.0)
        deps.workoutRepository.discardSession(fixture.session.id)
        val replacement = insertTestExercise(deps, "front-squat", "Front squat", muscleGroup = "Quads")
        val vm = createViewModel(fixture.routine.id)
        val item = vm.uiState.first { it.routine?.exercises?.size == 1 }.routine!!.exercises.single()

        vm.stageTargets(item.id, targetSets = 3, targetReps = 5, targetWeightKg = 80.0, restSeconds = 90)
        vm.requestSwap(item.id)
        vm.swapExercise(replacement)
        vm.uiState.first { it.routine?.exercises?.singleOrNull()?.exercise?.id == replacement.id }

        vm.leave()
        vm.exitRequested.first { it }
        val stored = checkNotNull(deps.routineRepository.getById(fixture.routine.id))
        assertEquals(replacement.id, stored.exercises.single().exercise.id)
        assertNull(stored.exercises.single().targetWeightKg)
        assertEquals(3, stored.exercises.single().targetSets)
        assertEquals(5, stored.exercises.single().targetReps)
    }

    @Test
    fun leaveWaitsForConfirmSoANewRoutineIsNotDeletedMidAdd() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel("new", delayedAdd(gate))
        try {
            vm.uiState.first { it.catalog.size >= 2 }
            vm.togglePendingAdd(squat)
            vm.togglePendingAdd(row)
            vm.confirmPendingAdd()
            vm.leave()
            dispatcher.scheduler.runCurrent()
            assertFalse(vm.exitRequested.value)

            gate.complete(Unit)
            vm.exitRequested.first { it }
            val saved = deps.routineRepository.observeAll().first().single()
            assertEquals(listOf(squat.id, row.id), saved.exercises.map { it.exercise.id })
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun addingLiftsBlocksReopeningThePickerUntilConfirmFinishes() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel("new", delayedAdd(gate))
        try {
            vm.uiState.first { it.catalog.isNotEmpty() }
            vm.togglePendingAdd(squat)
            vm.confirmPendingAdd()
            val busy = vm.uiState.first { it.addingLifts }
            assertTrue(busy.addingLifts)
            vm.setPickerVisible(true)
            assertFalse(vm.uiState.value.showExercisePicker)
            gate.complete(Unit)
            val done = vm.uiState.first { !it.addingLifts && it.routine?.exercises?.size == 1 }
            assertFalse(done.addingLifts)
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun leaveWaitsForRemoveSoAnEmptyStubIsDiscarded() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel("new", delayedDelete(gate))
        try {
            vm.uiState.first { it.catalog.isNotEmpty() }
            vm.togglePendingAdd(squat)
            vm.confirmPendingAdd()
            val created = awaitRoutine { it.exercises.size == 1 }
            vm.removeExercise(created.exercises.single().id)
            vm.leave()
            dispatcher.scheduler.runCurrent()
            assertFalse(vm.exitRequested.value)
            assertEquals(1, deps.routineRepository.observeAll().first().single().exercises.size)

            gate.complete(Unit)
            vm.exitRequested.first { it }
            assertTrue(deps.routineRepository.observeAll().first().isEmpty())
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun leaveWaitsForAddExerciseSoTheStubIsNotDeletedMidWrite() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel("new", delayedAdd(gate))
        try {
            vm.uiState.first { it.catalog.isNotEmpty() }
            vm.addExercise(squat, 3, 5, null, 90)
            vm.leave()
            dispatcher.scheduler.runCurrent()
            assertFalse(vm.exitRequested.value)

            gate.complete(Unit)
            vm.exitRequested.first { it }
            val saved = deps.routineRepository.observeAll().first().single()
            assertEquals(squat.id, saved.exercises.single().exercise.id)
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun aFailedConfirmDoesNotReopenThePickerAfterLeave() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel("new", failingAdd(gate))
        try {
            vm.uiState.first { it.catalog.isNotEmpty() }
            vm.setPickerVisible(true)
            vm.togglePendingAdd(squat)
            vm.confirmPendingAdd()
            vm.leave()
            dispatcher.scheduler.runCurrent()
            assertFalse(vm.exitRequested.value)
            gate.complete(Unit)
            vm.exitRequested.first { it }
            assertFalse(vm.uiState.value.showExercisePicker)
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun processDeathKeepsNameAndDoesNotMintASecondRoutine() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val handle = SavedStateHandle(mapOf("routineId" to "new"))
        val first = createViewModel("new", savedStateHandle = handle)
        first.uiState.first { it.catalog.size >= 2 }
        first.onNameChange("Push")
        first.setPickerVisible(true)
        first.togglePendingAdd(squat)
        first.confirmPendingAdd()
        val created = awaitRoutine { it.exercises.size == 1 }
        first.uiState.first { it.routine?.exercises?.size == 1 && !it.addingLifts }
        first.clearAndJoinForTest()
        viewModel = null

        val restored = createViewModel("new", savedStateHandle = handle)
        restored.uiState.first { !it.isLoading && it.name == "Push" }
        restored.setPickerVisible(true)
        restored.togglePendingAdd(row)
        restored.confirmPendingAdd()
        restored.uiState.first { it.routine?.exercises?.size == 2 && !it.addingLifts }

        val routines = deps.routineRepository.observeAll().first()
        assertEquals(1, routines.size)
        assertEquals(created.id, routines.single().id)
        assertEquals("Push", routines.single().name)
        assertEquals(
            listOf(squat.id, row.id),
            routines.single().exercises.map { it.exercise.id },
        )
    }

    // ---- UX04: Save is truthful ----

    /**
     * The defect: the details write threw, the failure was logged, and the screen popped with
     * the owner believing the rename was stored. Save must stay, say so, and retry cleanly.
     */
    @Test
    fun saveWithFailingDetailsWriteStaysAndKeepsTheName() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val gate = FailureGate(shouldFail = true)
        val dao = FailingUpdateRoutineDao(deps.database.routineDao(), gate)
        val vm = createViewModel(fixture.routine.id, container = withRoutineDao(dao))
        vm.uiState.first { it.routine != null && it.name == fixture.routine.name }

        vm.onNameChange("Lower strength")
        vm.saveAndLeave()

        val blocked = vm.uiState.first { it.saveError != null && !it.saving }
        assertFalse(vm.exitRequested.value)
        assertEquals(RoutineSaveCopy.DETAILS_FAILED, blocked.saveError)
        assertEquals("Lower strength", blocked.name)
        assertNull(blocked.unsavedOnBack)
        assertEquals(fixture.routine.name, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).name)
        assertEquals(0, dao.updates)

        gate.shouldFail = false
        vm.saveAndLeave()
        vm.exitRequested.first { it }
        val saved = checkNotNull(deps.routineRepository.getById(fixture.routine.id))
        assertEquals("Lower strength", saved.name)
        assertEquals(1, saved.exercises.size)
        assertEquals(1, dao.updates)
    }

    /** A staged target whose write threw stays staged; the retry lands it exactly once. */
    @Test
    fun saveWithFailingTargetWriteStaysAndRetryPersistsOnce() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5)
        deps.workoutRepository.discardSession(fixture.session.id)
        val itemId = fixture.routine.exercises.single().id
        val gate = FailureGate(shouldFail = true)
        val dao = FailingUpsertExerciseDao(deps.database.routineDao(), gate)
        val vm = createViewModel(fixture.routine.id, container = withRoutineDao(dao))
        vm.uiState.first { it.routine != null }

        vm.stageTargets(itemId, targetSets = 4, targetReps = 6, targetWeightKg = 110.0, restSeconds = 120)
        vm.saveAndLeave()

        val blocked = vm.uiState.first { it.saveError != null && !it.saving }
        assertFalse(vm.exitRequested.value)
        assertEquals(RoutineSaveCopy.targetsFailed(fixture.exercise.name), blocked.saveError)
        assertEquals(3, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single().targetSets)
        assertEquals(0, dao.upserts)

        gate.shouldFail = false
        vm.saveAndLeave()
        vm.exitRequested.first { it }
        val stored = checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single()
        assertEquals(4, stored.targetSets)
        assertEquals(6, stored.targetReps)
        assertEquals(110.0, stored.targetWeightKg)
        assertEquals(120, stored.restSeconds)
        assertEquals(1, dao.upserts)
    }

    /**
     * A rejected value blocks Save with the card's own words, said once: the focus-change
     * commit already put it in the banner, and the dock takes it over rather than doubling it.
     */
    @Test
    fun saveWithARejectedTargetStaysWithTheExistingErrorSaidOnce() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5)
        deps.workoutRepository.discardSession(fixture.session.id)
        val itemId = fixture.routine.exercises.single().id
        val vm = createViewModel(fixture.routine.id)
        vm.uiState.first { it.routine != null }

        vm.stageTargets(itemId, targetSets = 0, targetReps = 5, targetWeightKg = 100.0, restSeconds = 90)
        vm.commitTargets(itemId)
        vm.uiState.first { it.error == RoutineSaveCopy.TARGETS_REJECTED }
        vm.saveAndLeave()

        val blocked = vm.uiState.first { it.saveError == RoutineSaveCopy.TARGETS_REJECTED && !it.saving }
        assertFalse(vm.exitRequested.value)
        assertNull(blocked.error)
        assertEquals(3, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single().targetSets)
    }

    /**
     * Back with a write that will not land must not drop it quietly. It asks; leave-anyway
     * pops without another attempt, and every write-through edit is still in Room.
     */
    @Test
    fun backWithUnsavedWritesOffersLeaveAnyway() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val row = insertTestExercise(deps, "row", "Row")
        val gate = FailureGate(shouldFail = false)
        val dao = FailingUpdateRoutineDao(deps.database.routineDao(), gate)
        val vm = createViewModel(fixture.routine.id, container = withRoutineDao(dao))
        vm.uiState.first { it.routine != null && it.name == fixture.routine.name }
        val before = checkNotNull(deps.routineRepository.getById(fixture.routine.id)).updatedAt
        vm.addExercise(row, 3, 8, null, 90)
        // Both conditions: the lift's upsert emits before addExercise's closing touch() has
        // run updateRoutine, and flipping the gate in that window would fail the add itself
        // rather than the details write this test is about. The touch moves updatedAt.
        awaitRoutine { it.exercises.size == 2 && it.updatedAt != before }

        gate.shouldFail = true
        vm.onNotesChange("tempo on the last set")
        vm.leave()

        val prompted = vm.uiState.first { it.unsavedOnBack != null && !it.saving }
        assertFalse(vm.exitRequested.value)
        assertEquals(listOf(RoutineSaveCopy.DETAILS_ITEM), prompted.unsavedOnBack?.items)
        assertEquals(RoutineSaveCopy.DETAILS_FAILED, prompted.unsavedOnBack?.message)
        assertNull(prompted.saveError)
        assertEquals("tempo on the last set", prompted.notes)

        vm.leaveAnyway()
        vm.exitRequested.first { it }
        val stored = checkNotNull(deps.routineRepository.getById(fixture.routine.id))
        assertEquals(listOf(fixture.exercise.id, row.id), stored.exercises.map { it.exercise.id })
        assertEquals("", stored.notes)
    }

    /** A card reading 0 sets is unsaved work too. Back asks; fixing it and trying again lands it. */
    @Test
    fun backWithARejectedTargetAsksInsteadOfDroppingIt() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5)
        deps.workoutRepository.discardSession(fixture.session.id)
        val itemId = fixture.routine.exercises.single().id
        val vm = createViewModel(fixture.routine.id)
        vm.uiState.first { it.routine != null }

        vm.stageTargets(itemId, targetSets = 0, targetReps = 5, targetWeightKg = 100.0, restSeconds = 90)
        vm.leave()

        val prompted = vm.uiState.first { it.unsavedOnBack != null && !it.saving }
        assertFalse(vm.exitRequested.value)
        assertEquals(RoutineSaveCopy.TARGETS_REJECTED, prompted.unsavedOnBack?.message)
        assertEquals(listOf(RoutineSaveCopy.targetsItem(fixture.exercise.name)), prompted.unsavedOnBack?.items)

        vm.stageTargets(itemId, targetSets = 4, targetReps = 5, targetWeightKg = 100.0, restSeconds = 90)
        vm.leave()
        vm.exitRequested.first { it }
        assertEquals(4, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single().targetSets)
    }

    /**
     * UX06 on the routine card: a box the owner typed "8.5" into is staged as a rejection with
     * the box's own rule. The focus-change commit refuses it, nothing is written, and Save
     * stays with that rule at the dock until the text is something the routine can hold.
     */
    @Test
    fun anUnreadableTargetBoxIsRefusedNotReadAsLeaveAlone() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5)
        deps.workoutRepository.discardSession(fixture.session.id)
        val itemId = fixture.routine.exercises.single().id
        val vm = createViewModel(fixture.routine.id)
        vm.uiState.first { it.routine != null }

        // What the card stages for sets "3", reps "8.5", rest "60", weight "": the reps box
        // has no value and carries the rule it broke.
        vm.stageTargets(
            itemId = itemId,
            targetSets = 3,
            targetReps = null,
            targetWeightKg = null,
            restSeconds = 60,
            invalidReason = NumericEntry.REPS_WHOLE_RULE,
        )
        vm.commitTargets(itemId)
        vm.uiState.first { it.error == NumericEntry.REPS_WHOLE_RULE }
        assertEquals(5, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single().targetReps)

        vm.saveAndLeave()
        val blocked = vm.uiState.first { it.saveError == NumericEntry.REPS_WHOLE_RULE && !it.saving }
        assertFalse(vm.exitRequested.value)
        assertNull(blocked.error)
        assertEquals(5, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single().targetReps)

        vm.stageTargets(itemId = itemId, targetSets = 3, targetReps = 8, targetWeightKg = null, restSeconds = 60)
        vm.saveAndLeave()
        vm.exitRequested.first { it }
        assertEquals(8, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single().targetReps)
    }

    /**
     * A read that throws on the way out used to leave the editor deaf: `leaving` stayed true
     * and nothing could pop it. It is now one more unsaved outcome, and leave-anyway still exits.
     */
    @Test
    fun aReadFaultOnTheWayOutStaysAndLeaveAnywayStillExits() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val gate = FailureGate(shouldFail = false)
        val vm = createViewModel(fixture.routine.id, container = failingHydration(gate))
        vm.uiState.first { it.routine != null && it.name == fixture.routine.name }

        gate.shouldFail = true
        vm.onNameChange("Lower strength")
        vm.saveAndLeave()
        val blocked = vm.uiState.first { it.saveError != null && !it.saving }
        assertEquals(RoutineSaveCopy.EXIT_READ_FAILED, blocked.saveError)
        assertFalse(vm.exitRequested.value)

        vm.leave()
        val prompted = vm.uiState.first { it.unsavedOnBack != null && !it.saving }
        assertEquals(RoutineSaveCopy.EXIT_READ_FAILED, prompted.unsavedOnBack?.message)
        assertEquals(listOf(RoutineSaveCopy.UNKNOWN_ITEMS), prompted.unsavedOnBack?.items)

        vm.leaveAnyway()
        vm.exitRequested.first { it }
        gate.shouldFail = false
        assertEquals(fixture.routine.name, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).name)
    }

    /** Leave-anyway is Back minus the flush: an empty stub created this session still goes. */
    @Test
    fun leaveAnywayStillDiscardsAnEmptyStubCreatedThisSession() = runBlocking {
        val exercise = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.uiState.first { !it.isLoading }
        vm.addExercise(exercise, 3, 8, null, 90)
        val created = awaitRoutine { it.exercises.size == 1 }
        vm.removeExercise(created.exercises.single().id)
        awaitRoutine { it.exercises.isEmpty() }

        vm.leaveAnyway()
        vm.exitRequested.first { it }
        assertTrue(deps.routineRepository.observeAll().first().isEmpty())
    }

    /** While the attempt runs the dock reads Saving…; a second Save or Back starts nothing. */
    @Test
    fun savingIsVisibleAndASecondPressIsIgnored() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val gate = CompletableDeferred<Unit>()
        val dao = GatedUpdateRoutineDao(deps.database.routineDao(), gate)
        val vm = createViewModel(fixture.routine.id, container = withRoutineDao(dao))
        try {
            vm.uiState.first { it.routine != null && it.name == fixture.routine.name }
            vm.onNameChange("Lower strength")
            vm.saveAndLeave()
            val busy = vm.uiState.first { it.saving }
            assertTrue(busy.saving)
            assertFalse(vm.exitRequested.value)

            vm.saveAndLeave()
            vm.leave()
            gate.complete(Unit)
            vm.exitRequested.first { it }
            assertEquals(1, dao.updates)
            assertEquals("Lower strength", checkNotNull(deps.routineRepository.getById(fixture.routine.id)).name)
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    private fun createViewModel(
        routineId: String,
        container: AppDependencies = deps,
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("routineId" to routineId)),
    ): RoutineEditorViewModel =
        RoutineEditorViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            savedStateHandle = savedStateHandle,
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

    private fun delayedAdd(gate: CompletableDeferred<Unit>): AppDependencies {
        val repo = RoutineRepository(GatedUpsertDao(deps.database.routineDao(), gate))
        return object : AppDependencies by deps {
            override val routineRepository: RoutineRepository = repo
        }
    }

    private fun delayedDelete(gate: CompletableDeferred<Unit>): AppDependencies {
        val repo = RoutineRepository(GatedDeleteDao(deps.database.routineDao(), gate))
        return object : AppDependencies by deps {
            override val routineRepository: RoutineRepository = repo
        }
    }

    private fun failingAdd(gate: CompletableDeferred<Unit>): AppDependencies {
        val repo = RoutineRepository(GatedFailingUpsertDao(deps.database.routineDao(), gate))
        return object : AppDependencies by deps {
            override val routineRepository: RoutineRepository = repo
        }
    }

    /** The graph with one DAO swapped in, so a test can hold the DAO and read its counters. */
    private fun withRoutineDao(dao: RoutineDao): AppDependencies {
        val repo = RoutineRepository(dao)
        return object : AppDependencies by deps {
            override val routineRepository: RoutineRepository = repo
        }
    }

    /** Fails the details write ([RoutineRepository.updateDetails]) while the gate is set. */
    private class FailingUpdateRoutineDao(
        private val delegate: RoutineDao,
        private val gate: FailureGate,
    ) : RoutineDao by delegate {
        var updates = 0

        override suspend fun updateRoutine(routine: RoutineEntity) {
            if (gate.shouldFail) error("boom: Room could not update routine ${routine.id}")
            updates += 1
            delegate.updateRoutine(routine)
        }
    }

    /** Fails the targets write ([RoutineRepository.updateExercise]) while the gate is set. */
    private class FailingUpsertExerciseDao(
        private val delegate: RoutineDao,
        private val gate: FailureGate,
    ) : RoutineDao by delegate {
        var upserts = 0

        override suspend fun upsertRoutineExercise(item: RoutineExerciseEntity) {
            if (gate.shouldFail) error("boom: Room could not write targets for ${item.id}")
            upserts += 1
            delegate.upsertRoutineExercise(item)
        }
    }

    /** Holds the details write until the gate completes, so an in-flight Save can be observed. */
    private class GatedUpdateRoutineDao(
        private val delegate: RoutineDao,
        private val gate: CompletableDeferred<Unit>,
    ) : RoutineDao by delegate {
        var updates = 0

        override suspend fun updateRoutine(routine: RoutineEntity) {
            gate.await()
            updates += 1
            delegate.updateRoutine(routine)
        }
    }

    private class GatedUpsertDao(
        private val delegate: RoutineDao,
        private val gate: CompletableDeferred<Unit>,
    ) : RoutineDao by delegate {
        override suspend fun upsertRoutineExercise(item: RoutineExerciseEntity) {
            gate.await()
            delegate.upsertRoutineExercise(item)
        }
    }

    private class GatedDeleteDao(
        private val delegate: RoutineDao,
        private val gate: CompletableDeferred<Unit>,
    ) : RoutineDao by delegate {
        override suspend fun deleteRoutineExercise(id: String) {
            gate.await()
            delegate.deleteRoutineExercise(id)
        }
    }

    private class GatedFailingUpsertDao(
        private val delegate: RoutineDao,
        private val gate: CompletableDeferred<Unit>,
    ) : RoutineDao by delegate {
        override suspend fun upsertRoutineExercise(item: RoutineExerciseEntity) {
            gate.await()
            error("boom: add blocked")
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
        withTimeout(5_000) {
            deps.routineRepository.observeAll().first { list ->
                list.singleOrNull()?.let(predicate) == true
            }.single()
        }
}
