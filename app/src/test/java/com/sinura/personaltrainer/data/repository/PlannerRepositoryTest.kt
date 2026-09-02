package com.sinura.personaltrainer.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.MoveToToday
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
    fun laterStrengthSurvivesASlotSwap() = runBlocking {
        val push = deps.routineRepository.create(name = "Push")
        val pull = deps.routineRepository.create(name = "Pull")
        val extra = deps.routineRepository.create(name = "Monday extra")
        deps.scheduleRepository.pin(push.id, null, Weekday.MONDAY)
        deps.plannerRepository.syncSlotsToRules()
        deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = SlotRuleImport.nextLaterHour(listOf(SlotRuleImport.DEFAULT_STRENGTH_HOUR)),
            minute = 0,
            modality = ScheduleModality.STRENGTH,
            routineId = extra.id,
        )
        deps.scheduleRepository.swapRoutine(deps.scheduleRepository.slots().single().id, pull.id)
        deps.plannerRepository.syncSlotsToRules()
        val rules = deps.plannerRepository.rules()
        assertEquals(2, rules.size)
        assertEquals(pull.id, rules.single { SlotRuleImport.isImportedSlotRule(it.id) }.routineId)
        val later = rules.single { SlotRuleImport.isUserTimedRule(it.id) }
        assertEquals(extra.id, later.routineId)
        assertEquals(20, later.hour)
        assertEquals(ScheduleModality.STRENGTH, later.modality)
    }

    @Test
    fun removeTimedRuleDropsPlannedExtraAndLeavesThePin() = runBlocking {
        val push = deps.routineRepository.create(name = "Push")
        val extra = deps.routineRepository.create(name = "Monday extra")
        deps.scheduleRepository.pin(push.id, null, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        val later = deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = 20,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
            routineId = extra.id,
        )
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1_700_000_000_000L)
        assertEquals(2, deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay).size)
        deps.plannerRepository.removeTimedRule(later.id)
        val monday = deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay)
        assertEquals(1, monday.size)
        assertEquals(1, deps.plannerRepository.rules().size)
        assertTrue(SlotRuleImport.isImportedSlotRule(deps.plannerRepository.rules().single().id))
        deps.plannerRepository.removeTimedRule(deps.plannerRepository.rules().single().id)
        assertEquals(1, deps.plannerRepository.rules().size)
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

    @Test
    fun moveOccurrenceToDayVacatesFridayAndPlansSaturday() = runBlocking {
        val routine = deps.routineRepository.create(name = "Push")
        deps.scheduleRepository.pin(routine.id, null, Weekday.FRIDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1_700_000_000_000L)
        val friday = weekStart.plusDays(4).epochDay
        val saturday = weekStart.plusDays(5).epochDay
        val leftover = deps.plannerRepository.occurrencesBetween(friday, friday).single()
        val outcome = deps.plannerRepository.moveOccurrenceToDay(
            leftover.id,
            saturday,
            nowMs = 2L,
        )
        val relocate = outcome as MoveToToday.Outcome.Relocate
        assertEquals(OccurrenceStatus.MOVED, deps.plannerRepository.getOccurrence(leftover.id)!!.status)
        val todayRow = deps.plannerRepository.getOccurrence(relocate.created.id)!!
        assertEquals(OccurrenceStatus.PLANNED, todayRow.status)
        assertEquals(saturday, todayRow.localEpochDay)
        assertEquals(leftover.hour, todayRow.hour)
        assertEquals(leftover.ruleId, todayRow.ruleId)
    }

    @Test
    fun applyDayOrderPermutesHoursOnTheOccurrenceAndRule() = runBlocking {
        val push = deps.routineRepository.create(name = "Push")
        deps.scheduleRepository.pin(push.id, null, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        val extra = deps.routineRepository.create(name = "Golf warm-up")
        deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = 20,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
            routineId = extra.id,
            templateId = com.sinura.personaltrainer.domain.ScheduleKind.aux("golf"),
        )
        val week = deps.plannerRepository.ensureWeek(weekStart, "UTC", 1_700_000_000_000L)
        val monday = week.filter { it.localEpochDay == weekStart.epochDay }
            .sortedBy { it.hour }
        assertEquals(listOf(18, 20), monday.map { it.hour })
        deps.plannerRepository.applyDayOrder(
            listOf(
                com.sinura.personaltrainer.domain.DayBlockOrder.HourMove(
                    monday[1].id, monday[1].ruleId, 18,
                ),
                com.sinura.personaltrainer.domain.DayBlockOrder.HourMove(
                    monday[0].id, monday[0].ruleId, 20,
                ),
            ),
        )
        val reordered = deps.plannerRepository.occurrencesBetween(
            weekStart.epochDay,
            weekStart.epochDay,
        ).sortedBy { it.hour }
        assertEquals(monday[1].id, reordered[0].id)
        assertEquals(18, reordered[0].hour)
        assertEquals(monday[0].id, reordered[1].id)
        assertEquals(20, reordered[1].hour)
        assertEquals(18, deps.plannerRepository.getRule(monday[1].ruleId)!!.hour)
    }

    // ---------------------------------------------------------------------------------------
    // A finished day stays finished (A3). The Skip and Move buttons live on a notification
    // that can sit in the shade for hours after the session it is about was actually done.
    // ---------------------------------------------------------------------------------------

    @Test
    fun skipCannotUnFinishADoneDay() = runBlocking {
        // You did the session. Hours later you tidy the notification away with Skip, and the
        // workout you finished is marked skipped: the day loses its credit and the week says
        // you did nothing.
        val occurrence = plannedMonday()
        deps.plannerRepository.markOccurrenceDone(occurrence.id, activityId = "act-1")

        deps.plannerRepository.skipOccurrence(occurrence.id)

        val after = deps.plannerRepository.getOccurrence(occurrence.id)!!
        assertEquals(OccurrenceStatus.DONE, after.status)
    }

    @Test
    fun moveCannotDuplicateADoneDay() = runBlocking {
        // Worse than Skip: Move restamps the finished row AND mints a second occurrence for
        // tomorrow — a workout invented out of a tidy-up.
        val occurrence = plannedMonday()
        deps.plannerRepository.markOccurrenceDone(occurrence.id, activityId = "act-1")
        val before = allOccurrenceIds()

        deps.plannerRepository.moveOccurrenceForward(occurrence.id)

        assertEquals(before, allOccurrenceIds())
        assertEquals(
            OccurrenceStatus.DONE,
            deps.plannerRepository.getOccurrence(occurrence.id)!!.status,
        )
    }

    @Test
    fun aPlannedDayCanStillBeSkipped() = runBlocking {
        // The guards must refuse settled days, not working ones.
        val occurrence = plannedMonday()
        deps.plannerRepository.skipOccurrence(occurrence.id)
        assertEquals(
            OccurrenceStatus.SKIPPED,
            deps.plannerRepository.getOccurrence(occurrence.id)!!.status,
        )
    }

    @Test
    fun aPlannedDayCanStillBeMoved() = runBlocking {
        val occurrence = plannedMonday()
        val before = allOccurrenceIds()

        deps.plannerRepository.moveOccurrenceForward(occurrence.id)

        assertEquals(
            OccurrenceStatus.MOVED,
            deps.plannerRepository.getOccurrence(occurrence.id)!!.status,
        )
        assertTrue("Move mints the next free day", allOccurrenceIds().size > before.size)
    }

    @Test
    fun aSkippedDayCannotBeSkippedIntoADifferentDayAgain() = runBlocking {
        // SKIPPED and MOVED are settled too: only PLANNED and MISSED are still the user's to
        // decide, and Move is PLANNED-only because MISSED days belong to the weekly prompt.
        val occurrence = plannedMonday()
        deps.plannerRepository.skipOccurrence(occurrence.id)
        val before = allOccurrenceIds()

        deps.plannerRepository.moveOccurrenceForward(occurrence.id)

        assertEquals(before, allOccurrenceIds())
        assertEquals(
            OccurrenceStatus.SKIPPED,
            deps.plannerRepository.getOccurrence(occurrence.id)!!.status,
        )
    }

    /** One PLANNED Monday occurrence from a real rule, which is what the buttons act on. */
    private suspend fun plannedMonday(): com.sinura.personaltrainer.domain.ScheduleOccurrence {
        val routine = deps.routineRepository.create(name = "Push")
        deps.scheduleRepository.pin(routine.id, null, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1_700_000_000_000L)
        return deps.plannerRepository
            .occurrencesBetween(weekStart.epochDay, weekStart.epochDay)
            .single()
    }

    private suspend fun allOccurrenceIds(): Set<String> =
        deps.plannerRepository
            .occurrencesBetween(weekStart.epochDay - 7, weekStart.epochDay + 21)
            .map { it.id }
            .toSet()

    @Test
    fun setRuleEnabledStopsNextWeekGeneration() = runBlocking {
        val extra = deps.routineRepository.create(name = "Shoulder warm-up")
        val rule = deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = 17,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
            routineId = extra.id,
            templateId = com.sinura.personaltrainer.domain.ScheduleKind.aux("shoulder"),
        )
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1_700_000_000_000L)
        assertEquals(1, deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay).size)
        deps.plannerRepository.setRuleEnabled(rule.id, false)
        val nextWeek = weekStart.plusDays(7)
        val generated = deps.plannerRepository.ensureWeek(nextWeek, "UTC", 1_700_000_000_000L)
        assertTrue(generated.none { it.localEpochDay == nextWeek.epochDay && it.ruleId == rule.id })
    }
}
