package com.sinura.personaltrainer.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.DayBlockOrder
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MissedWorkPreviewTest {
    private val week = CivilDate(2026, 8, 17)
    private val at = JvmTime.startOfDayMillis(week.plusDays(2), "UTC") + 12 * 60 * 60_000
    private lateinit var deps: FakeAppDependencies
    @Before fun setup() { deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), time = FrozenTime(at, "UTC")) }
    @After fun close() { deps.close() }

    private suspend fun seed() {
        addRule(Weekday.MONDAY, 18, ScheduleModality.CARDIO)
        addRule(Weekday.THURSDAY, 7, ScheduleModality.CARDIO)
        deps.plannerRepository.ensureWeek(week, "UTC", at - 1)
    }
    private suspend fun addRule(day: Weekday, hour: Int, modality: ScheduleModality) = deps.plannerRepository.addTimedRule(
        weekday = day, hour = hour, minute = 0, modality = modality,
        nowMs = JvmTime.startOfDayMillis(week, "UTC") - 1)
    private suspend fun preview(choice: MissedWorkChoice) = deps.plannerRepository.previewMissedWork(
        choice, week, week.plusDays(2).epochDay, 12 * 60, "UTC", at)
    private suspend fun rows() = deps.plannerRepository.occurrencesBetween(week.epochDay, week.epochDay + 6).sortedBy { it.id }

    @Test fun previewDoesNotWriteAndCommitMatchesItsExactRowsAndDecision() = runBlocking {
        seed()
        val before = rows()
        val rules = deps.plannerRepository.rules()
        val proposal = preview(MissedWorkChoice.MOVE_REMAINING)
        assertEquals(before, rows())
        assertNull(deps.plannerRepository.decisionFor(week.epochDay))
        assertEquals(DraftRecoveryOutcome.UNCOMMITTED, deps.plannerRepository.inspectMissedWork(proposal))
        deps.plannerRepository.commitMissedWorkLocked(proposal)
        assertEquals(proposal.occurrencesAfter, rows())
        assertEquals(proposal.decision, deps.plannerRepository.decisionFor(week.epochDay))
        assertEquals(rules, deps.plannerRepository.rules())
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, deps.plannerRepository.inspectMissedWork(proposal))
        assertTrue(runCatching { deps.plannerRepository.commitMissedWorkLocked(proposal) }.isFailure)
        assertEquals(proposal.occurrencesAfter, rows())
    }

    @Test fun allFourPolicyPreviewsPreserveACompletedOccurrenceAndWeeklyRules() = runBlocking {
        for (choice in MissedWorkChoice.entries) {
            // Isolate each policy under its own fresh database, rather than replay a decided week.
            if (choice != MissedWorkChoice.entries.first()) { deps.close(); setup() }
            seed()
            val thursday = rows().single { it.localEpochDay == week.plusDays(3).epochDay }
            deps.plannerRepository.markOccurrenceDone(thursday.id, "synthetic-completed")
            val completed = deps.plannerRepository.getOccurrence(thursday.id)
            val proposal = preview(choice)
            deps.plannerRepository.commitMissedWorkLocked(proposal)
            assertEquals(proposal.occurrencesAfter, rows())
            assertEquals(completed, deps.plannerRepository.getOccurrence(thursday.id))
            assertEquals(proposal.rulesBefore, deps.plannerRepository.rules().sortedBy { it.id })
        }
    }

    @Test fun changingARuleOrCompletingAPlannedRowRefusesTheOldPreview() = runBlocking {
        seed()
        val proposal = preview(MissedWorkChoice.MOVE_REMAINING)
        val rule = deps.plannerRepository.rules().first()
        deps.plannerRepository.upsertRule(rule.copy(reminderOffsetMinutes = 30, updatedAtMs = at + 1))
        assertEquals(DraftRecoveryOutcome.CONFLICT, deps.plannerRepository.inspectMissedWork(proposal))
        assertTrue(runCatching { deps.plannerRepository.commitMissedWorkLocked(proposal) }.isFailure)
        assertEquals(proposal.occurrencesBefore, rows())
        assertNull(deps.plannerRepository.decisionFor(week.epochDay))
        val fresh = preview(MissedWorkChoice.MOVE_REMAINING)
        val planned = rows().last()
        deps.plannerRepository.markOccurrenceDone(planned.id, "synthetic-completed")
        val changed = rows()
        assertTrue(runCatching { deps.plannerRepository.commitMissedWorkLocked(fresh) }.isFailure)
        assertEquals(changed, rows())
    }

    @Test fun sundayMoveRemainsInsideThisWeekAndNextWeekUsesTheUnchangedRule() = runBlocking {
        seed()
        val sunday = deps.plannerRepository.previewMissedWork(MissedWorkChoice.MOVE_REMAINING, week,
            week.plusDays(6).epochDay, 12 * 60, "UTC", at + 4 * 24 * 60 * 60_000L)
        deps.plannerRepository.commitMissedWorkLocked(sunday)
        assertEquals(sunday.occurrencesAfter, rows())
        assertTrue(rows().all { it.localEpochDay <= week.epochDay + 6 })
        val next = deps.plannerRepository.ensureWeek(week.plusDays(7), "UTC", at + 5 * 24 * 60 * 60_000L)
        assertEquals(setOf(week.epochDay + 7, week.epochDay + 10), next.map { it.localEpochDay }.toSet())
    }

    @Test fun orderingKeepsCompletedRowsExactAndUpdatesAlreadyGeneratedFutureRows() = runBlocking {
        val a = addRule(Weekday.MONDAY, 7, ScheduleModality.CARDIO)
        val b = addRule(Weekday.MONDAY, 18, ScheduleModality.STRENGTH)
        deps.plannerRepository.ensureWeek(week, "UTC", at - 1)
        deps.plannerRepository.ensureWeek(week.plusDays(7), "UTC", at - 1)
        val current = rows().filter { it.localEpochDay == week.epochDay }
        val done = current.single { it.ruleId == a.id }
        val other = current.single { it.ruleId == b.id }
        deps.plannerRepository.markOccurrenceDone(done.id, "synthetic-completed")
        val captured = deps.plannerRepository.getOccurrence(done.id)
        deps.plannerRepository.applyDayOrder(listOf(DayBlockOrder.HourMove(done.id, a.id, 18), DayBlockOrder.HourMove(other.id, b.id, 7)))
        assertEquals(captured, deps.plannerRepository.getOccurrence(done.id))
        val next = deps.plannerRepository.occurrencesBetween(week.epochDay + 7, week.epochDay + 13)
        assertEquals(18, next.single { it.ruleId == a.id }.hour)
        assertEquals(7, next.single { it.ruleId == b.id }.hour)
        assertEquals(7, deps.plannerRepository.getOccurrence(other.id)!!.hour)
    }

    @Test fun fabricatedAfterRowsCannotReachBusinessWrites() = runBlocking {
        seed()
        val proposal = preview(MissedWorkChoice.MOVE_REMAINING)
        val fabricated = proposal.copy(occurrencesAfter = proposal.occurrencesAfter.map { it.copy(hour = 1) })
        assertTrue(runCatching { deps.plannerRepository.commitMissedWorkLocked(fabricated) }.isFailure)
        assertEquals(proposal.occurrencesBefore, rows())
        assertNull(deps.plannerRepository.decisionFor(week.epochDay))
    }

    @Test fun equalTimeReorderPersistsRequestedOrderForThisAndAlreadyGeneratedNextWeek() = runBlocking {
        val a = addRule(Weekday.THURSDAY, 18, ScheduleModality.STRENGTH)
        val b = addRule(Weekday.THURSDAY, 18, ScheduleModality.CARDIO)
        deps.plannerRepository.ensureWeek(week, "UTC", at - 1)
        deps.plannerRepository.ensureWeek(week.plusDays(7), "UTC", at - 1)
        val day = week.plusDays(3).epochDay
        val before = deps.plannerRepository.occurrencesBetween(day, day)
        val desired = before.reversed()
        deps.plannerRepository.applyDayOrder(desired.map { DayBlockOrder.HourMove(it.id, it.ruleId, it.hour) })
        assertEquals(desired.map { it.id }, deps.plannerRepository.occurrencesBetween(day, day).map { it.id })
        assertEquals(listOf(1080, 1081), deps.plannerRepository.occurrencesBetween(day, day).map { it.minutesOfDay })
        val next = deps.plannerRepository.occurrencesBetween(day + 7, day + 7)
        assertEquals(desired.map { it.ruleId }, next.map { it.ruleId })
        assertEquals(listOf(1080, 1081), next.map { it.minutesOfDay })
        val rules = listOf(a.id, b.id).map { deps.plannerRepository.getRule(it)!! }
        assertEquals(desired.map { it.ruleId }, rules.sortedBy { it.minutesOfDay }.map { it.id })
        next.forEach { assertEquals(it.hour * 60 + it.minute,
            JvmTime.wallMinutesOfDay(it.captured.instantMillis, it.captured.zoneId)) }
    }
}
