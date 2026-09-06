package com.sinura.personaltrainer.data.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileOutputStream

/**
 * Persists restore phase + incoming JSON so a killed process can finish
 * the committed work. Paths never leave this store.
 */
class RestoreJournalStore(
    private val dir: File,
) {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    /**
     * The open journal, or null when there is none.
     *
     * Sweeps an orphaned incoming file on the way past. Nothing points at one — no state file
     * means no restore to finish — so it is a decrypted copy of the owner's entire training
     * history sitting in app storage with nothing that will ever come back for it. It can be
     * left behind by a crash between the two deletes in [clear], or by a staging failure.
     */
    fun read(): RestoreJournalRecord? {
        val file = File(dir, RestoreJournal.STATE_FILE)
        if (!file.isFile) {
            File(dir, RestoreJournal.INCOMING_FILE).delete()
            return null
        }
        return try {
            val record = gson.fromJson(file.readText(Charsets.UTF_8), RestoreJournalRecord::class.java)
            if (record.phase.isNullOrBlank() || record.sourceName.isNullOrBlank()) null else record
        } catch (_: Exception) {
            null
        }
    }

    fun readIncoming(): String {
        return readIncomingOrNull() ?: throw BackupException(RestoreJournal.INTERRUPTED)
    }

    /**
     * The incoming copy, or null when it is not on disk.
     *
     * Recovery reads through this rather than [readIncoming] so a `room` journal whose input
     * is gone is a state it can name — settings lost, journal closed — instead of an
     * exception it throws on every launch.
     */
    fun readIncomingOrNull(): String? {
        val file = File(dir, RestoreJournal.INCOMING_FILE)
        if (!file.isFile) return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun stage(record: RestoreJournalRecord, incomingJson: String) {
        ensureDir()
        writeText(File(dir, RestoreJournal.INCOMING_FILE), incomingJson)
        writeState(record.copy(phase = RestoreJournal.STAGED))
    }

    fun mark(phase: String) {
        val current = read() ?: throw BackupException(RestoreJournal.INTERRUPTED)
        writeState(current.copy(phase = phase))
    }

    /**
     * Closes the journal, incoming copy first.
     *
     * The order matters and it is the opposite of what [stage] writes in. A crash between the
     * two deletes with the state file gone first leaves the decrypted backup on disk with
     * nothing pointing at it — invisible to [isOpen], never swept, and the owner's whole
     * history in the clear. Losing the incoming file first is safe only because every caller
     * marks [RestoreJournal.DONE] before coming here: a `done` journal with no input is swept
     * by the next recovery pass, whereas a `room` journal with no input used to throw on every
     * launch and refuse every workout start.
     */
    fun clear() {
        File(dir, RestoreJournal.INCOMING_FILE).delete()
        File(dir, RestoreJournal.STATE_FILE).delete()
    }

    fun isOpen(): Boolean = read() != null

    private fun writeState(record: RestoreJournalRecord) {
        ensureDir()
        writeText(File(dir, RestoreJournal.STATE_FILE), gson.toJson(record) + "\n")
    }

    private fun writeText(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(tmp).use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
                stream.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (thrown: Exception) {
            tmp.delete()
            throw BackupException(RestoreJournal.INTERRUPTED)
        }
    }

    private fun ensureDir() {
        if (!dir.exists() && !dir.mkdirs()) {
            throw BackupException(RestoreJournal.INTERRUPTED)
        }
        if (!dir.isDirectory || !dir.canWrite()) {
            throw BackupException(RestoreJournal.INTERRUPTED)
        }
    }
}
