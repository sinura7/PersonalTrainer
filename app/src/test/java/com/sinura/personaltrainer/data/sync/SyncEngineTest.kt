package com.sinura.personaltrainer.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import com.sinura.personaltrainer.domain.SyncEntityType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {
    private lateinit var database: TemperDatabase
    private lateinit var remote: FakeSyncRemote
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TemperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        remote = FakeSyncRemote()
        engine = SyncEngine(
            database = database,
            syncDao = database.syncDao(),
            activityDao = database.activityDao(),
            plannerDao = database.plannerDao(),
            routineDao = database.routineDao(),
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
}
