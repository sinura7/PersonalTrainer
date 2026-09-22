package com.sinura.personaltrainer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * v6 → v7 adds `exercises.updatedAtMs` (Temper Account sync, custom lifts) and nothing else.
 * Like v5 → v6 it shipped without a JVM test because `7.json` was never copied into
 * `app/src/debug/assets`.
 */
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
    fun existingLiftsKeepEverythingAndStartAtUpdatedZero() {
        helper.createDatabase(DB_POPULATED, 6).use { db ->
            seedFinishedWorkout(db)
            db.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, notes, isCustom, equipment, loadType, nameKey) " +
                    "VALUES ('custom1', 'Landmine press', 'Shoulders', 'bar in a corner', 1, 'BARBELL', " +
                    "'EXTERNAL', 'landmine press')",
            )
        }
        val db = helper.runMigrationsAndValidate(DB_POPULATED, 7, true, MIGRATION_TEMPER_6_7)
        assertFinishedWorkoutIntact(db)
        db.query(
            "SELECT name, notes, isCustom, equipment, nameKey, updatedAtMs FROM exercises WHERE id = 'custom1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Landmine press", cursor.getString(0))
            assertEquals("bar in a corner", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals("BARBELL", cursor.getString(3))
            assertEquals("landmine press", cursor.getString(4))
            assertEquals(0L, cursor.getLong(5))
        }
        db.query("SELECT updatedAtMs FROM exercises WHERE id = 'ex1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
        db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        db.close()
    }

    /** A v4 phone (Room frozen, before holds and sync) reaches today with its history. */
    @Test
    fun populatedV4ReachesCurrentWithHistoryIntact() {
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

    @Test
    fun theNextSchemaBumpCannotShipWithoutItsTestAndItsDebugAsset() {
        // Rules 3 and 4 of app/schemas/README.md, checked instead of remembered: a v8 must
        // bring TemperMigration7To8Test and a copy of 8.json into debug assets, which is
        // exactly what v6 and v7 did not do.
        assertEquals(
            "FoundationGeneration.VERSION moved: add TemperMigration7To8Test and copy the new " +
                "schema into app/src/debug/assets, then update this line",
            7,
            FoundationGeneration.VERSION,
        )
        val module = listOf(File("."), File("app")).first { File(it, "schemas").isDirectory }
        val folder = "com.sinura.personaltrainer.data.local.TemperDatabase"
        (1..FoundationGeneration.VERSION).forEach { version ->
            val exported = File(module, "schemas/$folder/$version.json")
            val copy = File(module, "src/debug/assets/$folder/$version.json")
            assertTrue("debug asset $version.json is missing", copy.isFile)
            // A stale copy would validate migrations against a schema the app no longer has.
            assertEquals("debug asset $version.json drifted", exported.readText(), copy.readText())
        }
    }

    private companion object {
        const val DB = "temper-empty-v6"
        const val DB_POPULATED = "temper-populated-v6"
        const val DB_CHAIN = "temper-populated-v4-to-current"
    }
}
