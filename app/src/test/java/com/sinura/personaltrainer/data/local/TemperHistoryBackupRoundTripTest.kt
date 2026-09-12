package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.BackupValidator
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.repository.ActivityRepository
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.LocalBackupRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.IdPort
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * History on the live database must survive export → wipe → restore.
 *
 * [BackupV2RoundTripTest] talks to [TrainerDatabase] and used to fingerprint
 * sessions without `session_exercises` — the join History cards picture.
 * An empty restore of that shape would look like a successful backup of a
 * log the owner can no longer open. This round-trips a finished strength
 * day and a completed activity through [LocalBackupRepository.replaceWith]
 * and asks the same queries History uses.
 */
@RunWith(RobolectricTestRunner::class)
class TemperHistoryBackupRoundTripTest {
    private lateinit var database: TemperDatabase
    private lateinit var activities: ActivityRepository
    private lateinit var local: LocalBackupRepository
    private lateinit var maintenance: DbMaintenance

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TemperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        maintenance = DbMaintenance(database)
        activities = ActivityRepository(database)
        local = LocalBackupRepository(
            database = database,
            preferencesRepository = PreferencesRepository(context),
            activityDao = database.activityDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportRestoreKeepsFinishedStrengthAndActivityOnHistoryQueries() = runBlocking {
        seedFinishedStrength()
        confirmEasyRun()

        val snapshot = local.createSnapshot()
        assertEquals(1, snapshot.sessions.size)
        assertEquals(FINISHED_ID, snapshot.sessions.single().id)
        assertEquals(1, snapshot.sessionExercises.size)
        assertEquals(1, snapshot.setLogs.size)
        assertEquals(1, snapshot.activities.size)
        assertEquals("Easy run", snapshot.activities.single().title)
        assertTrue(snapshot.sessions.none { it.finishedAt == null })

        val json = BackupJson.encode(snapshot)
        assertTrue(json.contains("\"id\": \"$FINISHED_ID\""))
        assertTrue(json.contains("Easy run"))
        val decoded = BackupJson.decode(json)
        assertTrue(
            BackupValidator.validate(
                document = decoded,
                localAuthored = local.authoredInventory(),
                allowEmptyDestructiveRestore = true,
            ) is com.sinura.personaltrainer.data.backup.BackupValidation.Valid,
        )

        database.workoutDao().upsertSession(liveSession())
        assertEquals(LIVE_ID, database.workoutDao().getInProgressSession()?.id)
        assertTrue(snapshot.sessions.none { it.id == LIVE_ID })

        maintenance.withMaintenanceLock {
            local.replaceWith(decoded)
            maintenance.reconcileCatalogLocked()
        }

        val summaries = database.workoutDao().sessionSummaries()
        assertEquals(1, summaries.size)
        assertEquals(FINISHED_ID, summaries.single().id)
        assertEquals(1, summaries.single().workingSets)
        assertEquals(1, database.workoutDao().sessionStills().size)
        assertEquals("must round-trip", database.workoutDao().getSessionRow(FINISHED_ID)?.notes)
        assertNull(database.workoutDao().getInProgressSession())

        val restored = activities.all().single()
        assertTrue(restored.isCardioOnly)
        assertEquals("Easy run", restored.title)
        assertEquals(1_500.0, restored.cardioBlocks.single().distanceMeters)
    }

    private suspend fun seedFinishedStrength() {
        database.exerciseDao().insert(
            ExerciseEntity(
                id = SQUAT_ID,
                name = "Barbell Back Squat",
                muscleGroup = "Quads",
                notes = "",
                isCustom = false,
                nameKey = "barbell back squat",
            ),
        )
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = FINISHED_ID,
                routineId = null,
                routineName = "Legs",
                date = STAMP,
                notes = "must round-trip",
                durationMinutes = 45,
                startedAt = STAMP,
                finishedAt = STAMP + 2_700_000,
            ),
        )
        database.workoutDao().upsertSessionExercise(
            SessionExerciseEntity(
                id = "item-1",
                sessionId = FINISHED_ID,
                exerciseId = SQUAT_ID,
                sortOrder = 0,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 90,
            ),
        )
        database.workoutDao().insertSet(
            SetLogEntity(
                id = "set-1",
                sessionId = FINISHED_ID,
                exerciseId = SQUAT_ID,
                setNumber = 1,
                weightKg = 100.0,
                reps = 5,
                rpe = 8,
                isWarmup = false,
                completedAt = STAMP + 600_000,
            ),
        )
    }

    private suspend fun confirmEasyRun() {
        val now = JvmTime.resolveLocal(
            CivilDateTime(date = CivilDate(2026, 8, 21), hour = 9, minute = 0),
            "Asia/Tokyo",
        )
        val morning = JvmTime.resolveLocal(
            CivilDateTime(date = CivilDate(2026, 8, 20), hour = 7, minute = 0),
            "Asia/Tokyo",
        )
        val write = activities.confirm(
            ActivityDraft(
                status = ActivityStatus.COMPLETED,
                origin = ActivityOrigin.BACKDATED,
                title = "Easy run",
                performedStart = morning,
                blocks = listOf(
                    CardioBlock(
                        id = "blk-run",
                        sortOrder = 0,
                        type = CardioType.RUN,
                        indoor = false,
                        elapsedSeconds = 480,
                        movingSeconds = 480,
                        distanceMeters = 1_500.0,
                        elevationMeters = null,
                        heartRateBpm = null,
                        energyKj = null,
                        rpe = 6,
                        routeRef = null,
                    ),
                ),
            ),
            now,
            IdPort { "act-1" },
            JvmTime,
        )
        assertTrue(write is ActivityWrite.Accepted)
    }

    private fun liveSession() = WorkoutSessionEntity(
        id = LIVE_ID,
        routineId = null,
        routineName = "Not in backup",
        date = STAMP + 86_400_000,
        notes = "",
        durationMinutes = 0,
        startedAt = STAMP + 86_400_000,
        finishedAt = null,
    )

    private companion object {
        const val FINISHED_ID = "hist-s1"
        const val LIVE_ID = "hist-live"
        const val SQUAT_ID = "ex-barbell-back-squat"
        const val STAMP = 1_700_000_000_000L
    }
}
