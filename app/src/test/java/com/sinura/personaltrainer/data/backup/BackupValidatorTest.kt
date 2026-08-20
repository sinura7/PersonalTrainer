package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The validator is the last thing standing between a bad file and an irreversible wipe, so
 * these tests are deliberately adversarial: every case here is a document that decodes
 * cleanly and would previously have been written straight to the database.
 */
class BackupValidatorTest {
    private val t0 = 1_755_000_000_000L // 2025-08-12, comfortably a real timestamp

    // ---- happy path ----

    @Test
    fun acceptsACompleteConsistentDocument() {
        val result = BackupValidator.validate(sample(), localHasData = true)
        assertTrue(result is BackupValidation.Valid)
        val summary = (result as BackupValidation.Valid).summary
        assertEquals(2, summary.exercises)
        assertEquals(1, summary.routines)
        assertEquals(1, summary.sessions)
        assertEquals(2, summary.setLogs)
        assertFalse(summary.isEmpty)
        assertTrue(summary.describe().contains("1 workout,"))
        assertTrue(summary.describe().contains("2 sets"))
    }

    @Test
    fun acceptsASessionWhoseRoutineWasDeleted() {
        // routineId null is legitimate history: the routine was deleted after the workout.
        val doc = sample().copy(
            sessions = listOf(session(routineId = null, routineName = "Push")),
        )
        assertTrue(BackupValidator.validate(doc, localHasData = true) is BackupValidation.Valid)
    }

    // ---- referential integrity ----

    @Test
    fun rejectsSetLogPointingAtAMissingSession() {
        val doc = sample().copy(
            setLogs = listOf(setLog(id = "set1", sessionId = "ghost-session")),
        )
        assertInvalid(doc, "workout that is not in this file")
    }

    @Test
    fun rejectsSetLogPointingAtAMissingExercise() {
        val doc = sample().copy(
            setLogs = listOf(setLog(id = "set1", exerciseId = "ghost-exercise")),
        )
        assertInvalid(doc, "exercise that is not in this file")
    }

    @Test
    fun rejectsSessionExercisePointingAtAMissingSession() {
        val doc = sample().copy(
            sessionExercises = listOf(sessionExercise(sessionId = "ghost")),
            setLogs = emptyList(),
        )
        assertInvalid(doc, "workout that is not in this file")
    }

    @Test
    fun rejectsRoutineEntryPointingAtAMissingRoutine() {
        val doc = sample().copy(
            routineExercises = listOf(routineExercise(routineId = "ghost")),
        )
        assertInvalid(doc, "routine that is not in this file")
    }

    @Test
    fun rejectsSessionPointingAtAMissingRoutine() {
        val doc = sample().copy(sessions = listOf(session(routineId = "ghost")))
        assertInvalid(doc, "routine that is not in this file")
    }

    // ---- blank / duplicate ids, the Gson-null-into-non-null case ----

    @Test
    fun rejectsBlankExerciseId() {
        val doc = sample().copy(
            exercises = listOf(BackupExercise("  ", "Squat", "Quads", "", false)),
            routineExercises = emptyList(),
            sessionExercises = emptyList(),
            setLogs = emptyList(),
        )
        assertInvalid(doc, "missing its name or id")
    }

    @Test
    fun rejectsNullNameInjectedByGson() {
        // The real mechanism, not a simulated one: Gson constructs these data classes through
        // Unsafe, so a JSON object with no "name" yields null inside a non-null Kotlin String.
        // Decoding succeeds; the validator has to survive reading that field and reject it.
        val decoded = BackupJson.decode(
            """{"version": 1, "app": "personal-trainer",
                "exercises": [{"id": "ex-1", "muscleGroup": "Quads", "isCustom": false}]}""",
        )
        assertEquals(1, decoded.exercises.size)
        @Suppress("SENSELESS_COMPARISON")
        val nameIsActuallyNull = decoded.exercises.first().name == null
        assertTrue("Gson no longer injects null; revisit this guard", nameIsActuallyNull)
        assertInvalid(decoded, "missing its name or id")
    }

    @Test
    fun rejectsDuplicateExerciseIds() {
        val doc = sample().copy(
            exercises = listOf(
                BackupExercise("dup", "Squat", "Quads", "", false),
                BackupExercise("dup", "Bench", "Chest", "", false),
            ),
            routineExercises = emptyList(),
            sessionExercises = emptyList(),
            setLogs = emptyList(),
        )
        assertInvalid(doc, "share the id")
    }

    @Test
    fun rejectsDuplicateSetLogIds() {
        val doc = sample().copy(
            setLogs = listOf(setLog(id = "same"), setLog(id = "same", setNumber = 2)),
        )
        assertInvalid(doc, "share the id")
    }

    // ---- numeric sanity ----

    @Test
    fun rejectsImpossibleValues() {
        assertInvalid(sample().copy(setLogs = listOf(setLog(reps = -1))), "negative reps")
        assertInvalid(sample().copy(setLogs = listOf(setLog(weightKg = -5.0))), "impossible weight")
        assertInvalid(sample().copy(setLogs = listOf(setLog(weightKg = Double.NaN))), "impossible weight")
        assertInvalid(sample().copy(setLogs = listOf(setLog(setNumber = 0))), "invalid set number")
        assertInvalid(sample().copy(setLogs = listOf(setLog(rpe = 42))), "RPE")
        assertInvalid(sample().copy(setLogs = listOf(setLog(completedAt = 0L))), "impossible timestamp")
    }

    @Test
    fun rejectsAWorkoutThatFinishesBeforeItStarts() {
        val doc = sample().copy(
            sessions = listOf(session(startedAt = t0, finishedAt = t0 - 60_000L)),
        )
        assertInvalid(doc, "finishes before it starts")
    }

    @Test
    fun rejectsAZeroTimestampSession() {
        val doc = sample().copy(sessions = listOf(session(startedAt = 0L)))
        assertInvalid(doc, "impossible start time")
    }

    // ---- identity ----

    @Test
    fun rejectsForeignAndFutureDocuments() {
        assertInvalid(sample().copy(app = "some-other-app"), "not a Personal Trainer backup")
        assertInvalid(sample().copy(version = BackupJson.CURRENT_VERSION + 1), "newer app version")
        assertInvalid(sample().copy(version = 0), "missing a valid version")
    }

    // ---- the empty-wipe guard ----

    @Test
    fun rejectsAnEmptyDocumentWhenThePhoneHasData() {
        val result = BackupValidator.validate(empty(), localHasData = true)
        assertTrue(result is BackupValidation.Invalid)
        assertTrue((result as BackupValidation.Invalid).reason.contains("empty"))
    }

    @Test
    fun acceptsAnEmptyDocumentOnAnEmptyPhone() {
        // Nothing to lose: restoring an empty backup onto a fresh install is a no-op.
        assertTrue(
            BackupValidator.validate(empty(), localHasData = false) is BackupValidation.Valid,
        )
    }

    @Test
    fun acceptsAnEmptyDocumentWhenTheUserExplicitlyConfirms() {
        val result = BackupValidator.validate(
            empty(),
            localHasData = true,
            allowEmptyDestructiveRestore = true,
        )
        assertTrue(result is BackupValidation.Valid)
        assertTrue((result as BackupValidation.Valid).summary.isEmpty)
    }

    // ---- end to end through the real codec ----

    @Test
    fun survivesAFullEncodeDecodeValidateRoundTrip() {
        val original = sample()
        val parsed = BackupJson.decode(BackupJson.encode(original))
        // encode() sorts collections by id, so normalise both sides before comparing.
        assertEquals(original.copy(exercises = original.exercises.sortedBy { it.id }), parsed)
        assertEquals(original.sessionExercises, parsed.sessionExercises)
        assertEquals(original.setLogs, parsed.setLogs)
        assertTrue(BackupValidator.validate(parsed, localHasData = true) is BackupValidation.Valid)
    }

    // ---- helpers ----

    private fun assertInvalid(document: BackupDocument, expectedFragment: String) {
        val result = BackupValidator.validate(document, localHasData = true)
        assertTrue(
            "expected invalid for fragment '$expectedFragment'",
            result is BackupValidation.Invalid,
        )
        val reason = (result as BackupValidation.Invalid).reason
        assertTrue("reason was: $reason", reason.contains(expectedFragment))
    }

    private fun empty() = BackupDocument(
        exportedAt = "2026-08-19T10:00:00Z",
        preferences = BackupPreferences(weightUnit = "kg"),
        exercises = emptyList(),
        routines = emptyList(),
        routineExercises = emptyList(),
        sessions = emptyList(),
        sessionExercises = emptyList(),
        setLogs = emptyList(),
    )

    private fun sample() = BackupDocument(
        exportedAt = "2026-08-19T10:00:00Z",
        preferences = BackupPreferences(weightUnit = "kg"),
        exercises = listOf(
            BackupExercise("ex-squat", "Barbell Back Squat", "Quads", "", false),
            BackupExercise("ex-bench", "Bench Press", "Chest", "paused", true),
        ),
        routines = listOf(BackupRoutine("r1", "Push", "", t0, t0)),
        routineExercises = listOf(routineExercise()),
        sessions = listOf(session()),
        sessionExercises = listOf(sessionExercise()),
        setLogs = listOf(setLog("set1"), setLog("set2", setNumber = 2)),
    )

    private fun routineExercise(
        routineId: String = "r1",
        exerciseId: String = "ex-squat",
    ) = BackupRoutineExercise("re1", routineId, exerciseId, 0, 3, 5, 80.0, 90)

    private fun session(
        routineId: String? = "r1",
        routineName: String? = "Push",
        startedAt: Long = 1_755_000_000_000L,
        finishedAt: Long? = 1_755_003_600_000L,
    ) = BackupSession("s1", routineId, routineName, startedAt, "", 60, startedAt, finishedAt)

    private fun sessionExercise(sessionId: String = "s1") =
        BackupSessionExercise("se1", sessionId, "ex-squat", 0, 3, 5, 80.0, 90)

    private fun setLog(
        id: String = "set1",
        sessionId: String = "s1",
        exerciseId: String = "ex-squat",
        setNumber: Int = 1,
        weightKg: Double = 100.0,
        reps: Int = 5,
        rpe: Int? = 8,
        completedAt: Long = 1_755_001_000_000L,
    ) = BackupSetLog(id, sessionId, exerciseId, setNumber, weightKg, reps, rpe, false, completedAt)
}
