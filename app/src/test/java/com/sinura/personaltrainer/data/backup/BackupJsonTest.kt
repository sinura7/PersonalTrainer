package com.sinura.personaltrainer.data.backup

import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupJsonTest {
    @Test
    fun encodesVersionedHumanReadableJson() {
        val json = BackupJson.encode(sampleDocument())
        assertTrue(json.contains("\"version\": 2"))
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
            exerciseMuscles = document.exerciseMuscles.reversed(),
            scheduleSlots = document.scheduleSlots.reversed(),
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
        exerciseMuscles = document.exerciseMuscles
            .sortedWith(compareBy({ it.exerciseId }, { it.muscleKey })),
        scheduleSlots = document.scheduleSlots.sortedWith(compareBy({ it.position }, { it.id })),
    )

    @Test
    fun missingWeightUnitDefaultsToPounds() {
        val parsed = BackupJson.decode(
            """{"version": 1, "app": "personal-trainer", "preferences": {}}""",
        )
        assertEquals("lbs", parsed.preferences.weightUnit)
    }

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
                BackupExercise(
                    id = exerciseId,
                    name = "Barbell Back Squat",
                    muscleGroup = "Quads",
                    notes = "",
                    isCustom = false,
                    equipment = "BARBELL",
                    loadType = "EXTERNAL",
                    movementKey = "squat",
                ),
                BackupExercise(
                    id = otherExerciseId,
                    name = "Bench Press",
                    muscleGroup = "Chest",
                    notes = "paused",
                    isCustom = true,
                    equipment = "BARBELL",
                    loadType = "EXTERNAL",
                ),
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
            exerciseMuscles = listOf(
                BackupExerciseMuscle(exerciseId, "quadriceps", 1.0),
                BackupExerciseMuscle(exerciseId, "glutes", 0.5),
                BackupExerciseMuscle(otherExerciseId, "chest", 1.0),
            ),
            scheduleSlots = listOf(
                BackupScheduleSlot("slot1", 0, "r1", null, 0, 1_700_000_000_000L, 1_700_000_000_000L),
                BackupScheduleSlot("slot2", 1, null, "pull", null, 1_700_000_000_000L, 1_700_000_000_000L),
            ),
        )
    }

    @Test
    fun readsABackupWrittenByTheOtherLineage() {
        // That lineage stored lowercase keys and had its own name for a belted bodyweight
        // lift. Both spellings reach the validator, which matches on the enum name, so before
        // canonicalization a document like this was refused whole — not one field of it.
        val parsed = BackupJson.decode(
            """
            {"version": 2, "exercises": [
              {"id": "a", "name": "Barbell Row", "muscleGroup": "Back", "notes": "",
               "isCustom": false, "equipment": "barbell", "loadType": "external"},
              {"id": "b", "name": "Weighted Dip", "muscleGroup": "Chest", "notes": "",
               "isCustom": false, "equipment": "bodyweight", "loadType": "weighted_bodyweight"}
            ]}
            """.trimIndent(),
        )

        val row = parsed.exercises.associateBy { it.id }
        assertEquals(EquipmentType.BARBELL.name, row.getValue("a").equipment)
        assertEquals(LoadType.EXTERNAL.name, row.getValue("a").loadType)
        assertEquals(EquipmentType.BODYWEIGHT.name, row.getValue("b").equipment)
        // The rename, which is the case a plain uppercase() would miss. Landing on EXTERNAL
        // here would make every belt-less set on this lift fail SetLogRules.
        assertEquals(LoadType.BODYWEIGHT_PLUS.name, row.getValue("b").loadType)

        val result = BackupValidator.validate(parsed, localHasData = true)
        assertTrue(result.toString(), result is BackupValidation.Valid)
    }

    @Test
    fun unrecognizedEquipmentIsPassedThroughSoTheValidatorStillRefusesIt() {
        // Canonicalization must not become a laundry: junk stays junk, and the validator is
        // still the thing that says no.
        val parsed = BackupJson.decode(
            """
            {"version": 2, "exercises": [
              {"id": "a", "name": "Mystery", "muscleGroup": "Back", "notes": "",
               "isCustom": false, "equipment": "trebuchet", "loadType": "external"}
            ]}
            """.trimIndent(),
        )
        assertEquals("trebuchet", parsed.exercises.single().equipment)
        val result = BackupValidator.validate(parsed, localHasData = true)
        assertTrue(result.toString(), result is BackupValidation.Invalid)
        assertTrue((result as BackupValidation.Invalid).reason.contains("equipment"))
    }

    @Test
    fun aV2DocumentWithNoCreditsStillGetsThemDerived() {
        // The precedence bug: `version == 1 || empty && version < 2` could never fire its
        // second test, so a v2 document carrying no junction rows derived nothing.
        val parsed = BackupJson.decode(
            """
            {"version": 2, "exercises": [
              {"id": "a", "name": "Barbell Row", "muscleGroup": "Back", "notes": "",
               "isCustom": false, "equipment": "BARBELL", "loadType": "EXTERNAL"}
            ], "exerciseMuscles": []}
            """.trimIndent(),
        )
        assertTrue("credits must be derived, not left empty", parsed.exerciseMuscles.isNotEmpty())
        assertTrue(parsed.exerciseMuscles.all { it.exerciseId == "a" })
    }

}
