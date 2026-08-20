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
        // Whole-document equality on purpose. The old assertions checked every collection
        // EXCEPT sessionExercises — the join table whose loss would restore every workout
        // with no exercises in it — and only one of seven preference fields.
        val original = sampleDocument()
        val parsed = BackupJson.decode(BackupJson.encode(original))
        // encode() sorts every collection by id for deterministic files, so compare against
        // the same normalisation rather than the order the caller happened to build.
        assertEquals(sortedForComparison(original), sortedForComparison(parsed))
        assertEquals(original.sessionExercises, parsed.sessionExercises)
        assertEquals(original.preferences, parsed.preferences)
        assertEquals(original.exportedAt, parsed.exportedAt)
    }

    @Test
    fun corruptInputFailsFriendlyAndNeverLeaksParserText() {
        val corrupt = listOf(
            "" to "not a",
            "   " to "not a",
            "not json at all" to "not a",
            "\"a bare string\"" to "not a",
            "12345" to "not a",
            "[]" to "not a",
            "{" to "not a",
            // Truncated mid-download: valid prefix, no closing brace.
            "{\"version\": 1, \"app\": \"personal-trainer\", \"exercises\": [{\"id\": \"e" to "not a",
            // An HTML error page where JSON was expected.
            "<!DOCTYPE html><html><body>502 Bad Gateway</body></html>" to "not a",
            // Well-formed JSON, no version.
            "{\"app\": \"personal-trainer\"}" to "not a",
            // Wrong app entirely.
            "{\"version\": 1, \"app\": \"some-other-app\"}" to "not a",
        )
        corrupt.forEach { (json, fragment) ->
            try {
                BackupJson.decode(json)
                throw AssertionError("expected BackupException for: $json")
            } catch (error: BackupException) {
                val message = error.message.orEmpty()
                assertTrue("message was: $message", message.contains(fragment, ignoreCase = true))
                assertNoParserLeakage(message)
            }
        }
    }

    @Test
    fun wrongFieldTypesFailFriendlyRatherThanCrashing() {
        // "exercises" as an object, not an array; a numeric field holding a string.
        val json = """{"version": 1, "app": "personal-trainer", "exercises": {"id": "x"},
            "setLogs": [{"id": "s", "sessionId": "a", "exerciseId": "b", "setNumber": "many",
            "weightKg": 1.0, "reps": 1, "isWarmup": false, "completedAt": 1}]}"""
        try {
            BackupJson.decode(json)
            throw AssertionError("expected BackupException")
        } catch (error: BackupException) {
            assertNoParserLeakage(error.message.orEmpty())
        }
    }

    @Test
    fun aVersionOnlyDocumentDecodesToEmptyListsSoValidationMustCatchIt() {
        // Documents this thin are exactly why decoding is not enough on its own.
        val parsed = BackupJson.decode("""{"version": 1}""")
        assertTrue(parsed.exercises.isEmpty())
        assertTrue(parsed.sessions.isEmpty())
        assertTrue(parsed.setLogs.isEmpty())
        assertTrue(parsed.sessionExercises.isEmpty())
    }

    private fun assertNoParserLeakage(message: String) {
        listOf("Exception", "com.google.gson", "java.lang", "BEGIN_OBJECT", "at line", "$").forEach {
            assertTrue("leaked parser text ($it): $message", !message.contains(it))
        }
    }

    @Test
    fun sortsRecordsForDeterministicOutput() {
        // Same records, different input order, byte-identical output — that is what the
        // sorting in encode() is for. (The previous version of this test swapped ids between
        // two differently named exercises and asserted the output matched, which it never
        // could; it had been failing unnoticed because nothing runs the suite automatically.)
        val document = sampleDocument()
        val shuffled = document.copy(
            exercises = document.exercises.reversed(),
            routines = document.routines.reversed(),
            routineExercises = document.routineExercises.reversed(),
            sessions = document.sessions.reversed(),
            sessionExercises = document.sessionExercises.reversed(),
            setLogs = document.setLogs.reversed(),
        )
        assertEquals(BackupJson.encode(document), BackupJson.encode(shuffled))
    }

    private fun sortedForComparison(document: BackupDocument) = document.copy(
        exercises = document.exercises.sortedBy { it.id },
        routines = document.routines.sortedBy { it.id },
        routineExercises = document.routineExercises.sortedBy { it.id },
        sessions = document.sessions.sortedBy { it.id },
        sessionExercises = document.sessionExercises.sortedBy { it.id },
        setLogs = document.setLogs.sortedBy { it.id },
    )

    @Test
    fun missingSchedulePreferencesUseDefaults() {
        val parsed = BackupJson.decode(
            """{"version": 1, "app": "personal-trainer", "preferences": {"weightUnit": "kg"}}""",
        )
        assertEquals("kg", parsed.preferences.weightUnit)
        assertEquals(4, parsed.preferences.trainingDaysPerWeek)
        assertEquals("auto", parsed.preferences.splitStyle)
        assertEquals("MONDAY", parsed.preferences.weekStart)
        assertEquals(true, parsed.preferences.restSoundEnabled)
        assertEquals(true, parsed.preferences.restVibrationEnabled)
        assertEquals(90, parsed.preferences.defaultRestSeconds)
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
                BackupSetLog("set2", "s1", exerciseId, 2, 82.5, 4, 9, false, 16L),
            ),
        )
    }
}
