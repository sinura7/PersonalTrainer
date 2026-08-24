package com.sinura.personaltrainer.data.backup

import android.content.pm.ApplicationInfo
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.data.local.MIGRATION_1_2
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device proof that Auto Backup is off and that a file-backed Room
 * database survives close/reopen — the upgrade-in-place analog.
 */
@RunWith(AndroidJUnit4::class)
class BackupPolicyInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "upgrade-inplace-device.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun debugPackageDisablesAllowBackup() {
        assertTrue(BuildConfig.APPLICATION_ID.endsWith(".debug"))
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(
            "allowBackup must be false so FLAG_ALLOW_BACKUP is unset",
            0,
            info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP,
        )
    }

    @Test
    fun fileBackedSessionSurvivesCloseAndReopen() = runBlocking {
        openDb().use { first ->
            first.workoutDao().upsertSession(
                WorkoutSessionEntity(
                    id = SENTINEL_ID,
                    routineId = null,
                    routineName = "Upgrade proof",
                    date = STAMP,
                    notes = "device reopen",
                    durationMinutes = 30,
                    startedAt = STAMP,
                    finishedAt = STAMP + 1_800_000,
                ),
            )
        }
        openDb().use { second ->
            val row = checkNotNull(second.workoutDao().getSessionRow(SENTINEL_ID))
            assertEquals("Upgrade proof", row.routineName)
            assertEquals("device reopen", row.notes)
        }
    }

    private fun openDb(): TrainerDatabase =
        Room.databaseBuilder(context, TrainerDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

    private companion object {
        const val SENTINEL_ID = "upgrade-inplace-device-sentinel"
        const val STAMP = 1_755_000_000_000L
    }
}

private inline fun TrainerDatabase.use(block: (TrainerDatabase) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}
