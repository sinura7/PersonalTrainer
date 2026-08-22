package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v2 format, and the one thing it has to get right: a v1 file taken before this release must
 * still restore, and must restore into a database that behaves the way v1 did.
 *
 * That is not automatic. A v1 document has no `exerciseMuscles` array at all, so restoring it
 * naively gives a catalog with no junction rows and a body map with nothing on it. `decode`
 * therefore derives the credits from each exercise's muscle group using the same rule the old
 * body map used to derive its secondaries — which is not inventing data, it is writing the v1
 * model down in the v2 shape.
 *
 * Runs under `./gradlew testDebugUnitTest` (Gson is not on the jar lane's classpath).
 */
class BackupV2FormatTest {

    @Test
    fun encodeDecodeV2RoundTripsAllNewFields() {
        val original = v2Document()
        val parsed = BackupJson.decode(BackupJson.encode(original))

        assertEquals(2, parsed.version)
        assertEquals("BARBELL", parsed.exercises.first { it.id == "ex-dl" }.equipment)
        assertEquals("BODYWEIGHT_PLUS", parsed.exercises.first { it.id == "ex-pull" }.loadType)
        assertEquals("deadlift", parsed.exercises.first { it.id == "ex-dl" }.movementKey)
        assertEquals(
            original.exerciseMuscles.sortedWith(compareBy({ it.exerciseId }, { it.muscleKey })),
            parsed.exerciseMuscles,
        )
        assertEquals(
            original.scheduleSlots.sortedWith(compareBy({ it.position }, { it.id })),
            parsed.scheduleSlots,
        )
    }

    @Test
    fun decodesV1DocumentUpgradingToV2() {
        val parsed = BackupJson.decode(V1_FIXTURE)

        // The version field keeps the source version for provenance; the SHAPE is v2.
        assertEquals(1, parsed.version)
        assertEquals("OTHER", parsed.exercises.single().equipment)
        assertEquals("EXTERNAL", parsed.exercises.single().loadType)

        // "Posterior chain" is BACK with hamstrings and glutes derived at the flat 0.4 —
        // exactly what v1's body map did with the same string.
        val credits = parsed.exerciseMuscles.associate { it.muscleKey to it.weight }
        assertEquals(mapOf("back" to 1.0, "hamstrings" to 0.4, "glutes" to 0.4), credits)
        assertTrue(parsed.exerciseMuscles.all { it.exerciseId == "ex-dl" })
    }

    @Test
    fun missingNewArraysDecodeAsEmpty() {
        val parsed = BackupJson.decode(
            """{"version": 2, "app": "personal-trainer", "preferences": {"weightUnit": "kg"}}""",
        )
        assertEquals(emptyList<BackupExerciseMuscle>(), parsed.exerciseMuscles)
        assertEquals(emptyList<BackupScheduleSlot>(), parsed.scheduleSlots)
        // Job 3 fields: an older file must not fail decode. Empty means "infer".
        assertEquals("", parsed.preferences.trainingAge)
        assertEquals(emptyList<String>(), parsed.preferences.preferredDays)
        assertEquals("", parsed.preferences.trainingPlace)
    }

    @Test
    fun nullEquipmentDefaultsToOther() {
        // Gson bypasses Kotlin constructors, so an explicit null lands as null rather than as
        // the declared default. Normalization in decode() is what makes the field trustworthy.
        val parsed = BackupJson.decode(
            """
            {"version": 2, "app": "personal-trainer", "preferences": {"weightUnit": "kg"},
             "exercises": [{"id": "ex-x", "name": "Thing", "muscleGroup": "Chest",
                            "notes": "", "isCustom": true, "equipment": null,
                            "loadType": null}]}
            """.trimIndent(),
        )
        assertEquals("OTHER", parsed.exercises.single().equipment)
        assertEquals("EXTERNAL", parsed.exercises.single().loadType)
    }

    private fun v2Document(): BackupDocument = BackupDocument(
        version = 2,
        exportedAt = "2026-08-21T10:00:00Z",
        preferences = BackupPreferences(weightUnit = "kg"),
        exercises = listOf(
            BackupExercise(
                id = "ex-dl", name = "Conventional Deadlift", muscleGroup = "Posterior chain",
                notes = "", isCustom = false, equipment = "BARBELL", loadType = "EXTERNAL",
                movementKey = "deadlift", imageKey = null,
            ),
            BackupExercise(
                id = "ex-pull", name = "Pull-Up", muscleGroup = "Back",
                notes = "", isCustom = false, equipment = "BODYWEIGHT",
                loadType = "BODYWEIGHT_PLUS", movementKey = "pull-up", imageKey = null,
            ),
        ),
        routines = listOf(BackupRoutine("r1", "Pull", "", 1_700_000_000_000L, 1_700_000_000_000L)),
        routineExercises = emptyList(),
        sessions = emptyList(),
        sessionExercises = emptyList(),
        setLogs = emptyList(),
        exerciseMuscles = listOf(
            BackupExerciseMuscle("ex-dl", "glutes", 1.0),
            BackupExerciseMuscle("ex-dl", "hamstrings", 0.5),
            BackupExerciseMuscle("ex-pull", "back", 1.0),
        ),
        scheduleSlots = listOf(
            BackupScheduleSlot("slot-a", 0, "r1", null, 0, 1_700_000_000_000L, 1_700_000_000_000L),
            BackupScheduleSlot("slot-b", 1, null, "push", null, 1_700_000_000_000L, 1_700_000_000_000L),
        ),
    )

    private companion object {
        /** A literal v1 file, exactly as an old build would have written it. */
        val V1_FIXTURE = """
            {
              "version": 1,
              "app": "personal-trainer",
              "exportedAt": "2026-01-01T00:00:00Z",
              "preferences": {"weightUnit": "kg"},
              "exercises": [
                {"id": "ex-dl", "name": "Conventional Deadlift",
                 "muscleGroup": "Posterior chain", "notes": "", "isCustom": false}
              ],
              "routines": [],
              "routineExercises": [],
              "sessions": [],
              "sessionExercises": [],
              "setLogs": []
            }
        """.trimIndent()
    }
}
