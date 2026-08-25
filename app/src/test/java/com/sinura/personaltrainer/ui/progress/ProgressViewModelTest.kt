package com.sinura.personaltrainer.ui.progress

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.util.toCivilDate
import java.time.LocalDate
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Body card says schedule a lighter week. The tap writes the same key Tune writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ProgressViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: ProgressViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
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
    fun firstMapUsesTheStoredHeatWindow() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        deps.preferencesRepository.setHeatWindow(HeatWindow.LAST_30_DAYS)
        viewModel = ProgressViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val state = viewModel!!.uiState.first { !it.isLoading }
        assertEquals(HeatWindow.LAST_30_DAYS, state.window)
    }

    @Test
    fun markLighterWeekWritesThisWeeksStart() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = ProgressViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val weekStart = deps.preferencesRepository.schedulePreferences.first().weekStart
        val expected = LighterWeek.weekStartEpochDay(
            today = LocalDate.now().toCivilDate(),
            weekStart = weekStart,
        )
        viewModel!!.markLighterWeek()
        val marked = withTimeout(5_000) {
            while (true) {
                dispatcher.scheduler.advanceUntilIdle()
                deps.preferencesRepository.lighterWeekStartEpochDay.first()
                    ?.takeIf { it == expected }
                    ?.let { return@withTimeout it }
                delay(10)
            }
            error("unreachable")
        }
        assertEquals(expected, marked)
    }
}
