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

    fun read(): RestoreJournalRecord? {
        val file = File(dir, RestoreJournal.STATE_FILE)
        if (!file.isFile) return null
        return try {
            val record = gson.fromJson(file.readText(Charsets.UTF_8), RestoreJournalRecord::class.java)
            if (record.phase.isNullOrBlank() || record.sourceName.isNullOrBlank()) null else record
        } catch (_: Exception) {
            null
        }
    }

    fun readIncoming(): String {
        val file = File(dir, RestoreJournal.INCOMING_FILE)
        if (!file.isFile) throw BackupException(RestoreJournal.INTERRUPTED)
        return file.readText(Charsets.UTF_8)
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

    fun clear() {
        File(dir, RestoreJournal.STATE_FILE).delete()
        File(dir, RestoreJournal.INCOMING_FILE).delete()
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
