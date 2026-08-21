package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleSlotEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.LocalBackupRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.domain.DefaultExercises
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Backup v2, end to end, against a real database.
 *
 * A schema change without a matching backup change is the quietest way to lose data this app
 * has: the export succeeds, the file looks fine, and the restore silently drops every field the
 * format does not know about. These tests are the reason the format co-evolved in the same phase
 * as the schema rather than in the next one.
 *
 * Exercised through `LocalBackupRepository` + `DbMaintenance` rather than `BackupRepository` —
 * that is the same decode → validate → replaceWith → reconcile sequence, minus the Drive clients
 * that have no business being constructed in a database test.
 */
@RunWith(RobolectricTestRunner::class)
class BackupV2RoundTripTest {

    private lateinit var database: TrainerDatabase
    private lateinit var local: LocalBackupRepository
    private lateinit var maintenance: DbMaintenance

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        maintenance = DbMaintenance(database)
        local = LocalBackupRepository(
            database = database,
            preferencesRepository = PreferencesRepository(context),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun v2ExportRestoreV2IsLossless() = runBlocking {
        maintenance.seedCatalog()
        seedUserData()

        val json = BackupJson.encode(local.createSnapshot())
        assertTrue(json.contains("\"version\": 2"))
        assertTrue(json.contains("exerciseMuscles"))
        assertTrue(json.contains("scheduleSlots"))

        val before = tableFingerprint()
        restore(json)

        assertEquals(before, tableFingerprint())

        // Reconciliation is idempotent on top of a restore, not just on a cold start: run the
        // whole thing again and nothing may move.
        restore(json)
        assertEquals(before, tableFingerprint())
    }

    @Test
    fun v1FileRestoreOntoV2RebuildsCatalog() = runBlocking {
        maintenance.seedCatalog()
        seedUserData()

        restore(V1_FIXTURE)

        // The built-ins come back at 37 with their v2 fields, even though the file described
        // none of them: reconciliation is what re-derives the catalog this build ships.
        val exercises = database.exerciseDao().getAll()
        assertEquals(38, exercises.size)
        assertEquals(37, exercises.count { !it.isCustom })
        val squat = database.exerciseDao().getById("ex-barbell-back-squat")!!
        assertEquals("BARBELL", squat.equipment)
        assertEquals("squat", squat.movementKey)

        // The custom from the file keeps its id and gains derived credits.
        val custom = database.exerciseDao().getById("ex-custom-1")
        assertNotNull(custom)
        assertEquals("sled push", custom!!.nameKey)
        assertTrue(database.catalogDao().creditsFor("ex-custom-1").isNotEmpty())

        assertEquals(
            DefaultExercises.CATALOG_VERSION,
            database.catalogDao().getSeedMeta()!!.catalogVersion,
        )
        // Collision detection re-ran on the restored rows; nothing here collides.
        assertEquals("[]", database.catalogDao().getSeedMeta()!!.pendingCollisions)
    }

    @Test
    fun hasLocalDataCountsScheduleSlots() = runBlocking {
        assertFalse("an empty database holds nothing", local.hasLocalData())

        database.routineDao().upsertRoutine(RoutineEntity("r1", "Push", "", STAMP, STAMP))
        database.scheduleDao().replaceAll(
            listOf(
                ScheduleSlotEntity(
                    id = "slot-1", position = 0, routineId = "r1", focusKind = null,
                    anchorDay = 0, createdAt = STAMP, updatedAt = STAMP,
                ),
            ),
        )
        // A pinned week is authored state. Without the slot count, someone whose only work so far
        // is a plan reads as empty and an empty-backup restore wipes it without asking.
        assertTrue(local.hasLocalData())
    }

    private suspend fun restore(json: String) {
        val document = BackupJson.decode(json)
        maintenance.withMaintenanceLock {
            local.replaceWith(document)
            maintenance.reconcileCatalogLocked()
        }
    }

    private suspend fun seedUserData() {
        database.exerciseDao().insert(
            ExerciseEntity(
                id = "ex-custom-1", name = "Sled Push", muscleGroup = "Legs",
                notes = "hill day", isCustom = true, nameKey = "sled push",
            ),
        )
        database.routineDao().upsertRoutine(RoutineEntity("r1", "Legs", "", STAMP, STAMP))
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = "s1", routineId = "r1", routineName = "Legs", date = STAMP,
                notes = "good", durationMinutes = 45, startedAt = STAMP,
                finishedAt = STAMP + 2_700_000,
            ),
        )
        database.workoutDao().insertSet(
            SetLogEntity(
                id = "set1", sessionId = "s1", exerciseId = "ex-barbell-back-squat",
                setNumber = 1, weightKg = 100.0, reps = 5, rpe = 8, isWarmup = false,
                completedAt = STAMP + 600_000,
            ),
        )
        database.scheduleDao().replaceAll(
            listOf(
                ScheduleSlotEntity(
                    id = "slot-1", position = 0, routineId = "r1", focusKind = null,
                    anchorDay = 0, createdAt = STAMP, updatedAt = STAMP,
                ),
                ScheduleSlotEntity(
                    id = "slot-2", position = 1, routineId = null, focusKind = "pull",
                    anchorDay = null, createdAt = STAMP, updatedAt = STAMP,
                ),
            ),
        )
        // Reconcile so the custom exercise gets its junction rows before the snapshot is taken.
        maintenance.withMaintenanceLock { maintenance.reconcileCatalogLocked() }
    }

    /** Every table that a restore is supposed to reproduce exactly, in a comparable shape. */
    private suspend fun tableFingerprint(): Map<String, List<String>> = mapOf(
        "exercises" to database.exerciseDao().getAll().map { it.toString() }.sorted(),
        "routines" to database.routineDao().getAllRoutines().map { it.toString() }.sorted(),
        "sessions" to database.workoutDao().getAllSessions().map { it.toString() }.sorted(),
        "sets" to database.workoutDao().getAllSets().map { it.toString() }.sorted(),
        "credits" to database.catalogDao().getAllCredits().map { it.toString() }.sorted(),
        "slots" to database.scheduleDao().getAll().map { it.toString() }.sorted(),
    )

    private companion object {
        const val STAMP = 1_700_000_000_000L

        /** A v1 file: no equipment, no junction, no slots. */
        val V1_FIXTURE = """
            {
              "version": 1,
              "app": "personal-trainer",
              "exportedAt": "2026-01-01T00:00:00Z",
              "preferences": {"weightUnit": "kg"},
              "exercises": [
                {"id": "ex-barbell-back-squat", "name": "Barbell Back Squat",
                 "muscleGroup": "Quads", "notes": "", "isCustom": false},
                {"id": "ex-custom-1", "name": "Sled Push", "muscleGroup": "Legs",
                 "notes": "hill day", "isCustom": true}
              ],
              "routines": [
                {"id": "r1", "name": "Legs", "notes": "", "createdAt": $STAMP, "updatedAt": $STAMP}
              ],
              "routineExercises": [],
              "sessions": [
                {"id": "s1", "routineId": "r1", "routineName": "Legs", "date": $STAMP,
                 "notes": "good", "durationMinutes": 45, "startedAt": $STAMP,
                 "finishedAt": ${STAMP + 2_700_000}}
              ],
              "sessionExercises": [],
              "setLogs": [
                {"id": "set1", "sessionId": "s1", "exerciseId": "ex-barbell-back-squat",
                 "setNumber": 1, "weightKg": 100.0, "reps": 5, "rpe": 8, "isWarmup": false,
                 "completedAt": ${STAMP + 600_000}}
              ]
            }
        """.trimIndent()
    }
}
