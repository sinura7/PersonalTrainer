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

    /**
     * A backup went to Drive, but reading it back failed. Distinct copy on purpose: telling
     * someone there is "no backup" when a file plainly uploaded reads as a bug and gets
     * ignored, and the actual state — a copy nothing has proven readable — is the one worth
     * acting on.
     */
    const val UNVERIFIED_CAPTION =
        "A backup was uploaded but could not be read back, so nothing has proven it opens. " +
            "Export to file as well. " + FRESH_CAPTION

    /**
     * Staleness is measured from the last **verified** backup, not the last written one.
     * A file that has never been read back is not evidence that the history survives.
     */
    fun isStale(lastVerifiedAt: Long?, nowMs: Long): Boolean {
        if (lastVerifiedAt == null) return true
        return nowMs - lastVerifiedAt >= STALE_AFTER_MS
    }

    fun caption(stale: Boolean): String = if (stale) STALE_CAPTION else FRESH_CAPTION

    /**
     * The one line Settings shows. Three states, one slot: proven, written-but-unproven, and
     * nothing recent.
     */
    fun caption(lastVerifiedAt: Long?, lastBackupAt: Long?, nowMs: Long): String {
        val stale = isStale(lastVerifiedAt, nowMs)
        val wroteRecently = lastBackupAt != null && nowMs - lastBackupAt < STALE_AFTER_MS
        return if (stale && wroteRecently) {
            UNVERIFIED_CAPTION
        } else {
            caption(stale)
        }
    }
}
