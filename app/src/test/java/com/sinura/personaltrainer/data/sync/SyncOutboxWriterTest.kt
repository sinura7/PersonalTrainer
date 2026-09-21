package com.sinura.personaltrainer.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.entity.ActivitySessionEntity
import com.sinura.personaltrainer.domain.SyncEntityType
import com.sinura.personaltrainer.domain.SyncOutboxOperation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncOutboxWriterTest {
    private lateinit var database: TemperDatabase
    private lateinit var writer: SyncOutboxWriter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TemperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        writer = SyncOutboxWriter(database.syncDao(), nowMillis = { 1_000L })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun enqueueCompletedSessionAddsSessionAndBlocks() = runTest {
        val dao = database.activityDao()
        val session = ActivitySessionEntity(
            id = "sess-1",
            status = "COMPLETED",
            origin = "LIVE",
            source = "CARDIO",
            title = "Run",
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
        dao.insertSession(session)
        writer.enqueueCompletedSession(dao, session.id, userId = "user-1")
        val pending = database.syncDao().peekOutbox(50)
        assertTrue(pending.any { it.entityType == SyncEntityType.ACTIVITY_SESSION.name })
        assertEquals(SyncOutboxOperation.UPSERT.name, pending.first { it.entityId == "sess-1" }.operation)
    }
}
