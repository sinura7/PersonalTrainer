package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupActivityFormatTest {
    @Test
    fun version3RoundTripsActivitiesAndTemplates() {
        val original = document(listOf(cardio(), lift()), listOf(template()))
        val parsed = BackupJson.decode(BackupJson.encode(original))
        assertEquals(3, parsed.version)
        assertEquals(original.activities.sortedBy { it.id }, parsed.activities)
        assertEquals(original.activityTemplates.sortedBy { it.id }, parsed.activityTemplates)
        assertTrue(
            BackupValidator.validate(parsed, AuthoredInventory.EMPTY) is BackupValidation.Valid,
        )
    }

    @Test
    fun v2FileDecodesWithEmptyActivities() {
        val parsed = BackupJson.decode(
            """{"version": 2, "app": "personal-trainer", "preferences": {"weightUnit": "kg"}}""",
        )
        assertTrue(parsed.activities.isEmpty())
        assertTrue(parsed.activityTemplates.isEmpty())
    }

    @Test
    fun authoredInventoryCountsActivities() {
        val incoming = AuthoredInventory.fromDocument(document(listOf(cardio()), emptyList()))
        assertEquals(1, incoming.activities)
        assertFalse(incoming.isEmpty)
        assertTrue(incoming.describe().contains("1 activity"))
    }

    @Test
    fun validatorRejectsUnknownKindAndCardioStrengthSets() {
        val badKind = document(
            listOf(cardio().copy(blocks = listOf(cardio().blocks.single().copy(kind = "YOGA")))),
            emptyList(),
        )
        assertTrue(BackupValidator.validate(badKind, AuthoredInventory.EMPTY) is BackupValidation.Invalid)
        val cardioWithSets = document(
            listOf(
                cardio().copy(
                    blocks = listOf(
                        cardio().blocks.single().copy(
                            sets = listOf(
                                BackupStrengthSet("s", 1, 0.0, 0, null, false, 1_700_000_000_000L),
                            ),
                        ),
                    ),
                ),
            ),
            emptyList(),
        )
        val result = BackupValidator.validate(cardioWithSets, AuthoredInventory.EMPTY)
        assertTrue(result is BackupValidation.Invalid)
        assertTrue((result as BackupValidation.Invalid).reason.contains("strength sets"))
    }

    @Test
    fun validatorRejectsDuplicateIdsAndUnknownOrigin() {
        val dup = document(listOf(cardio(), cardio()), emptyList())
        assertTrue(BackupValidator.validate(dup, AuthoredInventory.EMPTY) is BackupValidation.Invalid)
        val origin = document(listOf(cardio().copy(origin = "DREAMT")), emptyList())
        assertTrue(BackupValidator.validate(origin, AuthoredInventory.EMPTY) is BackupValidation.Invalid)
        val emptyBlocks = document(listOf(cardio().copy(blocks = emptyList())), emptyList())
        assertTrue(BackupValidator.validate(emptyBlocks, AuthoredInventory.EMPTY) is BackupValidation.Invalid)
    }

    @Test
    fun validatorRefusesWhatTheMapperWouldExplodeOn() {
        // Every one of these used to pass validation and then throw inside the
        // restore transaction — after the user's confirm, with a misleading
        // failure message. Refuse them up front.
        val badSource = document(listOf(cardio().copy(source = "STRAVA")), emptyList())
        assertTrue(BackupValidator.validate(badSource, AuthoredInventory.EMPTY) is BackupValidation.Invalid)

        val badLoad = document(
            listOf(lift().copy(blocks = listOf(lift().blocks.single().copy(loadType = "MYSTERY")))),
            emptyList(),
        )
        assertTrue(BackupValidator.validate(badLoad, AuthoredInventory.EMPTY) is BackupValidation.Invalid)

        val badCardioType = document(
            listOf(cardio().copy(blocks = listOf(cardio().blocks.single().copy(cardioType = "SWIM_BIKE_RUN")))),
            emptyList(),
        )
        assertTrue(BackupValidator.validate(badCardioType, AuthoredInventory.EMPTY) is BackupValidation.Invalid)

        // Two activities reusing one block id ABORTs the DAO insert.
        val first = lift()
        val second = lift().copy(id = "act-2")
        val dupBlocks = document(listOf(first, second), emptyList())
        assertTrue(BackupValidator.validate(dupBlocks, AuthoredInventory.EMPTY) is BackupValidation.Invalid)
    }

    @Test
    fun generatedMembersRoundTripOnNewTypes() {
        val time = BackupCapturedTime(1L, "UTC", 0, 0)
        assertEquals(time, time.copy())
        assertEquals(time.hashCode(), time.copy().hashCode())
        val muscle = BackupActivityMuscle("quads", 1.0)
        assertEquals(muscle, muscle.copy(weight = 1.0))
        val set = BackupStrengthSet("s", 1, 100.0, 5, 8, false, 2L)
        assertEquals(set, set.copy(reps = 5))
        val interval = BackupCardioInterval("i", 0, 30, 100.0, 7)
        assertEquals(interval, interval.copy(rpe = 7))
        val block = cardio().blocks.single()
        assertEquals(block, block.copy(kind = "CARDIO"))
        val activity = cardio()
        assertEquals(activity, activity.copy(title = "Easy run"))
        val tmpl = template()
        assertEquals(tmpl, tmpl.copy(notes = ""))
    }

    private fun document(
        activities: List<BackupActivity>,
        templates: List<BackupActivityTemplate>,
    ) = BackupDocument(
        version = 3,
        exportedAt = "2026-08-24T10:00:00Z",
        preferences = BackupPreferences(weightUnit = "kg"),
        exercises = emptyList(),
        routines = emptyList(),
        routineExercises = emptyList(),
        sessions = emptyList(),
        sessionExercises = emptyList(),
        setLogs = emptyList(),
        activities = activities,
        activityTemplates = templates,
    )

    private fun cardio() = BackupActivity(
        id = "act-run",
        status = "COMPLETED",
        origin = "BACKDATED",
        source = "TEMPER",
        title = "Easy run",
        notes = "",
        performedStart = BackupCapturedTime(1_700_000_000_000L, "Asia/Tokyo", 9 * 3600, 20_000L),
        performedEnd = BackupCapturedTime(1_700_000_480_000L, "Asia/Tokyo", 9 * 3600, 20_000L),
        templateId = null,
        occurrenceId = "occ-1",
        createdAtMs = 1L,
        updatedAtMs = 1L,
        revision = 1L,
        blocks = listOf(
            BackupActivityBlock(
                id = "blk-run",
                sortOrder = 0,
                kind = "CARDIO",
                cardioType = "RUN",
                indoor = false,
                elapsedSeconds = 480,
                movingSeconds = 480,
                distanceMeters = 1_500.0,
                elevationMeters = 10.0,
                heartRateBpm = 140,
                energyKj = 400.0,
                rpe = 6,
                routeRef = null,
                intervals = listOf(BackupCardioInterval("int-1", 0, 120, 400.0, 6)),
            ),
        ),
    )

    private fun lift() = BackupActivity(
        id = "act-lift",
        status = "COMPLETED",
        origin = "LIVE",
        source = "TEMPER",
        title = "Squat day",
        notes = "work",
        performedStart = BackupCapturedTime(1_700_000_000_000L, "Asia/Tokyo", 9 * 3600, 20_000L),
        createdAtMs = 2L,
        updatedAtMs = 2L,
        revision = 1L,
        blocks = listOf(
            BackupActivityBlock(
                id = "blk-squat",
                sortOrder = 0,
                kind = "STRENGTH",
                exerciseId = "ex-squat",
                exerciseName = "Squat",
                loadType = "EXTERNAL",
                equipment = "BARBELL",
                muscles = listOf(BackupActivityMuscle("quadriceps", 1.0)),
                sets = listOf(
                    BackupStrengthSet("set-1", 1, 100.0, 5, null, false, 1_700_000_000_000L),
                ),
            ),
        ),
    )

    private fun template() = BackupActivityTemplate(
        id = "tmpl-1",
        title = "Push",
        notes = "",
        blocks = lift().blocks,
    )
}
