package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveToTodayTest {
    private val friday = 20_000L
    private val saturday = friday + 1
    private val zone = "UTC"

    @Test
    fun leftoverPlannedRelocatesOntoToday() {
        val current = occ("old", "rule-fri", friday, OccurrenceStatus.PLANNED)
        val outcome = MoveToToday.decide(
            current = current,
            todayEpochDay = saturday,
            existingOnToday = emptyList(),
            rule = rule("rule-fri"),
            nowMs = NOW,
            time = JvmTime,
            deviceZoneId = zone,
        )
        val relocate = outcome as MoveToToday.Outcome.Relocate
        assertEquals(OccurrenceStatus.MOVED, relocate.vacated.status)
        assertEquals(friday, relocate.vacated.localEpochDay)
        assertEquals(OccurrenceStatus.PLANNED, relocate.created.status)
        assertEquals(saturday, relocate.created.localEpochDay)
        assertEquals(18, relocate.created.hour)
        assertEquals(OccurrenceGenerator.occurrenceId("rule-fri", saturday), relocate.created.id)
    }

    @Test
    fun missedLeftoverAlsoMoves() {
        val current = occ("old", "rule-fri", friday, OccurrenceStatus.MISSED)
        val outcome = MoveToToday.decide(
            current,
            saturday,
            emptyList(),
            rule("rule-fri"),
            NOW,
            JvmTime,
            zone,
        )
        assertTrue(outcome is MoveToToday.Outcome.Relocate)
    }

    @Test
    fun alreadyPlannedOnTodayIsAlreadyThere() {
        val current = occ("old", "rule-fri", friday, OccurrenceStatus.PLANNED)
        val here = occ(
            OccurrenceGenerator.occurrenceId("rule-fri", saturday),
            "rule-fri",
            saturday,
            OccurrenceStatus.PLANNED,
        )
        val outcome = MoveToToday.decide(
            current,
            saturday,
            listOf(here),
            rule("rule-fri"),
            NOW,
            JvmTime,
            zone,
        )
        assertEquals(here.id, (outcome as MoveToToday.Outcome.AlreadyThere).occurrence.id)
    }

    @Test
    fun doneOnTodayBlocksTheMove() {
        val current = occ("old", "rule-fri", friday, OccurrenceStatus.PLANNED)
        val done = occ("done", "rule-fri", saturday, OccurrenceStatus.DONE)
        val outcome = MoveToToday.decide(
            current,
            saturday,
            listOf(done),
            rule("rule-fri"),
            NOW,
            JvmTime,
            zone,
        )
        assertEquals(
            MoveToToday.ALREADY_HERE,
            (outcome as MoveToToday.Outcome.Blocked).message,
        )
    }

    @Test
    fun skippedIsNotMovable() {
        val current = occ("old", "rule-fri", friday, OccurrenceStatus.SKIPPED)
        val outcome = MoveToToday.decide(
            current,
            saturday,
            emptyList(),
            rule("rule-fri"),
            NOW,
            JvmTime,
            zone,
        )
        assertEquals(
            MoveToToday.NOT_MOVABLE,
            (outcome as MoveToToday.Outcome.Blocked).message,
        )
    }

    @Test
    fun sameDayPlannedIsAlreadyThere() {
        val current = occ("today", "rule-sat", saturday, OccurrenceStatus.PLANNED)
        val outcome = MoveToToday.decide(
            current,
            saturday,
            listOf(current),
            rule("rule-sat"),
            NOW,
            JvmTime,
            zone,
        )
        assertEquals(current.id, (outcome as MoveToToday.Outcome.AlreadyThere).occurrence.id)
    }

    @Test
    fun leftoverNoteNamesTheWeekday() {
        assertEquals(
            "This was Friday. Starting it today moves it here.",
            MoveToToday.leftoverNote(Weekday.FRIDAY),
        )
    }

    @Test
    fun relocatingOntoADayWhoseCanonicalIdIsMovedKeepsBothRows() {
        val canonical = OccurrenceGenerator.occurrenceId("rule-fri", saturday)
        val vacatedSaturday = occ(canonical, "rule-fri", saturday, OccurrenceStatus.MOVED)
        val leftover = occ("thu", "rule-fri", friday - 1, OccurrenceStatus.PLANNED)
        val outcome = MoveToToday.decide(
            leftover,
            saturday,
            listOf(vacatedSaturday),
            rule("rule-fri"),
            NOW,
            JvmTime,
            zone,
        )
        val relocate = outcome as MoveToToday.Outcome.Relocate
        assertEquals(canonical, vacatedSaturday.id)
        assertTrue(relocate.created.id != canonical)
        assertEquals(saturday, relocate.created.localEpochDay)
        assertEquals(OccurrenceStatus.PLANNED, relocate.created.status)
        assertEquals(OccurrenceStatus.MOVED, relocate.vacated.status)
        assertEquals(friday - 1, relocate.vacated.localEpochDay)
    }

    @Test
    fun isLeftoverIgnoresTodayAndDone() {
        val plannedFri = occ("a", "r", friday, OccurrenceStatus.PLANNED)
        val plannedSat = occ("b", "r", saturday, OccurrenceStatus.PLANNED)
        val doneFri = occ("c", "r", friday, OccurrenceStatus.DONE)
        assertTrue(MoveToToday.isLeftover(plannedFri, saturday))
        assertTrue(!MoveToToday.isLeftover(plannedSat, saturday))
        assertTrue(!MoveToToday.isLeftover(doneFri, saturday))
    }

    private fun rule(id: String) = ScheduleRule(
        id = id,
        weekday = Weekday.FRIDAY,
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
    ) = ScheduleOccurrence(
        id = id,
        ruleId = ruleId,
        status = status,
        captured = CapturedCivilTime(NOW, zone, 0, epochDay),
        hour = 18,
        minute = 0,
        createdAtMs = NOW,
        updatedAtMs = NOW,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
