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
class TemperMigration4To5Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TemperDatabase::class.java,
    )

    @Test
    fun migratesEmptyV4Database() {
        helper.createDatabase(DB, 4).close()
        val db = helper.runMigrationsAndValidate(DB, 5, true, MIGRATION_TEMPER_4_5)
        assertHoldColumns(db, "routine_exercises")
        assertHoldColumns(db, "session_exercises")
        db.query("PRAGMA table_info('set_logs')").use { cursor ->
            val names = columnNames(cursor)
            assertTrue(names.contains("durationSeconds"))
        }
        db.close()
    }

    @Test
    fun migratesPopulatedV4AndLeavesOldRowsUntouched() {
        helper.createDatabase(DB_POPULATED, 4).use { db ->
            db.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, notes, isCustom) VALUES " +
                    "('ex1', 'Squat', 'Quads', '', 0)",
            )
            db.execSQL(
                "INSERT INTO routines (id, name, notes, createdAt, updatedAt) VALUES " +
                    "('r1', 'Lower', '', $STAMP, $STAMP)",
            )
            db.execSQL(
                "INSERT INTO routine_exercises " +
                    "(id, routineId, exerciseId, sort_order, targetSets, targetReps, targetWeightKg, restSeconds) VALUES " +
                    "('re1', 'r1', 'ex1', 0, 3, 5, 100.0, 90)",
            )
            db.execSQL(
                "INSERT INTO workout_sessions " +
                    "(id, routineId, routineName, date, notes, durationMinutes, startedAt, finishedAt) VALUES " +
                    "('s1', 'r1', 'Lower', $STAMP, '', 40, $STAMP, ${STAMP + 1_000})",
            )
            db.execSQL(
                "INSERT INTO session_exercises " +
                    "(id, sessionId, exerciseId, sort_order, targetSets, targetReps, targetWeightKg, restSeconds) VALUES " +
                    "('se1', 's1', 'ex1', 0, 3, 5, 100.0, 90)",
            )
            db.execSQL(
                "INSERT INTO set_logs " +
                    "(id, sessionId, exerciseId, setNumber, weightKg, reps, rpe, isWarmup, completedAt) VALUES " +
                    "('sl1', 's1', 'ex1', 1, 100.0, 5, NULL, 0, $STAMP)",
            )
        }
        val db = helper.runMigrationsAndValidate(DB_POPULATED, 5, true, MIGRATION_TEMPER_4_5)
        db.query("SELECT targetSets, targetReps, targetSeconds, targetSecondsMax FROM routine_exercises").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
            assertEquals(5, cursor.getInt(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        }
        db.query("SELECT targetSeconds, targetSecondsMax FROM session_exercises").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
        }
        db.query("SELECT reps, durationSeconds FROM set_logs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(5, cursor.getInt(0))
            assertTrue(cursor.isNull(1))
        }
        db.close()
    }

    private fun assertHoldColumns(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String) {
        db.query("PRAGMA table_info('$table')").use { cursor ->
            val names = columnNames(cursor)
            assertTrue("$table.targetSeconds", names.contains("targetSeconds"))
            assertTrue("$table.targetSecondsMax", names.contains("targetSecondsMax"))
        }
    }

    private fun columnNames(cursor: android.database.Cursor): List<String> {
        val names = mutableListOf<String>()
        val nameCol = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) names.add(cursor.getString(nameCol))
        return names
    }

    private companion object {
        const val DB = "temper-empty-v4"
        const val DB_POPULATED = "temper-populated-v4"
        const val STAMP = 1_700_000_000_000L
    }
}
