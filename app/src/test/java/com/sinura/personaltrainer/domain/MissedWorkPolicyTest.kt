package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedWorkPolicyTest {
    private val weekStart = CivilDate(2026, 8, 17)
    private val wednesday = weekStart.plusDays(2).epochDay
    private val thursday = weekStart.plusDays(3).epochDay
    private val today = weekStart.plusDays(3) // Thursday
    private val zone = "UTC"

    @Test
    fun promptIsDedupedOnceADecisionExists() {
        val overdue = listOf(occ("o1", "r1", wednesday, OccurrenceStatus.PLANNED))
        assertTrue(MissedWorkPolicy.promptNeeded(overdue, null))
        assertFalse(
            MissedWorkPolicy.promptNeeded(
                overdue,
                MissedWorkDecision(weekStart.epochDay, MissedWorkChoice.KEEP_DATES, NOW),
            ),
        )
        assertFalse(MissedWorkPolicy.promptNeeded(emptyList(), null))
    }

    @Test
    fun keepDatesMarksOverdueMissedAndLeavesRulesUntouched() {
        val planned = occ("o1", "r1", wednesday, OccurrenceStatus.PLANNED)
        val future = occ("o2", "r1", weekStart.plusDays(5).epochDay, OccurrenceStatus.PLANNED)
        val result = MissedWorkPolicy.apply(
            MissedWorkChoice.KEEP_DATES,
            listOf(planned, future),
            weekStart,
            today.epochDay,
            nowMinutesOfDay = 12 * 60,
            nowMs = NOW,
            time = JvmTime,
            deviceZoneId = zone,
        )
        assertEquals(OccurrenceStatus.MISSED, result.occurrences.first { it.id == "o1" }.status)
        assertEquals(OccurrenceStatus.PLANNED, result.occurrences.first { it.id == "o2" }.status)
    }

    @Test
    fun skipMissedDoesNotMoveTheDay() {
        val planned = occ("o1", "r1", wednesday, OccurrenceStatus.PLANNED)
        val result = MissedWorkPolicy.apply(
            MissedWorkChoice.SKIP_MISSED,
            listOf(planned),
            weekStart,
            today.epochDay,
            12 * 60,
            NOW,
            JvmTime,
            zone,
        )
        assertEquals(OccurrenceStatus.SKIPPED, result.occurrences.single().status)
        assertEquals(wednesday, result.occurrences.single().localEpochDay)
    }

    @Test
    fun moveRemainingPushesToTheNextOpenDay() {
        val planned = occ("o1", "r1", wednesday, OccurrenceStatus.PLANNED)
        val result = MissedWorkPolicy.apply(
            MissedWorkChoice.MOVE_REMAINING,
            listOf(planned),
            weekStart,
            today.epochDay,
            12 * 60,
            NOW,
            JvmTime,
            zone,
            rules = listOf(rule("r1")),
        )
        val moved = result.occurrences.first { it.id == "o1" }
        val created = result.created.single()
        assertEquals(OccurrenceStatus.MOVED, moved.status)
        assertEquals(OccurrenceStatus.PLANNED, created.status)
        assertEquals(thursday, created.localEpochDay)
        assertEquals(wednesday, moved.localEpochDay)
    }

    @Test
    fun adaptWeekMarksMissedAndFillsMissingFutureRules() {
        val mondayRule = rule("r-mon", Weekday.MONDAY)
        val fridayRule = rule("r-fri", Weekday.FRIDAY)
        val overdue = occ("o-mon", "r-mon", weekStart.epochDay, OccurrenceStatus.PLANNED)
        val result = MissedWorkPolicy.apply(
            MissedWorkChoice.ADAPT_WEEK,
            listOf(overdue),
            weekStart,
            today.epochDay,
            12 * 60,
            NOW,
            JvmTime,
            zone,
            rules = listOf(mondayRule, fridayRule),
        )
        assertEquals(OccurrenceStatus.MISSED, result.occurrences.first { it.id == "o-mon" }.status)
        assertTrue(result.occurrences.any { it.ruleId == "r-fri" && it.status == OccurrenceStatus.PLANNED })
    }

    @Test
    fun todayBeforeScheduledTimeIsNotOverdue() {
        val evening = occ("o1", "r1", today.epochDay, OccurrenceStatus.PLANNED, hour = 18)
        assertTrue(MissedWorkPolicy.overdue(listOf(evening), today.epochDay, 12 * 60).isEmpty())
        assertEquals(1, MissedWorkPolicy.overdue(listOf(evening), today.epochDay, 19 * 60).size)
    }

    private fun rule(id: String, weekday: Weekday = Weekday.MONDAY) = ScheduleRule(
        id = id,
        weekday = weekday,
        hour = 18,
        minute = 0,
        modality = ScheduleModality.STRENGTH,
        createdAtMs = NOW,
        updatedAtMs = NOW,
    )

    private fun occ(
        id: String,
        ruleId: String,
        epochDay: Long,
        status: OccurrenceStatus,
        hour: Int = 18,
    ) = ScheduleOccurrence(
        id = id,
        ruleId = ruleId,
        status = status,
        captured = CapturedCivilTime(NOW, zone, 0, epochDay),
        hour = hour,
        minute = 0,
        createdAtMs = NOW,
        updatedAtMs = NOW,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
