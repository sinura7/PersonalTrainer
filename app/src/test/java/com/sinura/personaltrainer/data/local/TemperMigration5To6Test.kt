package com.sinura.personaltrainer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v5 → v6 adds the three Temper Account sync tables and nothing else.
 *
 * It shipped on 21 September without this test: `6.json` never reached
 * `app/src/debug/assets`, which is where Robolectric's [MigrationTestHelper] reads schemas
 * (`app/schemas/README.md`, rule 4), so no JVM test could even open a v6 database.
 */
@RunWith(RobolectricTestRunner::class)
class TemperMigration5To6Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TemperDatabase::class.java,
    )

    @Test
    fun migratesEmptyV5AndSeedsTheOneMetadataRow() {
        helper.createDatabase(DB, 5).close()
        val db = helper.runMigrationsAndValidate(DB, 6, true, MIGRATION_TEMPER_5_6)
        assertEquals(0, countOf(db, "sync_outbox"))
        assertEquals(0, countOf(db, "sync_table_cursors"))
        db.query("SELECT id, lastSuccessAtMs, lastError FROM sync_metadata").use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }
        db.close()
    }

    @Test
    fun migratesPopulatedV5AndLeavesTrainingUntouched() {
        helper.createDatabase(DB_POPULATED, 5).use { db ->
            seedFinishedWorkout(db)
            db.execSQL(
                "INSERT INTO bodyweight_entries (epochDay, kg, recordedAtMs, zoneId, offsetSeconds) VALUES " +
                    "(19700, 82.5, $MIGRATION_STAMP, 'Europe/London', 3600)",
            )
        }
        val db = helper.runMigrationsAndValidate(DB_POPULATED, 6, true, MIGRATION_TEMPER_5_6)
        assertFinishedWorkoutIntact(db)
        db.query("SELECT kg FROM bodyweight_entries WHERE epochDay = 19700").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(82.5, cursor.getDouble(0), 0.0001)
        }
        // Nothing already on the phone is queued for upload by the migration itself.
        assertEquals(0, countOf(db, "sync_outbox"))
        db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        db.close()
    }

    private companion object {
        const val DB = "temper-empty-v5"
        const val DB_POPULATED = "temper-populated-v5"
    }
}

internal const val MIGRATION_STAMP = 1_700_000_000_000L

/** A routine, a lift, and one finished workout with a set, written in raw SQL. */
internal fun seedFinishedWorkout(db: SupportSQLiteDatabase) {
    db.execSQL(
        "INSERT INTO exercises (id, name, muscleGroup, notes, isCustom) VALUES " +
            "('ex1', 'Squat', 'Quads', '', 0)",
    )
    db.execSQL(
        "INSERT INTO routines (id, name, notes, createdAt, updatedAt) VALUES " +
            "('r1', 'Lower', '', $MIGRATION_STAMP, $MIGRATION_STAMP)",
    )
    db.execSQL(
        "INSERT INTO routine_exercises " +
            "(id, routineId, exerciseId, sort_order, targetSets, targetReps, targetWeightKg, restSeconds) VALUES " +
            "('re1', 'r1', 'ex1', 0, 3, 5, 100.0, 90)",
    )
    db.execSQL(
        "INSERT INTO workout_sessions " +
            "(id, routineId, routineName, date, notes, durationMinutes, startedAt, finishedAt) VALUES " +
            "('s1', 'r1', 'Lower', $MIGRATION_STAMP, '', 40, $MIGRATION_STAMP, ${MIGRATION_STAMP + 1_000})",
    )
    db.execSQL(
        "INSERT INTO session_exercises " +
            "(id, sessionId, exerciseId, sort_order, targetSets, targetReps, targetWeightKg, restSeconds) VALUES " +
            "('se1', 's1', 'ex1', 0, 3, 5, 100.0, 90)",
    )
    db.execSQL(
        "INSERT INTO set_logs " +
            "(id, sessionId, exerciseId, setNumber, weightKg, reps, rpe, isWarmup, completedAt) VALUES " +
            "('sl1', 's1', 'ex1', 1, 100.0, 5, NULL, 0, $MIGRATION_STAMP)",
    )
}

/** History keeps the session, its routine link, its card, and its set, value for value. */
internal fun assertFinishedWorkoutIntact(db: SupportSQLiteDatabase) {
    db.query("SELECT routineId, routineName, finishedAt FROM workout_sessions WHERE id = 's1'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("r1", cursor.getString(0))
        assertEquals("Lower", cursor.getString(1))
        assertEquals(MIGRATION_STAMP + 1_000, cursor.getLong(2))
    }
    assertEquals(1, countOf(db, "session_exercises"))
    assertEquals(1, countOf(db, "routine_exercises"))
    db.query("SELECT weightKg, reps FROM set_logs WHERE id = 'sl1'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(100.0, cursor.getDouble(0), 0.0001)
        assertEquals(5, cursor.getInt(1))
    }
}

internal fun countOf(db: SupportSQLiteDatabase, table: String): Int =
    db.query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
