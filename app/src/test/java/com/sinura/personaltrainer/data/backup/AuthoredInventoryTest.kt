package com.sinura.personaltrainer.data.backup

import com.sinura.personaltrainer.domain.BlockArchive
import com.sinura.personaltrainer.domain.BodyweightEntry
import com.sinura.personaltrainer.domain.BodyweightLog
import com.sinura.personaltrainer.domain.TrainingBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthoredInventoryTest {
    @Test
    fun builtInExercisesAloneAreNotAuthoredData() {
        val incoming = AuthoredInventory.fromDocument(catalogOnly())
        assertTrue(incoming.isEmpty)
        assertEquals(0, incoming.customExercises)
    }

    @Test
    fun customExerciseCounts() {
        val incoming = AuthoredInventory.fromDocument(
            catalogOnly().copy(
                exercises = listOf(
                    exercise("ex-squat", isCustom = false),
                    exercise("ex-mine", name = "My Squat", isCustom = true),
                ),
            ),
        )
        assertFalse(incoming.isEmpty)
        assertEquals(1, incoming.customExercises)
    }

    @Test
    fun bodyweightKgWithoutLogCountsAsOne() {
        val incoming = AuthoredInventory.fromDocument(
            catalogOnly().copy(
                preferences = BackupPreferences(weightUnit = "kg", bodyweightKg = 82.0),
            ),
        )
        assertEquals(1, incoming.bodyweightEntries)
        assertFalse(incoming.isEmpty)
    }

    @Test
    fun bodyweightLogCountsEntries() {
        val log = BodyweightLog.encode(
            listOf(
                BodyweightEntry(epochDay = 1, kg = 80.0),
                BodyweightEntry(epochDay = 2, kg = 81.0),
            ),
        )
        val incoming = AuthoredInventory.fromDocument(
            catalogOnly().copy(
                preferences = BackupPreferences(
                    weightUnit = "kg",
                    bodyweightKg = 81.0,
                    bodyweightLog = log,
                ),
            ),
        )
        assertEquals(2, incoming.bodyweightEntries)
    }

    @Test
    fun currentAndPastBlocksCount() {
        val past = BlockArchive.encode(listOf(TrainingBlock(startEpochDay = 10, weeks = 12)))
        val incoming = AuthoredInventory.fromDocument(
            catalogOnly().copy(
                preferences = BackupPreferences(
                    weightUnit = "kg",
                    blockStartEpochDay = 100,
                    blockWeeks = 12,
                    pastBlocks = past,
                ),
            ),
        )
        assertEquals(2, incoming.blocks)
        assertFalse(incoming.isEmpty)
    }

    @Test
    fun confirmCopyNamesBothSidesAndNeverOffersReset() {
        val incoming = AuthoredInventory.EMPTY
        val local = AuthoredInventory(
            sessions = 12,
            setLogs = 80,
            routines = 3,
            customExercises = 1,
            scheduleSlots = 4,
            bodyweightEntries = 6,
            blocks = 1,
        )
        val body = AuthoredInventory.confirmBody("phone.json", incoming, local)
        assertTrue(body.contains("This file:"))
        assertTrue(body.contains("This phone:"))
        assertTrue(body.contains("12 sessions"))
        assertTrue(body.contains("80 sets"))
        assertTrue(body.contains("6 weigh-ins"))
        assertFalse(body.contains("continue anyway", ignoreCase = true))
        assertFalse(body.contains("reset", ignoreCase = true))
        assertTrue(body.contains("saved first"))
        assertTrue(body.contains("from Settings"))
    }

    private fun catalogOnly() = BackupDocument(
        exportedAt = "2026-08-24T10:00:00Z",
        preferences = BackupPreferences(weightUnit = "kg"),
        exercises = listOf(exercise("ex-squat")),
        routines = emptyList(),
        routineExercises = emptyList(),
        sessions = emptyList(),
        sessionExercises = emptyList(),
        setLogs = emptyList(),
    )

    private fun exercise(
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
}
