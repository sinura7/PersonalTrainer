package com.sinura.personaltrainer.data.local

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The copy of `temper.db` taken before Room can migrate it (audit X2b).
 *
 * The legacy copy ([PreMigrationSnapshot]) covers only `personal_trainer.db` v1 → v2, and its
 * marker is already set on every phone, so without this the next `temper.db` bump (S2b's v8)
 * would migrate the owner's whole history with nothing to roll back to. These plant a real
 * older `temper.db` — Room's own v6 schema, a finished workout in it, a transaction still in the
 * WAL — and hold that the copy is the file as it stood before the migration, sidecars and all.
 */
@RunWith(RobolectricTestRunner::class)
class TemperPreMigrationCopyTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TemperDatabase::class.java,
    )

    private lateinit var context: IsolatedCopyContext

    @Before
    fun setUp() {
        context = IsolatedCopyContext(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        context.root.deleteRecursively()
    }

    @Test
    fun anOlderTemperDatabaseIsCopiedWithItsWalEvenAfterTheLegacyCopyIsDone() {
        plantV6()
        // Every phone already carries the legacy marker; the temper copy must not hide behind it.
        markLegacySnapshotDone()
        // A transaction committed but still in the WAL: copying only temper.db would lose it.
        val writer = openWalWriter()
        try {
            writer.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, notes, isCustom) " +
                    "VALUES ('wal-only', 'Front squat', 'Quads', '', 1)",
            )
            assertTrue("the planted WAL must hold the last write", liveFile("-wal").length() > 0)

            PreMigrationSnapshot.ensure(context)

            val copy = copyDir(fromVersion = 6)
            assertArrayEquals(liveFile("").readBytes(), File(copy, DB_NAME).readBytes())
            assertArrayEquals(liveFile("-wal").readBytes(), File(copy, "$DB_NAME-wal").readBytes())
            assertTrue(File(copy, "$DB_NAME-shm").isFile)
        } finally {
            writer.close()
        }
        openCopy(fromVersion = 6).use { copy ->
            assertEquals(6, copy.version)
            assertEquals(1, countWhere(copy, "workout_sessions", "id = 's1'"))
            assertEquals(1, countWhere(copy, "exercises", "id = 'wal-only'"))
        }
        assertEquals(FoundationGeneration.VERSION, checkedVersion())
    }

    @Test
    fun theCopyStillHoldsTheOldFileAfterRoomMigratesTheLiveOne() {
        plantV6()

        PreMigrationSnapshot.ensure(context)
        val live = TemperDatabase.create(context)
        try {
            assertEquals(FoundationGeneration.VERSION, live.openHelper.writableDatabase.version)
        } finally {
            live.close()
        }

        openCopy(fromVersion = 6).use { copy ->
            assertEquals("the copy is the file before the migration", 6, copy.version)
            assertEquals(1, countWhere(copy, "workout_sessions", "id = 's1'"))
        }
    }

    @Test
    fun aCurrentDatabaseIsNeverCopied() {
        plantCurrent()

        PreMigrationSnapshot.ensure(context)

        assertFalse(temperCopies().any())
        assertEquals(FoundationGeneration.VERSION, checkedVersion())
    }

    @Test
    fun aFreshInstallCopiesNothingAndIsNotCheckedAgain() {
        PreMigrationSnapshot.ensure(context)

        assertFalse(File(context.filesDir, "pre-migration").exists())
        assertEquals(FoundationGeneration.VERSION, checkedVersion())
    }

    @Test
    fun aCompleteCopyIsNeverOverwritten() {
        plantV6()
        val copy = copyDir(fromVersion = 6).also { it.mkdirs() }
        File(copy, DB_NAME).writeText("the copy taken last launch")

        PreMigrationSnapshot.ensure(context)

        assertEquals("the copy taken last launch", File(copy, DB_NAME).readText())
        assertEquals(FoundationGeneration.VERSION, checkedVersion())
    }

    @Test
    fun aCopyLeftHalfWrittenByADeadLaunchIsTakenAgainNotKept() {
        plantV6()
        // A launch killed mid-copy leaves the .partial folder with a truncated main file and no
        // sidecars. It must never pass as a copy.
        val partial = File(context.filesDir, "pre-migration/temper-v6.partial").also { it.mkdirs() }
        File(partial, DB_NAME).writeBytes(liveFile("").readBytes().copyOf(4096))
        // A sidecar the live file no longer has: it must not ride into the new copy.
        File(partial, "$DB_NAME-journal").writeText("from the dead launch")

        PreMigrationSnapshot.ensure(context)

        assertFalse(partial.exists())
        assertArrayEquals(liveFile("").readBytes(), File(copyDir(fromVersion = 6), DB_NAME).readBytes())
        assertFalse(File(copyDir(fromVersion = 6), "$DB_NAME-journal").exists())
        openCopy(fromVersion = 6).use { copy -> assertEquals(1, countWhere(copy, "workout_sessions", "id = 's1'")) }
    }

    @Test
    fun aCopyThatFailsPartWayIsNeverKeptAndTheNextLaunchTakesItWhole() {
        plantV6()
        val writer = openWalWriter()
        try {
            writer.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, notes, isCustom) " +
                    "VALUES ('wal-only', 'Front squat', 'Quads', '', 1)",
            )
            // The main file is copied, then the disk fails on the WAL.
            var copied = 0
            val failsOnTheSecondFile: (File, File) -> Unit = { from, to ->
                copied += 1
                if (copied == 2) throw IOException("the disk failed mid-copy")
                from.copyTo(to)
            }

            TemperPreMigrationCopy.ensure(context, copyFile = failsOnTheSecondFile)

            assertEquals("the main file was copied before the failure", 2, copied)
            assertFalse("half a copy never takes the finished name", copyDir(fromVersion = 6).exists())
            assertFalse(File(context.filesDir, "pre-migration/temper-v6.partial").exists())
            assertEquals(0, checkedVersion())

            TemperPreMigrationCopy.ensure(context)

            assertArrayEquals(liveFile("").readBytes(), File(copyDir(fromVersion = 6), DB_NAME).readBytes())
            assertArrayEquals(liveFile("-wal").readBytes(), File(copyDir(fromVersion = 6), "$DB_NAME-wal").readBytes())
            assertEquals(FoundationGeneration.VERSION, checkedVersion())
        } finally {
            writer.close()
        }
    }

    @Test
    fun aCopyFinishedByALaunchThatDiedBeforeItsMarkerIsKeptAndOlderOnesAreStillTidied() {
        plantV6()
        listOf(4, 5, 6).forEach { version ->
            File(copyDir(fromVersion = version).also { it.mkdirs() }, DB_NAME).writeText("copy of v$version")
        }

        PreMigrationSnapshot.ensure(context)

        assertEquals("copy of v6", File(copyDir(fromVersion = 6), DB_NAME).readText())
        assertEquals(listOf(5, 6), temperCopies().sorted())
        assertEquals(FoundationGeneration.VERSION, checkedVersion())
    }

    @Test
    fun aLeftoverJournalIsCopiedWithTheFile() {
        plantV6()
        // A journal SQLite keeps in PERSIST mode: zeroed, not hot, so the file still opens
        // read-only. It is part of the file's state on disk and goes with the copy.
        liveFile("-journal").writeBytes(ByteArray(512))

        PreMigrationSnapshot.ensure(context)

        assertArrayEquals(liveFile("-journal").readBytes(), File(copyDir(fromVersion = 6), "$DB_NAME-journal").readBytes())
    }

    @Test
    fun theVersionIsReadThroughSqliteNotFromTheHeaderBytes() {
        plantV6()
        // Room's own commit of the new version can still be in the WAL: the header says 6,
        // the database says 7. A migrated file must never become the v6 rollback copy.
        val writer = openWalWriter()
        try {
            writer.version = FoundationGeneration.VERSION
            assertEquals("the header still says 6", 6, headerUserVersion(liveFile("")))

            PreMigrationSnapshot.ensure(context)

            assertFalse(temperCopies().any())
            assertEquals(FoundationGeneration.VERSION, checkedVersion())
        } finally {
            writer.close()
        }
    }

    @Test
    fun aNewerDatabaseIsNotCopied() {
        helper.createDatabase(PLANTED, FoundationGeneration.VERSION).close()
        movePlanted()
        SQLiteDatabase.openDatabase(liveFile("").path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.version = FoundationGeneration.VERSION + 1
        }

        PreMigrationSnapshot.ensure(context)

        assertFalse(temperCopies().any())
        assertEquals(FoundationGeneration.VERSION, checkedVersion())
    }

    @Test
    fun aDatabaseWithNoSchemaYetIsNotCopied() {
        liveFile("").parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(liveFile(""), null).close()

        PreMigrationSnapshot.ensure(context)

        assertFalse(temperCopies().any())
        assertEquals(FoundationGeneration.VERSION, checkedVersion())
    }

    @Test
    fun anUnreadableFileIsNotCopiedAndIsLookedAtAgain() {
        liveFile("").parentFile?.mkdirs()
        liveFile("").writeText("not a database")

        PreMigrationSnapshot.ensure(context)

        assertFalse(File(context.filesDir, "pre-migration").exists())
        assertEquals(0, checkedVersion())
    }

    @Test
    fun onANearlyFullPhoneTheCopyIsSkippedSoTheMigrationKeepsItsRoom() {
        plantV6()

        // Room for the copy, not for the migration after it.
        val nearlyFull: (File) -> Long = { liveFile("").length() * 2 }
        TemperPreMigrationCopy.ensure(context, usableBytes = nearlyFull)

        assertFalse(File(context.filesDir, "pre-migration").exists())
        assertEquals("the app still opens and migrates; nothing marked", 0, checkedVersion())
    }

    @Test
    fun aDeadLaunchsHalfCopyIsThrownAwayEvenWhenThereIsNoRoomForANewOne() {
        plantV6()
        // Half-written, and a plain-text copy of the history: it must not linger on a full phone.
        val partial = File(context.filesDir, "pre-migration/temper-v6.partial").also { it.mkdirs() }
        File(partial, DB_NAME).writeBytes(liveFile("").readBytes().copyOf(4096))

        val nearlyFull: (File) -> Long = { liveFile("").length() * 2 }
        TemperPreMigrationCopy.ensure(context, usableBytes = nearlyFull)

        assertFalse(partial.exists())
        assertFalse(copyDir(fromVersion = 6).exists())
        assertEquals(0, checkedVersion())
    }

    @Test
    fun theSpaceCheckCountsTheWalNotJustTheMainFile() {
        plantV6()
        val writer = openWalWriter()
        try {
            writer.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, notes, isCustom) " +
                    "VALUES ('wal-only', 'Front squat', 'Quads', '', 1)",
            )
            // Exactly enough for the main file alone, not for the file with its WAL.
            assertTrue(
                "room for the main file alone",
                TemperPreMigrationCopy.hasRoomFor(bytes = liveFile("").length(), usable = roomForTheMainFileOnly()),
            )
            val roomForTheMainFile: (File) -> Long = { roomForTheMainFileOnly() }

            TemperPreMigrationCopy.ensure(context, usableBytes = roomForTheMainFile)

            assertFalse(copyDir(fromVersion = 6).exists())
            assertEquals(0, checkedVersion())
        } finally {
            writer.close()
        }
    }

    @Test
    fun roomForACopyLeavesTheMigrationItsOwn() {
        val mib = 1024L * 1024
        assertTrue(TemperPreMigrationCopy.hasRoomFor(bytes = 10 * mib, usable = 62 * mib))
        assertFalse(TemperPreMigrationCopy.hasRoomFor(bytes = 10 * mib, usable = 61 * mib))
    }

    @Test
    fun aFailedCopyNeverStopsTheAppAndMarksNothing() {
        plantV6()
        // A regular file where the copy directory belongs: the copy cannot be written.
        val blocker = copyDir(fromVersion = 6)
        blocker.parentFile?.mkdirs()
        blocker.writeText("in the way")

        PreMigrationSnapshot.ensure(context)

        assertTrue("nothing of ours was deleted", blocker.isFile)
        // Unmarked. Room migrates in this same launch, so this bump goes without a copy (owner
        // decision); what matters is that the app opened and no half-copy looks like a whole one.
        assertEquals(0, checkedVersion())
        assertFalse(File(context.filesDir, "pre-migration/temper-v6.partial").exists())
    }

    @Test
    fun onlyOlderCopyFoldersCountAndAnythingElseThereIsLeftAlone() {
        listOf(4, 5).forEach { version ->
            File(copyDir(fromVersion = version).also { it.mkdirs() }, DB_NAME).writeText("copy of v$version")
        }
        // Names this code never writes: a folder at or past the code's version, a number too
        // long for an Int, a plain file. None is a copy, so none is deleted or takes a place.
        val atTheCodesVersion = copyDir(fromVersion = FoundationGeneration.VERSION).also { it.mkdirs() }
        val hugeNumber = File(context.filesDir, "pre-migration/temper-v99999999999").also { it.mkdirs() }
        val plainFile = copyDir(fromVersion = 3).also { it.writeText("not a folder") }
        val otherPartial = File(context.filesDir, "pre-migration/temper-v3.partial").also { it.mkdirs() }
        plantV6()

        PreMigrationSnapshot.ensure(context)

        assertTrue("the copy just taken stays", File(copyDir(fromVersion = 6), DB_NAME).isFile)
        assertEquals("copy of v5", File(copyDir(fromVersion = 5), DB_NAME).readText())
        assertFalse("beyond the newest two", copyDir(fromVersion = 4).exists())
        assertTrue(atTheCodesVersion.isDirectory)
        assertTrue(hugeNumber.isDirectory)
        assertTrue(plainFile.isFile)
        assertFalse("a dead launch's partial for another version goes", otherPartial.exists())
        assertEquals(FoundationGeneration.VERSION, checkedVersion())
    }

    @Test
    fun onlyTheNewestTwoTemperCopiesAreKeptAndTheLegacyCopyIsNeverTouched() {
        val legacy = File(context.filesDir, "pre-migration/v1").also { it.mkdirs() }
        File(legacy, "personal_trainer.db").writeText("legacy rollback")
        listOf(4, 5).forEach { version ->
            val old = copyDir(fromVersion = version).also { it.mkdirs() }
            File(old, DB_NAME).writeText("copy of v$version")
        }
        plantV6()

        PreMigrationSnapshot.ensure(context)

        assertEquals(listOf(5, 6), temperCopies().sorted())
        assertEquals("legacy rollback", File(legacy, "personal_trainer.db").readText())
    }

    @Test
    fun aCheckedVersionIsNotCheckedAgain() {
        context.getSharedPreferences(PreMigrationSnapshot.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(TemperPreMigrationCopy.KEY_CHECKED_VERSION, FoundationGeneration.VERSION).commit()
        plantV6()

        PreMigrationSnapshot.ensure(context)

        assertFalse("already checked at this version", temperCopies().any())
    }

    private fun plantV6() {
        helper.createDatabase(PLANTED, 6).use { db -> seedFinishedWorkout(db) }
        movePlanted()
    }

    private fun plantCurrent() {
        helper.createDatabase(PLANTED, FoundationGeneration.VERSION).close()
        movePlanted()
    }

    /** MigrationTestHelper writes to the shared Robolectric database folder; move it into ours. */
    private fun movePlanted() {
        val source = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(PLANTED)
        val target = liveFile("")
        target.parentFile?.mkdirs()
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            val from = File(source.path + suffix)
            if (from.exists()) {
                from.copyTo(File(target.path + suffix), overwrite = true)
                from.delete()
            }
        }
    }

    private fun openWalWriter(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(liveFile("").path, null, SQLiteDatabase.OPEN_READWRITE).also {
            it.enableWriteAheadLogging()
        }

    private fun openCopy(fromVersion: Int): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            File(copyDir(fromVersion), DB_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        )

    private fun countWhere(db: SQLiteDatabase, table: String, where: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table WHERE $where", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun markLegacySnapshotDone() {
        context.getSharedPreferences(PreMigrationSnapshot.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(PreMigrationSnapshot.KEY_LAST_OPENED_SCHEMA, PreMigrationSnapshot.TARGET_SCHEMA).commit()
    }

    /** Free space that just fits a copy of the main file alone and leaves the migration its room. */
    private fun roomForTheMainFileOnly(): Long =
        liveFile("").length() * 3 + TemperPreMigrationCopy.MIGRATION_HEADROOM_BYTES

    private fun checkedVersion(): Int =
        context.getSharedPreferences(PreMigrationSnapshot.PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(TemperPreMigrationCopy.KEY_CHECKED_VERSION, 0)

    private fun liveFile(suffix: String): File = File(context.getDatabasePath(DB_NAME).path + suffix)

    private fun copyDir(fromVersion: Int): File = File(context.filesDir, "pre-migration/temper-v$fromVersion")

    private fun temperCopies(): List<Int> =
        File(context.filesDir, "pre-migration").listFiles().orEmpty()
            .mapNotNull { Regex("temper-v(\\d+)").matchEntire(it.name)?.groupValues?.get(1)?.toInt() }

    /** SQLite's `user_version` as the main file's header holds it (bytes 60-63), ignoring the WAL. */
    private fun headerUserVersion(file: File): Int {
        val header = file.readBytes().copyOf(64)
        return ((header[60].toInt() and 0xff) shl 24) or ((header[61].toInt() and 0xff) shl 16) or
            ((header[62].toInt() and 0xff) shl 8) or (header[63].toInt() and 0xff)
    }

    private companion object {
        const val DB_NAME = FoundationGeneration.DATABASE_FILE
        const val PLANTED = "temper-planted-for-copy"
    }
}

/**
 * Own databases, files and preferences, so neither Room's WAL from another class nor another
 * class's schema marker can reach this one. `applicationContext` is this wrapper too:
 * [TemperDatabase.create] builds on it.
 */
private class IsolatedCopyContext(base: Context) : ContextWrapper(base) {
    val root = File(base.cacheDir, "temper-copy-${System.nanoTime()}").also { it.mkdirs() }
    private val databases = File(root, "databases").also { it.mkdirs() }
    private val files = File(root, "files").also { it.mkdirs() }
    private val prefix = root.name

    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = files

    override fun getDatabasePath(name: String): File = File(databases, name)

    override fun deleteDatabase(name: String): Boolean {
        var deleted = true
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            val file = File(databases, name + suffix)
            if (file.exists() && !file.delete()) deleted = false
        }
        return deleted
    }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences("$prefix-$name", mode)
}
