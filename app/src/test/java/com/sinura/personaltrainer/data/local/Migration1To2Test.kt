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

/**
 * The v1 → v2 migration, against a v1 database built from the committed schema JSON.
 *
 * This is the only test in the project that stands between the owner's training history and a
 * migration bug, because the migration runs exactly once on a phone with no destructive fallback
 * and no way to downgrade. `runMigrationsAndValidate(..., validateDroppedTables = true)` is the
 * assertion that matters most: it compares the migrated database against Room's own expectation,
 * column by column, index by index, default by default — which is the same comparison Room
 * performs at open, so a mismatch here is exactly the crash loop a phone would see.
 *
 * **Requires `app/schemas/…/2.json`.** MigrationTestHelper reads its expectation from the
 * exported schema, and only a real Gradle build with the Android SDK can write that file. Until
 * the owner's first `./gradlew :app:assembleDebug` on this branch commits it, this class fails
 * with a missing-schema error — that failure is the round-trip still being open, not a defect in
 * the migration.
 *
 * **Host-OS constraint.** Robolectric 4.14.1 uses native SQLite everywhere except Windows, where
 * it falls back to legacy SQLite 3.7.10 whose `PRAGMA table_info` cannot express composite
 * primary keys. `exercise_muscles` has one. A validation failure naming its primary keys on a
 * Windows host is the harness, not the migration — the emulator lane
 * (`connectedDebugAndroidTest`) is the migration lane of record on this project.
 */
@RunWith(RobolectricTestRunner::class)
class Migration1To2Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrainerDatabase::class.java,
    )

    @Test
    fun migratesEmptyV1Database() {
        helper.createDatabase(DB, 1).close()
        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        db.query("SELECT id, catalogVersion, pendingCollisions FROM seed_meta").use { cursor ->
            assertTrue("seed_meta must hold its single row", cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            // Zero, not CATALOG_VERSION: the migration writes no catalog data, so the first
            // startup after it has to run a full seed pass.
            assertEquals(0, cursor.getInt(1))
            assertEquals("[]", cursor.getString(2))
            assertEquals(1, cursor.count)
        }
        db.close()
    }

    @Test
    fun migratesPopulatedV1DatabaseWithHistory() {
        helper.createDatabase(DB_POPULATED, 1).use { db ->
            db.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, notes, isCustom) VALUES " +
                    "('ex-barbell-back-squat', 'Barbell Back Squat', 'Quads', '', 0), " +
                    "('ex-barbell-bench-press', '  Barbell Bench Press ', 'Chest', 'paused', 0), " +
                    "('ex-custom-1', 'Sled Push', 'Legs', 'hill day', 1)",
            )
            db.execSQL(
                "INSERT INTO routines (id, name, notes, createdAt, updatedAt) " +
                    "VALUES ('r1', 'Legs', '', $STAMP, $STAMP)",
            )
            db.execSQL(
                "INSERT INTO routine_exercises " +
                    "(id, routineId, exerciseId, sort_order, targetSets, targetReps, targetWeightKg, restSeconds) " +
                    "VALUES ('re1', 'r1', 'ex-barbell-back-squat', 0, 3, 5, 100.0, 180)",
            )
            db.execSQL(
                "INSERT INTO workout_sessions " +
                    "(id, routineId, routineName, date, notes, durationMinutes, startedAt, finishedAt) VALUES " +
                    "('s1', 'r1', 'Legs', $STAMP, 'good', 45, $STAMP, ${STAMP + 2_700_000}), " +
                    "('s2', NULL, 'Free', ${STAMP + 86_400_000}, '', 0, ${STAMP + 86_400_000}, NULL)",
            )
            db.execSQL(
                "INSERT INTO session_exercises " +
                    "(id, sessionId, exerciseId, sort_order, targetSets, targetReps, targetWeightKg, restSeconds) " +
                    "VALUES ('se1', 's1', 'ex-barbell-back-squat', 0, 3, 5, 100.0, 180)",
            )
            db.execSQL(
                "INSERT INTO set_logs " +
                    "(id, sessionId, exerciseId, setNumber, weightKg, reps, rpe, isWarmup, completedAt) VALUES " +
                    "('set1', 's1', 'ex-barbell-back-squat', 1, 100.0, 5, 8, 0, ${STAMP + 600_000}), " +
                    "('set2', 's1', 'ex-barbell-back-squat', 2, 105.0, 3, 9, 0, ${STAMP + 900_000})",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_POPULATED, 2, true, MIGRATION_1_2)

        assertEquals(3, countOf(db, "exercises"))
        assertEquals(1, countOf(db, "routines"))
        assertEquals(1, countOf(db, "routine_exercises"))
        assertEquals(2, countOf(db, "workout_sessions"))
        assertEquals(1, countOf(db, "session_exercises"))
        assertEquals(2, countOf(db, "set_logs"))
        assertEquals(0, countOf(db, "exercise_muscles"))
        assertEquals(0, countOf(db, "schedule_slots"))

        db.query("SELECT id, equipment, loadType, movementKey, imageKey, nameKey FROM exercises ORDER BY id")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    assertEquals("OTHER", cursor.getString(1))
                    assertEquals("EXTERNAL", cursor.getString(2))
                    assertNull(cursor.getString(3))
                    assertNull(cursor.getString(4))
                }
            }
        // The backfill trims and lowercases, including the row with padding around its name.
        assertEquals("barbell bench press", nameKeyOf(db, "ex-barbell-bench-press"))
        assertEquals("sled push", nameKeyOf(db, "ex-custom-1"))

        // The in-progress session survives as in-progress, which is what stops a migration from
        // quietly ending a workout somebody was standing in.
        db.query("SELECT finishedAt FROM workout_sessions WHERE id = 's2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        db.close()
    }

    @Test
    fun foreignKeyIntegrityAfterMigration() {
        helper.createDatabase(DB_FK, 1).use { db ->
            db.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, notes, isCustom) " +
                    "VALUES ('ex-1', 'Squat', 'Quads', '', 0)",
            )
            db.execSQL(
                "INSERT INTO routines (id, name, notes, createdAt, updatedAt) " +
                    "VALUES ('r1', 'Legs', '', $STAMP, $STAMP)",
            )
            db.execSQL(
                "INSERT INTO workout_sessions " +
                    "(id, routineId, routineName, date, notes, durationMinutes, startedAt, finishedAt) " +
                    "VALUES ('s1', 'r1', 'Legs', $STAMP, '', 30, $STAMP, ${STAMP + 1})",
            )
            db.execSQL(
                "INSERT INTO set_logs " +
                    "(id, sessionId, exerciseId, setNumber, weightKg, reps, rpe, isWarmup, completedAt) " +
                    "VALUES ('set1', 's1', 'ex-1', 1, 100.0, 5, NULL, 0, ${STAMP + 1})",
            )
        }
        val db = helper.runMigrationsAndValidate(DB_FK, 2, true, MIGRATION_1_2)
        db.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals("foreign_key_check reported violations", 0, cursor.count)
        }
        db.close()
    }

    @Test
    fun indexesMatchDeclaredSchema() {
        helper.createDatabase(DB_INDEX, 1).close()
        val db = helper.runMigrationsAndValidate(DB_INDEX, 2, true, MIGRATION_1_2)

        // origin 'c' means CREATE INDEX. SQLite auto-creates origin 'pk' entries for the TEXT
        // and composite primary keys; those are expected and are not ours to declare.
        assertEquals(setOf("index_exercises_nameKey"), declaredIndexes(db, "exercises"))
        assertEquals(
            setOf("index_exercise_muscles_muscleKey"),
            declaredIndexes(db, "exercise_muscles"),
        )
        assertEquals(
            setOf("index_schedule_slots_routineId"),
            declaredIndexes(db, "schedule_slots"),
        )
        db.close()
    }

    private fun declaredIndexes(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
    ): Set<String> {
        val names = mutableSetOf<String>()
        db.query("PRAGMA index_list(`$table`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val originColumn = cursor.getColumnIndexOrThrow("origin")
            while (cursor.moveToNext()) {
                if (cursor.getString(originColumn) == "c") names.add(cursor.getString(nameColumn))
            }
        }
        return names
    }

    private fun countOf(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun nameKeyOf(db: androidx.sqlite.db.SupportSQLiteDatabase, id: String): String =
        db.query("SELECT nameKey FROM exercises WHERE id = '$id'").use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }

    private companion object {
        const val DB = "migration-1-2-empty"
        const val DB_POPULATED = "migration-1-2-populated"
        const val DB_FK = "migration-1-2-fk"
        const val DB_INDEX = "migration-1-2-index"
        const val STAMP = 1_700_000_000_000L
    }
}
