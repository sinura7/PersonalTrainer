package com.sinura.personaltrainer.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TemperDatabase
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
}
