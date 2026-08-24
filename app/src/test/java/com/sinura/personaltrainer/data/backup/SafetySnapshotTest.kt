package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SafetySnapshotTest {
    @Test
    fun matchingAuthoredPasses() {
        val document = authoredSample()
        val expected = AuthoredInventory.fromDocument(document)
        val authored = SafetySnapshot.verify(BackupJson.encode(document), expected)
        assertEquals(expected, authored)
        assertEquals(1, authored.sessions)
        assertEquals(2, authored.setLogs)
        assertEquals(1, authored.customExercises)
    }

    @Test
    fun emptySnapshotMatchesEmptyExpected() {
        val document = catalogOnly()
        val authored = SafetySnapshot.verify(
            BackupJson.encode(document),
            AuthoredInventory.EMPTY,
        )
        assertTrue(authored.isEmpty)
    }

    @Test
    fun authoredMismatchFails() {
        try {
            SafetySnapshot.verify(BackupJson.encode(catalogOnly()), AuthoredInventory.PRESENT)
            fail("mismatch should fail")
        } catch (thrown: BackupException) {
            assertEquals(SafetySnapshot.VERIFY_FAILED, thrown.message)
        }
    }

    @Test
    fun garbageJsonFails() {
        try {
            SafetySnapshot.verify("{not-json", AuthoredInventory.EMPTY)
            fail("garbage should fail")
        } catch (thrown: BackupException) {
            assertEquals(SafetySnapshot.VERIFY_FAILED, thrown.message)
        }
    }

    @Test
    fun structurallyBrokenDocumentFails() {
        val broken = authoredSample().copy(
            setLogs = authoredSample().setLogs.map { it.copy(sessionId = "ghost") },
        )
        try {
            SafetySnapshot.verify(
                BackupJson.encode(broken),
                AuthoredInventory.fromDocument(broken),
            )
            fail("broken document should fail")
        } catch (thrown: BackupException) {
            assertEquals(SafetySnapshot.VERIFY_FAILED, thrown.message)
        }
    }

    @Test
    fun idIsFilenameOnly() {
        assertTrue(SafetySnapshot.isSafeId("pre-restore-1750000000000.json"))
        assertFalse(SafetySnapshot.isSafeId("../pre-restore-1.json"))
        assertFalse(SafetySnapshot.isSafeId("pre-restore-1.json.tmp"))
        assertFalse(SafetySnapshot.isSafeId("pre-restore-1.json/../../secret"))
        assertFalse(SafetySnapshot.isSafeId("/tmp/pre-restore-1.json"))
        assertEquals(1_750_000_000_000L, SafetySnapshot.createdAtFromId("pre-restore-1750000000000.json"))
    }
}

internal fun catalogOnly() = BackupDocument(
    exportedAt = "2026-08-24T10:00:00Z",
    preferences = BackupPreferences(weightUnit = "kg"),
    exercises = listOf(sampleExercise("ex-squat", isCustom = false)),
    routines = emptyList(),
    routineExercises = emptyList(),
    sessions = emptyList(),
    sessionExercises = emptyList(),
    setLogs = emptyList(),
)

internal fun authoredSample(): BackupDocument {
    val t0 = 1_755_000_000_000L
    return BackupDocument(
        exportedAt = "2026-08-24T10:00:00Z",
        preferences = BackupPreferences(weightUnit = "kg"),
        exercises = listOf(
            sampleExercise("ex-squat", isCustom = false),
            sampleExercise("ex-mine", name = "My Squat", isCustom = true),
        ),
        routines = listOf(BackupRoutine("r1", "Push", "", t0, t0)),
        routineExercises = listOf(
            BackupRoutineExercise("re1", "r1", "ex-squat", 0, 3, 5, 80.0, 90),
        ),
        sessions = listOf(
            BackupSession("s1", "r1", "Push", t0, "", 60, t0, t0 + 3_600_000),
        ),
        sessionExercises = listOf(
            BackupSessionExercise("se1", "s1", "ex-squat", 0, 3, 5, 80.0, 90),
        ),
        setLogs = listOf(
            BackupSetLog("set1", "s1", "ex-squat", 1, 100.0, 5, 8, false, t0 + 60_000),
            BackupSetLog("set2", "s1", "ex-squat", 2, 100.0, 5, 8, false, t0 + 120_000),
        ),
    )
}

internal fun sampleExercise(
    id: String,
    name: String = "Barbell Back Squat",
    isCustom: Boolean = false,
) = BackupExercise(
    id = id,
    name = name,
    muscleGroup = "Quads",
    notes = "",
    isCustom = isCustom,
    equipment = "BARBELL",
    loadType = "EXTERNAL",
)
