package com.sinura.personaltrainer.ui.summary

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
 * Summary reads once. A later historical repair must not rewrite the
 * celebration the user is looking at.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WorkoutSummaryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: WorkoutSummaryViewModel? = null

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
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun missingSessionResolvesMissingInsteadOfSpinning() = runBlocking {
        val vm = createViewModel("missing")
        val state = vm.uiState.first { !it.isLoading }

        assertTrue(state.missing)
        assertFalse(state.isLoading)
    }

    @Test
    fun finishedWorkingSessionBuildsVolumeSetsTitleAndNotes() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            routineName = "Summary lower",
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
            notes = "summary note",
        )
        val vm = createViewModel(fixture.session.id)

        val state = vm.uiState.first { !it.isLoading }
        assertFalse(state.missing)
        assertTrue(state.summary.hasWork)
        assertEquals("Summary lower", state.summary.title)
        assertEquals(1, state.summary.workingSets)
        assertEquals(500.0, state.summary.volumeKg, 0.0001)
        assertEquals("summary note", state.summary.notes)
    }

    @Test
    fun warmupOnlySessionIsSavedButHasNoSummaryWork() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(20.0, 5, isWarmup = true)),
            finish = true,
        )
        val vm = createViewModel(fixture.session.id)

        val state = vm.uiState.first { !it.isLoading }
        assertFalse(state.missing)
        assertFalse(state.summary.hasWork)
        assertEquals(0, state.summary.workingSets)
    }

    @Test
    fun blankSessionIdResolvesMissingInsteadOfSpinning() = runBlocking {
        val vm = createViewModel("")
        val state = vm.uiState.first { !it.isLoading }

        assertTrue(state.missing)
        assertFalse(state.isLoading)
    }

    @Test
    fun summaryLoadsOnceAndDoesNotChangeAfterHistoricalRepair() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
        )
        val vm = createViewModel(fixture.session.id)
        val before = vm.uiState.first { !it.isLoading }.summary

        val set = fixture.session.sets.single()
        deps.workoutRepository.updateSet(set.id, 110.0, 5, null, false)

        assertEquals(500.0, vm.uiState.value.summary.volumeKg, 0.0001)
        assertEquals(before, vm.uiState.value.summary)
        assertNull(deps.workoutRepository.getInProgress())
    }

    private fun createViewModel(sessionId: String): WorkoutSummaryViewModel =
        WorkoutSummaryViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also { viewModel = it }
}
