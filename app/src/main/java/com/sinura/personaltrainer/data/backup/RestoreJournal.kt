package com.sinura.personaltrainer.data.backup

/**
 * On-disk restore phases. Process death mid-restore must resume the
 * committed work, not report failure over a mixed phone.
 *
 * Room replacement is one transaction, so [WIPING] is binary on recovery:
 * Room holds either the phone's own tables or the incoming ones, and the
 * witnesses say which. [beforeWitness] and [afterWitness] are
 * [RestoreWitness] digests; the count fingerprints stay so a journal written
 * by the previous build still resolves on the old rule.
 */
data class RestoreJournalRecord(
    val phase: String,
    val sourceName: String,
    val snapshotId: String,
    val beforeFingerprint: String,
    val afterFingerprint: String,
    val beforeWitness: String? = null,
    val afterWitness: String? = null,
)

object RestoreJournal {
    const val STAGED = "staged"
    const val WIPING = "wiping"
    const val ROOM = "room"
    const val PREFS = "prefs"

    /**
     * Everything required is on the phone; only the two journal files are left to delete.
     *
     * Marked BEFORE cleanup. Without it, a crash between deleting `incoming.json` and
     * `state.json` left a `room`/`prefs` journal whose input was gone: every launch tried to
     * finish preferences from a file that did not exist, threw, and left the journal open —
     * and an open journal refused every workout start, forever.
     */
    const val DONE = "done"

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

    /** Room is restored; the preferences write failed and the journal is kept for a retry. */
    const val SETTINGS_PENDING =
        "Your training data is in. Settings could not be applied yet — Temper will finish " +
            "them the next time the app opens, or tap Finish restore."

    /**
     * Room was restored but the incoming copy is gone, so the settings half can never be
     * applied. Only a journal written by an older build's cleanup order can get here.
     */
    const val SETTINGS_LOST =
        "An interrupted restore finished without its settings. Check units, rest defaults " +
            "and your schedule in Settings."

    /**
     * Recovery could not prove which database the crash left behind, so it applied nothing
     * and closed the journal rather than guess. The safety copy taken just before that
     * restore is still under Safety copies.
     */
    const val UNRESOLVED =
        "An interrupted restore could not be verified, so its settings were not applied. " +
            "Your training data is either the backup or what was on the phone before it. " +
            "The safety copy saved just before that restore is under Safety copies."

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
    fun commitFailureMessage(phase: String?, reported: String?): String = when {
        replacedRoom(phase) -> RECOVERED_MIXED
        reported.isNullOrBlank() || reported == INTERRUPTED -> NOTHING_CHANGED
        else -> reported
    }

    /** Phases in which the Room replacement may already have committed: everything past [STAGED]. */
    fun replacedRoom(phase: String?): Boolean =
        phase == WIPING || phase == ROOM || phase == PREFS || phase == DONE

    /**
     * Whether a start may land on the tables while a journal is in [phase].
     *
     * Only [STAGED] and [WIPING] are about to replace Room. From [ROOM] on, the training data
     * is final: what recovery still owes is preferences, the history tables, the catalog
     * reconcile and reminders, all of which run under the same maintenance lock a start
     * takes. Refusing starts there turned a failed preferences write into a phone that could
     * not train until the write succeeded.
     */
    fun blocksStart(phase: String?): Boolean = phase == STAGED || phase == WIPING

    /** Phases in which recovery has work left: everything after the wipe, before [DONE]. */
    fun awaitsFinish(phase: String?): Boolean = phase == ROOM || phase == PREFS || phase == WIPING

    /**
     * The legacy Room-transaction witness: five counts and the lowest session id. Kept
     * only to resolve a journal written before [RestoreWitness] existed. Two backups with
     * the same counts and ids but different content share it, which is why it is no longer
     * written as the deciding evidence.
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

/**
 * What a launch-time recovery pass found and did. Every branch is a durable outcome the
 * caller can log or show; none of them is a bare Boolean.
 */
sealed interface RestoreRecovery {
    /** No journal was open. */
    data object None : RestoreRecovery

    /** A journal was open but Room had not been replaced; it was closed. Nothing changed. */
    data object NothingChanged : RestoreRecovery

    /** Room, preferences, history tables and the catalog are all finished. */
    data class Finished(val sourceName: String) : RestoreRecovery

    /** Room is restored; preferences failed again and the journal is kept for the next try. */
    data class SettingsPending(val sourceName: String) : RestoreRecovery

    /** Room is restored; the incoming copy was gone so preferences could not be applied. */
    data class SettingsLost(val sourceName: String) : RestoreRecovery

    /** Neither witness matched Room; nothing was applied and the journal was closed. */
    data class Unresolved(val sourceName: String, val safetySnapshotId: String) : RestoreRecovery
}
