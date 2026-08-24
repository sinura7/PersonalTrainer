package com.sinura.personaltrainer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TemperMigration3To4Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TemperDatabase::class.java,
    )

    @Test
    fun migratesEmptyV3Database() {
        helper.createDatabase(DB, 3).close()
        val db = helper.runMigrationsAndValidate(DB, 4, true, MIGRATION_TEMPER_3_4)
        assertEquals(0, countOf(db, "measurable_goals"))
        db.query("PRAGMA table_info('bodyweight_entries')").use { cursor ->
            val names = mutableListOf<String>()
            val nameCol = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) names.add(cursor.getString(nameCol))
            assertTrue(names.contains("zoneId"))
            assertTrue(names.contains("offsetSeconds"))
        }
        db.close()
    }

    @Test
    fun migratesPopulatedV3AndLeavesGoalsEmpty() {
        helper.createDatabase(DB_POPULATED, 3).use { db ->
            db.execSQL(
                "INSERT INTO bodyweight_entries (epochDay, kg, recordedAtMs) VALUES (20000, 80.0, $STAMP)",
            )
            db.execSQL(
                "INSERT INTO workout_sessions " +
                    "(id, routineId, routineName, date, notes, durationMinutes, startedAt, finishedAt) VALUES " +
                    "('s1', NULL, 'Push', $STAMP, '', 40, $STAMP, ${STAMP + 1_000})",
            )
        }
        val db = helper.runMigrationsAndValidate(DB_POPULATED, 4, true, MIGRATION_TEMPER_3_4)
        assertEquals(1, countOf(db, "workout_sessions"))
        assertEquals(1, countOf(db, "bodyweight_entries"))
        assertEquals(0, countOf(db, "measurable_goals"))
        db.query("SELECT zoneId, offsetSeconds FROM bodyweight_entries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("UTC", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
        db.close()
    }

    private fun countOf(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int {
        db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private companion object {
        const val DB = "temper-empty-v3"
        const val DB_POPULATED = "temper-populated-v3"
        const val STAMP = 1_700_000_000_000L
    }
}
