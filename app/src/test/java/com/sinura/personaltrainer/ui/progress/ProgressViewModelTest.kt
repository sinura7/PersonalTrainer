package com.sinura.personaltrainer.ui.progress

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.util.toCivilDate
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
        deps.preferencesRepository.setHeatWindow(HeatWindow.CURRENT_MONTH)
        viewModel = ProgressViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val state = viewModel!!.uiState.awaitFirst { !it.isLoading }
        assertEquals(HeatWindow.CURRENT_MONTH, state.window)
    }

    @Test
    fun markLighterWeekWritesThisWeeksStart() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = ProgressViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val weekStart = deps.preferencesRepository.schedulePreferences.first().weekStart
        val expected = LighterWeek.weekStartEpochDay(
            today = LocalDate.now().toCivilDate(),
            weekStart = weekStart,
        )
        viewModel!!.markLighterWeek()
        // No wait at all. markLighterWeek launches on viewModelScope and immediately suspends
        // on a preferences read, so its continuation is queued on the test scheduler; with
        // DataStore, Room queries, IO and compute also on that scheduler (see
        // FakeAppDependencies.scheduler), advanceUntilIdle drives read, compute and write
        // to completion. The value is simply there afterwards, so a wrong one fails as an
        // assertion naming both numbers rather than as an opaque timeout.
        dispatcher.scheduler.advanceUntilIdle()
        val marked = deps.preferencesRepository.lighterWeekStartEpochDay.first()
        assertEquals(expected, marked)
    }

    @Test
    fun markLighterWeekUsesFrozenCivilTodayNotWallClock() = runBlocking {
        val zone = ZoneId.of("America/New_York")
        val frozenMs = ZonedDateTime.of(1999, 12, 31, 23, 59, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
        val frozenToday = com.sinura.personaltrainer.domain.CivilDate.of(1999, 12, 31)
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            time = FrozenTime(frozenMs, zone.id),
        )
        viewModel = ProgressViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val weekStart = deps.preferencesRepository.schedulePreferences.first().weekStart
        val expected = LighterWeek.weekStartEpochDay(
            today = frozenToday,
            weekStart = weekStart,
        )
        val wallExpected = LighterWeek.weekStartEpochDay(
            today = LocalDate.now().toCivilDate(),
            weekStart = weekStart,
        )
        assertNotEquals(
            "Frozen 1999-12-31 must not be wall today, or this would pass on trunk.",
            wallExpected,
            expected,
        )
        viewModel!!.markLighterWeek()
        dispatcher.scheduler.advanceUntilIdle()
        val marked = deps.preferencesRepository.lighterWeekStartEpochDay.first()
        assertEquals(expected, marked)
    }

    @Test
    fun firstLaunchNamesCatalogLifts() = runBlocking {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        deps.dbMaintenance.seedCatalog()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel = ProgressViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val state = viewModel!!.uiState.awaitFirst { !it.isLoading && it.firstLifts.isNotEmpty() }
        assertEquals("ex-barbell-back-squat", state.firstLifts.first().id)
        assertTrue(state.snapshot?.hasAnyWorkingSets != true)
        assertEquals(4, state.firstLifts.size)
    }
}
