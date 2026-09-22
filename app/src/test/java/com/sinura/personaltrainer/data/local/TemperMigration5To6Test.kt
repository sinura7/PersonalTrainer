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
 * v5 → v6 adds the three Temper Account sync tables and nothing else. Robolectric's
 * [MigrationTestHelper] reads schemas from `app/src/debug/assets` (`app/schemas/README.md`,
 * rule 4); [TemperSchemaAssetsTest] keeps that copy complete.
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
                    "(19700, 82.5, $SEED_STAMP_MS, 'Europe/London', 3600)",
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
