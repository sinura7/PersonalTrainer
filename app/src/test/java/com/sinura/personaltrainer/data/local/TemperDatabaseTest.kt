package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.repository.ActivityBackupIo
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.ActivitySource
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TemperDatabaseTest {
    private lateinit var database: TemperDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TemperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun opensBesideLegacyNameAndHasNoDestructiveFallback() {
        assertEquals(FoundationGeneration.DATABASE_FILE, "temper.db")
        assertEquals(4, FoundationGeneration.VERSION)
        assertTrue(FoundationGeneration.FROZEN)
        val source = java.io.File("src/main/java/com/sinura/personaltrainer/data/local/TemperDatabase.kt")
            .takeIf { it.isFile }
            ?: java.io.File("app/src/main/java/com/sinura/personaltrainer/data/local/TemperDatabase.kt")
        val body = source.readText()
        assertFalse(body.contains(".fallbackToDestructiveMigration"))
        assertTrue(body.contains("temper.db") || body.contains("DATABASE_FILE"))
    }

    @Test
    fun cardioOnlySessionHasZeroStrengthSets() = runBlocking {
        val session = cardioSession()
        ActivityBackupIo.insertSession(database.activityDao(), session)
        val loaded = database.activityDao().getSessionGraph(session.id)!!.toDomain()
        assertTrue(loaded.isCardioOnly)
        assertEquals(0, loaded.strengthSetCount())
        assertEquals(0, database.workoutDao().getAllSets().size)
    }

    private fun cardioSession() = ActivitySession(
        id = "act-1",
        status = ActivityStatus.COMPLETED,
        origin = ActivityOrigin.BACKDATED,
        source = ActivitySource.TEMPER,
        title = "Easy run",
        notes = "",
        performedStart = CapturedCivilTime(1_000L, "Asia/Tokyo", 9 * 3600, 20_000L),
        performedEnd = null,
        templateId = null,
        occurrenceId = null,
        blocks = listOf(
            CardioBlock(
                id = "blk-1",
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
        createdAtMs = 1L,
        updatedAtMs = 1L,
        revision = 1L,
    )
}
