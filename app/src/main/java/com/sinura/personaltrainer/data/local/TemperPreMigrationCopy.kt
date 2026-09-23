package com.sinura.personaltrainer.data.local

import android.content.Context
import android.content.SharedPreferences
import com.sinura.personaltrainer.logging.AppLog
import java.io.File
import java.io.FileOutputStream

/**
 * A byte-for-byte copy of `temper.db`, taken before Room can migrate it (audit X2b, ADR-010
 * decision 12).
 *
 * [PreMigrationSnapshot] did this once, for the legacy `personal_trainer.db` v1 → v2, and its
 * marker is set on every phone. Every later schema bump migrates `temper.db` — the owner's whole
 * training history — and until this there was nothing to roll back to if a migration went
 * wrong: Room refuses to open a file newer than the code, so a downgrade cannot read it.
 *
 * The rules, and why:
 *
 * 1. **Before anything opens the database.** It runs from [PreMigrationSnapshot.ensure], first
 *    in `Application.onCreate`, before `AppContainer` builds Room.
 * 2. **The sidecars too.** `-wal` holds committed transactions not yet in the main file.
 *    Nothing is checkpointed first. A `-journal` is copied when there is one.
 * 3. **All or nothing.** The copy is written into `temper-v<from>.partial`, each file synced to
 *    disk, and only then renamed to `temper-v<from>`. A folder with that final name is a whole
 *    copy and is never replaced. A `.partial` is only ever what a launch that died left, so every
 *    one is thrown away before anything else, and the copy is taken again. A copy truncated by a
 *    killed process or a power cut cannot pass as done.
 * 4. **Never a migrated file.** The version is read through SQLite, not the header bytes — the
 *    migration's own commit can still be in the WAL — and a file already at
 *    [FoundationGeneration.VERSION] is never copied.
 *
 * The copy just taken is always kept, with the newest older ones up to [KEEP] in all. Only
 * folders named `temper-v<n>` with *n* below the code's version count as copies; anything else
 * there, the legacy `pre-migration/v1` copy included, is never touched. `pre-migration` is
 * excluded from device backup by name (ADR-009 decision 4).
 *
 * It never throws and never blocks launch (owner decision, 23 September 2026). When the copy
 * cannot be taken, Room migrates anyway in the same launch, so **that schema bump has no rollback
 * copy**; the failure is logged. That happens when writing fails (a full or failing disk), when
 * there is too little free space to copy and still leave the migration room to run (see
 * [hasRoomFor]), and when the version cannot be read — which includes a hot rollback journal left
 * by a crash, since SQLite will not open that file read-only. A power cut in the moments after
 * the copy can lose it too: the files are synced, the folder's rename is not. The owner's exports
 * and Drive backup remain the authoritative recovery path (ADR-009 decision 1); this is the one
 * that needs no preparation.
 */
internal object TemperPreMigrationCopy {
    /** The newest code version that has already checked this install. Not the legacy key. */
    const val KEY_CHECKED_VERSION = "temperSnapshotCheckedVersion"
    const val KEEP = 2

    private const val TAG = "PT/TemperPreMigrationCopy"
    private const val DIRECTORY = "pre-migration"
    private const val FOLDER_PREFIX = "temper-v"
    private const val PARTIAL = ".partial"
    private val FOLDER = Regex("$FOLDER_PREFIX(\\d+)")
    private val PARTIAL_FOLDER = Regex("$FOLDER_PREFIX\\d+\\.partial")
    private val SIDECARS = listOf("", "-wal", "-shm", "-journal")

    /** Free space left for the migration itself after the copy: table rebuilds and their WAL. */
    internal const val MIGRATION_HEADROOM_BYTES = 32L * 1024 * 1024

    fun ensure(
        context: Context,
        targetVersion: Int = FoundationGeneration.VERSION,
        usableBytes: (File) -> Long = ::freeBytes,
        copyFile: (from: File, to: File) -> Unit = ::copySynced,
    ) {
        val prefs = context.getSharedPreferences(PreMigrationSnapshot.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_CHECKED_VERSION, 0) >= targetVersion) return

        val root = File(context.filesDir, DIRECTORY)
        discardPartials(root)

        val source = context.getDatabasePath(FoundationGeneration.DATABASE_FILE)
        if (!source.exists()) {
            // A fresh install: nothing to preserve for this version of the code.
            markChecked(prefs, targetVersion)
            return
        }
        val onDisk = PreMigrationSnapshot.userVersion(source)
        if (onDisk == null) {
            // Unreadable — corrupt, or a hot rollback journal from a crash. Unmarked, so a
            // launch that can read it looks again; Room may well migrate this launch regardless.
            AppLog.w(TAG, "Could not read the database version; no pre-migration copy this launch")
            return
        }
        if (onDisk <= 0 || onDisk >= targetVersion) {
            // Nothing Room will migrate: an empty file, the current schema, or a newer one.
            markChecked(prefs, targetVersion)
            return
        }

        val finished = File(root, "$FOLDER_PREFIX$onDisk")
        if (finished.isDirectory) {
            // Written and renamed by an earlier launch that died before the marker.
            prune(root, keep = finished, below = targetVersion)
            markChecked(prefs, targetVersion)
            return
        }
        val sources = SIDECARS.map { File(source.path + it) }.filter { it.exists() }
        val needed = sources.sumOf { it.length() }
        if (!hasRoomFor(needed, usableBytes(context.filesDir))) {
            AppLog.w(TAG, "Too little free space to copy schema $onDisk and still migrate; no copy")
            return
        }

        val partial = File(root, "$FOLDER_PREFIX$onDisk$PARTIAL")
        try {
            if (!partial.mkdirs()) error("Could not create the pre-migration folder")
            sources.forEach { file -> copyFile(file, File(partial, file.name)) }
            if (!partial.renameTo(finished)) error("Could not finish the pre-migration folder")
            AppLog.d(TAG, "Copied schema $onDisk before migrating to $targetVersion")
        } catch (error: Exception) {
            // Half a copy is not a copy. Unmarked: this launch migrates without one.
            partial.deleteRecursively()
            AppLog.e(TAG, "Pre-migration copy failed; migrating without it", error)
            return
        }
        prune(root, keep = finished, below = targetVersion)
        markChecked(prefs, targetVersion)
    }

    /**
     * Whether a copy of [bytes] fits and still leaves the migration room to work: it can rebuild
     * tables to about the database's own size again, plus its WAL. Skipping the copy on a nearly
     * full phone keeps the migration from failing for the space the copy took.
     */
    internal fun hasRoomFor(bytes: Long, usable: Long): Boolean =
        usable >= bytes * 3 + MIGRATION_HEADROOM_BYTES

    /**
     * Space a plain write can use right now. Not `StorageManager.getAllocatableBytes`: that also
     * counts other apps' caches the system could clear, but neither this copy nor Room's
     * migration asks it to, so on a nearly full phone the migration would meet a full disk.
     */
    @Suppress("UsableSpace")
    private fun freeBytes(dir: File): Long = dir.usableSpace

    /**
     * Nothing has started a copy yet this launch, so every `.partial` is one a dead launch left:
     * never a copy, and space the free-space check should count as free.
     */
    private fun discardPartials(root: File) {
        root.listFiles().orEmpty()
            .filter { PARTIAL_FOLDER.matches(it.name) }
            .forEach { it.deleteRecursively() }
    }

    private fun copySynced(from: File, to: File) {
        from.inputStream().use { input ->
            FileOutputStream(to).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    /**
     * Keeps [keep] and the newest other copies up to [KEEP] in all. A copy is a folder named
     * `temper-v<n>` with *n* below [below]: this code never writes any other, so anything else is
     * left alone and not counted.
     */
    private fun prune(root: File, keep: File, below: Int) {
        try {
            root.listFiles().orEmpty()
                .filter { it != keep && it.isDirectory }
                .mapNotNull { folder ->
                    FOLDER.matchEntire(folder.name)?.groupValues?.get(1)?.toLongOrNull()
                        ?.takeIf { it < below }
                        ?.let { it to folder }
                }
                .sortedByDescending { it.first }
                .drop(KEEP - 1)
                .forEach { (_, folder) -> folder.deleteRecursively() }
        } catch (error: Exception) {
            // Tidying is not worth a copy: the one just written stays either way.
            AppLog.w(TAG, "Could not prune older pre-migration copies", error)
        }
    }

    @Suppress("ApplySharedPref")
    private fun markChecked(prefs: SharedPreferences, version: Int) {
        // commit(): the next launch reads this before anything else.
        prefs.edit().putInt(KEY_CHECKED_VERSION, version).commit()
    }
}
