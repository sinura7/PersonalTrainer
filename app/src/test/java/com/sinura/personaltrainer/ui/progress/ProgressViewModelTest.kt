package com.sinura.personaltrainer.ui.progress

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.dao.RecordSetRow
import com.sinura.personaltrainer.data.local.dao.WorkingSetCountRow
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.ActivityReadGate
import com.sinura.personaltrainer.testutil.FailingObserveActivityRecordSetsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    /** Moved from History (D10): Body's records come from the whole log, not a date window. */
    @Test
    fun bodyRecordsComeFromTheWholeLogNotTheLastMonth() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val bench = insertTestExercise(deps = deps, id = "bench", name = "Bench", muscleGroup = "Chest")
        val day = 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        for ((id, at, kg) in listOf(Triple("old", now - 60 * day, 120.0), Triple("recent", now - day, 100.0))) {
            deps.database.workoutDao().upsertSession(
                WorkoutSessionEntity(id = id, routineId = null, routineName = "Push", date = at, notes = "",
                    durationMinutes = 45, startedAt = at, finishedAt = at + 45L * 60L * 1000L),
            )
            deps.database.workoutDao().insertSet(
                SetLogEntity(id = "$id-set", sessionId = id, exerciseId = bench.id, setNumber = 1, weightKg = kg,
                    reps = 5, rpe = null, isWarmup = false, completedAt = at),
            )
        }
        viewModel = ProgressViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val trends = viewModel!!.trends.awaitFirst { it.recordsRead == CardRead.OK && it.records.isNotEmpty() }
        val record = trends.records.single()
        assertEquals("Bench", record.exerciseName)
        assertEquals(120.0, record.valueKg, 0.0)
        assertEquals(now - 60 * day, record.achievedAt)
    }

    /** Critic major 1: Body's records are the union of workout and activity sets, like History's. */
    @Test
    fun bodyRecordsIncludeAnActivityRecord() = runBlocking {
        val row = RecordSetRow(
            setId = "activity-set", sessionId = "activity-1", exerciseId = "goblet", exerciseName = "Goblet squat",
            loadType = null, weightKg = 32.0, reps = 10, completedAt = 1_790_000_000_000L,
        )
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            activityDaoDecorator = { real ->
                object : ActivityDao by real {
                    override fun observeCompletedStrengthSetRecords(): Flow<List<RecordSetRow>> = flowOf(listOf(row))
                }
            },
        )
        viewModel = ProgressViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val trends = viewModel!!.trends.awaitFirst { it.recordsRead == CardRead.OK && it.records.isNotEmpty() }
        val record = trends.records.single()
        assertEquals("goblet", record.exerciseId)
        assertEquals("Goblet squat", record.exerciseName)
    }

    /** Critic major 2: a failed records read is FAILED, not an empty "no records yet". */
    @Test
    fun aFailedActivityRecordReadMarksTheRecordsCardFailed() = runBlocking {
        val gate = ActivityReadGate(shouldFail = true)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            activityDaoDecorator = { FailingObserveActivityRecordSetsDao(it, gate) },
        )
        viewModel = ProgressViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.trends.awaitFirst { it.recordsRead == CardRead.FAILED }
        gate.shouldFail = false
        viewModel!!.retryTrends()
        viewModel!!.trends.awaitFirst { it.recordsRead == CardRead.OK }
        Unit
    }

    /** Critic major 2: a failed weekly read with nothing held is FAILED, never "no working sets". */
    @Test
    fun aFailedWeeklyReadMarksTheVolumeCardFailed() = runBlocking {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            workoutDaoDecorator = { real ->
                object : WorkoutDao by real {
                    override suspend fun workingSetCountsSince(sinceMs: Long): List<WorkingSetCountRow> = error("disk")
                }
            },
        )
        viewModel = ProgressViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val trends = viewModel!!.trends.awaitFirst { it.volumeRead != CardRead.LOADING }
        assertEquals(CardRead.FAILED, trends.volumeRead)
        assertTrue(trends.weeklySets.isEmpty())
    }

    /** Critic minor 3: the 8 weeks follow the day the screen feeds in, so midnight moves them. */
    @Test
    fun theWeeklyWindowFollowsTheFedToday() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = ProgressViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val sunday = LocalDate.of(2026, 11, 8).toEpochDay()
        viewModel!!.setTrendsToday(sunday)
        viewModel!!.trends.awaitFirst { it.firstWeekStartEpochDay == LocalDate.of(2026, 9, 14).toEpochDay() }
        viewModel!!.setTrendsToday(sunday + 1)
        viewModel!!.trends.awaitFirst { it.firstWeekStartEpochDay == LocalDate.of(2026, 9, 21).toEpochDay() }
        Unit
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
