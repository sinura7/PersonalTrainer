package com.sinura.personaltrainer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** v7 → v8 adds gyms and nullable session gymId (Packet G1). */
@RunWith(RobolectricTestRunner::class)
class TemperMigration7To8Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TemperDatabase::class.java,
    )

    @Test
    fun migratesEmptyV7() {
        helper.createDatabase(DB, 7).close()
        helper.runMigrationsAndValidate(DB, 8, true, MIGRATION_TEMPER_7_8).close()
    }

    @Test
    fun seededHistoryStaysIntactAndUnassigned() {
        helper.createDatabase(DB_POPULATED, 7).use { db ->
            seedFinishedWorkout(db)
            db.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, notes, isCustom, equipment, loadType, nameKey, updatedAtMs) " +
                    "VALUES ('machine1', 'Pec Deck', 'Chest', '', 0, 'MACHINE', 'EXTERNAL', 'pec deck', 0)",
            )
            db.execSQL(
                "INSERT INTO workout_sessions " +
                    "(id, routineId, routineName, date, notes, durationMinutes, startedAt, finishedAt) VALUES " +
                    "('s2', NULL, 'Gym day', $SEED_STAMP_MS, '', 30, $SEED_STAMP_MS, ${SEED_STAMP_MS + 500})",
            )
            db.execSQL(
                "INSERT INTO session_exercises " +
                    "(id, sessionId, exerciseId, sort_order, targetSets, targetReps, targetWeightKg, restSeconds) VALUES " +
                    "('se2', 's2', 'machine1', 0, 3, 10, 45.0, 90)",
            )
            db.execSQL(
                "INSERT INTO set_logs " +
                    "(id, sessionId, exerciseId, setNumber, weightKg, reps, isWarmup, completedAt) VALUES " +
                    "('sl2', 's2', 'machine1', 1, 45.0, 10, 0, $SEED_STAMP_MS)",
            )
        }
        val db = helper.runMigrationsAndValidate(DB_POPULATED, 8, true, MIGRATION_TEMPER_7_8)
        db.query("SELECT routineId, routineName, finishedAt FROM workout_sessions WHERE id = 's1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("r1", cursor.getString(0))
            assertEquals("Lower", cursor.getString(1))
            assertEquals(SEED_STAMP_MS + 1_000, cursor.getLong(2))
        }
        db.query("SELECT COUNT(*) FROM session_exercises WHERE sessionId = 's1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        assertEquals(1, countOf(db, "routine_exercises"))
        db.query("SELECT weightKg, reps FROM set_logs WHERE id = 'sl1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(100.0, cursor.getDouble(0), 0.0001)
            assertEquals(5, cursor.getInt(1))
        }
        assertEquals(2, countOf(db, "session_exercises"))
        db.query("SELECT gymId FROM workout_sessions WHERE id = 's1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertNull(if (cursor.isNull(0)) null else cursor.getString(0))
        }
        assertEquals(5, countOf(db, "gyms"))
        db.query("SELECT id FROM gyms WHERE id = 'gym-af-weston'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.query("SELECT weightKg, reps FROM set_logs WHERE id = 'sl2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(45.0, cursor.getDouble(0), 0.0001)
            assertEquals(10, cursor.getInt(1))
        }
        db.close()
    }

    private companion object {
        const val DB = "temper-empty-v7"
        const val DB_POPULATED = "temper-populated-v7"
    }
}
