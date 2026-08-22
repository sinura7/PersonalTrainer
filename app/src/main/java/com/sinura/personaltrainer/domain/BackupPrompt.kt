package com.sinura.personaltrainer.domain

/**
 * When Settings should nag about a backup — before they need the file.
 *
 * Restore and the live-session rule do not change. Export and Drive stay the taps.
 * This is a caption, not a second volt.
 */
object BackupPrompt {
    const val STALE_AFTER_MS = 14L * 24 * 60 * 60 * 1000

    const val FRESH_CAPTION =
        "Training always works offline — a backup is only read when you ask for one. " +
            "The file path needs no Google account, and still works if sign-in ever breaks. " +
            "An in-progress workout is left out of the file."

    const val STALE_CAPTION =
        "No backup in the last 14 days. Export one before you need the file. " +
            FRESH_CAPTION

    fun isStale(lastBackupAt: Long?, nowMs: Long): Boolean {
        if (lastBackupAt == null) return true
        return nowMs - lastBackupAt >= STALE_AFTER_MS
    }

    fun caption(stale: Boolean): String = if (stale) STALE_CAPTION else FRESH_CAPTION
}
