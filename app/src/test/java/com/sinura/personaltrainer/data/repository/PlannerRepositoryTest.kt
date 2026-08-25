package com.sinura.personaltrainer.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SlotRuleImport
import com.sinura.personaltrainer.domain.Weekday
import kotlinx.coroutines.flow.first
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
class PlannerRepositoryTest {
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
    fun importsSlotsAsEveningStrengthThenAddsMorningCardio() = runBlocking {
        val routine = deps.routineRepository.create(name = "Push")
        deps.scheduleRepository.pin(routine.id, null, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1_700_000_000_000L)
        val firstRules = deps.plannerRepository.rules()
        assertEquals(1, firstRules.size)
        assertEquals(SlotRuleImport.DEFAULT_STRENGTH_HOUR, firstRules.single().hour)
        assertEquals(ScheduleModality.STRENGTH, firstRules.single().modality)

        deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = SlotRuleImport.DEFAULT_CARDIO_HOUR,
            minute = 0,
            modality = ScheduleModality.CARDIO,
        )
        val week = deps.plannerRepository.ensureWeek(weekStart, "UTC", 1_700_000_000_000L)
        val monday = week.filter { it.localEpochDay == weekStart.epochDay }
        assertEquals(2, monday.size)
        assertEquals(setOf(7, 18), monday.map { it.hour }.toSet())
        assertTrue(monday.all { it.status == OccurrenceStatus.PLANNED })
    }

    @Test
    fun importIsIdempotentWhenRulesAlreadyExist() = runBlocking {
        deps.scheduleRepository.pin(null, SessionFocusKind.PULL, Weekday.TUESDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        val afterFirst = deps.plannerRepository.rules().size
        deps.plannerRepository.importSlotsIfNeeded()
        assertEquals(afterFirst, deps.plannerRepository.rules().size)
    }

    @Test
    fun syncUpsertsRoutineIdWhenASlotIsSwapped() = runBlocking {
        val push = deps.routineRepository.create(name = "Push")
        val pull = deps.routineRepository.create(name = "Pull")
        deps.scheduleRepository.pin(push.id, null, Weekday.MONDAY)
        deps.plannerRepository.syncSlotsToRules()
        val before = deps.plannerRepository.rules().single()
        assertEquals(push.id, before.routineId)
        deps.scheduleRepository.swapRoutine(deps.scheduleRepository.slots().single().id, pull.id)
        deps.plannerRepository.syncSlotsToRules()
        val after = deps.plannerRepository.rules().single()
        assertEquals(pull.id, after.routineId)
        assertEquals(before.id, after.id)
        assertEquals(18, after.hour)
    }

    @Test
    fun keepDatesPersistsOneDecision() = runBlocking {
        deps.scheduleRepository.pin(null, SessionFocusKind.LEGS, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L)
        deps.plannerRepository.applyMissedWork(
            choice = MissedWorkChoice.KEEP_DATES,
            weekStart = weekStart,
            todayEpochDay = weekStart.plusDays(3).epochDay,
            nowMinutesOfDay = 12 * 60,
            deviceZoneId = "UTC",
            nowMs = 2L,
        )
        val decision = deps.plannerRepository.decisionFor(weekStart.epochDay)!!
        assertEquals(MissedWorkChoice.KEEP_DATES, decision.choice)
        val monday = deps.plannerRepository.occurrencesBetween(
            weekStart.epochDay,
            weekStart.epochDay,
        ).single()
        assertEquals(OccurrenceStatus.MISSED, monday.status)
        assertEquals(1, deps.plannerRepository.observeDecisions().first().size)
    }
}
