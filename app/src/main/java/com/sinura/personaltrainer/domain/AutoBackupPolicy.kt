package com.sinura.personaltrainer.domain

/**
 * Whether a just-finished workout should be copied to Drive without being asked.
 *
 * Backup is the thing that makes training history survive a lost phone, and until this
 * existed the only way to take one was to remember to open Settings and tap. A caption
 * after fourteen days ([BackupPrompt]) is a reminder, not a copy.
 *
 * Pure on purpose: every input is read once from preferences by the caller and passed in,
 * so the decision is a table rather than a sequence of suspending reads that could observe
 * two different generations of the same toggle.
 */
object AutoBackupPolicy {
    /** Shown under the summary while the copy is going out. */
    const val RUNNING = "Backing up…"

    /** Shown when the copy landed. Names no file: the summary is a celebration, not Settings. */
    const val DONE = "Backed up to Drive."

    /**
     * The grant lapsed. Deliberately not phrased as an error the owner caused — a Google
     * authorization for an app in Testing expires on its own, roughly weekly.
     */
    const val NEEDS_SIGN_IN = "Automatic backup paused — sign in to Drive again in Settings."

    /**
     * Anything else: offline, Drive refused, the envelope would not wrap. The workout is
     * saved locally either way, and saying so is the point of the sentence.
     */
    const val FAILED = "Could not back up to Drive. Your workout is saved on this phone."

    /**
     * @param enabled the owner turned automatic backup on.
     * @param hasStoredPassphrase a sealed passphrase is present and openable. Without one a
     *   backup could only be written as plaintext, which ADR-009 §9 makes a warned choice and
     *   never a default — so no passphrase means no automatic copy, not an unprotected one.
     * @param lastBackedUpSessionId the session the last automatic copy covered, persisted.
     *   Compared rather than remembered in memory because the summary screen is re-created
     *   with the same session after process death, which would otherwise upload twice.
     * @param sessionId the session whose summary is on screen.
     */
    fun shouldBackUp(
        enabled: Boolean,
        hasStoredPassphrase: Boolean,
        lastBackedUpSessionId: String?,
        sessionId: String,
    ): Boolean {
        if (!enabled) return false
        if (!hasStoredPassphrase) return false
        if (sessionId.isBlank()) return false
        return sessionId != lastBackedUpSessionId
    }
}
