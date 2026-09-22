package com.sinura.personaltrainer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** v6 → v7 adds `exercises.updatedAtMs` (Temper Account sync, custom lifts) and nothing else. */
@RunWith(RobolectricTestRunner::class)
class TemperMigration6To7Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TemperDatabase::class.java,
    )

    @Test
    fun migratesEmptyV6() {
        helper.createDatabase(DB, 6).close()
        helper.runMigrationsAndValidate(DB, 7, true, MIGRATION_TEMPER_6_7).close()
    }

    @Test
    fun existingLiftsKeepEveryColumnAndStartAtUpdatedZero() {
        helper.createDatabase(DB_POPULATED, 6).use { db ->
            seedFinishedWorkout(db)
            db.execSQL(
                "INSERT INTO exercises " +
                    "(id, name, muscleGroup, notes, isCustom, equipment, loadType, movementKey, imageKey, nameKey) " +
                    "VALUES ('custom1', 'Landmine press', 'Shoulders', 'bar in a corner', 1, 'BARBELL', " +
                    "'EXTERNAL', 'press', 'still-landmine', 'landmine press')",
            )
        }
        val db = helper.runMigrationsAndValidate(DB_POPULATED, 7, true, MIGRATION_TEMPER_6_7)
        assertFinishedWorkoutIntact(db)
        db.query(
            "SELECT name, muscleGroup, notes, isCustom, equipment, loadType, movementKey, imageKey, " +
                "nameKey, updatedAtMs FROM exercises WHERE id = 'custom1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Landmine press", cursor.getString(0))
            assertEquals("Shoulders", cursor.getString(1))
            assertEquals("bar in a corner", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals("BARBELL", cursor.getString(4))
            assertEquals("EXTERNAL", cursor.getString(5))
            assertEquals("press", cursor.getString(6))
            assertEquals("still-landmine", cursor.getString(7))
            assertEquals("landmine press", cursor.getString(8))
            assertEquals(0L, cursor.getLong(9))
        }
        db.query("SELECT updatedAtMs FROM exercises WHERE id = 'ex1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
        db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        db.close()
    }

    /** A v4 phone (Room frozen, before holds and sync) reaches v7 with its history. */
    @Test
    fun populatedV4ReachesV7WithHistoryIntact() {
        helper.createDatabase(DB_CHAIN, 4).use { db -> seedFinishedWorkout(db) }
        val db = helper.runMigrationsAndValidate(
            DB_CHAIN,
            7,
            true,
            MIGRATION_TEMPER_4_5,
            MIGRATION_TEMPER_5_6,
            MIGRATION_TEMPER_6_7,
        )
        assertFinishedWorkoutIntact(db)
        assertEquals(0, countOf(db, "sync_outbox"))
        db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        db.close()
    }

    private companion object {
        const val DB = "temper-empty-v6"
        const val DB_POPULATED = "temper-populated-v6"
        const val DB_CHAIN = "temper-populated-v4-to-v7"
    }
}
