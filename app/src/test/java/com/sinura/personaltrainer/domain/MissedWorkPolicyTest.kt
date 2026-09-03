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
    fun moveRemainingOntoAVacatedDayDoesNotReuseTheMovedId() {
        val planned = occ("o1", "r1", wednesday, OccurrenceStatus.PLANNED)
        val vacated = occ(
            OccurrenceGenerator.occurrenceId("r1", thursday),
            "r1",
            thursday,
            OccurrenceStatus.MOVED,
        )
        val result = MissedWorkPolicy.apply(
            MissedWorkChoice.MOVE_REMAINING,
            listOf(planned, vacated),
            weekStart,
            today.epochDay,
            12 * 60,
            NOW,
            JvmTime,
            zone,
            rules = listOf(rule("r1")),
        )
        val created = result.created.single()
        assertTrue(created.id != vacated.id)
        assertEquals(thursday, created.localEpochDay)
        assertEquals(OccurrenceStatus.MOVED, result.occurrences.first { it.id == vacated.id }.status)
        assertEquals(OccurrenceStatus.MOVED, result.occurrences.first { it.id == "o1" }.status)
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
    fun adaptWeekRegeneratesRemainingDaysFromTheRulesAsTheyStandNow() {
        // The Friday rule moved to 07:00 after the week was generated. Keep
        // the dates leaves the stale 18:00 row; Adapt re-derives it. This is
        // the difference that made the two choices identical before.
        val fridayRule = rule("r-fri", Weekday.FRIDAY).copy(hour = 7)
        val friday = weekStart.plusDays(4).epochDay
        val stale = occ(
            OccurrenceGenerator.occurrenceId("r-fri", friday),
            "r-fri",
            friday,
            OccurrenceStatus.PLANNED,
            hour = 18,
        )
        val result = MissedWorkPolicy.apply(
            MissedWorkChoice.ADAPT_WEEK,
            listOf(stale),
            weekStart,
            today.epochDay,
            12 * 60,
            NOW,
            JvmTime,
            zone,
            rules = listOf(fridayRule),
        )
        val regenerated = result.occurrences.single { it.ruleId == "r-fri" }
        assertEquals(7, regenerated.hour)
        assertEquals(friday, regenerated.localEpochDay)
        assertEquals(OccurrenceStatus.PLANNED, regenerated.status)
    }

    @Test
    fun adaptWeekRetiresARemovedRulesFutureDaysAndKeepsHistory() {
        val keptRule = rule("r-fri", Weekday.FRIDAY)
        val friday = weekStart.plusDays(4).epochDay
        val doneWed = occ("o-done", "r-gone", wednesday, OccurrenceStatus.DONE)
        val orphanFriday = occ("o-orphan", "r-gone", friday, OccurrenceStatus.PLANNED)
        val keptFriday = occ(
            OccurrenceGenerator.occurrenceId("r-fri", friday),
            "r-fri",
            friday,
            OccurrenceStatus.PLANNED,
        )
        val result = MissedWorkPolicy.apply(
            MissedWorkChoice.ADAPT_WEEK,
            listOf(doneWed, orphanFriday, keptFriday),
            weekStart,
            today.epochDay,
            12 * 60,
            NOW,
            JvmTime,
            zone,
            rules = listOf(keptRule),
        )
        // Finished history is never adapted away.
        assertEquals(OccurrenceStatus.DONE, result.occurrences.single { it.id == "o-done" }.status)
        assertTrue(result.occurrences.any { it.ruleId == "r-fri" })
        // A gone rule's leftover PLANNED row is a one-off, not regenerable.
        assertTrue(result.occurrences.any { it.id == "o-orphan" })
        assertTrue(result.removed.isEmpty())
    }

    @Test
    fun adaptWeekKeepsADisabledOnceRuleRow() {
        val friday = weekStart.plusDays(4).epochDay
        val onceRule = rule("r-once", Weekday.FRIDAY).copy(enabled = false)
        val once = occ(
            OccurrenceGenerator.occurrenceId("r-once", friday),
            "r-once",
            friday,
            OccurrenceStatus.PLANNED,
        )
        val result = MissedWorkPolicy.apply(
            MissedWorkChoice.ADAPT_WEEK,
            listOf(once),
            weekStart,
            today.epochDay,
            12 * 60,
            NOW,
            JvmTime,
            zone,
            rules = listOf(onceRule),
        )
        assertEquals(once.id, result.occurrences.single().id)
        assertTrue(result.removed.isEmpty())
    }

    @Test
    fun adaptWeekKeepsARelocatedId() {
        val friday = weekStart.plusDays(4).epochDay
        val fridayRule = rule("r-fri", Weekday.FRIDAY)
        val relocatedId = OccurrenceGenerator.unusedOccurrenceId(
            "r-fri",
            friday,
            setOf(OccurrenceGenerator.occurrenceId("r-fri", friday)),
            wednesday,
        )
        val relocated = occ(relocatedId, "r-fri", friday, OccurrenceStatus.PLANNED)
        val result = MissedWorkPolicy.apply(
            MissedWorkChoice.ADAPT_WEEK,
            listOf(relocated),
            weekStart,
            today.epochDay,
            12 * 60,
            NOW,
            JvmTime,
            zone,
            rules = listOf(fridayRule),
        )
        assertTrue(result.occurrences.any { it.id == relocatedId })
        assertTrue(result.removed.none { it == relocatedId })
    }

    @Test
    fun adaptWeekDoesNotMintInstantlyOverdueRowsBehindToday() {
        // A rule added mid-week whose weekday already passed must not
        // generate a row behind today that immediately re-raises the prompt.
        val mondayRule = rule("r-mon", Weekday.MONDAY)
        val result = MissedWorkPolicy.apply(
            MissedWorkChoice.ADAPT_WEEK,
            emptyList(),
            weekStart,
            today.epochDay,
            12 * 60,
            NOW,
            JvmTime,
            zone,
            rules = listOf(mondayRule),
        )
        assertTrue(result.occurrences.isEmpty())
        assertTrue(result.created.isEmpty())
    }

    @Test
    fun eveningSessionIsNotOverdueUntilTomorrow() {
        val evening = occ("o1", "r1", today.epochDay, OccurrenceStatus.PLANNED, hour = 19)
        // 19:01 same civil day — still tonight.
        assertTrue(MissedWorkPolicy.overdue(listOf(evening), today.epochDay).isEmpty())
        assertEquals(1, MissedWorkPolicy.overdue(listOf(evening), today.plusDays(1).epochDay).size)
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
