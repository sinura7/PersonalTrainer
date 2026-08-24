package com.sinura.personaltrainer.data.local

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The manifest change must not wipe a phone that already has history.
 *
 * An upgrade is a new process on the same files. This opens a file-backed
 * Room database, writes a finished session, closes it, and reopens the same
 * file. Auto Backup is off, so the OS will not replace those files.
 */
@RunWith(RobolectricTestRunner::class)
class UpgradeInPlaceTest {
    private lateinit var context: Context
    private val dbName = "upgrade-inplace-${System.nanoTime()}.db"

    @Before
    fun setUp() {
        context = IsolatedUpgradeContext(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
        File(context.filesDir, SENTINEL_FILE).delete()
    }

    @Test
    fun finishedSessionAndPrivateFileSurviveCloseAndReopen() = runBlocking {
        File(context.filesDir, SENTINEL_FILE).writeText(SENTINEL_BODY)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY, SENTINEL_BODY)
            .commit()

        openDb().use { first ->
            first.workoutDao().upsertSession(sentinelSession())
            assertEquals(SENTINEL_ID, first.workoutDao().getSessionRow(SENTINEL_ID)?.id)
        }

        assertTrue(File(context.filesDir, SENTINEL_FILE).readText() == SENTINEL_BODY)
        assertEquals(
            SENTINEL_BODY,
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREFS_KEY, null),
        )

        openDb().use { second ->
            val row = checkNotNull(second.workoutDao().getSessionRow(SENTINEL_ID))
            assertEquals("Upgrade proof", row.routineName)
            assertEquals("must survive reopen", row.notes)
            assertTrue(row.finishedAt != null)
        }
    }

    private fun openDb(): TrainerDatabase =
        Room.databaseBuilder(context, TrainerDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

    private fun sentinelSession() = WorkoutSessionEntity(
        id = SENTINEL_ID,
        routineId = null,
        routineName = "Upgrade proof",
        date = STAMP,
        notes = "must survive reopen",
        durationMinutes = 40,
        startedAt = STAMP,
        finishedAt = STAMP + 2_400_000,
    )

    private companion object {
        const val SENTINEL_ID = "upgrade-inplace-sentinel"
        const val SENTINEL_FILE = "upgrade-inplace-sentinel.txt"
        const val SENTINEL_BODY = "temper-upgrade-inplace"
        const val PREFS = "upgrade_inplace_marker"
        const val PREFS_KEY = "body"
        const val STAMP = 1_755_000_000_000L
    }
}

private class IsolatedUpgradeContext(base: Context) : ContextWrapper(base) {
    private val root = File(base.cacheDir, "upgrade-inplace-${System.nanoTime()}").also { it.mkdirs() }

    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }

    override fun getDatabasePath(name: String): File =
        File(File(root, "databases").also { it.mkdirs() }, name)
}

private inline fun TrainerDatabase.use(block: (TrainerDatabase) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}
