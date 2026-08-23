package com.sinura.personaltrainer.data.local

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        // Own files and database paths. Other Robolectric classes in this JVM open Room on
        // the shared applicationId; a leftover WAL on personal_trainer.db used to land in
        // the copy this class plants as a plain-text stand-in.
        context = IsolatedSnapshotContext(ApplicationProvider.getApplicationContext())
        context.deleteDatabase(DB_NAME)
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

    @Test
    fun incompleteCopyIsRetriedWhileSourceIsStillV1() {
        writeDatabase(main = "v1-main", wal = "v1-wal", shm = null)
        copyDir().mkdirs()
        File(copyDir(), DB_NAME).writeText("partial")

        PreMigrationSnapshot.ensure(context)

        assertEquals("v1-main", File(copyDir(), DB_NAME).readText())
        assertEquals("v1-wal", File(copyDir(), "$DB_NAME-wal").readText())
        assertEquals(marker(), PreMigrationSnapshot.TARGET_SCHEMA)
    }

    @Test
    fun alreadyMigratedSourceIsNeverCopiedAsV1Rollback() {
        writeSqlite(userVersion = 2)

        PreMigrationSnapshot.ensure(context)

        assertFalse("a v2 live file must not become the v1 rollback", copyDir().exists())
        assertEquals(marker(), PreMigrationSnapshot.TARGET_SCHEMA)
    }

    @Test
    fun incompleteCopyIsNotReplacedByAlreadyMigratedSource() {
        copyDir().mkdirs()
        File(copyDir(), DB_NAME).writeText("v1-partial")
        writeSqlite(userVersion = 2)
        File(context.getDatabasePath(DB_NAME).parentFile, "$DB_NAME-wal").writeText("v2-wal")

        PreMigrationSnapshot.ensure(context)

        assertEquals("v1-partial", File(copyDir(), DB_NAME).readText())
        assertFalse(File(copyDir(), "$DB_NAME-wal").exists())
        assertEquals(marker(), PreMigrationSnapshot.TARGET_SCHEMA)
    }

    @Test
    fun textStandInIsNotReadAsASchemaVersion() {
        writeDatabase(main = "v1-main", wal = null, shm = null)
        assertNull(PreMigrationSnapshot.userVersion(context.getDatabasePath(DB_NAME)))
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

    private fun writeSqlite(userVersion: Int) {
        val target = context.getDatabasePath(DB_NAME)
        target.parentFile?.mkdirs()
        File(target.parentFile, "$DB_NAME-wal").delete()
        File(target.parentFile, "$DB_NAME-shm").delete()
        SQLiteDatabase.openOrCreateDatabase(target, null).use { db ->
            db.version = userVersion
        }
    }

    private companion object {
        const val DB_NAME = "personal_trainer.db"
    }
}

/**
 * A private files/database root so this class cannot see Room's WAL from other tests.
 *
 * [ContextWrapper.deleteDatabase] and [ContextWrapper.getDatabasePath] both delegate to the
 * base context, which is the shared Robolectric application. Override both, or a leftover
 * `personal_trainer.db-wal` from a previous class lands in the copy this plants as text.
 */
private class IsolatedSnapshotContext(base: Context) : ContextWrapper(base) {
    private val root = File(base.cacheDir, "pre-migration-snapshot-test").also { dir ->
        dir.deleteRecursively()
        dir.mkdirs()
    }
    private val databases = File(root, "databases").also { it.mkdirs() }
    private val files = File(root, "files").also { it.mkdirs() }

    override fun getDatabasePath(name: String): File = File(databases, name)

    override fun getFilesDir(): File = files

    override fun deleteDatabase(name: String): Boolean {
        var deleted = true
        listOf("", "-wal", "-shm").forEach { suffix ->
            val file = File(databases, name + suffix)
            if (file.exists() && !file.delete()) deleted = false
        }
        return deleted
    }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences("snapshot-test-$name", mode)
}
