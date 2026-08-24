package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupV4FormatTest {
    @Test
    fun decodesV3DocumentWithEmptyPlannerArrays() {
        val parsed = BackupJson.decode(
            """{"version": 3, "app": "personal-trainer", "preferences": {"weightUnit": "kg"}}""",
        )
        assertEquals(3, parsed.version)
        assertEquals(emptyList<BackupScheduleRule>(), parsed.scheduleRules)
        assertEquals(emptyList<BackupScheduleOccurrence>(), parsed.scheduleOccurrences)
        assertEquals(emptyList<BackupMissedWorkDecision>(), parsed.missedWorkDecisions)
        assertEquals(emptyList<BackupReminderDelivery>(), parsed.reminderDeliveries)
        assertEquals(false, parsed.preferences.reminderOptOut)
        assertEquals(22, parsed.preferences.reminderQuietStartHour)
        assertEquals(7, parsed.preferences.reminderQuietEndHour)
    }

    @Test
    fun encodeDecodeRoundTripsPlannerTables() {
        val original = BackupDocument(
            version = 4,
            exportedAt = "2026-08-24T12:00:00Z",
            preferences = BackupPreferences(
                weightUnit = "kg",
                reminderOptOut = true,
                reminderQuietStartHour = 21,
                reminderQuietEndHour = 6,
            ),
            exercises = emptyList(),
            routines = emptyList(),
            routineExercises = emptyList(),
            sessions = emptyList(),
            sessionExercises = emptyList(),
            setLogs = emptyList(),
            scheduleRules = listOf(
                BackupScheduleRule(
                    id = "rule-1",
                    weekday = 1,
                    hour = 18,
                    minute = 0,
                    modality = "STRENGTH",
                    createdAtMs = 1_700_000_000_000L,
                    updatedAtMs = 1_700_000_000_000L,
                ),
            ),
            scheduleOccurrences = listOf(
                BackupScheduleOccurrence(
                    id = "occ-1",
                    ruleId = "rule-1",
                    status = "PLANNED",
                    instantMs = 1_700_000_000_000L,
                    zoneId = "UTC",
                    offsetSeconds = 0,
                    localEpochDay = 20_000L,
                    hour = 18,
                    minute = 0,
                    createdAtMs = 1_700_000_000_000L,
                    updatedAtMs = 1_700_000_000_000L,
                ),
            ),
        )
        val parsed = BackupJson.decode(BackupJson.encode(original))
        assertEquals(4, parsed.version)
        assertEquals(original.scheduleRules, parsed.scheduleRules)
        assertEquals(original.scheduleOccurrences, parsed.scheduleOccurrences)
        assertTrue(parsed.preferences.reminderOptOut)
        assertEquals(21, parsed.preferences.reminderQuietStartHour)
    }
}
