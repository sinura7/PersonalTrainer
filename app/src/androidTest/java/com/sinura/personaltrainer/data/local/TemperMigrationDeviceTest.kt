package com.sinura.personaltrainer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-truth twin of [TemperMigration1To2Test]. The emulator lane is
 * the migration lane of record after foundation freeze.
 */
@RunWith(AndroidJUnit4::class)
class TemperMigrationDeviceTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TemperDatabase::class.java,
    )

    @Test
    fun migratesAndMatchesDeclaredSchemaOnRealSqlite() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO workout_sessions " +
                    "(id, routineId, routineName, date, notes, durationMinutes, startedAt, finishedAt) VALUES " +
                    "('s1', NULL, 'Push', $STAMP, '', 30, $STAMP, ${STAMP + 1_000})",
            )
        }
        helper.runMigrationsAndValidate(
            DB,
            4,
            true,
            MIGRATION_TEMPER_1_2,
            MIGRATION_TEMPER_2_3,
            MIGRATION_TEMPER_3_4,
        ).use { db ->
            assertEquals(1, countOf(db, "workout_sessions"))
            assertEquals(0, countOf(db, "bodyweight_entries"))
            assertEquals(0, countOf(db, "training_blocks"))
            assertEquals(0, countOf(db, "schedule_rules"))
            assertEquals(0, countOf(db, "schedule_occurrences"))
            assertEquals(0, countOf(db, "measurable_goals"))
            db.query("PRAGMA table_info('bodyweight_entries')").use { cursor ->
                val names = mutableListOf<String>()
                val nameCol = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) names.add(cursor.getString(nameCol))
                assertTrue(names.contains("zoneId"))
                assertTrue(names.contains("offsetSeconds"))
            }
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals(0, cursor.count)
            }
        }
    }

    private fun countOf(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int {
        db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private companion object {
        const val DB = "temper-device-v1"
        const val STAMP = 1_700_000_000_000L
    }
}
