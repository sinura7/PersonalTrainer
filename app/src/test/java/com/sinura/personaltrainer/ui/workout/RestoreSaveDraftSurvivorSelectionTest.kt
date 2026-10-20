package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.DurableWorkoutDrafts
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.testutil.ControllableTimePort
import com.sinura.personaltrainer.testutil.TestWorkoutFixture
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import com.sinura.personaltrainer.workout.SavedStateWorkoutSave
import com.sinura.personaltrainer.workout.WorkoutDraft
import com.sinura.personaltrainer.workout.WorkoutDraftPersistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression for [ActiveWorkoutViewModel.restoreSaveDraft]: a pending save for a lift already
 * removed from Room must not overwrite the surviving lift's keypad with the released command.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestoreSaveDraftSurvivorSelectionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = ControllableTimePort()
    private lateinit var deps: FakeAppDependencies
    private lateinit var fixture: TestWorkoutFixture
    private lateinit var store: DurableDraftStore
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            time = clock,
        )
        fixture = seedTestWorkout(deps, targetSets = 12)
        deps.workoutRepository.updateSessionNotes(fixture.session.id, "Existing notes")
        store = DurableDraftStore(directory = temporary.newFolder("private"))
        install()
    }

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    private fun service() =
        DurableWorkoutDrafts(
            store = store,
            repository = deps.workoutRepository,
            maintenance = deps.dbMaintenance,
            restoreOpen = { false },
        )

    private fun install(port: WorkoutDraftPersistence = service()) {
        deps.workoutDraftCache.persistence = port
    }

    @Test
    fun releasedSaveForRemovedLiftDoesNotOverwriteSurvivorDraft() = runBlocking {
        val handle = SavedStateHandle(mapOf("sessionId" to fixture.session.id))
        val survivor = insertTestExercise(deps, "survivor-restore-draft", "Surviving row").also {
            deps.workoutRepository.addExerciseToSession(fixture.session.id, it, 3, 8, 40.0, 90)
            SavedStateWorkoutDraft(handle).write(
                WorkoutDraft(fixture.session.id, it.id, 44.5, 11, null, false, "Existing notes", dirty = true),
                null,
            )
        }
        val command = WorkoutSetSave(
            fixture.session.id,
            fixture.exercise.id,
            "retired-restore-draft",
            fixture.session.startedAt,
            WorkoutSetValues(99.0, 19, 9, false, 23),
        )
        SavedStateWorkoutSave(handle).write(command)
        deps.workoutRepository.removeExerciseFromSession(
            fixture.session.id,
            fixture.session.exercises.single().id,
        )
        val vm = active(handle)
        vm.primaryAction.awaitFirst { it.kind == WorkoutPrimaryKind.REVIEW_SAVE && it.enabled }
        vm.editFailedSave()
        vm.uiState.awaitFirst { !it.save.pending && !it.logging && it.selectedExerciseId != command.exerciseId }
        vm.privateRecovery.awaitFirst { it.phase == WorkoutPrivatePhase.READY }
        val entry = vm.uiState.awaitFirst { it.canLog && it.selectedExerciseId == survivor.id }
        assertEquals(44.5, entry.draft.weightKg, 0.0)
        assertEquals(11, entry.draft.reps)
        assertNull(entry.draft.rpe)
        assertNull(SavedStateWorkoutDraft(handle).readLift(fixture.session.id, command.exerciseId))
    }

    private fun active(handle: SavedStateHandle): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = handle,
            container = deps,
        ).also(viewModels::add)
}
