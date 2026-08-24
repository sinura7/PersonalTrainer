package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestSetInput
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
 * Finished-session repair: timestamps stay put, notes flush on leave,
 * undo restores identity, and Repeat never silently steals a live workout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SessionDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: SessionDetailViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun missingSessionResolvesWithoutSpinner() = runBlocking {
        val vm = createViewModel("missing")
        val state = vm.uiState.first { !it.isLoading }

        assertNull(state.session)
        assertFalse(state.isLoading)
    }

    @Test
    fun finishedSessionLoadsNotesIntoDraft() = runBlocking {
        val fixture = seedFinished(notes = "original note")
        val vm = createViewModel(fixture.id)

        val state = vm.uiState.first { !it.isLoading && it.notes == "original note" }
        assertEquals(fixture.id, state.session?.id)
    }

    @Test
    fun updateSetPreservesTimestampAndSetNumber() = runBlocking {
        val fixture = seedFinished()
        val original = fixture.sets.single()
        val vm = createViewModel(fixture.id)
        vm.uiState.first { !it.isLoading }

        vm.updateSet(original.id, 105.0, 6, rpe = 9, isWarmup = false)

        val updated = awaitSession(fixture.id) {
            it.sets.singleOrNull()?.weightKg == 105.0
        }.sets.single()
        assertEquals(original.id, updated.id)
        assertEquals(original.completedAt, updated.completedAt)
        assertEquals(original.setNumber, updated.setNumber)
        assertEquals(6, updated.reps)
        assertEquals(9, updated.rpe)
    }

    @Test
    fun addSetAppendsInsideFinishedSessionWindow() = runBlocking {
        val fixture = seedFinished()
        val vm = createViewModel(fixture.id)
        vm.uiState.first { !it.isLoading }

        vm.addSet(TEST_EXERCISE, 90.0, 8, rpe = 8, isWarmup = false)

        val updated = awaitSession(fixture.id) { it.sets.size == 2 }
        val added = updated.sets.maxBy { it.setNumber }
        assertEquals(2, added.setNumber)
        assertTrue(added.completedAt >= updated.startedAt)
        assertTrue(added.completedAt <= checkNotNull(updated.finishedAt))
    }

    @Test
    fun deleteOffersUndoAndRestoresOriginalIdentityAndTime() = runBlocking {
        val fixture = seedFinished()
        val original = fixture.sets.single()
        val vm = createViewModel(fixture.id)
        vm.uiState.first { !it.isLoading }

        vm.deleteSet(original.id)

        val offered = eventually { vm.deletedSet.value }
        assertEquals(original.id, offered.setId)
        awaitSession(fixture.id) { it.sets.isEmpty() }

        vm.undoDeleteSet()

        val restored = awaitSession(fixture.id) { it.sets.size == 1 }.sets.single()
        assertEquals(original.id, restored.id)
        assertEquals(original.completedAt, restored.completedAt)
        assertNull(vm.deletedSet.value)
    }

    @Test
    fun notesDebounceAndExitFlushBothPersist() = runBlocking {
        val fixture = seedFinished()
        val vm = createViewModel(fixture.id)
        vm.uiState.first { !it.isLoading }

        vm.setNotes("debounced")
        dispatcher.scheduler.advanceTimeBy(399)
        assertEquals("", deps.workoutRepository.getSession(fixture.id)?.notes)
        dispatcher.scheduler.advanceTimeBy(2)
        dispatcher.scheduler.runCurrent()
        awaitSession(fixture.id) { it.notes == "debounced" }

        vm.setNotes("leave immediately")
        vm.persistNotesForExit()
        val flushed = awaitSession(fixture.id) { it.notes == "leave immediately" }
        assertEquals("leave immediately", flushed.notes)
    }

    @Test
    fun validationErrorIsUserFacingAndCanBeAcknowledged() = runBlocking {
        val fixture = seedFinished()
        val original = fixture.sets.single()
        val vm = createViewModel(fixture.id)
        vm.uiState.first { !it.isLoading }

        vm.updateSet(original.id, 0.0, 5, rpe = null, isWarmup = false)

        assertEquals(SetLogRules.ZERO_WORKING_WEIGHT, eventually { vm.error.value })
        vm.onErrorShown()
        assertNull(vm.error.value)
        assertEquals(100.0, deps.workoutRepository.getSession(fixture.id)!!.sets.single().weightKg, 0.0001)
    }

    @Test
    fun repeatWhenIdleNavigatesOnceAndCopiesNoLoggedSets() = runBlocking {
        val fixture = seedFinished()
        val vm = createViewModel(fixture.id)
        vm.uiState.first { !it.isLoading }

        vm.repeatSession()

        val newId = eventually { vm.navigateToSession.value }
        val repeated = checkNotNull(deps.workoutRepository.getSession(newId))
        assertTrue(repeated.sets.isEmpty())
        assertEquals(1, repeated.exercises.size)
        vm.onNavigationHandled()
        assertNull(vm.navigateToSession.value)
    }

    @Test
    fun repeatWhileLiveSurfacesBlockedAndResumeNavigatesToLive() = runBlocking {
        val fixture = seedFinished()
        val live = deps.workoutRepository.startFreeWorkout()
        val vm = createViewModel(fixture.id)
        vm.uiState.first { !it.isLoading }

        vm.repeatSession()

        val blocked = eventually { vm.blockedRepeat.value }
        assertEquals(live.id, blocked.inProgressSessionId)
        assertNull(vm.navigateToSession.value)
        vm.resumeBlockedSession()
        assertNull(vm.blockedRepeat.value)
        assertEquals(live.id, vm.navigateToSession.value)
    }

    @Test
    fun addSetValidationErrorIsUserFacingAndWritesNothing() = runBlocking {
        val fixture = seedFinished()
        val vm = createViewModel(fixture.id)
        vm.uiState.first { !it.isLoading }

        vm.addSet(TEST_EXERCISE, 0.0, 5, rpe = null, isWarmup = false)

        assertEquals(SetLogRules.ZERO_WORKING_WEIGHT, eventually { vm.error.value })
        assertEquals(1, deps.workoutRepository.getSession(fixture.id)!!.sets.size)
    }

    @Test
    fun repeatMissingSessionSurfacesFailedWithoutNavigating() = runBlocking {
        val vm = createViewModel("missing")
        vm.uiState.first { !it.isLoading }

        vm.repeatSession()

        assertEquals("That session is no longer available.", eventually { vm.error.value })
        assertNull(vm.navigateToSession.value)
        assertNull(vm.blockedRepeat.value)
        assertNull(deps.workoutRepository.getInProgress())
    }

    @Test
    fun dismissBlockedRepeatClearsTheOfferWithoutNavigating() = runBlocking {
        val fixture = seedFinished()
        val live = deps.workoutRepository.startFreeWorkout()
        val vm = createViewModel(fixture.id)
        vm.uiState.first { !it.isLoading }

        vm.repeatSession()
        eventually { vm.blockedRepeat.value }

        vm.dismissBlockedRepeat()

        assertNull(vm.blockedRepeat.value)
        assertNull(vm.navigateToSession.value)
        assertEquals(live.id, deps.workoutRepository.getInProgress()?.id)
    }

    @Test
    fun undoWithNothingPendingIsANoOp() = runBlocking {
        val fixture = seedFinished()
        val vm = createViewModel(fixture.id)
        vm.uiState.first { !it.isLoading }

        vm.undoDeleteSet()

        assertEquals(1, deps.workoutRepository.getSession(fixture.id)!!.sets.size)
        assertNull(vm.deletedSet.value)
        assertNull(vm.error.value)
    }

    @Test
    fun deleteSessionRemovesRowAndEmitsDeleted() = runBlocking {
        val fixture = seedFinished()
        val vm = createViewModel(fixture.id)
        vm.uiState.first { !it.isLoading }

        vm.deleteSession()

        eventually { true.takeIf { vm.deleted.value } }
        assertNull(deps.workoutRepository.getSession(fixture.id))
    }

    private fun createViewModel(sessionId: String): SessionDetailViewModel =
        SessionDetailViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also { viewModel = it }

    private suspend fun seedFinished(notes: String = ""): WorkoutSession =
        seedTestWorkout(
            deps = deps,
            exerciseId = TEST_EXERCISE,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
            notes = notes,
        ).session

    private suspend fun awaitSession(
        id: String,
        predicate: (WorkoutSession) -> Boolean,
    ): WorkoutSession = eventually {
        deps.workoutRepository.getSession(id)?.takeIf(predicate)
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

    private companion object {
        const val TEST_EXERCISE = "test-squat"
    }
}
