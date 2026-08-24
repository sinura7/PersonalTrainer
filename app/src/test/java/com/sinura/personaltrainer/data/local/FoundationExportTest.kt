package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.BackupValidator
import com.sinura.personaltrainer.data.repository.ActivityBackupIo
import com.sinura.personaltrainer.data.repository.ActivityRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoundationExportTest {
    private lateinit var database: TemperDatabase
    private lateinit var activities: ActivityRepository
    private lateinit var local: LocalBackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TemperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
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
    fun exportImportRecoveryRoundTripsACardioActivity() = runBlocking {
        val now = JvmTime.resolveLocal(
            CivilDateTime(CivilDate(2026, 8, 21), hour = 9, minute = 0),
            "Asia/Tokyo",
        )
        val morning = JvmTime.resolveLocal(
            CivilDateTime(CivilDate(2026, 8, 20), hour = 7, minute = 0),
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

        val snapshot = local.createSnapshot()
        val json = BackupJson.encode(snapshot)
        assertTrue(json.contains("\"activities\""))
        assertEquals(BackupJson.CURRENT_VERSION, snapshot.version)
        val decoded = BackupJson.decode(json)
        assertEquals(1, decoded.activities.size)
        assertEquals("Easy run", decoded.activities.single().title)
        assertEquals("CARDIO", decoded.activities.single().blocks.single().kind)
        assertTrue(
            BackupValidator.validate(decoded, local.authoredInventory(), allowEmptyDestructiveRestore = true)
                is com.sinura.personaltrainer.data.backup.BackupValidation.Valid,
        )

        database.activityDao().deleteAllSessions()
        assertEquals(0, activities.all().size)
        ActivityBackupIo.replace(database.activityDao(), decoded)
        val restored = activities.all().single()
        assertTrue(restored.isCardioOnly)
        assertEquals(1_500.0, restored.cardioBlocks.single().distanceMeters)
        assertEquals(morning.localEpochDay, restored.localEpochDay)
        assertEquals("Asia/Tokyo", restored.performedStart.zoneId)
    }
}
