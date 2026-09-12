package com.sinura.personaltrainer.ui.plan

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.DayFill
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.WeekBoard
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.home.HomeViewModel
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pinning a routine to civil Saturday must show on Home today and Plan today,
 * not only under Routines.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SaturdayAssignmentTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var plan: PlanViewModel? = null
    private var home: HomeViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        runBlocking {
            plan?.clearAndJoinForTest()
            home?.clearAndJoinForTest()
        }
        plan = null
        home = null
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun pinOnCivilSaturdayShowsOnHomeAndPlanToday() = runBlocking {
        val saturday = LocalDate.of(2026, 9, 12)
        val frozenMs = ZonedDateTime.of(2026, 9, 12, 21, 0, 0, 0, ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            MutableStateFlow(TrainingInsights()),
            scheduler = dispatcher,
            time = FrozenTime(frozenMs, "UTC"),
        )
        val app = ApplicationProvider.getApplicationContext<Application>()
        plan = PlanViewModel(app, deps)
        home = HomeViewModel(app, deps)
        plan!!.uiState.awaitFirst { !it.isLoading }
        home!!.uiState.awaitFirst { !it.isLoading }

        val routine = deps.routineRepository.create("Saturday")
        plan!!.pinRoutine(saturday.toEpochDay(), routine.id)

        val planState = withTimeout(TestWaits.FLOW_MS) {
            plan!!.uiState.first { state ->
                DailyAgenda.forDay(
                    saturday.toEpochDay(),
                    state.occurrences,
                    state.rules,
                    state.routines.associate { it.id to it.name },
                ).isNotEmpty()
            }
        }
        val homeState = withTimeout(TestWaits.FLOW_MS) {
            home!!.uiState.first { state ->
                DailyAgenda.forDay(
                    saturday.toEpochDay(),
                    state.occurrences,
                    state.rules,
                    state.routines.associate { it.id to it.name },
                ).isNotEmpty()
            }
        }
        assertTrue(
            DailyAgenda.forDay(
                saturday.toEpochDay(),
                planState.occurrences,
                planState.rules,
                planState.routines.associate { it.id to it.name },
            ).isNotEmpty(),
        )
        assertTrue(
            DailyAgenda.forDay(
                saturday.toEpochDay(),
                homeState.occurrences,
                homeState.rules,
                homeState.routines.associate { it.id to it.name },
            ).isNotEmpty(),
        )
        val weekStart = CivilDate.fromEpochDay(saturday.toEpochDay())
            .previousOrSame(Weekday.MONDAY)
            .epochDay
        val saturdayCell = WeekBoard.forWeek(
            weekStart,
            homeState.occurrences,
            homeState.rules,
        ).first { it.epochDay == saturday.toEpochDay() }
        assertTrue(saturdayCell.fill != DayFill.EMPTY)
        assertTrue(saturdayCell.caption != WeekBoard.REST)
    }
}
