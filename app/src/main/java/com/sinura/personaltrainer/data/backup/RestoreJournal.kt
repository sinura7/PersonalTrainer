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
     * A restore that threw before the wipe. The phone holds exactly what it held before, and
     * the one thing the owner needs to know is that nothing of theirs is at risk.
     */
    const val NOTHING_CHANGED = "Restore failed. Nothing was changed."

    /** Narrower: it never got as far as trying. Same reassurance, more accurate. */
    const val NOTHING_STARTED = "Restore could not start. Nothing was changed."

    /**
     * What to tell the owner about a restore that threw, given how far it got.
     *
     * @param phase the journal's phase at the moment of the failure, or null if it is closed.
     * @param reported the message the failure itself carried, if any.
     *
     * Only the phases past the wipe have replaced anything, and only they may say so. [STAGED]
     * and a closed journal have not, and their message must never be [INTERRUPTED] — a
     * journal-write failure underneath throws exactly that, and "Temper is finishing it from
     * the copy already on this phone" then promises a recovery that is not going to happen,
     * about data that was never touched. That sentence, shown for a restore that failed
     * before it started, is the whole of the symptom this rule exists to remove.
     */
    fun commitFailureMessage(phase: String?, reported: String?): String = when (phase) {
        ROOM, PREFS, WIPING -> RECOVERED_MIXED
        else -> if (reported.isNullOrBlank() || reported == INTERRUPTED) {
            NOTHING_CHANGED
        } else {
            reported
        }
    }

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
