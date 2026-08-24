package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {
    private val occurrence = ScheduleOccurrence(
        id = "occ-1",
        ruleId = "r1",
        status = OccurrenceStatus.PLANNED,
        captured = CapturedCivilTime(1_000L, "UTC", 0, 20_000L),
        hour = 18,
        minute = 0,
        createdAtMs = 1L,
        updatedAtMs = 1L,
    )
    private val pending = ReminderDelivery(
        id = "rem-1",
        occurrenceId = "occ-1",
        scheduledAtMs = 500L,
        status = ReminderDeliveryStatus.PENDING,
        createdAtMs = 1L,
        updatedAtMs = 1L,
    )

    @Test
    fun optOutSkips() {
        assertEquals(
            ReminderDecision.SKIP,
            ReminderPolicy.decide(pending, occurrence, 1_000L, ReminderPreferences(optOut = true), 12 * 60),
        )
    }

    @Test
    fun completedOccurrenceIsStale() {
        assertEquals(
            ReminderDecision.STALE,
            ReminderPolicy.decide(
                pending,
                occurrence.copy(status = OccurrenceStatus.DONE),
                1_000L,
                ReminderPreferences.DEFAULT,
                12 * 60,
            ),
        )
    }

    @Test
    fun quietHoursDeferOvernightWindow() {
        assertTrue(ReminderPolicy.inQuietHours(23 * 60, 22, 7))
        assertTrue(ReminderPolicy.inQuietHours(6 * 60, 22, 7))
        assertFalse(ReminderPolicy.inQuietHours(12 * 60, 22, 7))
        assertEquals(
            ReminderDecision.DEFER_QUIET,
            ReminderPolicy.decide(pending, occurrence, 1_000L, ReminderPreferences.DEFAULT, 23 * 60),
        )
    }

    @Test
    fun tooEarlyWhenNowIsBeforeScheduled() {
        assertEquals(
            ReminderDecision.TOO_EARLY,
            ReminderPolicy.decide(
                pending.copy(scheduledAtMs = 2_000L),
                occurrence,
                1_000L,
                ReminderPreferences.DEFAULT,
                12 * 60,
            ),
        )
    }

    @Test
    fun deliversOutsideQuietHours() {
        assertEquals(
            ReminderDecision.DELIVER,
            ReminderPolicy.decide(pending, occurrence, 1_000L, ReminderPreferences.DEFAULT, 12 * 60),
        )
    }

    @Test
    fun rebuildMarksPastDueStaleAndKeepsFuture() {
        assertEquals(
            ReminderRebuildAction.MARK_STALE,
            ReminderPolicy.rebuildAction(pending.copy(scheduledAtMs = 100L), nowMs = 1_000L),
        )
        assertEquals(
            ReminderRebuildAction.RESCHEDULE,
            ReminderPolicy.rebuildAction(pending.copy(scheduledAtMs = 2_000L), nowMs = 1_000L),
        )
        assertEquals(
            ReminderRebuildAction.IGNORE,
            ReminderPolicy.rebuildAction(
                pending.copy(status = ReminderDeliveryStatus.DELIVERED),
                nowMs = 1_000L,
            ),
        )
    }

    @Test
    fun scheduledAtSubtractsOffset() {
        assertEquals(1_000L - 15 * 60_000L, ReminderPolicy.scheduledAtMillis(occurrence.copy(
            captured = occurrence.captured.copy(instantMillis = 1_000L),
        ), 15))
    }

    @Test
    fun minutesUntilQuietEndWraps() {
        assertEquals(60, ReminderPolicy.minutesUntilQuietEnd(6 * 60, 7))
        assertTrue(ReminderPolicy.minutesUntilQuietEnd(23 * 60, 7) > 60)
    }

    @Test
    fun sameStartAndEndIsNeverQuiet() {
        assertFalse(ReminderPolicy.inQuietHours(3 * 60, 8, 8))
    }
}
