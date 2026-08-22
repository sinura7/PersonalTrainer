package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearForTest
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Repeat while a session is already live must surface the blocked state.
 * Quietly opening Tuesday's half-finished Legs is the failure this exists to stop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HistoryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: HistoryViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModel?.clearForTest()
        viewModel = null
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun repeatWhileLiveSurfacesBlockedNotSilentResume() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val finished = deps.workoutRepository.startFreeWorkout("Push")
        deps.workoutRepository.finishSession(finished.id, notes = "")
        val live = deps.workoutRepository.startFreeWorkout("Legs")

        viewModel = HistoryViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.repeatSession(finished.id)

        val blocked = withTimeout(5_000) {
            viewModel!!.blockedRepeat.first { it != null }
        }
        assertEquals(live.id, blocked!!.inProgressSessionId)
        assertEquals("Legs", blocked.inProgressName)
        assertNull(viewModel!!.navigateToSession.value)
    }
}
