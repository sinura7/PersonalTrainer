package com.sinura.personaltrainer.data.local

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import com.sinura.personaltrainer.logging.AppLog
import java.io.File

/**
 * A byte-for-byte copy of the v1 database, taken before anything can open it.
 *
 * This is the entire rollback story for the v1 → v2 migration, and it exists because every
 * other route is closed: Room refuses to open a database whose version is ahead of the code,
 * so downgrading the app cannot recover a migrated file, and v1 builds explicitly reject v2
 * backup JSON, so an export taken after the upgrade is unreadable by the version you would be
 * rolling back to. If the migration corrupts the owner's history, this copy is what a
 * corrective release reads.
 *
 * Three properties make it work, and all three are easy to lose:
 *
 * 1. **It runs before the container exists.** `AppContainer`'s constructor builds the Room
 *    instance, and Room migrates on open — so a copy taken after that point is a copy of the
 *    already-migrated file.
 * 2. **It copies the sidecars.** A WAL-mode database is not one file. Copying only
 *    `personal_trainer.db` without `-wal` loses every committed transaction still in the log.
 * 3. **It never overwrites.** The copy is taken once, at the last moment the file was still
 *    v1, and kept forever. A "refresh the backup" pass would eventually overwrite the v1 copy
 *    with a v2 one and quietly delete the only thing worth having.
 * 4. **It never copies a migrated file.** If the first attempt failed and Room then opened
 *    the live database, a later launch must not write that v2 file into `pre-migration/v1`.
 *    An incomplete copy is retried only while `user_version` is still 1.
 *
 * It also never throws. A phone that cannot be opened because its rollback copy failed is
 * strictly worse than a phone with no rollback copy: on failure the marker is left unwritten
 * so the next launch retries, and the app carries on.
 *
 * The copy lives in app-private storage and dies with an uninstall, which is why the upgrade
 * runbook also has the owner take a JSON export they keep off the phone.
 */
object PreMigrationSnapshot {
    const val PREFS_NAME = "schema_marker"
    const val KEY_LAST_OPENED_SCHEMA = "lastOpenedSchemaVersion"
    const val TARGET_SCHEMA = 2

    private const val TAG = "PT/PreMigrationSnapshot"
    private const val DB_NAME = "personal_trainer.db"
    private val SIDECARS = listOf("", "-wal", "-shm")

    /**
     * Call FIRST in `Application.onCreate`, before `AppContainer` exists. Synchronous by
     * design — the copy has to complete before Room can touch the file — and does real work at
     * most once per install lifetime.
     */
    fun ensure(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_LAST_OPENED_SCHEMA, 0) >= TARGET_SCHEMA) return

        val source = context.getDatabasePath(DB_NAME)
        if (!source.exists()) {
            // Fresh install: there is no v1 database to preserve, and never will be.
            writeMarker(prefs)
            return
        }

        val destination = File(context.filesDir, "pre-migration/v1")
        val sourceParent = source.parentFile
        val alreadyMigrated = userVersion(source).let { it != null && it >= TARGET_SCHEMA }

        if (destination.isDirectory) {
            // An earlier run copied something and died before writing the marker.
            // A complete copy is the pre-v2 file and must be kept. An incomplete copy
            // can be retried only while the live file is still v1 — recopying after
            // Room has migrated would write v2 into the only rollback path.
            if (snapshotComplete(sourceParent, destination) || alreadyMigrated) {
                writeMarker(prefs)
                return
            }
            destination.deleteRecursively()
        }

        if (alreadyMigrated) {
            // Missed the window. Never write a v2 file into the v1 rollback folder.
            writeMarker(prefs)
            AppLog.w(TAG, "Live database is already schema $TARGET_SCHEMA; skipped v1 rollback copy")
            return
        }

        try {
            if (!destination.mkdirs() && !destination.isDirectory) {
                error("Could not create ${destination.absolutePath}")
            }
            SIDECARS.forEach { suffix ->
                val file = File(sourceParent, DB_NAME + suffix)
                if (file.exists()) {
                    file.copyTo(File(destination, DB_NAME + suffix), overwrite = false)
                }
            }
            writeMarker(prefs)
            AppLog.d(TAG, "Pre-migration copy written to ${destination.absolutePath}")
        } catch (error: Exception) {
            // Marker deliberately left unwritten so the next launch tries again. Opening the
            // app matters more than having the copy — but the retry must refuse a v2 source.
            AppLog.e(TAG, "Pre-migration copy failed; continuing without it", error)
        }
    }

    /**
     * SQLite `user_version`, or null when the file is missing or is not a database
     * (the unit tests write plain text stand-ins).
     */
    internal fun userVersion(file: File): Int? {
        if (!file.exists() || file.length() < SQLITE_HEADER_BYTES) return null
        return try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                .use { it.version }
        } catch (_: Exception) {
            null
        }
    }

    private fun snapshotComplete(sourceParent: File?, destination: File): Boolean {
        val main = File(destination, DB_NAME)
        if (!main.exists() || main.length() == 0L) return false
        SIDECARS.filter { it.isNotEmpty() }.forEach { suffix ->
            val sourceSidecar = File(sourceParent, DB_NAME + suffix)
            if (sourceSidecar.exists() && !File(destination, DB_NAME + suffix).exists()) {
                return false
            }
        }
        return true
    }

    private fun writeMarker(prefs: SharedPreferences) {
        prefs.edit().putInt(KEY_LAST_OPENED_SCHEMA, TARGET_SCHEMA).commit()
    }

    private const val SQLITE_HEADER_BYTES = 100L
}
