package com.sinura.personaltrainer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The device-truth twin of [SchemaV1BaselineTest]: identical assertion, real Android SQLite.
 *
 * The JVM lane is fast and always available but runs Robolectric's own bundled SQLite; this
 * lane is the truth check. Phase 3's migration suite must pass in BOTH. On a Windows host
 * this is the ONLY valid migration lane (see [SchemaV1BaselineTest]'s host-OS note).
 *
 * Runs on an emulator, never the owner's phone: debug and release share an applicationId
 * with different signing keys, so the test APK cannot install beside the real app, and
 * uninstalling it would delete real training history.
 */
@RunWith(AndroidJUnit4::class)
class SchemaV1BaselineDeviceTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrainerDatabase::class.java,
    )

    @Test
    fun v1BaselineOpensWithAllSixTables() {
        val db = helper.createDatabase("schema-v1-smoke-device", 1)
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
