package com.sinura.personaltrainer.ui.navigation

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.testutil.FakeClock
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.workout.WorkoutDraft
import java.util.concurrent.TimeUnit
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
 * The one live-session chrome: elapsed and staleness come from an injected
 * clock, finish/discard go through the shared use cases, and a zero-set
 * session cannot be finished.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LiveSessionBarViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: LiveSessionBarViewModel? = null

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
    fun noLiveSessionEmitsNull() = runBlocking {
        val vm = createViewModel(FakeClock())
        assertNull(vm.uiState.first())
    }

    @Test
    fun liveSessionPublishesCountsRestAndElapsed() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(
                TestSetInput(40.0, 5, isWarmup = true),
                TestSetInput(100.0, 5),
            ),
        )
        deps.restTimerController.start(90, fixture.session.id)
        val clock = FakeClock(fixture.session.startedAt + 65_000)
        val vm = createViewModel(clock)

        val state = vm.uiState.first { it?.totalSets == 2 }
        checkNotNull(state)
        assertEquals(fixture.session.id, state.sessionId)
        assertEquals(1, state.workingSets)
        assertEquals(2, state.totalSets)
        assertTrue(state.canFinish)
        assertTrue(state.restRunning)
        assertEquals(90, state.restRemainingSeconds)
        assertEquals("1:05", state.elapsedLabel)
        assertFalse(state.stale)
    }

    @Test
    fun stalenessRecomputesFromInjectedClockAtFourHours() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val clock = FakeClock(fixture.session.startedAt + TimeUnit.HOURS.toMillis(3))
        val vm = createViewModel(clock)
        assertFalse(checkNotNull(vm.uiState.first { it != null }).stale)

        clock.nowMs = fixture.session.startedAt + TimeUnit.HOURS.toMillis(4)
        dispatcher.scheduler.advanceTimeBy(1_001)
        dispatcher.scheduler.runCurrent()

        val stale = vm.uiState.first { it?.stale == true }
        assertEquals(4L, stale?.staleHours)
    }

    @Test
    fun finishFromBarPreservesNotesStopsRestAndEmitsOneShotNavigation() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            notes = "keep this",
        )
        deps.workoutRepository.updateSessionNotes(fixture.session.id, "keep this")
        deps.workoutDraftCache.put(
            WorkoutDraft(fixture.session.id, fixture.exercise.id, 100.0, 5, null, false, ""),
        )
        deps.restTimerController.start(90, fixture.session.id)
        val vm = createViewModel(FakeClock(fixture.session.startedAt + 1_000))
        vm.uiState.first { it?.canFinish == true }

        vm.finishFromBar()

        val navigation = eventually { vm.finishedNavigation.value }
        assertEquals(fixture.session.id, navigation)
        val saved = checkNotNull(deps.workoutRepository.getSession(fixture.session.id))
        assertEquals("keep this", saved.notes)
        assertTrue(saved.isFinished)
        assertFalse(deps.restTimerStore.current().running)
        assertNull(deps.workoutDraftCache.get(fixture.session.id))

        vm.onFinishNavigationHandled()
        assertNull(vm.finishedNavigation.value)
    }

    @Test
    fun finishFromBarWithNoSetsDoesNotNavigateOrWriteFinishedAt() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val vm = createViewModel(FakeClock(fixture.session.startedAt + 1_000))
        val state = checkNotNull(vm.uiState.first { it != null })
        assertFalse(state.canFinish)

        vm.finishFromBar()
        dispatcher.scheduler.runCurrent()

        assertNull(vm.finishedNavigation.value)
        assertNull(deps.workoutRepository.getSession(fixture.session.id)?.finishedAt)
        assertEquals(fixture.session.id, deps.workoutRepository.getInProgress()?.id)
    }

    @Test
    fun elapsedLabelAdvancesFromInjectedClock() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val clock = FakeClock(fixture.session.startedAt + 5_000)
        val vm = createViewModel(clock)
        assertEquals("0:05", checkNotNull(vm.uiState.first { it != null }).elapsedLabel)

        clock.nowMs = fixture.session.startedAt + 90_000
        dispatcher.scheduler.advanceTimeBy(1_001)
        dispatcher.scheduler.runCurrent()

        assertEquals("1:30", checkNotNull(vm.uiState.first { it?.elapsedLabel == "1:30" }).elapsedLabel)
    }

    @Test
    fun aHiddenRouteStopsElapsedTicksUntilTheBarIsShownAgain() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val clock = FakeClock(fixture.session.startedAt + 5_000)
        val vm = createViewModel(clock)
        assertEquals("0:05", checkNotNull(vm.uiState.first { it != null }).elapsedLabel)

        vm.setRouteHidesBar(true)
        clock.nowMs = fixture.session.startedAt + 90_000
        dispatcher.scheduler.advanceTimeBy(5_000)
        dispatcher.scheduler.runCurrent()
        assertEquals("0:05", checkNotNull(vm.uiState.value).elapsedLabel)

        vm.setRouteHidesBar(false)
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()
        assertEquals("1:30", checkNotNull(vm.uiState.first { it?.elapsedLabel == "1:30" }).elapsedLabel)
    }

    @Test
    fun discardFromBarDeletesZeroSetSessionAndDraft() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutDraftCache.put(
            WorkoutDraft(fixture.session.id, fixture.exercise.id, 100.0, 5, null, false, ""),
        )
        val vm = createViewModel(FakeClock(fixture.session.startedAt))
        val state = checkNotNull(vm.uiState.first { it != null })
        assertFalse(state.canFinish)

        vm.discardFromBar()

        eventually {
            if (deps.workoutRepository.getSession(fixture.session.id) == null) true else null
        }
        assertNull(vm.finishedNavigation.value)
        assertNull(deps.workoutDraftCache.get(fixture.session.id))
    }

    private fun createViewModel(clock: FakeClock): LiveSessionBarViewModel =
        LiveSessionBarViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            container = deps,
            clock = clock,
        ).also { viewModel = it }

    // A ceiling, not a target. This polls in real time while Room answers on its own
    // executor, so a loaded runner can blow a tight budget with nothing actually wrong:
    // 5 s failed twice on CI in six runs, in this helper, on assertions that hold.
    // Raising it costs nothing on a passing test. J4 is the real fix — value-based
    // waits instead of polling — and this is a stopgap until it lands.
    private suspend fun <T : Any> eventually(block: suspend () -> T?): T =
        withTimeout(30_000) {
            while (true) {
                dispatcher.scheduler.runCurrent()
                block()?.let { return@withTimeout it }
                delay(10)
            }
            error("unreachable")
        }
}
