package com.sinura.personaltrainer.domain

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.Weekday
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlanCreditOnFinishTest {
    private lateinit var deps: FakeAppDependencies
    private val weekStart = CivilDate(2026, 8, 17)

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun autoCreditsWhenRoutineMatches() = runBlocking {
        val routine = deps.routineRepository.create("Push")
        deps.scheduleRepository.pin(routine.id, null, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L)
        val session = WorkoutSession(
            id = "s-free",
            routineId = routine.id,
            routineName = "Push",
            date = 0L,
            notes = "",
            durationMinutes = 30,
            startedAt = 0L,
            finishedAt = 1L,
            exercises = emptyList(),
            sets = listOf(
                SetLog("l1", "s-free", "ex", "Bench", 1, 60.0, 8, null, false, 0L),
            ),
        )
        val result = com.sinura.personaltrainer.workout.PlanCreditOnFinish.afterFinish(
            deps = deps,
            session = session,
            boundOccurrenceId = null,
            todayEpochDay = weekStart.epochDay,
        )
        assertTrue(result is com.sinura.personaltrainer.domain.PlanCreditResult.Credited)
        val occ = deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay).single()
        assertEquals(OccurrenceStatus.DONE, occ.status)
        assertEquals("s-free", occ.completedActivityId)
    }
}
