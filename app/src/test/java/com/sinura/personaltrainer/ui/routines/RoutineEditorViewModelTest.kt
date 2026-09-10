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
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.testutil.stalledThreads
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
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
        val state = vm.awaitState { !it.isLoading }
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
        val state = vm.awaitState { !it.isLoading && it.routine != null }

        assertEquals(fixture.routine.id, state.routine?.id)
        assertEquals(fixture.routine.name, state.name)
        assertEquals("keep these", state.notes)
        assertEquals(1, state.routine?.exercises?.size)
    }

    @Test
    fun missingRoutineResolvesMissingWithUserMessage() = runBlocking {
        val vm = createViewModel("gone")
        val state = vm.awaitState { it.missing && it.error != null }
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

        val state = vm.awaitState { it.failed }
        assertFalse(state.isLoading)
        assertFalse(state.missing)
    }

    @Test
    fun retryAfterHydrationFailureLoadsTheRoutine() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val gate = FailureGate(shouldFail = true)
        val vm = createViewModel(fixture.routine.id, container = failingHydration(gate))
        vm.awaitState { it.failed }

        gate.shouldFail = false
        vm.retryHydration()

        // The editable name is hydrated by a later write than the row, so wait for the
        // emission that carries it too. Waiting on the row alone read the name as "" —
        // 1 of 30 runs, as `expected:<[Test lower]> but was:<[]>`.
        val recovered = vm.awaitState {
            !it.failed && !it.isLoading && it.routine != null && it.name == fixture.routine.name
        }
        assertFalse(recovered.missing)
        assertEquals(fixture.routine.id, recovered.routine?.id)
        assertEquals(fixture.routine.name, recovered.name)
    }

    @Test
    fun addExerciseCreatesTheStubAndPersistsTheLift() = runBlocking {
        val exercise = insertTestExercise(deps, "row", "Chest-supported row")
        val vm = createViewModel("new")
        vm.awaitState { !it.isLoading }
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
        vm.awaitState { it.routine != null }

        vm.addExercise(fixture.exercise, 3, 5, 100.0, 90)

        assertEquals(
            "${fixture.exercise.name} is already in this routine.",
            vm.awaitState { it.error == "${fixture.exercise.name} is already in this routine." }.error,
        )
        assertEquals(1, deps.routineRepository.getById(fixture.routine.id)?.exercises?.size)
    }

    @Test
    fun leaveDiscardsAnEmptyStubCreatedThisSession() = runBlocking {
        val exercise = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.awaitState { !it.isLoading }
        vm.addExercise(exercise, 3, 8, null, 90)
        val created = awaitRoutine { it.exercises.size == 1 }
        vm.removeExercise(created.exercises.single().id)
        awaitRoutine { it.exercises.isEmpty() }

        vm.leave()
        vm.awaitExit()
        assertTrue(deps.routineRepository.observeAll().first().isEmpty())
        vm.onExitHandled()
        assertFalse(vm.exitRequested.value)
    }

    /**
     * Binning an untouched stub is housekeeping, and [RoutineEditorViewModel.leaveAnyway]
     * already states the rule: an empty routine left behind is a nuisance, an editor that
     * cannot be left is not. `discardEmptyStub` guards its own delete for that reason, but the
     * count read in front of the delete sat outside the guard — so a transient Room fault at
     * Back time raised "Some changes are not saved" over a Back that then refused to pop, with
     * nothing unsaved at all. Only that first read fails here; the details compare behind it
     * succeeds, so a genuine save failure is not what is being waved through.
     */
    @Test
    fun aFailedStubCheckOnBackDoesNotClaimUnsavedChanges() = runBlocking {
        val exercise = insertTestExercise(deps, "row", "Row")
        val gate = FailureGate(shouldFail = false)
        val vm = createViewModel(
            "new",
            container = withRoutineDao(FailNextGetByIdDao(deps.database.routineDao(), gate)),
        )
        vm.awaitState { !it.isLoading }
        vm.addExercise(exercise, 3, 8, null, 90)
        val created = awaitRoutine { it.exercises.size == 1 }
        vm.removeExercise(created.exercises.single().id)
        awaitRoutine { it.exercises.isEmpty() }

        gate.shouldFail = true
        vm.leave()

        vm.awaitExit()
        assertNull(vm.uiState.value.unsavedOnBack)
    }

    @Test
    fun leavePersistsRenamedNotesOnAnExistingRoutine() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val vm = createViewModel(fixture.routine.id)
        vm.awaitState { it.routine != null && it.name == fixture.routine.name }

        vm.onNameChange("Lower strength")
        vm.onNotesChange("tempo on the last set")
        vm.leave()

        vm.awaitExit()
        val saved = checkNotNull(deps.routineRepository.getById(fixture.routine.id))
        assertEquals("Lower strength", saved.name)
        assertEquals("tempo on the last set", saved.notes)
    }

    @Test
    fun saveAndLeaveKeepsACreatedRoutineWithLiftsAndPersistsTheName() = runBlocking {
        val exercise = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.awaitState { !it.isLoading }
        vm.onNameChange("Push")
        vm.addExercise(exercise, 3, 8, null, 90)
        val created = awaitRoutine { it.exercises.size == 1 }

        vm.saveAndLeave()
        vm.awaitExit()
        val saved = checkNotNull(deps.routineRepository.getById(created.id))
        assertEquals("Push", saved.name)
        assertEquals(1, saved.exercises.size)
    }

    @Test
    fun saveAndLeaveWithoutLiftsStaysOnTheEditor() = runBlocking {
        val vm = createViewModel("new")
        vm.awaitState { !it.isLoading }
        vm.saveAndLeave()
        assertFalse(vm.exitRequested.value)
        assertEquals(
            SessionOrderCopy.NEED_A_LIFT,
            vm.awaitState { it.error == SessionOrderCopy.NEED_A_LIFT }.error,
        )
        assertTrue(deps.routineRepository.observeAll().first().isEmpty())
    }

    @Test
    fun stagedTargetsCommitWhenTheyDifferAndRejectZeroSets() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5)
        deps.workoutRepository.discardSession(fixture.session.id)
        val itemId = fixture.routine.exercises.single().id
        val vm = createViewModel(fixture.routine.id)
        vm.awaitState { it.routine != null }

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
            vm.awaitState { it.error == "Sets and reps must be at least 1." }.error,
        )
        assertEquals(4, deps.routineRepository.getById(fixture.routine.id)!!.exercises.single().targetSets)
    }

    @Test
    fun aTargetTypedDuringASlowCommitSurvivesToTheExitWrite() = runBlocking {
        // The commit that is already in the DAO must not take the next typed value down with
        // it. Dropping the staged value by key alone did exactly that: the write that landed
        // was the older one, the card went on showing a number the routine did not hold, and
        // the exit flush had nothing left to save. It also swallowed the refusal in
        // stagedTargetsCommitWhenTheyDifferAndRejectZeroSets whenever the removal happened to
        // land after the second stageTargets — which is how it failed on a two-core runner.
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5)
        deps.workoutRepository.discardSession(fixture.session.id)
        val itemId = fixture.routine.exercises.single().id
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(fixture.routine.id, delayedAdd(gate))
        try {
            vm.awaitState { it.routine != null }
            vm.stageTargets(itemId, targetSets = 4, targetReps = 6, targetWeightKg = 110.0, restSeconds = 120)
            vm.commitTargets(itemId)
            vm.stageTargets(itemId, targetSets = 5, targetReps = 8, targetWeightKg = 120.0, restSeconds = 150)
            gate.complete(Unit)

            // leave() joins the write before it flushes, so the drop has certainly happened
            // by the time the flush looks for something to save.
            vm.leave()
            vm.awaitExit()

            val stored = checkNotNull(deps.routineRepository.getById(fixture.routine.id))
                .exercises
                .single()
            assertEquals(5, stored.targetSets)
            assertEquals(8, stored.targetReps)
            assertEquals(150, stored.restSeconds)
            assertEquals(120.0, stored.targetWeightKg)
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun aTargetsRefusalSurvivesThePreviousCommitsSuccess() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5)
        deps.workoutRepository.discardSession(fixture.session.id)
        val itemId = fixture.routine.exercises.single().id
        val vm = createViewModel(fixture.routine.id)
        vm.awaitState { it.routine != null }

        // Nothing is awaited between the two commits on purpose. The first is slow (a Room
        // write) and succeeds; the second is refused before it writes. Before ErrorSlot the
        // first commit's success-path `error = null` landed after the refusal and erased it,
        // and the wait below ran forever — the wedge the CI watchdog caught.
        vm.stageTargets(itemId, targetSets = 4, targetReps = 6, targetWeightKg = 110.0, restSeconds = 120)
        vm.commitTargets(itemId)
        vm.stageTargets(itemId, targetSets = 0, targetReps = 6, targetWeightKg = 110.0, restSeconds = 120)
        vm.commitTargets(itemId)

        assertEquals(
            "Sets and reps must be at least 1.",
            vm.awaitState { it.error != null }.error,
        )
        val written = awaitRoutine { it.exercises.single().targetSets == 4 }
        assertEquals(6, written.exercises.single().targetReps)
    }

    @Test
    fun removeAndMovePersistOrder() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val row = insertTestExercise(deps, "row", "Row")
        deps.routineRepository.addExercise(fixture.routine.id, row, 3, 8, null, 90)
        val vm = createViewModel(fixture.routine.id)
        val loaded = vm.awaitState { it.routine?.exercises?.size == 2 }.routine!!
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
        val item = vm.awaitState { it.routine?.exercises?.size == 1 }.routine!!.exercises.single()

        vm.requestSwap(item.id)
        vm.awaitState { it.swapItemId == item.id }
        vm.swapExercise(replacement)

        val saved = vm.awaitState {
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
        vm.awaitState { !it.isLoading }

        vm.createAndSelect("  ", "Back")
        assertEquals(
            SessionOrderCopy.LIFT_NAME_REQUIRED,
            vm.awaitState { it.error == SessionOrderCopy.LIFT_NAME_REQUIRED }.error,
        )

        vm.createAndSelect("Existing lift", "Back")
        assertEquals(
            "That name is already in your library",
            vm.awaitState { it.error == "That name is already in your library" }.error,
        )
        assertTrue(deps.exerciseRepository.observeAll().first().none { it.isCustom })
    }

    @Test
    fun createAndSelectAfterDismissDoesNotLeaveAGhostCart() = runBlocking {
        val vm = createViewModel("new")
        vm.awaitState { !it.isLoading }
        vm.setPickerVisible(true)
        vm.setPickerVisible(false)
        vm.createAndSelect("Good morning", "Hamstrings")

        awaitList("exerciseRepository", deps.exerciseRepository.observeAll()) { list -> list.any { it.name == "Good morning" } }
        assertTrue(vm.uiState.value.pickedIds.isEmpty())
        assertFalse(vm.uiState.value.showExercisePicker)
        assertTrue(deps.routineRepository.observeAll().first().isEmpty())
    }

    @Test
    fun aTapWritesTheLiftStraightOnToTheRoutine() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.awaitState { it.catalog.size >= 2 }

        vm.setPickerVisible(true)
        vm.togglePicked(squat)
        awaitRoutine { it.exercises.size == 1 }
        vm.togglePicked(row)

        val saved = awaitRoutine { it.exercises.size == 2 }
        assertEquals(listOf(squat.id, row.id), saved.exercises.map { it.exercise.id })
        // The sheet stays open on the lifts it has written: adding two is two taps, not
        // two taps and a Confirm, and the numbers on the rows are the routine's own order.
        val state = vm.awaitState { it.pickedIds == listOf(squat.id, row.id) }
        assertTrue(state.showExercisePicker)
    }

    @Test
    fun closingThePickerKeepsEveryLiftAlreadyTapped() = runBlocking {
        // The defect this replaced: a tap outside the sheet emptied the cart, and a list
        // the owner had built by hand had to be built again from the first lift.
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.awaitState { it.catalog.size >= 2 }

        vm.setPickerVisible(true)
        vm.togglePicked(squat)
        vm.togglePicked(row)
        awaitRoutine { it.exercises.size == 2 }
        vm.setPickerVisible(false)

        val closed = vm.awaitState { !it.showExercisePicker && it.routine?.exercises?.size == 2 }
        assertEquals(listOf(squat.id, row.id), closed.pickedIds)
        vm.setPickerVisible(true)
        val reopened = vm.awaitState { it.showExercisePicker }
        assertEquals(listOf(squat.id, row.id), reopened.pickedIds)
    }

    /**
     * A refusal raised while a second tap was queued must outlive that tap's success.
     *
     * `writePick` runs under `pickWrites`, so a second tap parks on the mutex. It used to take
     * its [ErrorSlot] mark inside `writePick` — i.e. the moment it WON the lock, which is after
     * the first tap has already failed and raised. `clearFrom(before = thatMark)` then read the
     * fresh refusal as stale and wiped it. The rows have no busy guard, so two fast taps is the
     * ordinary case; the lifter saw the second lift appear and never learned the first had not.
     *
     * Both taps are adds, so both are ERR_ADD_LIFT: the family rule cannot save this one, and
     * only the mark's position can. The mark now belongs to the tap.
     */
    @Test
    fun aRefusalRaisedWhileASecondTapWaitedSurvivesThatTapsSuccess() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val gate = CompletableDeferred<Unit>()
        val container = withRoutineDao(GateThenFailFirstAddDao(deps.database.routineDao(), gate))
        val vm = createViewModel("new", container = container)
        vm.awaitState { it.catalog.size >= 2 }

        vm.setPickerVisible(true)
        // The first tap suspends inside `pickWrites`, so the second parks on the mutex behind
        // it — which is the whole situation. Both taps are made before either write finishes.
        vm.togglePicked(squat)
        vm.togglePicked(row)
        gate.complete(Unit)

        // The second lift lands; the first was refused.
        awaitRoutine { it.exercises.size == 1 }
        val settled = vm.awaitState { !it.addingLifts && it.routine?.exercises?.size == 1 }
        assertEquals(listOf(row.id), settled.routine!!.exercises.map { it.exercise.id })
        assertEquals(
            "the refused tap must still be on screen after the queued tap succeeded",
            SessionOrderCopy.ADD_LIFT_FAILED,
            settled.error,
        )
    }

    @Test
    fun aSecondTapTakesTheLiftBackOut() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.awaitState { it.catalog.size >= 2 }

        vm.setPickerVisible(true)
        vm.togglePicked(squat)
        vm.togglePicked(row)
        awaitRoutine { it.exercises.size == 2 }
        vm.togglePicked(squat)

        val saved = awaitRoutine { it.exercises.size == 1 }
        assertEquals(listOf(row.id), saved.exercises.map { it.exercise.id })
        assertEquals(listOf(row.id), vm.awaitState { it.pickedIds == listOf(row.id) }.pickedIds)
    }

    @Test
    fun tapOrderIsTheOrderTheRoutineKeeps() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.awaitState { it.catalog.size >= 2 }

        vm.togglePicked(row)
        awaitRoutine { it.exercises.size == 1 }
        vm.togglePicked(squat)

        val saved = awaitRoutine { it.exercises.size == 2 }
        assertEquals(listOf(row.id, squat.id), saved.exercises.map { it.exercise.id })
    }

    @Test
    fun aRoutineOpensThePickerWithItsOwnLiftsAlreadyChosen() = runBlocking {
        val fixture = seedTestWorkout(deps, exerciseId = "squat", exerciseName = "Squat")
        deps.workoutRepository.discardSession(fixture.session.id)
        val row = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel(fixture.routine.id)
        vm.awaitState { it.routine?.exercises?.size == 1 && it.catalog.size >= 2 }

        vm.setPickerVisible(true)
        assertEquals(listOf("squat"), vm.uiState.value.pickedIds)

        // Numbered from the routine, so the second tap continues the session rather than
        // starting a second count of its own.
        vm.togglePicked(row)
        val saved = awaitRoutine { it.exercises.size == 2 }
        assertEquals(listOf("squat", row.id), saved.exercises.map { it.exercise.id })
    }

    @Test
    fun tapsWhileAWriteIsInFlightRunInTapOrder() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val bench = insertTestExercise(deps, "bench", "Bench", muscleGroup = "Chest")
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel("new", delayedAdd(gate))
        try {
            vm.awaitState { it.catalog.size >= 2 }
            vm.setPickerVisible(true)
            vm.togglePicked(squat)
            // The row answers the finger before Room does. Without that the second tap
            // would read a routine without the squat on it and add it a second time.
            val busy = vm.awaitState { it.pickedIds == listOf(squat.id) }
            assertTrue(busy.addingLifts)
            assertTrue(busy.routine?.exercises.orEmpty().isEmpty())

            vm.togglePicked(squat)
            vm.togglePicked(bench)
            gate.complete(Unit)

            // Three taps, one lift: the squat went on and came off again while its own
            // write was still queued, and the bench that followed it landed after both.
            val saved = awaitRoutine { routine ->
                routine.exercises.map { it.exercise.id } == listOf(bench.id)
            }
            assertEquals(listOf(bench.id), saved.exercises.map { it.exercise.id })
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun aFailedTapSaysSoAndLeavesNothingChosen() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel("new", failingAdd(gate))
        try {
            vm.awaitState { it.catalog.isNotEmpty() }
            vm.setPickerVisible(true)
            vm.togglePicked(squat)
            gate.complete(Unit)

            val refused = vm.awaitState { it.error == SessionOrderCopy.ADD_LIFT_FAILED }
            // The pick is dropped with the write: a row still numbered after a failure is a
            // lift the owner believes is on the routine and is not.
            assertTrue(refused.pickedIds.isEmpty())
            assertTrue(refused.showExercisePicker)
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun createAndSelectWritesTheNewLiftStraightOnToTheRoutine() = runBlocking {
        val vm = createViewModel("new")
        vm.awaitState { !it.isLoading }
        vm.setPickerVisible(true)
        vm.createAndSelect("Good morning", "Hamstrings")

        val saved = awaitRoutine { it.exercises.size == 1 }
        assertEquals("Good morning", saved.exercises.single().exercise.name)
        assertTrue(saved.exercises.single().exercise.isCustom)
        // The sheet stays open. Creating a lift is one more tap in a list being built.
        assertTrue(vm.awaitState { it.pickedIds.size == 1 }.showExercisePicker)
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
        val state = vm.awaitState {
            it.searchResults.size >= 2 && it.searchResults.first().name == "ZZ Squat"
        }
        assertEquals("ZZ Squat", state.searchResults.first().name)
    }

    @Test
    fun createAndSelectAppearsInPickerResultsBeforeTheCatalogCatchesUp() = runBlocking {
        val vm = createViewModel("new")
        vm.awaitState { !it.isLoading }
        vm.setPickerVisible(true)
        vm.createAndSelect("Good morning", "Hamstrings")
        val state = vm.awaitState { it.pickedIds.isNotEmpty() }
        assertTrue(state.searchResults.any { it.name == "Good morning" })
    }

    @Test
    fun stagedLoadDoesNotSurviveASwapWhenLeaving() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5, targetWeightKg = 100.0)
        deps.workoutRepository.discardSession(fixture.session.id)
        val replacement = insertTestExercise(deps, "front-squat", "Front squat", muscleGroup = "Quads")
        val vm = createViewModel(fixture.routine.id)
        val item = vm.awaitState { it.routine?.exercises?.size == 1 }.routine!!.exercises.single()

        vm.stageTargets(item.id, targetSets = 3, targetReps = 5, targetWeightKg = 80.0, restSeconds = 90)
        vm.requestSwap(item.id)
        vm.swapExercise(replacement)
        vm.awaitState { it.routine?.exercises?.singleOrNull()?.exercise?.id == replacement.id }

        vm.leave()
        vm.awaitExit()
        val stored = checkNotNull(deps.routineRepository.getById(fixture.routine.id))
        assertEquals(replacement.id, stored.exercises.single().exercise.id)
        assertNull(stored.exercises.single().targetWeightKg)
        assertEquals(3, stored.exercises.single().targetSets)
        assertEquals(5, stored.exercises.single().targetReps)
    }

    @Test
    fun leaveWaitsForATappedLiftSoANewRoutineIsNotDeletedMidAdd() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val row = insertTestExercise(deps, "row", "Row")
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel("new", delayedAdd(gate))
        try {
            vm.awaitState { it.catalog.size >= 2 }
            vm.togglePicked(squat)
            vm.togglePicked(row)
            vm.leave()
            dispatcher.scheduler.runCurrent()
            assertFalse(vm.exitRequested.value)

            gate.complete(Unit)
            vm.awaitExit()
            val saved = deps.routineRepository.observeAll().first().single()
            assertEquals(listOf(squat.id, row.id), saved.exercises.map { it.exercise.id })
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun thePickerStaysUsableWhileATapIsStillBeingWritten() = runBlocking {
        // It used to be shut for the length of the confirm write. Nothing waits on a write
        // now: the next lift can be tapped while the last one is still landing.
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel("new", delayedAdd(gate))
        try {
            vm.awaitState { it.catalog.isNotEmpty() }
            vm.setPickerVisible(true)
            vm.togglePicked(squat)
            val busy = vm.awaitState { it.addingLifts }
            assertTrue(busy.showExercisePicker)
            vm.setPickerVisible(false)
            vm.setPickerVisible(true)
            assertTrue(vm.uiState.value.showExercisePicker)

            gate.complete(Unit)
            val done = vm.awaitState { !it.addingLifts && it.routine?.exercises?.size == 1 }
            assertEquals(listOf(squat.id), done.pickedIds)
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
            vm.awaitState { it.catalog.isNotEmpty() }
            vm.togglePicked(squat)
            val created = awaitRoutine { it.exercises.size == 1 }
            vm.removeExercise(created.exercises.single().id)
            vm.leave()
            dispatcher.scheduler.runCurrent()
            assertFalse(vm.exitRequested.value)
            assertEquals(1, deps.routineRepository.observeAll().first().single().exercises.size)

            gate.complete(Unit)
            vm.awaitExit()
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
            vm.awaitState { it.catalog.isNotEmpty() }
            vm.addExercise(squat, 3, 5, null, 90)
            vm.leave()
            dispatcher.scheduler.runCurrent()
            assertFalse(vm.exitRequested.value)

            gate.complete(Unit)
            vm.awaitExit()
            val saved = deps.routineRepository.observeAll().first().single()
            assertEquals(squat.id, saved.exercises.single().exercise.id)
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
        }
    }

    @Test
    fun aFailedTapDoesNotReopenThePickerAfterLeave() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel("new", failingAdd(gate))
        try {
            vm.awaitState { it.catalog.isNotEmpty() }
            vm.setPickerVisible(true)
            vm.togglePicked(squat)
            vm.leave()
            dispatcher.scheduler.runCurrent()
            assertFalse(vm.exitRequested.value)
            gate.complete(Unit)
            vm.awaitExit()
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
        first.awaitState { it.catalog.size >= 2 }
        first.onNameChange("Push")
        first.setPickerVisible(true)
        first.togglePicked(squat)
        val created = awaitRoutine { it.exercises.size == 1 }
        first.awaitState { it.routine?.exercises?.size == 1 && !it.addingLifts }
        first.clearAndJoinForTest()
        viewModel = null

        val restored = createViewModel("new", savedStateHandle = handle)
        restored.awaitState { !it.isLoading && it.name == "Push" }
        restored.setPickerVisible(true)
        restored.togglePicked(row)
        restored.awaitState { it.routine?.exercises?.size == 2 && !it.addingLifts }

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
        vm.uiState.awaitFirst { it.routine != null && it.name == fixture.routine.name }

        vm.onNameChange("Lower strength")
        vm.saveAndLeave()

        val blocked = vm.uiState.awaitFirst { it.saveError != null && !it.saving }
        assertFalse(vm.exitRequested.value)
        assertEquals(RoutineSaveCopy.DETAILS_FAILED, blocked.saveError)
        assertEquals("Lower strength", blocked.name)
        assertNull(blocked.unsavedOnBack)
        assertEquals(fixture.routine.name, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).name)
        assertEquals(0, dao.updates)

        gate.shouldFail = false
        vm.saveAndLeave()
        vm.exitRequested.awaitFirst { it }
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
        vm.uiState.awaitFirst { it.routine != null }

        vm.stageTargets(itemId, targetSets = 4, targetReps = 6, targetWeightKg = 110.0, restSeconds = 120)
        vm.saveAndLeave()

        val blocked = vm.uiState.awaitFirst { it.saveError != null && !it.saving }
        assertFalse(vm.exitRequested.value)
        assertEquals(RoutineSaveCopy.targetsFailed(fixture.exercise.name), blocked.saveError)
        assertEquals(3, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single().targetSets)
        assertEquals(0, dao.upserts)

        gate.shouldFail = false
        vm.saveAndLeave()
        vm.exitRequested.awaitFirst { it }
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
        vm.uiState.awaitFirst { it.routine != null }

        vm.stageTargets(itemId, targetSets = 0, targetReps = 5, targetWeightKg = 100.0, restSeconds = 90)
        vm.commitTargets(itemId)
        vm.uiState.awaitFirst { it.error == RoutineSaveCopy.TARGETS_REJECTED }
        vm.saveAndLeave()

        val blocked = vm.uiState.awaitFirst { it.saveError == RoutineSaveCopy.TARGETS_REJECTED && !it.saving }
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
        vm.uiState.awaitFirst { it.routine != null && it.name == fixture.routine.name }
        val before = checkNotNull(deps.routineRepository.getById(fixture.routine.id)).updatedAt
        vm.addExercise(row, 3, 8, null, 90)
        // Both conditions: the lift's upsert emits before addExercise's closing touch() has
        // run updateRoutine, and flipping the gate in that window would fail the add itself
        // rather than the details write this test is about. The touch moves updatedAt.
        awaitRoutine { it.exercises.size == 2 && it.updatedAt != before }

        gate.shouldFail = true
        vm.onNotesChange("tempo on the last set")
        vm.leave()

        val prompted = vm.uiState.awaitFirst { it.unsavedOnBack != null && !it.saving }
        assertFalse(vm.exitRequested.value)
        assertEquals(listOf(RoutineSaveCopy.DETAILS_ITEM), prompted.unsavedOnBack?.items)
        assertEquals(RoutineSaveCopy.DETAILS_FAILED, prompted.unsavedOnBack?.message)
        assertNull(prompted.saveError)
        assertEquals("tempo on the last set", prompted.notes)

        vm.leaveAnyway()
        vm.exitRequested.awaitFirst { it }
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
        vm.uiState.awaitFirst { it.routine != null }

        vm.stageTargets(itemId, targetSets = 0, targetReps = 5, targetWeightKg = 100.0, restSeconds = 90)
        vm.leave()

        val prompted = vm.uiState.awaitFirst { it.unsavedOnBack != null && !it.saving }
        assertFalse(vm.exitRequested.value)
        assertEquals(RoutineSaveCopy.TARGETS_REJECTED, prompted.unsavedOnBack?.message)
        assertEquals(listOf(RoutineSaveCopy.targetsItem(fixture.exercise.name)), prompted.unsavedOnBack?.items)

        vm.stageTargets(itemId, targetSets = 4, targetReps = 5, targetWeightKg = 100.0, restSeconds = 90)
        vm.leave()
        vm.exitRequested.awaitFirst { it }
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
        vm.uiState.awaitFirst { it.routine != null }

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
        vm.uiState.awaitFirst { it.error == NumericEntry.REPS_WHOLE_RULE }
        assertEquals(5, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single().targetReps)

        vm.saveAndLeave()
        val blocked = vm.uiState.awaitFirst { it.saveError == NumericEntry.REPS_WHOLE_RULE && !it.saving }
        assertFalse(vm.exitRequested.value)
        assertNull(blocked.error)
        assertEquals(5, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single().targetReps)

        vm.stageTargets(itemId = itemId, targetSets = 3, targetReps = 8, targetWeightKg = null, restSeconds = 60)
        vm.saveAndLeave()
        vm.exitRequested.awaitFirst { it }
        assertEquals(8, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single().targetReps)
    }

    /**
     * Folding a card shut discards its four boxes; reopening reads the stored numbers back.
     * A rejection held past that refuses Save for a box showing a perfectly good value, with
     * nothing on screen to correct — the dead end a removed card used to leave behind. The
     * card's own DisposableEffect calls [RoutineEditorViewModel.forgetTargetRule] at exactly
     * that moment.
     */
    @Test
    fun foldingAnUnreadableCardAwayLetsSaveThroughAgain() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5)
        deps.workoutRepository.discardSession(fixture.session.id)
        val itemId = fixture.routine.exercises.single().id
        val vm = createViewModel(fixture.routine.id)
        vm.uiState.awaitFirst { it.routine != null }

        vm.stageTargets(
            itemId = itemId,
            targetSets = 3,
            targetReps = null,
            targetWeightKg = null,
            restSeconds = 60,
            invalidReason = NumericEntry.REPS_WHOLE_RULE,
        )
        vm.commitTargets(itemId)
        vm.uiState.awaitFirst { it.error == NumericEntry.REPS_WHOLE_RULE }

        vm.saveAndLeave()
        vm.uiState.awaitFirst { it.saveError == NumericEntry.REPS_WHOLE_RULE && !it.saving }
        assertFalse(vm.exitRequested.value)

        vm.forgetTargetRule(itemId)
        assertNull(vm.uiState.awaitFirst { it.error == null }.error)

        // Save leaves, and the stored reps are untouched: nothing was ever readable to write.
        vm.saveAndLeave()
        vm.exitRequested.awaitFirst { it }
        assertEquals(5, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single().targetReps)
    }

    /**
     * Only the rule goes when the boxes do. A value the owner typed and the routine can hold is
     * still owed a write, so folding the card must not quietly drop it.
     */
    @Test
    fun foldingACardAwayKeepsTheValuesItStagedAndDropsOnlyTheRule() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5)
        deps.workoutRepository.discardSession(fixture.session.id)
        val itemId = fixture.routine.exercises.single().id
        val vm = createViewModel(fixture.routine.id)
        vm.uiState.awaitFirst { it.routine != null }

        // Sets read cleanly as 4; the weight box holds something the routine cannot store.
        vm.stageTargets(
            itemId = itemId,
            targetSets = 4,
            targetReps = 5,
            targetWeightKg = null,
            restSeconds = 60,
            invalidReason = NumericEntry.WEIGHT_NEGATIVE,
        )
        vm.forgetTargetRule(itemId)

        vm.saveAndLeave()
        vm.exitRequested.awaitFirst { it }
        val stored = checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single()
        assertEquals(4, stored.targetSets)
        assertEquals(5, stored.targetReps)
        // The box that could not be read issued no instruction. This assertion is the whole
        // point of the test and its absence cost a stored target: without it the wipe below
        // was invisible, because sets and reps are safe on their own.
        assertEquals(100.0, checkNotNull(stored.targetWeightKg), 0.0001)
    }

    /**
     * The defect this class exists to prevent, stated as its own test.
     *
     * A weight box that cannot be read stages null, and null in the weight column is not
     * "unknown" — it is the instruction to clear the stored target. While the card's rule
     * stands the commit refuses the card outright and the two never meet. Folding the card
     * shut drops the rule, and for one release that left the null behind with nothing to say
     * it had never been an answer: Save carried it out, the 100 kg target went, and the editor
     * popped reporting success. The owner typed `-50`, changed their mind, and lost a number
     * they never touched.
     */
    @Test
    fun aWeightBoxThatCouldNotBeReadNeverClearsTheStoredTarget() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5, targetWeightKg = 100.0)
        deps.workoutRepository.discardSession(fixture.session.id)
        val itemId = fixture.routine.exercises.single().id
        val vm = createViewModel(fixture.routine.id)
        vm.uiState.awaitFirst { it.routine != null }

        // Exactly what the card stages for "-50" in the weight box: no weight, and the rule
        // that says why. Every other box still reads its stored value.
        vm.stageTargets(
            itemId = itemId,
            targetSets = 3,
            targetReps = 5,
            targetWeightKg = null,
            restSeconds = 90,
            invalidReason = NumericEntry.WEIGHT_NEGATIVE,
        )
        // The card is folded shut — a tap on its header, or on another lift's.
        vm.forgetTargetRule(itemId)
        vm.saveAndLeave()
        vm.exitRequested.awaitFirst { it }

        val stored = checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single()
        assertEquals(100.0, checkNotNull(stored.targetWeightKg), 0.0001)
        assertNull(vm.uiState.value.error)
    }

    /**
     * The other half of the same rule: a weight box the owner really did clear still clears the
     * target. The fix above must not buy safety by making the weight unclearable.
     */
    @Test
    fun aWeightBoxTheOwnerClearedStillClearsTheStoredTarget() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5, targetWeightKg = 100.0)
        deps.workoutRepository.discardSession(fixture.session.id)
        val itemId = fixture.routine.exercises.single().id
        val vm = createViewModel(fixture.routine.id)
        vm.uiState.awaitFirst { it.routine != null }

        // An empty weight box reads cleanly. There is no rule, so nothing is ever forgotten.
        vm.stageTargets(
            itemId = itemId,
            targetSets = 3,
            targetReps = 5,
            targetWeightKg = null,
            restSeconds = 90,
            invalidReason = null,
        )
        vm.saveAndLeave()
        vm.exitRequested.awaitFirst { it }

        assertNull(checkNotNull(deps.routineRepository.getById(fixture.routine.id)).exercises.single().targetWeightKg)
    }

    /**
     * A valid target typed but not yet committed, then the process reclaimed. The card is
     * rebuilt from saved state showing "8", so Save owes that write — it used to pop claiming
     * success while the routine still held 5, because only an UNREADABLE box re-registered
     * itself on restore. This is what the card's restore hook stages; the ViewModel half is
     * that a staged value survives to the exit flush and is written.
     */
    @Test
    fun aValidTargetRestoredAfterProcessDeathIsStillWrittenBySave() = runBlocking {
        val fixture = seedTestWorkout(deps, targetSets = 3, targetReps = 5)
        deps.workoutRepository.discardSession(fixture.session.id)
        val itemId = fixture.routine.exercises.single().id
        val vm = createViewModel(fixture.routine.id)
        vm.uiState.awaitFirst { it.routine != null }

        // What SessionLiftEditor's restore hook re-stages when the box text differs from the
        // stored numbers: the typed reps, no rule.
        vm.stageTargets(
            itemId = itemId,
            targetSets = 3,
            targetReps = 8,
            targetWeightKg = null,
            restSeconds = 60,
        )

        vm.saveAndLeave()
        vm.exitRequested.awaitFirst { it }
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
        vm.uiState.awaitFirst { it.routine != null && it.name == fixture.routine.name }

        gate.shouldFail = true
        vm.onNameChange("Lower strength")
        vm.saveAndLeave()
        val blocked = vm.uiState.awaitFirst { it.saveError != null && !it.saving }
        assertEquals(RoutineSaveCopy.EXIT_READ_FAILED, blocked.saveError)
        assertFalse(vm.exitRequested.value)

        // Back names what actually failed. The stub check is housekeeping and is now guarded,
        // so the only thing still owed here is the rename — and the prompt says so, where it
        // used to fall back to "could not check what still needs saving". saveAndLeave above
        // still reports the vaguer sentence: its count read is a precondition, not cleanup.
        vm.leave()
        val prompted = vm.uiState.awaitFirst { it.unsavedOnBack != null && !it.saving }
        assertEquals(RoutineSaveCopy.DETAILS_FAILED, prompted.unsavedOnBack?.message)
        assertEquals(listOf(RoutineSaveCopy.DETAILS_ITEM), prompted.unsavedOnBack?.items)

        vm.leaveAnyway()
        vm.exitRequested.awaitFirst { it }
        gate.shouldFail = false
        assertEquals(fixture.routine.name, checkNotNull(deps.routineRepository.getById(fixture.routine.id)).name)
    }

    /** Leave-anyway is Back minus the flush: an empty stub created this session still goes. */
    @Test
    fun leaveAnywayStillDiscardsAnEmptyStubCreatedThisSession() = runBlocking {
        val exercise = insertTestExercise(deps, "row", "Row")
        val vm = createViewModel("new")
        vm.uiState.awaitFirst { !it.isLoading }
        vm.addExercise(exercise, 3, 8, null, 90)
        val created = awaitRoutine { it.exercises.size == 1 }
        vm.removeExercise(created.exercises.single().id)
        awaitRoutine { it.exercises.isEmpty() }

        vm.leaveAnyway()
        vm.exitRequested.awaitFirst { it }
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
            vm.uiState.awaitFirst { it.routine != null && it.name == fixture.routine.name }
            vm.onNameChange("Lower strength")
            vm.saveAndLeave()
            val busy = vm.uiState.awaitFirst { it.saving }
            assertTrue(busy.saving)
            assertFalse(vm.exitRequested.value)

            vm.saveAndLeave()
            vm.leave()
            gate.complete(Unit)
            vm.exitRequested.awaitFirst { it }
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

    /**
     * Holds the FIRST add open on [gate] and then refuses it; later adds go straight through.
     *
     * The gate is what makes the race real under [UnconfinedTestDispatcher], which runs a
     * launched coroutine eagerly: without it the first tap finishes before the second is even
     * made, and the two never share the queue the defect lives in. Suspended inside
     * `pickWrites`, the first tap parks the second on the mutex, which is the phone's ordinary
     * case — two fast taps on rows that have no busy guard.
     */
    private class GateThenFailFirstAddDao(
        private val delegate: RoutineDao,
        private val gate: CompletableDeferred<Unit>,
    ) : RoutineDao by delegate {
        private var seen = 0

        override suspend fun upsertRoutineExercise(item: RoutineExerciseEntity) {
            seen += 1
            if (seen == 1) {
                gate.await()
                error("boom: the first add is refused")
            }
            delegate.upsertRoutineExercise(item)
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

    /**
     * Fails exactly the next [RoutineDao.getById] and then steps aside. The exit path reads the
     * routine twice — once to decide whether an untouched stub should be binned, once to compare
     * the typed name and notes — and only the first of those is housekeeping.
     */
    private class FailNextGetByIdDao(
        private val delegate: RoutineDao,
        private val gate: FailureGate,
    ) : RoutineDao by delegate {
        override suspend fun getById(id: String): RoutineWithExercises? {
            if (gate.shouldFail) {
                gate.shouldFail = false
                error("boom: Room could not read routine $id")
            }
            return delegate.getById(id)
        }
    }

    /**
     * A bare `first { }` on the ViewModel had no ceiling. When a refusal was wiped before this
     * collector saw it, the wait was not a failed test but a wedged JVM: CI sat for 31 minutes
     * on exactly that, in `stagedTargetsCommitWhenTheyDifferAndRejectZeroSets`. Bounded, and
     * naming the state it never reached, because "timed out" alone is the one fact already
     * known. Reading `uiState.value` in the catch cannot perturb the race — it has already
     * lost by then.
     */
    private suspend fun RoutineEditorViewModel.awaitState(
        predicate: (RoutineEditorUiState) -> Boolean,
    ): RoutineEditorUiState = try {
        withTimeout(TestWaits.FLOW_MS) { uiState.first(predicate) }
    } catch (timedOut: TimeoutCancellationException) {
        throw AssertionError(
            "awaitState gave up; last uiState was ${uiState.value}\n${stalledThreads()}",
            timedOut,
        )
    }

    private suspend fun RoutineEditorViewModel.awaitExit() {
        try {
            withTimeout(TestWaits.FLOW_MS) { exitRequested.first { it } }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError(
                "awaitExit gave up; last uiState was ${uiState.value}\n${stalledThreads()}",
                timedOut,
            )
        }
    }

    /**
     * A repository wait, bounded the same way. The third wedge in this class sat on
     * `exerciseRepository.observeAll().first { }` after a create, with every thread idle;
     * this names what the list held instead of hanging.
     */
    private suspend fun <T> awaitList(
        what: String,
        flow: Flow<List<T>>,
        predicate: (List<T>) -> Boolean,
    ): List<T> {
        var last: List<T>? = null
        return try {
            withTimeout(TestWaits.FLOW_MS) { flow.first { list -> last = list; predicate(list) } }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError(
                "awaitList($what) gave up; last list was $last; " +
                    "uiState was ${viewModel?.uiState?.value}\n${stalledThreads()}",
                timedOut,
            )
        }
    }

    /**
     * The routine wait, bounded and reporting like its three siblings. A bare
     * `TimeoutCancellationException` here named neither the routine nor the count: the
     * predicate only runs on a single-routine list, so a database holding none or two never
     * matches it and the failure looked identical to a value that was merely wrong.
     */
    private suspend fun awaitRoutine(predicate: (Routine) -> Boolean): Routine {
        var last: List<Routine>? = null
        return try {
            withTimeout(TestWaits.FLOW_MS) {
                deps.routineRepository.observeAll().first { list ->
                    last = list
                    list.singleOrNull()?.let(predicate) == true
                }.single()
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError(
                "awaitRoutine gave up; last routine list was $last; ${stalledThreads()}\n" +
                    "uiState was ${viewModel?.uiState?.value}",
                timedOut,
            )
        }
    }
}
