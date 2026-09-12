package com.sinura.personaltrainer.data.backup

import android.content.pm.ApplicationInfo
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.data.local.MIGRATION_TEMPER_1_2
import com.sinura.personaltrainer.data.local.MIGRATION_TEMPER_2_3
import com.sinura.personaltrainer.data.local.MIGRATION_TEMPER_3_4
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device proof that Auto Backup is off and that a file-backed
 * [TemperDatabase] survives close/reopen — the upgrade-in-place analog
 * for Temper Debug (`com.sinura.personaltrainer.debug`).
 *
 * Uses a side file, never production `temper.db`, so this cannot erase
 * a phone that already holds History.
 */
@RunWith(AndroidJUnit4::class)
class BackupPolicyInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "upgrade-inplace-device-${System.nanoTime()}.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun debugPackageDisablesAllowBackup() {
        assertTrue(BuildConfig.APPLICATION_ID.endsWith(".debug"))
        assertTrue(!BuildConfig.APPLICATION_ID.contains(BuildConfig.VERSION_CODE.toString()))
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(
            "allowBackup must be false so FLAG_ALLOW_BACKUP is unset",
            0,
            info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP,
        )
    }

    @Test
    fun fileBackedHistorySurvivesCloseAndReopen() = runBlocking {
        openDb().use { first ->
            first.exerciseDao().insert(
                ExerciseEntity(
                    id = SQUAT_ID,
                    name = "Barbell Back Squat",
                    muscleGroup = "Quads",
                    notes = "",
                    isCustom = false,
                    nameKey = "barbell back squat",
                ),
            )
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
            first.workoutDao().upsertSessionExercise(
                SessionExerciseEntity(
                    id = "device-item",
                    sessionId = SENTINEL_ID,
                    exerciseId = SQUAT_ID,
                    sortOrder = 0,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = 100.0,
                    restSeconds = 90,
                ),
            )
            first.workoutDao().insertSet(
                SetLogEntity(
                    id = "device-set",
                    sessionId = SENTINEL_ID,
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
        openDb().use { second ->
            val row = checkNotNull(second.workoutDao().getSessionRow(SENTINEL_ID))
            assertEquals("Upgrade proof", row.routineName)
            assertEquals("device reopen", row.notes)
            val summaries = second.workoutDao().sessionSummaries()
            assertEquals(1, summaries.size)
            assertEquals(SENTINEL_ID, summaries.single().id)
            assertEquals(1, second.workoutDao().sessionStills().size)
        }
    }

    private fun openDb(): TemperDatabase =
        Room.databaseBuilder(context, TemperDatabase::class.java, dbName)
            .addMigrations(MIGRATION_TEMPER_1_2, MIGRATION_TEMPER_2_3, MIGRATION_TEMPER_3_4)
            .allowMainThreadQueries()
            .build()

    private companion object {
        const val SENTINEL_ID = "upgrade-inplace-device-sentinel"
        const val SQUAT_ID = "ex-barbell-back-squat"
        const val STAMP = 1_755_000_000_000L
    }
}

private inline fun TemperDatabase.use(block: (TemperDatabase) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}
