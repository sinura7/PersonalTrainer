package com.sinura.personaltrainer.data.backup

/**
 * On-disk restore phases. Process death mid-restore must resume the
 * committed work, not report failure over a mixed phone.
 *
 * Room replacement is one transaction, so [WIPING] is binary on recovery:
 * the before-fingerprint means the wipe rolled back; the after-fingerprint
 * means Room already holds the incoming file.
 */
data class RestoreJournalRecord(
    val phase: String,
    val sourceName: String,
    val snapshotId: String,
    val beforeFingerprint: String,
    val afterFingerprint: String,
)

object RestoreJournal {
    const val STAGED = "staged"
    const val WIPING = "wiping"
    const val ROOM = "room"
    const val PREFS = "prefs"

    const val STATE_FILE = "state.json"
    const val INCOMING_FILE = "incoming.json"

    const val RECOVERED_MIXED =
        "Training data was replaced from that backup. Settings will finish applying " +
            "the next time the app opens."
    const val INTERRUPTED =
        "A restore was interrupted. Temper is finishing it from the copy already on this phone."

    /**
     * The Room-transaction witness. Bodyweight entries and blocks are restored
     * by the PREFERENCES phase, not by replaceRoom's transaction, so including
     * them compared the document's counts against stores the wipe never wrote:
     * any cross-device or older-file restore then read as "rolled back" on
     * WIPING recovery, cleared the journal, and silently dropped the
     * preferences half of the restore.
     */
    fun fingerprint(authored: AuthoredInventory, firstSessionId: String?): String =
        listOf(
            authored.sessions,
            authored.setLogs,
            authored.routines,
            authored.customExercises,
            authored.scheduleSlots,
            firstSessionId.orEmpty(),
        ).joinToString("|")

    fun fingerprint(document: BackupDocument): String =
        fingerprint(
            authored = AuthoredInventory.fromDocument(document),
            firstSessionId = document.sessions.minByOrNull { it.id }?.id,
        )
}
