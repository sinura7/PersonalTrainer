package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R01: recovery from a crash inside the Room wipe must be able to tell the phone's own
 * tables from the incoming ones even when every count and every id matches.
 */
class RestoreWitnessTest {
    @Test
    fun sameCountsAndIdsWithDifferentContentAreToldApart() {
        val original = witnessFixture()
        val heavier = original.copy(
            setLogs = original.setLogs.map { it.copy(weightKg = it.weightKg + 20.0) },
            sessions = original.sessions.map { it.copy(notes = "felt strong") },
        )

        // The legacy witness is exactly what let recovery mistake one for the other.
        assertEquals(RestoreJournal.fingerprint(original), RestoreJournal.fingerprint(heavier))
        assertNotEquals(RestoreWitness.of(original), RestoreWitness.of(heavier))
    }

    @Test
    fun activityContentIsWitnessedToo() {
        val original = witnessFixture()
        val longerRun = original.copy(
            activities = original.activities.map { activity ->
                activity.copy(
                    blocks = activity.blocks.map { block ->
                        if (block.kind == "CARDIO") block.copy(distanceMeters = 6_000.0) else block
                    },
                )
            },
        )
        assertEquals(RestoreJournal.fingerprint(original), RestoreJournal.fingerprint(longerRun))
        assertNotEquals(RestoreWitness.of(original), RestoreWitness.of(longerRun))
    }

    @Test
    fun rowOrderNeverChangesTheWitness() {
        val document = witnessFixture()
        val shuffled = document.copy(
            exercises = document.exercises.reversed(),
            exerciseMuscles = document.exerciseMuscles.reversed(),
            sessions = document.sessions.reversed(),
            sessionExercises = document.sessionExercises.reversed(),
            setLogs = document.setLogs.reversed(),
            scheduleSlots = document.scheduleSlots.reversed(),
            activities = document.activities.reversed().map { activity ->
                activity.copy(
                    blocks = activity.blocks.reversed().map { block ->
                        block.copy(
                            sets = block.sets.reversed(),
                            intervals = block.intervals.reversed(),
                            muscles = block.muscles.reversed(),
                        )
                    },
                )
            },
            scheduleRules = document.scheduleRules.reversed(),
            scheduleOccurrences = document.scheduleOccurrences.reversed(),
            reminderDeliveries = document.reminderDeliveries.reversed(),
            measurableGoals = document.measurableGoals.reversed(),
        )
        assertEquals(RestoreWitness.of(document), RestoreWitness.of(shuffled))
    }

    @Test
    fun onlyRoomTablesAreWitnessed() {
        val document = witnessFixture()
        val sameTables = document.copy(
            version = 2,
            exportedAt = "1999-01-01T00:00:00Z",
            preferences = document.preferences.copy(weightUnit = "lbs", bodyweightKg = 90.0),
        )
        assertEquals(RestoreWitness.of(document), RestoreWitness.of(sameTables))
    }

    @Test
    fun unfinishedSessionsAndLiveActivitiesAreLeftOutOnBothSides() {
        // A backup never carries them, replaceRoom never writes a live activity, and a
        // workout started after the crash must not make recovery undecidable.
        val document = witnessFixture()
        val withLive = document.copy(
            sessions = document.sessions + BackupSession(
                id = "s-live",
                routineId = null,
                routineName = "Free workout",
                date = 1_700_100_000_000L,
                notes = "",
                durationMinutes = 0,
                startedAt = 1_700_100_000_000L,
                finishedAt = null,
            ),
            setLogs = document.setLogs + BackupSetLog(
                id = "set-live",
                sessionId = "s-live",
                exerciseId = "ex-1",
                setNumber = 1,
                weightKg = 60.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAt = 1_700_100_000_500L,
            ),
            activities = document.activities + document.activities.first().copy(
                id = "act-live",
                status = "ACTIVE",
            ),
        )
        assertEquals(RestoreWitness.of(document), RestoreWitness.of(withLive))
    }

    @Test
    fun blockDefaultsMatchWhatTheMappersWrite() {
        // A strength block that arrives with null load/equipment/exercise is stored as
        // EXTERNAL / OTHER / "" and read back that way; the witness must agree with the
        // stored form, or a legal document would look unrestored after its own restore.
        val document = witnessFixture()
        val explicit = document.copy(
            activities = document.activities.map { activity ->
                activity.copy(
                    blocks = activity.blocks.map { block ->
                        when (block.kind) {
                            "STRENGTH" -> block.copy(loadType = "EXTERNAL", equipment = "OTHER")
                            else -> block.copy(indoor = false, elapsedSeconds = 0L)
                        }
                    },
                )
            },
        )
        val implicit = document.copy(
            activities = document.activities.map { activity ->
                activity.copy(
                    blocks = activity.blocks.map { block ->
                        when (block.kind) {
                            "STRENGTH" -> block.copy(loadType = null, equipment = null)
                            else -> block.copy(indoor = null, elapsedSeconds = null)
                        }
                    },
                )
            },
        )
        assertEquals(RestoreWitness.of(explicit), RestoreWitness.of(implicit))
    }

    @Test
    fun survivesTheJsonCodec() {
        // The journal's incoming copy is encoded and decoded before recovery reads it.
        val document = witnessFixture()
        assertEquals(
            RestoreWitness.of(document),
            RestoreWitness.of(BackupJson.decode(BackupJson.encode(document))),
        )
    }

    @Test
    fun witnessIsVersionedAndComparable() {
        val witness = RestoreWitness.of(witnessFixture())
        assertTrue(witness, witness.startsWith("${RestoreWitness.VERSION}:"))
        assertEquals(RestoreWitness.VERSION.length + 1 + 64, witness.length)
        assertTrue(RestoreWitness.isCurrent(witness))
        assertFalse(RestoreWitness.isCurrent(null))
        assertFalse(RestoreWitness.isCurrent("w0:" + "0".repeat(64)))
        assertFalse(RestoreWitness.isCurrent("12|300|1|0|0|s-1"))
        // Deterministic across calls, not just across copies.
        assertEquals(witness, RestoreWitness.of(witnessFixture()))
    }

    @Test
    fun canonicalFormNamesItsVersionFirst() {
        val json = RestoreWitness.canonicalJson(witnessFixture())
        assertTrue(json, json.startsWith("{\"witness\":\"${RestoreWitness.VERSION}\",\"exercises\":["))
        assertTrue(json, json.contains("\"activities\":["))
        assertFalse(json, json.contains("exportedAt"))
        assertFalse(json, json.contains("weightUnit"))
    }
}

/**
 * One row in every table the Room replacement writes: strength history, credits, plan,
 * a mixed activity with nulls the mappers default, a template, planner rows and a goal.
 */
internal fun witnessFixture(): BackupDocument {
    val stamp = 1_700_000_000_000L
    return BackupDocument(
        version = BackupJson.CURRENT_VERSION,
        app = BackupJson.APP_ID,
        exportedAt = "2026-09-06T10:00:00Z",
        preferences = BackupPreferences(weightUnit = "kg", bodyweightKg = 80.0),
        exercises = listOf(
            BackupExercise(
                id = "ex-1",
                name = "Back squat",
                muscleGroup = "Quads",
                notes = "",
                isCustom = true,
                equipment = "BARBELL",
                loadType = "EXTERNAL",
            ),
            BackupExercise(
                id = "ex-2",
                name = "Row",
                muscleGroup = "Back",
                notes = "cue: elbows",
                isCustom = true,
                equipment = "DUMBBELL",
                loadType = "EXTERNAL",
            ),
        ),
        exerciseMuscles = listOf(
            BackupExerciseMuscle(exerciseId = "ex-1", muscleKey = "quads", weight = 1.0),
            BackupExerciseMuscle(exerciseId = "ex-1", muscleKey = "glutes", weight = 0.4),
            BackupExerciseMuscle(exerciseId = "ex-2", muscleKey = "lats", weight = 1.0),
        ),
        routines = listOf(BackupRoutine("r-1", "Lower", "", stamp, stamp)),
        routineExercises = listOf(
            BackupRoutineExercise(
                id = "ri-1",
                routineId = "r-1",
                exerciseId = "ex-1",
                sortOrder = 0,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 120,
            ),
        ),
        sessions = listOf(
            BackupSession(
                id = "s-1",
                routineId = "r-1",
                routineName = "Lower",
                date = stamp,
                notes = "",
                durationMinutes = 45,
                startedAt = stamp,
                finishedAt = stamp + 2_700_000L,
            ),
            BackupSession(
                id = "s-2",
                routineId = null,
                routineName = "Free workout",
                date = stamp + 86_400_000L,
                notes = "",
                durationMinutes = 30,
                startedAt = stamp + 86_400_000L,
                finishedAt = stamp + 86_400_000L + 1_800_000L,
            ),
        ),
        sessionExercises = listOf(
            BackupSessionExercise(
                id = "si-1",
                sessionId = "s-1",
                exerciseId = "ex-1",
                sortOrder = 0,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 120,
            ),
            BackupSessionExercise(
                id = "si-2",
                sessionId = "s-2",
                exerciseId = "ex-2",
                sortOrder = 0,
                targetSets = 3,
                targetReps = 8,
                targetWeightKg = null,
                restSeconds = 90,
            ),
        ),
        setLogs = listOf(
            BackupSetLog("set-1", "s-1", "ex-1", 1, 100.0, 5, 8, false, stamp + 600_000L),
            BackupSetLog("set-2", "s-1", "ex-1", 2, 100.0, 5, 9, false, stamp + 900_000L),
            BackupSetLog("set-3", "s-2", "ex-2", 1, 30.0, 8, null, true, stamp + 86_400_000L + 300_000L),
        ),
        scheduleSlots = listOf(
            BackupScheduleSlot(
                id = "slot-1",
                position = 0,
                routineId = "r-1",
                focusKind = null,
                anchorDay = 1,
                createdAt = stamp,
                updatedAt = stamp,
            ),
        ),
        activities = listOf(
            BackupActivity(
                id = "act-1",
                status = "COMPLETED",
                origin = "BACKDATED",
                source = "TEMPER",
                title = "Run and press",
                notes = "mixed",
                performedStart = BackupCapturedTime(stamp, "Europe/London", 3600, 19_675L),
                performedEnd = BackupCapturedTime(stamp + 3_600_000L, "Europe/London", 3600, 19_675L),
                templateId = null,
                occurrenceId = null,
                createdAtMs = stamp,
                updatedAtMs = stamp,
                revision = 1,
                blocks = listOf(
                    BackupActivityBlock(
                        id = "blk-cardio",
                        sortOrder = 0,
                        kind = "CARDIO",
                        cardioType = "RUN",
                        indoor = false,
                        elapsedSeconds = 1_800L,
                        movingSeconds = 1_700L,
                        distanceMeters = 5_000.0,
                        intervals = listOf(
                            BackupCardioInterval("int-1", 0, 900L, 2_500.0, 6),
                            BackupCardioInterval("int-2", 1, 900L, 2_500.0, 7),
                        ),
                    ),
                    BackupActivityBlock(
                        id = "blk-press",
                        sortOrder = 1,
                        kind = "STRENGTH",
                        exerciseId = "ex-2",
                        exerciseName = "Row",
                        loadType = null,
                        equipment = null,
                        muscles = listOf(
                            BackupActivityMuscle("lats", 1.0),
                            BackupActivityMuscle("biceps", 0.4),
                        ),
                        sets = listOf(
                            BackupStrengthSet("aset-1", 1, 30.0, 8, 7, false, stamp + 2_000_000L),
                            BackupStrengthSet("aset-2", 2, 32.5, 8, 8, false, stamp + 2_200_000L),
                        ),
                    ),
                ),
            ),
        ),
        activityTemplates = listOf(
            BackupActivityTemplate(
                id = "tpl-1",
                title = "Easy run",
                notes = "",
                blocks = listOf(
                    BackupActivityBlock(
                        id = "tpl-blk-1",
                        sortOrder = 0,
                        kind = "CARDIO",
                        cardioType = "RUN",
                        indoor = true,
                        elapsedSeconds = 1_200L,
                    ),
                ),
            ),
        ),
        scheduleRules = listOf(
            BackupScheduleRule(
                id = "rule-1",
                weekday = 1,
                hour = 18,
                minute = 30,
                modality = "WORKOUT",
                routineId = "r-1",
                createdAtMs = stamp,
                updatedAtMs = stamp,
            ),
        ),
        scheduleOccurrences = listOf(
            BackupScheduleOccurrence(
                id = "occ-1",
                ruleId = "rule-1",
                status = "PLANNED",
                instantMs = stamp + 172_800_000L,
                zoneId = "Europe/London",
                offsetSeconds = 3600,
                localEpochDay = 19_677L,
                hour = 18,
                minute = 30,
                createdAtMs = stamp,
                updatedAtMs = stamp,
            ),
        ),
        missedWorkDecisions = listOf(
            BackupMissedWorkDecision(weekStartEpochDay = 19_670L, choice = "KEEP", decidedAtMs = stamp),
        ),
        reminderDeliveries = listOf(
            BackupReminderDelivery(
                id = "del-1",
                occurrenceId = "occ-1",
                scheduledAtMs = stamp + 172_000_000L,
                status = "PENDING",
                createdAtMs = stamp,
                updatedAtMs = stamp,
            ),
        ),
        measurableGoals = listOf(
            BackupMeasurableGoal(
                id = "goal-1",
                kind = "VOLUME",
                targetValue = 10_000.0,
                exerciseId = "ex-1",
                exerciseName = "Back squat",
                period = "WEEK",
                instantMs = stamp,
                zoneId = "Europe/London",
                offsetSeconds = 3600,
                localEpochDay = 19_675L,
                createdAtMs = stamp,
                updatedAtMs = stamp,
            ),
        ),
    )
}
