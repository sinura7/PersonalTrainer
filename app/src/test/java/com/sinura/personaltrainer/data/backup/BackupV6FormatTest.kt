package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupV6FormatTest {

    @Test
    fun decodesV5DocumentWithEmptyGyms() {
        val parsed = BackupJson.decode(
            """{"version": 5, "app": "personal-trainer", "preferences": {"weightUnit": "kg"}}""",
        )
        assertEquals(5, parsed.version)
        assertEquals(emptyList<BackupGym>(), parsed.gyms)
    }

    @Test
    fun encodeDecodeRoundTripsGymsAndSessionGymIds() {
        val gym = BackupGym(
            id = "gym-af-weston",
            name = "Anytime Fitness Weston",
            shortName = "Weston",
            badge = "WS",
            sortOrder = 3,
            archivedAtMs = null,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        val original = BackupDocument(
            version = 6,
            exportedAt = "2026-11-09T12:00:00Z",
            preferences = BackupPreferences(weightUnit = "kg"),
            exercises = emptyList(),
            routines = emptyList(),
            routineExercises = emptyList(),
            sessions = listOf(
                BackupSession(
                    id = "s1",
                    routineId = null,
                    routineName = "Legs",
                    date = 1L,
                    notes = "",
                    durationMinutes = 30,
                    startedAt = 1L,
                    finishedAt = 2L,
                    gymId = "gym-af-weston",
                ),
            ),
            sessionExercises = emptyList(),
            setLogs = emptyList(),
            gyms = listOf(gym),
        )
        val parsed = BackupJson.decode(BackupJson.encode(original))
        assertEquals(6, parsed.version)
        assertEquals(listOf(gym), parsed.gyms)
        assertEquals("gym-af-weston", parsed.sessions.single().gymId)
    }

    @Test
    fun v5SessionsRestoreWithNullGymId() {
        val v5 = BackupDocument(
            version = 5,
            exportedAt = "2026-01-01T00:00:00Z",
            preferences = BackupPreferences(weightUnit = "kg"),
            exercises = emptyList(),
            routines = emptyList(),
            routineExercises = emptyList(),
            sessions = listOf(
                BackupSession(
                    id = "s1",
                    routineId = null,
                    routineName = "Free",
                    date = 1L,
                    notes = "",
                    durationMinutes = 0,
                    startedAt = 1L,
                    finishedAt = 2L,
                ),
            ),
            sessionExercises = emptyList(),
            setLogs = emptyList(),
        )
        val encoded = BackupJson.encode(v5)
        val decoded = BackupJson.decode(encoded)
        assertNull(decoded.sessions.single().gymId)
    }

    @Test
    fun danglingGymIdIsRefused() {
        val doc = BackupDocument(
            version = 6,
            exportedAt = "2026-01-01T00:00:00Z",
            preferences = BackupPreferences(weightUnit = "kg"),
            exercises = emptyList(),
            routines = emptyList(),
            routineExercises = emptyList(),
            sessions = listOf(
                BackupSession(
                    id = "s1",
                    routineId = null,
                    routineName = "Free",
                    date = 1L,
                    notes = "",
                    durationMinutes = 0,
                    startedAt = 1L,
                    finishedAt = 2L,
                    gymId = "missing-gym",
                ),
            ),
            sessionExercises = emptyList(),
            setLogs = emptyList(),
            gyms = listOf(
                BackupGym(
                    id = "gym-home",
                    name = "Home workout",
                    shortName = "Home",
                    badge = "HM",
                    sortOrder = 0,
                    archivedAtMs = null,
                    createdAtMs = 1L,
                    updatedAtMs = 1L,
                ),
            ),
        )
        val result = BackupValidator.validate(doc, localHasData = false, allowEmptyDestructiveRestore = true)
        assertTrue(result is BackupValidation.Invalid)
    }

    @Test
    fun emptyGymsListIsRefusedForV6() {
        val doc = BackupDocument(
            version = 6,
            exportedAt = "2026-01-01T00:00:00Z",
            preferences = BackupPreferences(weightUnit = "kg"),
            exercises = emptyList(),
            routines = emptyList(),
            routineExercises = emptyList(),
            sessions = emptyList(),
            sessionExercises = emptyList(),
            setLogs = emptyList(),
            gyms = emptyList(),
        )
        val result = BackupValidator.validate(doc, localHasData = false, allowEmptyDestructiveRestore = true)
        assertTrue(result is BackupValidation.Invalid)
    }
}
