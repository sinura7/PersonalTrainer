package com.sinura.personaltrainer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Temper v1 → v2: bodyweight and training-block tables appear, and
 * existing workout / activity history survives. Room validates the
 * migrated file against the committed `2.json`.
 */
@RunWith(RobolectricTestRunner::class)
class TemperMigration1To2Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TemperDatabase::class.java,
    )

    @Test
    fun migratesEmptyV1Database() {
        helper.createDatabase(DB, 1).close()
        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_TEMPER_1_2)
        assertEquals(0, countOf(db, "bodyweight_entries"))
        assertEquals(0, countOf(db, "training_blocks"))
        db.close()
    }

    @Test
    fun migratesPopulatedV1HistoryAndLeavesNewTablesEmpty() {
        helper.createDatabase(DB_POPULATED, 1).use { db ->
            db.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, notes, isCustom, equipment, loadType, nameKey) " +
                    "VALUES ('ex-1', 'Squat', 'Quads', '', 0, 'BARBELL', 'EXTERNAL', 'squat')",
            )
            db.execSQL(
                "INSERT INTO workout_sessions " +
                    "(id, routineId, routineName, date, notes, durationMinutes, startedAt, finishedAt) VALUES " +
                    "('s1', NULL, 'Legs', $STAMP, '', 40, $STAMP, ${STAMP + 1_000})",
            )
            db.execSQL(
                "INSERT INTO activity_sessions " +
                    "(id, status, origin, source, title, notes, performedStartInstantMs, " +
                    "performedStartZoneId, performedStartOffsetSeconds, performedStartLocalEpochDay, " +
                    "createdAtMs, updatedAtMs, revision, liveToken) VALUES " +
                    "('a1', 'COMPLETED', 'BACKDATED', 'TEMPER', 'Easy run', '', $STAMP, " +
                    "'UTC', 0, 20000, $STAMP, $STAMP, 1, NULL)",
            )
        }
        val db = helper.runMigrationsAndValidate(DB_POPULATED, 2, true, MIGRATION_TEMPER_1_2)
        assertEquals(1, countOf(db, "workout_sessions"))
        assertEquals(1, countOf(db, "activity_sessions"))
        assertEquals(0, countOf(db, "bodyweight_entries"))
        assertEquals(0, countOf(db, "training_blocks"))
        db.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals("migrated database must have no FK violations", 0, cursor.count)
        }
        db.query("PRAGMA index_list('training_blocks')").use { cursor ->
            val names = mutableListOf<String>()
            val nameCol = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) names.add(cursor.getString(nameCol))
            assertTrue(names.any { it.contains("startEpochDay") })
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
        const val DB = "temper-empty-v1"
        const val DB_POPULATED = "temper-populated-v1"
        const val STAMP = 1_700_000_000_000L
    }
}
