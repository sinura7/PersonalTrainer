package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The rollback copy, and the three ways it could quietly stop being one.
 *
 * It could copy the wrong file (only the .db, losing everything still in the WAL); it could
 * overwrite itself on a later launch with a post-migration copy, deleting the only artifact worth
 * having; or it could fail silently and leave the marker set, so no launch ever retries.
 */
@RunWith(RobolectricTestRunner::class)
class PreMigrationSnapshotTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PreMigrationSnapshot.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        copyDir().deleteRecursively()
        databaseFiles().forEach { it.delete() }
    }

    @Test
    fun copiesDbAndSidecarFilesOnce() {
        writeDatabase(main = "v1-main", wal = "v1-wal", shm = "v1-shm")

        PreMigrationSnapshot.ensure(context)

        assertEquals("v1-main", File(copyDir(), DB_NAME).readText())
        assertEquals("v1-wal", File(copyDir(), "$DB_NAME-wal").readText())
        assertEquals("v1-shm", File(copyDir(), "$DB_NAME-shm").readText())
        assertEquals(marker(), PreMigrationSnapshot.TARGET_SCHEMA)
    }

    @Test
    fun secondRunDoesNotOverwriteExistingCopy() {
        writeDatabase(main = "v1-main", wal = "v1-wal", shm = null)
        PreMigrationSnapshot.ensure(context)

        // The migration has since run; the file on disk is now v2. A second pass must leave the
        // copy alone — overwriting it here is the failure mode that loses the only rollback.
        writeDatabase(main = "v2-main", wal = "v2-wal", shm = null)
        context.getSharedPreferences(PreMigrationSnapshot.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        PreMigrationSnapshot.ensure(context)

        assertEquals("v1-main", File(copyDir(), DB_NAME).readText())
        assertEquals("v1-wal", File(copyDir(), "$DB_NAME-wal").readText())
        assertEquals(marker(), PreMigrationSnapshot.TARGET_SCHEMA)
    }

    @Test
    fun freshInstallWritesMarkerWithoutCopy() {
        PreMigrationSnapshot.ensure(context)

        assertFalse("nothing to copy on a fresh install", copyDir().exists())
        assertEquals(marker(), PreMigrationSnapshot.TARGET_SCHEMA)
    }

    @Test
    fun copyFailureLeavesMarkerUnset() {
        writeDatabase(main = "v1-main", wal = null, shm = null)
        // A regular file where the copy directory belongs: mkdirs cannot succeed here.
        val blocker = copyDir()
        blocker.parentFile?.mkdirs()
        blocker.writeText("in the way")

        PreMigrationSnapshot.ensure(context)

        assertEquals("the next launch must retry", 0, marker())
        assertTrue(blocker.isFile)
    }

    private fun marker(): Int =
        context.getSharedPreferences(PreMigrationSnapshot.PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PreMigrationSnapshot.KEY_LAST_OPENED_SCHEMA, 0)

    private fun copyDir(): File = File(context.filesDir, "pre-migration/v1")

    private fun databaseFiles(): List<File> {
        val main = context.getDatabasePath(DB_NAME)
        return listOf(main, File(main.parentFile, "$DB_NAME-wal"), File(main.parentFile, "$DB_NAME-shm"))
    }

    private fun writeDatabase(main: String, wal: String?, shm: String?) {
        val target = context.getDatabasePath(DB_NAME)
        target.parentFile?.mkdirs()
        target.writeText(main)
        val walFile = File(target.parentFile, "$DB_NAME-wal")
        val shmFile = File(target.parentFile, "$DB_NAME-shm")
        if (wal != null) walFile.writeText(wal) else walFile.delete()
        if (shm != null) shmFile.writeText(shm) else shmFile.delete()
    }

    private companion object {
        const val DB_NAME = "personal_trainer.db"
    }
}
