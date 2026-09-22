package com.sinura.personaltrainer.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rules 3 and 4 of `app/schemas/README.md`, checked instead of remembered.
 *
 * v6 and v7 shipped without a JVM migration test because their schemas never reached
 * `app/src/debug/assets`, the only place Robolectric's `MigrationTestHelper` looks. Every
 * version up to [FoundationGeneration.VERSION] must now have its exported schema, a
 * byte-identical debug-asset copy, and a `TemperMigration{N-1}To{N}Test`.
 */
class TemperSchemaAssetsTest {
    private val module = listOf(File("."), File("app"))
        .firstOrNull { File(it, "schemas").isDirectory }
        ?: error("run from the repository root or the app module: no schemas/ folder found")

    @Test
    fun everyVersionHasAnExportedSchemaAndAnIdenticalDebugAsset() {
        (1..FoundationGeneration.VERSION).forEach { version ->
            val exported = File(module, "schemas/$FOLDER/$version.json")
            val copy = File(module, "src/debug/assets/$FOLDER/$version.json")
            assertTrue("app/schemas is missing $version.json", exported.isFile)
            assertTrue("app/src/debug/assets is missing $version.json; copy it from app/schemas", copy.isFile)
            // A stale copy would validate migrations against a schema the app no longer has.
            assertEquals("debug asset $version.json drifted from app/schemas", exported.readText(), copy.readText())
        }
    }

    @Test
    fun everyMigrationHasItsJvmTest() {
        val tests = File(module, "src/test/java/com/sinura/personaltrainer/data/local")
        (2..FoundationGeneration.VERSION).forEach { version ->
            val name = "TemperMigration${version - 1}To${version}Test.kt"
            assertTrue("$name is missing: a schema bump ships with its migration test", File(tests, name).isFile)
        }
    }

    private companion object {
        const val FOLDER = "com.sinura.personaltrainer.data.local.TemperDatabase"
    }
}
