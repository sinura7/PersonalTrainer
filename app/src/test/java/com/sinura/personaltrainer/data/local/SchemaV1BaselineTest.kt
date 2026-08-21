package com.sinura.personaltrainer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves the committed v1 schema baseline (app/schemas/.../1.json) opens through
 * [MigrationTestHelper] on the JVM. This is the substrate Phase 3's v1 -> v2 migration
 * suite builds on: `createDatabase(name, 1)` builds the database FROM the committed JSON,
 * which is exactly how the migration tests will build their "before" state.
 *
 * Known limitation, on purpose: Robolectric bundles its own SQLite — it is NOT the SQLite
 * on the owner's phone. Green here is necessary, never sufficient; the device-truth lane is
 * `connectedDebugAndroidTest` ([SchemaV1BaselineDeviceTest]).
 *
 * Host-OS constraint: this lane requires macOS or Linux. Robolectric 4.14.1 defaults to
 * NATIVE SQLite everywhere except Windows, where it hard-falls back to LEGACY mode
 * (SQLite 3.7.10) whose `PRAGMA table_info` cannot express composite primary keys — which
 * makes Room schema validation fail falsely for entities with compound PKs, exactly what
 * Phase 3's `exercise_muscles` uses. On a Windows host the emulator lane is the only valid
 * migration lane.
 *
 * This test runs only under Gradle (`testDebugUnitTest`, Studio, CI). It deliberately lives
 * outside `domain/` because `tools/run-domain-tests.sh` compiles that tree with no Android
 * classpath, and a Robolectric import there would break the jar lane.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaV1BaselineTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrainerDatabase::class.java,
    )

    @Test
    fun v1BaselineOpensWithAllSixTables() {
        val db = helper.createDatabase("schema-v1-smoke", 1)
        val tables = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%' " +
                "AND name != 'room_master_table'",
        ).use { cursor ->
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
        }
        db.close()
        assertEquals(
            setOf(
                "exercises", "routines", "routine_exercises",
                "workout_sessions", "session_exercises", "set_logs",
            ),
            tables,
        )
    }
}
