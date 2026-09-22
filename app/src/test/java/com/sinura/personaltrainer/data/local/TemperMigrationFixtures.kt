package com.sinura.personaltrainer.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Raw-SQL rows shared by the TemperDatabase migration tests. Valid from schema v4 on. */
internal const val SEED_STAMP_MS = 1_700_000_000_000L

/** A routine, a lift, and one finished workout with a set. */
internal fun seedFinishedWorkout(db: SupportSQLiteDatabase) {
    db.execSQL(
        "INSERT INTO exercises (id, name, muscleGroup, notes, isCustom) VALUES " +
            "('ex1', 'Squat', 'Quads', '', 0)",
    )
    db.execSQL(
        "INSERT INTO routines (id, name, notes, createdAt, updatedAt) VALUES " +
            "('r1', 'Lower', '', $SEED_STAMP_MS, $SEED_STAMP_MS)",
    )
    db.execSQL(
        "INSERT INTO routine_exercises " +
            "(id, routineId, exerciseId, sort_order, targetSets, targetReps, targetWeightKg, restSeconds) VALUES " +
            "('re1', 'r1', 'ex1', 0, 3, 5, 100.0, 90)",
    )
    db.execSQL(
        "INSERT INTO workout_sessions " +
            "(id, routineId, routineName, date, notes, durationMinutes, startedAt, finishedAt) VALUES " +
            "('s1', 'r1', 'Lower', $SEED_STAMP_MS, '', 40, $SEED_STAMP_MS, ${SEED_STAMP_MS + 1_000})",
    )
    db.execSQL(
        "INSERT INTO session_exercises " +
            "(id, sessionId, exerciseId, sort_order, targetSets, targetReps, targetWeightKg, restSeconds) VALUES " +
            "('se1', 's1', 'ex1', 0, 3, 5, 100.0, 90)",
    )
    db.execSQL(
        "INSERT INTO set_logs " +
            "(id, sessionId, exerciseId, setNumber, weightKg, reps, rpe, isWarmup, completedAt) VALUES " +
            "('sl1', 's1', 'ex1', 1, 100.0, 5, NULL, 0, $SEED_STAMP_MS)",
    )
}

/** History keeps the session, its routine link, its card, and its set's weight and reps. */
internal fun assertFinishedWorkoutIntact(db: SupportSQLiteDatabase) {
    db.query("SELECT routineId, routineName, finishedAt FROM workout_sessions WHERE id = 's1'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("r1", cursor.getString(0))
        assertEquals("Lower", cursor.getString(1))
        assertEquals(SEED_STAMP_MS + 1_000, cursor.getLong(2))
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
