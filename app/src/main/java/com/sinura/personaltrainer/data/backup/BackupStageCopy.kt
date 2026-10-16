package com.sinura.personaltrainer.data.backup

/**
 * The heading for a backup or restore outcome.
 *
 * The body is the message the operation already produced. The heading says which
 * kind of stop it was, so a wrong password does not look like a dead network.
 */
object BackupStageCopy {
    const val WRONG_PASSWORD = "Wrong password"
    const val NEWER_BACKUP = "Newer backup"
    const val TOO_BIG = "File is too big"
    const val OFFLINE = "Drive needs a connection"
    const val SETTINGS_STILL_OWED = "Settings still to apply"
    const val LIVE_SESSION =
        "Finish or discard the live session before restoring. A restore would " +
            "delete the session you are standing in."
    const val PARTIAL_SETTINGS = "Training data is in. Settings still need to be applied."
    const val GENERIC = "Backup didn't finish"

    fun titleFor(message: String): String = when {
        message.contains(BackupEnvelope.WRONG_PASSWORD) -> WRONG_PASSWORD
        message.contains("newer app version") -> NEWER_BACKUP
        message.contains(BackupScaleBudget.TOO_BIG_TO_IMPORT) ||
            message.contains(BackupScaleBudget.TOO_BIG_TO_EXPORT) -> TOO_BIG
        message.contains("Connect to the internet") -> OFFLINE
        message.contains(RestoreJournal.SETTINGS_PENDING) -> SETTINGS_STILL_OWED
        else -> GENERIC
    }

    /** A success line is the one written after the restore commit returns. */
    fun isCompletedRestore(status: String): Boolean = status.startsWith("Restored ")

    /**
     * The sentence after a restore commit. Only a full settings write starts with
     * "Restored ". A week that still owes settings must not, or it is headed as finished.
     */
    fun restoreStatus(
        sourceName: String,
        summary: String,
        preferencesRestored: Boolean,
        settingsPending: Boolean,
    ): String {
        val safety = "A verified copy of the previous data is under Safety copies on this phone."
        val done = "Restored $sourceName — $summary. $safety"
        return when {
            preferencesRestored -> done
            settingsPending -> "$PARTIAL_SETTINGS ${RestoreJournal.SETTINGS_PENDING} $safety"
            else -> "Your training data is in; settings couldn't be applied, so check units and rest defaults. $safety"
        }
    }
}

/**
 * Takes the pending restore once. The slot is empty before the commit runs, so a
 * second tap has nothing left to commit.
 */
object RestoreCommitGate {
    fun <T> claim(current: T?): Claimed<T> = Claimed(slot = null, taken = current)
}

data class Claimed<T>(val slot: T?, val taken: T?)
