package com.sinura.personaltrainer.data.local

import android.content.Context
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
            prefs.edit().putInt(KEY_LAST_OPENED_SCHEMA, TARGET_SCHEMA).apply()
            return
        }

        val destination = File(context.filesDir, "pre-migration/v1")
        if (destination.exists()) {
            // An earlier run copied the file and died before writing the marker. The copy on
            // disk is the pre-v2 one; taking it again now would capture a migrated file.
            prefs.edit().putInt(KEY_LAST_OPENED_SCHEMA, TARGET_SCHEMA).apply()
            return
        }

        try {
            if (!destination.mkdirs() && !destination.isDirectory) {
                error("Could not create ${destination.absolutePath}")
            }
            SIDECARS.forEach { suffix ->
                val file = File(source.parentFile, DB_NAME + suffix)
                if (file.exists()) {
                    file.copyTo(File(destination, DB_NAME + suffix), overwrite = false)
                }
            }
            prefs.edit().putInt(KEY_LAST_OPENED_SCHEMA, TARGET_SCHEMA).apply()
            AppLog.d(TAG, "Pre-migration copy written to ${destination.absolutePath}")
        } catch (error: Exception) {
            // Marker deliberately left unwritten so the next launch tries again. Opening the
            // app matters more than having the copy.
            AppLog.e(TAG, "Pre-migration copy failed; continuing without it", error)
        }
    }
}
