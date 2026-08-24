package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupV5FormatTest {
    @Test
    fun decodesV4DocumentWithEmptyGoalsArray() {
        val parsed = BackupJson.decode(
            """{"version": 4, "app": "personal-trainer", "preferences": {"weightUnit": "kg"}}""",
        )
        assertEquals(4, parsed.version)
        assertEquals(emptyList<BackupMeasurableGoal>(), parsed.measurableGoals)
    }

    @Test
    fun encodeDecodeRoundTripsMeasurableGoals() {
        val original = BackupDocument(
            version = 5,
            exportedAt = "2026-08-24T12:00:00Z",
            preferences = BackupPreferences(weightUnit = "kg"),
            exercises = emptyList(),
            routines = emptyList(),
            routineExercises = emptyList(),
            sessions = emptyList(),
            sessionExercises = emptyList(),
            setLogs = emptyList(),
            measurableGoals = listOf(
                BackupMeasurableGoal(
                    id = "goal-1",
                    kind = "SESSION_COUNT",
                    targetValue = 4.0,
                    period = "WEEK",
                    instantMs = 1_700_000_000_000L,
                    zoneId = "UTC",
                    offsetSeconds = 0,
                    localEpochDay = 20_000L,
                    paused = false,
                    createdAtMs = 1_700_000_000_000L,
                    updatedAtMs = 1_700_000_000_000L,
                ),
            ),
        )
        val parsed = BackupJson.decode(BackupJson.encode(original))
        assertEquals(5, parsed.version)
        assertEquals(original.measurableGoals, parsed.measurableGoals)
        assertTrue(BackupJson.encode(parsed).contains("\"measurableGoals\""))
    }
}
