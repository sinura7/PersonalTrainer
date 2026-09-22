package com.sinura.personaltrainer.data.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.repository.prefs.SettingsStore
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.entity.ActivityBlockEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ActivitySessionEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import com.sinura.personaltrainer.data.local.entity.SyncOutboxEntity
import com.sinura.personaltrainer.domain.SyncEntityType
import com.sinura.personaltrainer.domain.SyncOutboxOperation
import org.junit.Assert.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {
    private lateinit var database: TemperDatabase
    private lateinit var remote: FakeSyncRemote
    private lateinit var engine: SyncEngine
    private lateinit var settingsStore: SettingsStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TemperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        remote = FakeSyncRemote()
        val prefsFile = File(context.filesDir, "sync-engine-test-${System.nanoTime()}.preferences_pb")
        settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(produceFile = { prefsFile }),
        )
        engine = SyncEngine(
            syncDao = database.syncDao(),
            activityDao = database.activityDao(),
            plannerDao = database.plannerDao(),
            routineDao = database.routineDao(),
            exerciseDao = database.exerciseDao(),
            catalogDao = database.catalogDao(),
            bodyweightDao = database.bodyweightDao(),
            goalDao = database.goalDao(),
            settingsStore = settingsStore,
            remote = remote,
            nowMillis = { 5_000L },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pullAppliesRemoteScheduleRuleWhenNewer() = runTest {
        val local = ScheduleRuleEntity(
            id = "rule-1",
            weekday = 1,
            hour = 18,
            minute = 0,
            modality = "STRENGTH",
            zonePolicy = "DEVICE",
            fixedZoneId = null,
            routineId = null,
            templateId = null,
            focusKind = null,
            reminderOffsetMinutes = 30,
            enabled = 1,
            createdAtMs = 100L,
            updatedAtMs = 100L,
        )
        database.plannerDao().upsertRule(local)
        val remoteRow = local.copy(updatedAtMs = 500L)
        remote.seed(
            SyncEntityType.SCHEDULE_RULE,
            encodeSync(remoteRow.toRemote(userId = "user-1")),
        )
        assertTrue(engine.run("user-1").isSuccess)
        assertEquals(500L, database.plannerDao().getRule("rule-1")!!.updatedAtMs)
    }

    @Test
    fun pushDrainsOutboxToFakeRemote() = runTest {
        val writer = SyncOutboxWriter(database.syncDao(), nowMillis = { 1L })
        writer.enqueueScheduleRules(
            userId = "user-1",
            rules = listOf(
                ScheduleRuleEntity(
                    id = "rule-2",
                    weekday = 2,
                    hour = 7,
                    minute = 0,
                    modality = "STRENGTH",
                    zonePolicy = "DEVICE",
                    fixedZoneId = null,
                    routineId = null,
                    templateId = null,
                    focusKind = null,
                    reminderOffsetMinutes = 0,
                    enabled = 1,
                    createdAtMs = 1L,
                    updatedAtMs = 2L,
                ),
            ),
        )
        assertTrue(engine.run("user-1").isSuccess)
        assertEquals(1, remote.upserts.count { it.first == SyncEntityType.SCHEDULE_RULE })
        assertEquals(0, database.syncDao().pendingCount())
    }

    @Test
    fun pullAppliesRemoteRoutineWhenNewer() = runTest {
        val local = RoutineEntity(
            id = "routine-1",
            name = "Push",
            notes = "",
            createdAt = 100L,
            updatedAt = 100L,
        )
        database.routineDao().upsertRoutine(local)
        val remoteRow = local.copy(updatedAt = 900L)
        remote.seed(
            SyncEntityType.ROUTINE,
            encodeSync(remoteRow.toRemote(userId = "user-1")),
        )
        assertTrue(engine.run("user-1").isSuccess)
        assertEquals(900L, database.routineDao().getById("routine-1")!!.routine.updatedAt)
    }

    @Test
    fun pullKeepsLocalRoutineWhenNewer() = runTest {
        val local = RoutineEntity(
            id = "routine-2",
            name = "Pull",
            notes = "",
            createdAt = 100L,
            updatedAt = 800L,
        )
        database.routineDao().upsertRoutine(local)
        val staleRemote = local.copy(name = "Stale", updatedAt = 200L)
        remote.seed(
            SyncEntityType.ROUTINE,
            encodeSync(staleRemote.toRemote(userId = "user-1")),
        )
        assertTrue(engine.run("user-1").isSuccess)
        assertEquals("Pull", database.routineDao().getById("routine-2")!!.routine.name)
        assertEquals(800L, database.routineDao().getById("routine-2")!!.routine.updatedAt)
    }

    @Test
    fun pushFailureStillRunsPull() = runTest {
        val writer = SyncOutboxWriter(database.syncDao(), nowMillis = { 1L })
        writer.enqueueScheduleRules(
            userId = "user-1",
            rules = listOf(
                ScheduleRuleEntity(
                    id = "rule-push-fail",
                    weekday = 2,
                    hour = 7,
                    minute = 0,
                    modality = "STRENGTH",
                    zonePolicy = "DEVICE",
                    fixedZoneId = null,
                    routineId = null,
                    templateId = null,
                    focusKind = null,
                    reminderOffsetMinutes = 0,
                    enabled = 1,
                    createdAtMs = 1L,
                    updatedAtMs = 2L,
                ),
            ),
        )
        remote.failNextUpsert = IllegalStateException("Supabase upsert failed (503): busy")
        val remoteRow = ScheduleRuleEntity(
            id = "rule-remote",
            weekday = 3,
            hour = 8,
            minute = 0,
            modality = "STRENGTH",
            zonePolicy = "DEVICE",
            fixedZoneId = null,
            routineId = null,
            templateId = null,
            focusKind = null,
            reminderOffsetMinutes = 0,
            enabled = 1,
            createdAtMs = 10L,
            updatedAtMs = 400L,
        )
        remote.seed(
            SyncEntityType.SCHEDULE_RULE,
            encodeSync(remoteRow.toRemote(userId = "user-1")),
        )
        assertTrue(engine.run("user-1").isFailure)
        assertEquals(1, database.syncDao().pendingCount())
        assertEquals(400L, database.plannerDao().getRule("rule-remote")!!.updatedAtMs)
        assertTrue(
            database.syncDao().getMetadata()?.lastError?.contains("Temper Account") == true,
        )
    }

    @Test
    fun pullPaginatesUntilTableExhaustedInOneRun() = runTest {
        remote.pullPageSize = SYNC_PULL_PAGE_SIZE
        repeat(550) { index ->
            val rule = ScheduleRuleEntity(
                id = "rule-page-$index",
                weekday = 1,
                hour = 6,
                minute = 0,
                modality = "STRENGTH",
                zonePolicy = "DEVICE",
                fixedZoneId = null,
                routineId = null,
                templateId = null,
                focusKind = null,
                reminderOffsetMinutes = 0,
                enabled = 1,
                createdAtMs = 1L,
                updatedAtMs = (index + 1).toLong() * 10L,
            )
            remote.seed(
                SyncEntityType.SCHEDULE_RULE,
                encodeSync(rule.toRemote(userId = "user-1")),
            )
        }
        assertTrue(engine.run("user-1").isSuccess)
        assertEquals(550, database.plannerDao().getRules().size)
    }

    @Test
    fun pullSkipsActivityBlockWhenParentSessionMissing() = runTest {
        val remoteBlock = RemoteActivityBlockRow(
            id = "block-orphan",
            userId = "user-1",
            sessionId = "session-missing",
            templateId = null,
            sortOrder = 0,
            kind = "STRENGTH",
            exerciseId = "ex-1",
            exerciseName = "Bench",
            loadType = "BARBELL",
            equipment = null,
            musclesEncoded = null,
            cardioType = null,
            indoor = null,
            elapsedSeconds = null,
            movingSeconds = null,
            distanceMeters = null,
            elevationMeters = null,
            heartRateBpm = null,
            energyKj = null,
            rpe = null,
            routeRef = null,
            updatedAtMs = 300L,
        )
        remote.seed(SyncEntityType.ACTIVITY_BLOCK, encodeSync(remoteBlock))
        assertTrue(engine.run("user-1").isSuccess)
        assertNull(database.activityDao().getBlock("block-orphan"))
    }

    @Test
    fun pullSkipsRemoteChildWhileLocalOutboxPending() = runTest {
        val session = ActivitySessionEntity(
            id = "sess-local",
            status = "COMPLETED",
            origin = "LIVE",
            source = "STRENGTH",
            title = "Lift",
            notes = "",
            performedStartInstantMs = 100L,
            performedStartZoneId = "UTC",
            performedStartOffsetSeconds = 0,
            performedStartLocalEpochDay = 0L,
            performedEndInstantMs = 200L,
            performedEndZoneId = "UTC",
            performedEndOffsetSeconds = 0,
            performedEndLocalEpochDay = 0L,
            templateId = null,
            occurrenceId = null,
            createdAtMs = 100L,
            updatedAtMs = 200L,
            revision = 1L,
            liveToken = null,
        )
        database.activityDao().insertSession(session)
        database.activityDao().insertBlock(
            ActivityBlockEntity(
                id = "block-local",
                sessionId = session.id,
                templateId = null,
                sortOrder = 0,
                kind = "STRENGTH",
                exerciseId = "ex-1",
                exerciseName = "Local name",
                loadType = "BARBELL",
                equipment = null,
                musclesEncoded = null,
                cardioType = null,
                indoor = null,
                elapsedSeconds = null,
                movingSeconds = null,
                distanceMeters = null,
                elevationMeters = null,
                heartRateBpm = null,
                energyKj = null,
                rpe = null,
                routeRef = null,
            ),
        )
        database.syncDao().insertOutbox(
            SyncOutboxEntity(
                id = "outbox-block",
                entityType = SyncEntityType.ACTIVITY_BLOCK.name,
                entityId = "block-local",
                operation = SyncOutboxOperation.UPSERT.name,
                payloadJson = encodeSync(
                    ActivityBlockEntity(
                        id = "block-local",
                        sessionId = session.id,
                        templateId = null,
                        sortOrder = 0,
                        kind = "STRENGTH",
                        exerciseId = "ex-1",
                        exerciseName = "Local name",
                        loadType = "BARBELL",
                        equipment = null,
                        musclesEncoded = null,
                        cardioType = null,
                        indoor = null,
                        elapsedSeconds = null,
                        movingSeconds = null,
                        distanceMeters = null,
                        elevationMeters = null,
                        heartRateBpm = null,
                        energyKj = null,
                        rpe = null,
                        routeRef = null,
                    ).toRemote(userId = "user-1", updatedAtMs = 400L),
                ),
                createdAtMs = 1L,
                attempts = 0,
                lastError = null,
            ),
        )
        val remoteBlock = RemoteActivityBlockRow(
            id = "block-local",
            userId = "user-1",
            sessionId = session.id,
            templateId = null,
            sortOrder = 0,
            kind = "STRENGTH",
            exerciseId = "ex-1",
            exerciseName = "Remote overwrite",
            loadType = "BARBELL",
            equipment = null,
            musclesEncoded = null,
            cardioType = null,
            indoor = null,
            elapsedSeconds = null,
            movingSeconds = null,
            distanceMeters = null,
            elevationMeters = null,
            heartRateBpm = null,
            energyKj = null,
            rpe = null,
            routeRef = null,
            updatedAtMs = 900L,
        )
        remote.seed(SyncEntityType.ACTIVITY_BLOCK, encodeSync(remoteBlock))
        remote.persistUpsertFailure = IllegalStateException("keep local outbox")
        assertTrue(engine.run("user-1").isFailure)
        remote.persistUpsertFailure = null
        assertEquals("Local name", database.activityDao().getBlock("block-local")!!.exerciseName)
    }

    @Test
    fun pullAppliesRoutineExerciseTombstone() = runTest {
        database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = "ex-squat",
                    name = "Squat",
                    muscleGroup = "Quads",
                    notes = "",
                    isCustom = false,
                    nameKey = "squat",
                ),
            ),
        )
        database.routineDao().upsertRoutine(
            RoutineEntity("routine-t", "Legs", "", 1L, 100L),
        )
        database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-t",
                routineId = "routine-t",
                exerciseId = "ex-squat",
                sortOrder = 0,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 120,
            ),
        )
        val tombstone = RemoteRoutineExerciseRow(
            id = "re-t",
            userId = "user-1",
            routineId = "routine-t",
            exerciseId = "ex-squat",
            sortOrder = 0,
            targetSets = 3,
            targetReps = 5,
            targetWeightKg = 100.0,
            restSeconds = 120,
            targetSeconds = null,
            targetSecondsMax = null,
            updatedAtMs = 500L,
            deletedAtMs = 500L,
        )
        remote.seed(SyncEntityType.ROUTINE_EXERCISE, encodeSync(tombstone))
        assertTrue(engine.run("user-1").isSuccess)
        assertNull(database.routineDao().getRoutineExercise("re-t"))
    }

    @Test
    fun pullAppliesRemoteCustomExerciseWhenNewer() = runTest {
        val local = ExerciseEntity(
            id = "ex-custom-1",
            name = "Old name",
            muscleGroup = "Quads",
            notes = "",
            isCustom = true,
            nameKey = "old",
            updatedAtMs = 100L,
        )
        database.exerciseDao().insert(local)
        val remoteRow = local.copy(name = "New name", updatedAtMs = 500L, nameKey = "new")
        remote.seed(
            SyncEntityType.CUSTOM_EXERCISE,
            encodeSync(remoteRow.toCustomRemote(userId = "user-1", createdAtMs = 100L)),
        )
        assertTrue(engine.run("user-1").isSuccess)
        assertEquals("New name", database.exerciseDao().getById("ex-custom-1")!!.name)
    }

    @Test
    fun pullAppliesBodyweightEntryWhenNewer() = runTest {
        database.bodyweightDao().upsert(
            com.sinura.personaltrainer.data.local.entity.BodyweightEntryEntity(
                epochDay = 20_000L,
                kg = 78.0,
                recordedAtMs = 100L,
            ),
        )
        val remoteRow = RemoteBodyweightEntryRow(
            epochDay = 20_000L,
            userId = "user-1",
            kg = 79.0,
            recordedAtMs = 500L,
            zoneId = "UTC",
            offsetSeconds = 0,
            updatedAtMs = 500L,
        )
        remote.seed(SyncEntityType.BODYWEIGHT_ENTRY, encodeSync(remoteRow))
        assertTrue(engine.run("user-1").isSuccess)
        assertEquals(79.0, database.bodyweightDao().getAll().single().kg, 0.001)
    }

    @Test
    fun pullAppliesRemoteMeasurableGoalWhenNewer() = runTest {
        val local = com.sinura.personaltrainer.data.local.entity.MeasurableGoalEntity(
            id = "goal-1",
            kind = "SESSION_COUNT",
            targetValue = 3.0,
            exerciseId = null,
            exerciseName = null,
            period = "WEEK",
            instantMs = 1L,
            zoneId = "UTC",
            offsetSeconds = 0,
            localEpochDay = 0L,
            paused = false,
            createdAtMs = 100L,
            updatedAtMs = 100L,
        )
        database.goalDao().upsert(local)
        val remoteRow = RemoteMeasurableGoalRow(
            id = "goal-1",
            userId = "user-1",
            kind = "SESSION_COUNT",
            targetValue = 5.0,
            exerciseId = null,
            exerciseName = null,
            period = "WEEK",
            instantMs = 1L,
            zoneId = "UTC",
            offsetSeconds = 0,
            localEpochDay = 0L,
            paused = true,
            createdAtMs = 100L,
            updatedAtMs = 500L,
        )
        remote.seed(SyncEntityType.MEASURABLE_GOAL, encodeSync(remoteRow))
        assertTrue(engine.run("user-1").isSuccess)
        val applied = database.goalDao().getAll().single()
        assertEquals(5.0, applied.targetValue, 0.001)
        assertTrue(applied.paused)
    }

    @Test
    fun pullAppliesRemoteCoachPrefsWhenNewer() = runTest {
        val remoteRow = RemoteCoachPrefsRow(
            userId = "user-1",
            trainingGoal = "STRENGTH",
            trainingEmphasis = "UPPER",
            availableEquipment = listOf("BARBELL"),
            trainingAge = "EXPERIENCED",
            trainingPlace = "FULL_GYM",
            trainingFocus = "STRENGTH",
            heatWindow = "LAST_30_DAYS",
            updatedAtMs = 900L,
        )
        remote.seed(SyncEntityType.COACH_PREFS, encodeSync(remoteRow))
        assertTrue(engine.run("user-1").isSuccess)
        val prefs = settingsStore.snapshot()
        assertEquals("STRENGTH", prefs[com.sinura.personaltrainer.data.repository.prefs.TRAINING_GOAL])
        assertEquals(900L, SyncAccountPrefs.coachUpdatedAtMs(prefs))
    }
}
