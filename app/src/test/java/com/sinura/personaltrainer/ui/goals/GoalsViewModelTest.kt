package com.sinura.personaltrainer.ui.goals

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.GoalKind
import com.sinura.personaltrainer.domain.GoalPeriod
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GoalsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: GoalsViewModel? = null

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
    fun addMeasuresAnEmptyWeekThenPauseAndDelete() = runBlocking {
        val vm = createViewModel()
        vm.uiState.first { !it.isLoading }
        vm.add(GoalKind.SESSION_COUNT, targetValue = 3.0, period = GoalPeriod.WEEK)
        val added = vm.uiState.first { it.snapshots.size == 1 }
        val snapshot = added.snapshots.single()
        assertEquals(GoalKind.SESSION_COUNT, snapshot.goal.kind)
        assertEquals(3.0, snapshot.goal.targetValue, 0.0001)
        assertEquals(0.0, snapshot.currentValue, 0.0001)
        assertFalse(snapshot.met)

        vm.setPaused(snapshot.goal.id, paused = true)
        val paused = vm.uiState.first { it.snapshots.single().goal.paused }
        assertTrue(paused.snapshots.single().goal.paused)
        assertFalse(paused.snapshots.single().met)

        vm.delete(snapshot.goal.id)
        val empty = vm.uiState.first { it.snapshots.isEmpty() }
        assertTrue(empty.snapshots.isEmpty())
    }

    private fun createViewModel(): GoalsViewModel =
        GoalsViewModel(
            application = ApplicationProvider.getApplicationContext(),
            container = deps,
        ).also { viewModel = it }
}
