package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.workout.WorkoutDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WorkoutReadRecoveryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val failReads = MutableStateFlow(false)
    private lateinit var deps: FakeAppDependencies
    private val models = mutableListOf<ViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            workoutDaoDecorator = { real ->
                object : WorkoutDao by real {
                    override fun observeSession(id: String): Flow<SessionWithDetails?> =
                        real.observeSession(id).combine(failReads) { row, fail ->
                            check(!fail) { "Injected session read failure" }
                            row
                        }
                }
            },
        )
    }

    @After
    fun tearDown() {
        runBlocking { models.forEach { it.clearAndJoinForTest() } }
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun initialFailurePreservesRecoveredDraftAndRetryLoadsRealSession() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutDraftCache.put(WorkoutDraft(
            sessionId = fixture.session.id, exerciseId = fixture.exercise.id,
            weightKg = 77.5, reps = 9, rpe = 8, isWarmup = false, notes = "Keep this", dirty = true,
        ))
        failReads.value = true
        val vm = active(fixture.session.id)
        val failed = vm.uiState.awaitFirst { it.loadState == SessionLoadState.FAILED }
        assertEquals(77.5, failed.draft.weightKg, 0.0)
        assertFalse(failed.canLog)
        assertFalse(failed.showDiscard)

        failReads.value = false
        vm.retrySession()
        val recovered = vm.uiState.awaitFirst { it.loadState == SessionLoadState.FOUND }
        assertEquals(fixture.session.id, recovered.session?.id)
        assertEquals(77.5, recovered.draft.weightKg, 0.0)
        assertEquals(9, recovered.draft.reps)
        assertEquals("Keep this", recovered.notes)
        assertTrue(recovered.session!!.sets.isEmpty())
    }

    @Test
    fun laterFailureKeepsLastGraphAndDraftButDisablesCommitUntilRetry() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val vm = active(fixture.session.id)
        vm.uiState.awaitFirst { it.loadState == SessionLoadState.FOUND && it.canLog }
        vm.setWeight(75.0)
        vm.uiState.awaitFirst { it.draft.weightKg == 75.0 }
        failReads.value = true
        val failed = vm.uiState.awaitFirst { it.loadState == SessionLoadState.FAILED }
        assertEquals(fixture.session.id, failed.session?.id)
        assertEquals(75.0, failed.draft.weightKg, 0.0)
        assertFalse(failed.canLog)

        failReads.value = false
        vm.retrySession()
        val recovered = vm.uiState.awaitFirst { it.loadState == SessionLoadState.FOUND && it.canLog }
        assertEquals(75.0, recovered.draft.weightKg, 0.0)
        assertTrue(recovered.session!!.sets.isEmpty())
    }

    @Test
    fun initialFailureWithoutADraftPrefillsThePlanAfterRetry() = runBlocking {
        val fixture = seedTestWorkout(deps, targetWeightKg = 82.5, targetReps = 7)
        failReads.value = true
        val vm = active(fixture.session.id)
        vm.uiState.awaitFirst { it.loadState == SessionLoadState.FAILED }
        failReads.value = false
        vm.retrySession()
        val recovered = vm.uiState.awaitFirst { it.canLog && it.draft.weightKg == 82.5 }
        assertEquals(7, recovered.draft.reps)
    }

    @Test
    fun idleRestLoadsItsPrescribedDurationAfterRetry() = runBlocking {
        val fixture = seedTestWorkout(deps, restSeconds = 150)
        failReads.value = true
        val vm = RestTimerViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to fixture.session.id)),
            container = deps,
        ).also(models::add)
        vm.uiState.awaitFirst { it.loadState == SessionLoadState.FAILED }
        failReads.value = false
        vm.retrySession()
        val recovered = vm.uiState.awaitFirst { it.loadState == SessionLoadState.FOUND && it.rest.totalSeconds == 150 }
        assertFalse(recovered.rest.running)
    }

    @Test
    fun staleFinishDiscardAndPickerCallbacksCannotMutateAfterAReadFailure() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val vm = active(fixture.session.id)
        vm.uiState.awaitFirst { it.canLog }
        vm.setPickerVisible(true)
        failReads.value = true
        vm.uiState.awaitFirst { it.loadState == SessionLoadState.FAILED }
        vm.finishWorkout()
        vm.discardWorkout()
        vm.removeSelectedLift()
        vm.logSet()
        val saved = deps.workoutRepository.getSession(fixture.session.id)!!
        assertFalse(saved.isFinished)
        assertEquals(1, saved.exercises.size)
        assertTrue(saved.sets.isEmpty())
    }

    @Test
    fun restReadFailureOffersRecoveryWithoutStoppingTheSharedTimer() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.restTimerController.start(90, fixture.session.id)
        failReads.value = true
        val vm = RestTimerViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to fixture.session.id)),
            container = deps,
        ).also(models::add)
        assertTrue(vm.uiState.awaitFirst { it.loadState == SessionLoadState.FAILED }.rest.running)
        failReads.value = false
        vm.retrySession()
        assertTrue(vm.uiState.awaitFirst { it.loadState == SessionLoadState.FOUND }.rest.running)
        assertTrue(deps.restTimerStore.current().running)
    }

    private fun active(sessionId: String) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
        container = deps,
    ).also(models::add)
}
