package com.sinura.personaltrainer

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.testutil.FakeClock
import com.sinura.personaltrainer.testutil.RefusingPlanLinkStore
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.catchingUncaught
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.navigation.LiveSessionBarViewModel
import com.sinura.personaltrainer.ui.workout.ActiveWorkoutViewModel
import com.sinura.personaltrainer.ui.workout.SessionLoadState
import com.sinura.personaltrainer.ui.workout.WorkoutExit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit UI-12: after a finish or discard had landed, the write that follows it, the link
 * between the session and its planned day, ran with no catch. When it failed the bar's
 * finish closed the app with the summary never opened, and the workout screen's finish said
 * it could not update the workout although the workout was already finished.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlanLinkFollowUpTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private lateinit var store: RefusingPlanLinkStore
    private val viewModels = mutableListOf<ViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            prefsStoreDecorator = { real -> RefusingPlanLinkStore(real).also { store = it } },
        )
    }

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        dispatcher.scheduler.advanceUntilIdle()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun aBarFinishWhoseLinkCannotBeSavedStillOpensTheSummary() = runBlocking {
        val fixture = seedTestWorkout(deps, loggedSets = listOf(TestSetInput(100.0, 5)))
        val planned = plannedDay()
        PendingOccurrence.bindForSession(deps, planned.id, fixture.session.id)
        val bar = bar(startedAt = fixture.session.startedAt)
        bar.uiState.awaitFirst { it?.canFinish == true }
        store.refuse = true

        var opened: String? = null
        val crash = catchingUncaught {
            bar.finishFromBar()
            opened = withTimeoutOrNull(TestWaits.FLOW_MS) { bar.finishedNavigation.first { it != null } }
        }

        assertNull("a refused follow-up write closed the app", crash)
        assertTrue("the link write never ran", store.refusals.get() > 0)
        assertEquals(fixture.session.id, opened)
        assertTrue(checkNotNull(deps.workoutRepository.getSession(fixture.session.id)).isFinished)
        assertNull(bar.actionError.value)
        val marked = checkNotNull(deps.plannerRepository.getOccurrence(planned.id))
        assertEquals(OccurrenceStatus.DONE, marked.status)
        assertEquals(fixture.session.id, marked.completedActivityId)
        // The clear that could not be saved is not read back from the file in this run.
        assertNull(PendingOccurrence.takeForComposer(deps))
        assertNull(deps.pendingOccurrenceId.value)
    }

    @Test
    fun aBarDiscardWhoseLinkCannotBeSavedStaysQuiet() = runBlocking {
        val fixture = seedTestWorkout(deps)
        PendingOccurrence.bindForSession(deps, "occ-planned", fixture.session.id)
        val bar = bar(startedAt = fixture.session.startedAt)
        bar.uiState.awaitFirst { it != null }
        store.refuse = true

        val crash = catchingUncaught {
            bar.discardFromBar()
            withTimeout(TestWaits.FLOW_MS) {
                while (store.refusals.get() == 0) delay(10)
            }
            // Nothing observable follows the refused write, so a throw from it is sampled for,
            // not awaited: long enough for the discard's coroutine to finish on a busy machine.
            settle(rounds = 50)
        }

        assertNull("a refused follow-up write closed the app", crash)
        assertNull(deps.workoutRepository.getSession(fixture.session.id))
        assertNull(bar.actionError.value)
        assertNull(deps.pendingOccurrenceId.value)
    }

    @Test
    fun aWorkoutScreenFinishWhoseLinkCannotBeSavedStillLeavesForTheSummary() = runBlocking {
        val fixture = seedTestWorkout(deps, loggedSets = listOf(TestSetInput(100.0, 5)))
        PendingOccurrence.bindForSession(deps, "occ-planned", fixture.session.id)
        val floor = ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to fixture.session.id)),
            container = deps,
        ).also(viewModels::add)
        floor.uiState.awaitFirst { it.loadState == SessionLoadState.FOUND && it.canFinish }
        store.refuse = true

        floor.finishWorkout()

        val exit = withTimeoutOrNull(TestWaits.FLOW_MS) { floor.exitRequested.first { it != null } }
        assertTrue("the link write never ran", store.refusals.get() > 0)
        assertEquals(WorkoutExit.Finished(fixture.session.id), exit)
        assertNull(floor.uiState.value.error)
    }

    /** A real planned strength day, so a finish has a row to mark done. */
    private suspend fun plannedDay(): ScheduleOccurrence {
        deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
        )
        return deps.plannerRepository.ensureWeek(CivilDate(2026, 8, 17), "UTC", 1L).single()
    }

    private fun bar(startedAt: Long): LiveSessionBarViewModel =
        LiveSessionBarViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            container = deps,
            clock = FakeClock(startedAt + 1_000),
        ).also(viewModels::add)

    private suspend fun settle(rounds: Int) = repeat(rounds) {
        dispatcher.scheduler.runCurrent()
        delay(10)
    }
}
