package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupJsonTest {
    @Test
    fun encodesVersionedHumanReadableJson() {
        val json = BackupJson.encode(sampleDocument())
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\"app\": \"personal-trainer\""))
        assertTrue(json.contains("\"weightUnit\": \"lbs\""))
        assertTrue(json.contains("Barbell Back Squat"))
        assertTrue(json.startsWith("{"))
        assertTrue(json.contains("\n  "))
    }

    @Test
    fun roundTripsACompleteBackup() {
        val original = sampleDocument()
        val parsed = BackupJson.decode(BackupJson.encode(original))
        assertEquals(original.version, parsed.version)
        assertEquals(original.preferences.weightUnit, parsed.preferences.weightUnit)
        assertEquals(original.exercises, parsed.exercises)
        assertEquals(original.routines, parsed.routines)
        assertEquals(original.routineExercises, parsed.routineExercises)
        assertEquals(original.sessions, parsed.sessions)
        assertEquals(original.setLogs, parsed.setLogs)
    }

    @Test
    fun sortsRecordsForDeterministicOutput() {
        val first = BackupJson.encode(sampleDocument(exerciseId = "ex-b", otherExerciseId = "ex-a"))
        val second = BackupJson.encode(sampleDocument(exerciseId = "ex-a", otherExerciseId = "ex-b"))
        assertEquals(first.substringAfter("\"exercises\""), second.substringAfter("\"exercises\""))
    }

    @Test
    fun rejectsNewerVersions() {
        try {
            BackupJson.decode("""{"version": 99, "app": "personal-trainer", "preferences": {"weightUnit": "kg"}}""")
            throw AssertionError("expected BackupException")
        } catch (error: BackupException) {
            assertTrue(error.message!!.contains("newer"))
        }
    }

    @Test
    fun fileNameIncludesLocalDateAndTime() {
        val name = BackupJson.fileName(Instant.parse("2026-08-18T16:45:00Z"))
        assertTrue(name.startsWith("personal-trainer-backup-"))
        assertTrue(name.endsWith(".json"))
        assertTrue(name.contains("-2026-") || name.contains("-2026"))
    }

    private fun sampleDocument(
        exerciseId: String = "ex-squat",
        otherExerciseId: String = "ex-bench",
    ): BackupDocument {
        return BackupDocument(
            exportedAt = "2026-08-18T16:45:00Z",
            preferences = BackupPreferences(weightUnit = "lbs"),
            exercises = listOf(
                BackupExercise(exerciseId, "Barbell Back Squat", "Quads", "", false),
                BackupExercise(otherExerciseId, "Bench Press", "Chest", "paused", true),
            ),
            routines = listOf(
                BackupRoutine("r1", "Push", "", 1L, 2L),
            ),
            routineExercises = listOf(
                BackupRoutineExercise("re1", "r1", exerciseId, 0, 3, 5, 80.0, 90),
            ),
            sessions = listOf(
                BackupSession("s1", "r1", "Push", 10L, "", 40, 10L, 20L),
            ),
            sessionExercises = listOf(
                BackupSessionExercise("se1", "s1", exerciseId, 0, 3, 5, 80.0, 90),
            ),
            setLogs = listOf(
                BackupSetLog("set1", "s1", exerciseId, 1, 80.0, 5, 8, false, 15L),
            ),
        )
    }
}
