package com.sinura.personaltrainer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The device-truth twin of [Migration1To2Test], and on this project the migration lane of
 * record.
 *
 * [Migration1To2Test] already covers v1 → v2 thoroughly on the JVM, so this is not here to
 * repeat it. It is here because that lane cannot be trusted on the host this app is actually
 * developed on. Robolectric 4.14.1 falls back to legacy SQLite 3.7.10 on Windows, whose
 * `PRAGMA table_info` cannot express a composite primary key — and `exercise_muscles` has one.
 * The JVM suite's own KDoc says as much and names the emulator lane as the arbiter. Until this
 * file existed there was no migration test in that lane at all: `androidTest` held a smoke test
 * and a v1 baseline, neither of which runs a migration.
 *
 * So the three assertions below are chosen for what only real Android SQLite can settle:
 *
 *  1. the schema comparison, including the composite primary key and both cascading foreign
 *     keys that the Windows fallback silently cannot see;
 *  2. that logged history survives still joined to the lift it belongs to — `exercises` is the
 *     RESTRICT parent of the tables holding the sets, and a migration that dropped and
 *     recreated it would pass a shape check and fail this;
 *  3. that the two `NOT NULL DEFAULT` columns actually land as 'OTHER' and 'EXTERNAL' on the
 *     real engine. That is the one open question in the migration: whether Room quotes an
 *     unquoted `@ColumnInfo(defaultValue = "OTHER")` the same way `ALTER TABLE ... DEFAULT
 *     'OTHER'` does. A fresh install cannot answer it — it builds the table from Room's own
 *     createSql and never runs a line of [MIGRATION_1_2]. Only an upgrade over a real v1
 *     database does, which is exactly what this test is.
 *
 * **Reads `app/schemas/…/2.json`, which is committed.** [MigrationTestHelper] reads its
 * expectation from that exported schema — the KSP-generated `TrainerDatabase/2.json` baseline —
 * so this class validates the migration against the same artifact Room checks at open.
 *
 * Runs on an emulator, never the owner's phone: debug and release share an applicationId with
 * different signing keys, so the test APK cannot install beside the real app, and uninstalling
 * it would delete real training history.
 *
 * Run with: `./gradlew connectedDebugAndroidTest` (needs a device or emulator).
 */
@RunWith(AndroidJUnit4::class)
class MigrationDeviceTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrainerDatabase::class.java,
    )

    /**
     * The shape comparison, on the engine that ships. `validateDroppedTables = true` makes Room
     * perform the same check it performs when it opens the database on a phone, so a mismatch
     * here is precisely the crash loop a phone would see — with the difference that no training
     * history is behind it.
     */
    @Test
    fun migratesAndMatchesTheDeclaredSchemaOnRealSqlite() {
        helper.createDatabase(EMPTY_DB, 1).close()
        helper.runMigrationsAndValidate(EMPTY_DB, 2, true, MIGRATION_1_2).use { db ->
            // The composite primary key the Windows JVM fallback cannot express. Asserted
            // directly rather than left to the validation above, so that a failure says which
            // property was lost instead of only that the schemas differ.
            val pk = mutableListOf<String>()
            db.query("PRAGMA table_info(`exercise_muscles`)").use { cursor ->
                val name = cursor.getColumnIndexOrThrow("name")
                val key = cursor.getColumnIndexOrThrow("pk")
                while (cursor.moveToNext()) {
                    if (cursor.getInt(key) > 0) pk.add(cursor.getString(name))
                }
            }
            assertEquals(setOf("exerciseId", "muscleKey"), pk.toSet())
        }
    }

    /**
     * The property that actually protects the owner. A lift, a session, a session exercise and
     * a logged set go in as v1, and the set has to come out the other side still joined to the
     * lift it was performed on.
     */
    @Test
    fun loggedHistorySurvivesTheMigration() {
        helper.createDatabase(HISTORY_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, notes, isCustom) " +
                    "VALUES ('ex-squat', 'Squat', 'Quads', '', 0)",
            )
            db.execSQL(
                "INSERT INTO workout_sessions " +
                    "(id, routineId, routineName, date, notes, durationMinutes, startedAt, finishedAt) " +
                    "VALUES ('s1', NULL, 'Leg Day', $STAMP, '', 45, $STAMP, ${STAMP + 2_700_000})",
            )
            db.execSQL(
                "INSERT INTO session_exercises " +
                    "(id, sessionId, exerciseId, sort_order, targetSets, targetReps, targetWeightKg, restSeconds) " +
                    "VALUES ('se1', 's1', 'ex-squat', 0, 3, 5, NULL, 120)",
            )
            db.execSQL(
                "INSERT INTO set_logs " +
                    "(id, sessionId, exerciseId, setNumber, weightKg, reps, rpe, isWarmup, completedAt) " +
                    "VALUES ('set1', 's1', 'ex-squat', 1, 142.5, 5, NULL, 0, ${STAMP + 600_000})",
            )
        }

        helper.runMigrationsAndValidate(HISTORY_DB, 2, true, MIGRATION_1_2).use { db ->
            db.query(
                "SELECT s.weightKg, s.reps, e.name FROM set_logs s " +
                    "JOIN exercises e ON e.id = s.exerciseId WHERE s.id = 'set1'",
            ).use { cursor ->
                assertTrue("the logged set must survive, still joined to its lift", cursor.moveToFirst())
                assertEquals(142.5, cursor.getDouble(0), 0.0001)
                assertEquals(5, cursor.getInt(1))
                assertEquals("Squat", cursor.getString(2))
            }
        }
    }

    /**
     * The default-value question, answered by the engine instead of from memory.
     *
     * If Room does not quote the annotation's value the way the migration's SQL does, the two
     * disagree and Room refuses to open — on a phone carrying real history, and only on the
     * upgrade path. This is the cheapest place to find that out.
     */
    @Test
    fun v1RowsGetTheDeclaredDefaultsRatherThanNullOrAQuotedLiteral() {
        helper.createDatabase(DEFAULTS_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, notes, isCustom) " +
                    "VALUES ('ex-custom-1', '  Neck Curl ', 'Neck', 'band around head', 1)",
            )
        }

        helper.runMigrationsAndValidate(DEFAULTS_DB, 2, true, MIGRATION_1_2).use { db ->
            db.query(
                "SELECT equipment, loadType, movementKey, imageKey, nameKey, notes " +
                    "FROM exercises WHERE id = 'ex-custom-1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                // Bare 'OTHER', not "'OTHER'" with the quotes stored as data.
                assertEquals("OTHER", cursor.getString(0))
                assertEquals("EXTERNAL", cursor.getString(1))
                // These two carry no DEFAULT, so a v1 row must read null rather than empty.
                assertNull(cursor.getString(2))
                assertNull(cursor.getString(3))
                // The provisional SQL backfill: trimmed and lowercased, padding and all.
                assertEquals("neck curl", cursor.getString(4))
                // And nothing the owner wrote was touched on the way through.
                assertEquals("band around head", cursor.getString(5))
            }
        }
    }

    private companion object {
        const val EMPTY_DB = "migration-device-empty"
        const val HISTORY_DB = "migration-device-history"
        const val DEFAULTS_DB = "migration-device-defaults"
        const val STAMP = 1_700_000_000_000L
    }
}
