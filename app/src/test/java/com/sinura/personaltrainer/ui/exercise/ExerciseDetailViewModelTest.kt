package com.sinura.personaltrainer.ui.exercise

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
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
 * Exercise Detail must tell missing apart from loading, and adding a lift
 * that is already in a routine must say so before writing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExerciseDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: ExerciseDetailViewModel? = null

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
    fun blankIdResolvesMissingWithoutSpinning() = runBlocking {
        val vm = createViewModel("")
        val state = vm.uiState.first { !it.isLoading }
        assertTrue(state.missing)
        assertNull(state.exercise)
    }

    @Test
    fun missingExerciseResolvesMissing() = runBlocking {
        val vm = createViewModel("gone")
        val state = vm.uiState.first { !it.isLoading }
        assertTrue(state.missing)
        assertNull(state.exercise)
    }

    @Test
    fun foundExerciseLoadsEmptyHistoryAndRoutineMembership() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val empty = deps.routineRepository.create("Empty")
        val holding = deps.routineRepository.create("Lower")
        deps.routineRepository.addExercise(holding.id, squat, 3, 5, null, 90)

        val vm = createViewModel(squat.id)
        val state = vm.uiState.first { !it.isLoading && it.exercise != null }
        assertFalse(state.missing)
        assertEquals(squat.id, state.exercise?.id)
        assertEquals(0, state.history.sessions.size)
        assertEquals(
            mapOf(empty.id to false, holding.id to true),
            state.routines.associate { it.routine.id to it.alreadyHolds },
        )
    }

    @Test
    fun finishedSetsAppearInHistory() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
        )
        val vm = createViewModel(fixture.exercise.id)
        val state = vm.uiState.first { it.history.sessions.isNotEmpty() }
        assertEquals(1, state.history.sessions.size)
        assertTrue(state.history.sessions.single().sets.any { it.weightKg == 100.0 && it.reps == 5 })
    }

    @Test
    fun addToRoutineAlreadyHoldingNoticesWithoutWriting() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val vm = createViewModel(fixture.exercise.id)
        vm.uiState.first { it.routines.any { membership -> membership.alreadyHolds } }

        vm.addToRoutine(fixture.routine.id)
        val notice = withTimeout(5_000) { vm.uiState.first { it.notice != null }.notice }
        assertEquals(
            "${fixture.exercise.name} is already in ${fixture.routine.name}.",
            notice,
        )
        assertEquals(1, deps.routineRepository.getById(fixture.routine.id)?.exercises?.size)
        vm.dismissNotice()
        assertNull(vm.uiState.first { it.notice == null }.notice)
    }

    @Test
    fun addToRoutinePersistsAndNotices() = runBlocking {
        val squat = insertTestExercise(deps, "squat", "Squat", muscleGroup = "Quads")
        val routine = deps.routineRepository.create("Upper")
        val vm = createViewModel(squat.id)
        vm.uiState.first { it.routines.any { it.routine.id == routine.id && !it.alreadyHolds } }

        vm.addToRoutine(routine.id)
        // One wait, on the view model, and it has to be both halves. The notice is set in
        // the same coroutine as the write, but membership arrives through Room's observeAll
        // a frame later — so the notice alone would let the read below race the commit.
        // Once membership is true the row is committed, and the one-shot getById is safe.
        val state = withTimeout(5_000) {
            vm.uiState.first {
                it.notice == "Added to ${routine.name}." &&
                    it.routines.any { membership ->
                        membership.routine.id == routine.id && membership.alreadyHolds
                    }
            }
        }
        val saved = requireNotNull(deps.routineRepository.getById(routine.id))
        assertEquals(1, saved.exercises.size)
        assertEquals(squat.id, saved.exercises.single().exercise.id)
        assertNull(saved.exercises.single().targetWeightKg)
        assertEquals("Added to ${routine.name}.", state.notice)
        assertTrue(state.routines.single { it.routine.id == routine.id }.alreadyHolds)
    }

    private fun createViewModel(exerciseId: String): ExerciseDetailViewModel =
        ExerciseDetailViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            savedStateHandle = SavedStateHandle(mapOf("exerciseId" to exerciseId)),
            container = deps,
        ).also { viewModel = it }
}
