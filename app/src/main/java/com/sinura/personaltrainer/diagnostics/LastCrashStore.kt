package com.sinura.personaltrainer.diagnostics

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * One redacted [DiagnosticEvent] that outlives the process that recorded it.
 *
 * [DiagnosticRing] is an in-process deque, so the one event the owner most
 * wants to share — the fatal one — was gone before Settings could build a
 * bundle. This keeps exactly that event, already passed through
 * [DiagnosticRedaction], in app-private storage until the next crash
 * replaces it or the owner taps Clear diagnostics. It is never uploaded;
 * the exclusion rules name its directory so Auto Backup cannot carry it.
 *
 * Pure JVM on purpose — java.io only, no Gson — so the domain lane can run
 * its tests. The format is line-based `key=value`. Nothing but the event's
 * six redacted fields is ever written: every string is clipped to
 * [MAX_FIELD] characters with line breaks removed and frames are capped at
 * [MAX_FRAMES], so the file stays under [MAX_FILE_BYTES] whatever came in.
 * A file that fails any of those bounds on the way back is treated as
 * corrupt and reads as null; nothing here throws on the read path.
 */
class LastCrashStore(private val dir: File) {

    /**
     * Replaces the stored crash. Temp file, flush, fsync, rename — the same
     * shape as the restore journal, because a half-written crash file that
     * parsed would be worse than none. Throws [IOException] when the
     * directory cannot be created or the write fails; the crash handler
     * wraps this call, the store does not swallow.
     */
    @Throws(IOException::class)
    fun save(event: DiagnosticEvent) {
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("diagnostics directory could not be created")
        }
        val file = File(dir, FILE_NAME)
        val tmp = File(dir, TEMP_NAME)
        val text = encode(event)
        try {
            FileOutputStream(tmp).use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
                stream.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
        } catch (thrown: IOException) {
            tmp.delete()
            throw thrown
        }
    }

    /** The stored crash, or null when there is none or the file is not one of ours. */
    fun load(): DiagnosticEvent? {
        val file = File(dir, FILE_NAME)
        return try {
            if (!file.isFile || file.length() > MAX_FILE_BYTES) {
                null
            } else {
                decode(file.readText(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        File(dir, TEMP_NAME).delete()
        File(dir, FILE_NAME).delete()
    }

    private fun encode(event: DiagnosticEvent): String = buildString {
        appendLine("$SCHEMA_KEY=$SCHEMA")
        appendLine("$ID_KEY=${clip(event.id)}")
        appendLine("$AT_KEY=${event.atMs}")
        appendLine("$KIND_KEY=${clip(event.kind)}")
        event.exceptionClass?.let { appendLine("$CLASS_KEY=${clip(it)}") }
        event.tag?.let { appendLine("$TAG_KEY=${clip(it)}") }
        event.frames.take(MAX_FRAMES).forEach { frame ->
            appendLine("$FRAME_KEY=${clip(frame)}")
        }
    }

    private fun decode(text: String): DiagnosticEvent? {
        val lines = text.split('\n').filter { it.isNotEmpty() }
        if (lines.firstOrNull() != "$SCHEMA_KEY=$SCHEMA") return null
        val fields = HashMap<String, String>()
        val frames = ArrayList<String>()
        for (line in lines.drop(1)) {
            val at = line.indexOf('=')
            if (at <= 0) return null
            val key = line.substring(0, at)
            val value = line.substring(at + 1)
            if (key == FRAME_KEY) {
                if (frames.size >= MAX_FRAMES) return null
                frames.add(value)
            } else if (fields.containsKey(key)) {
                return null
            } else {
                fields[key] = value
            }
        }
        val id = fields[ID_KEY]?.takeIf { it.isNotBlank() } ?: return null
        val atMs = fields[AT_KEY]?.toLongOrNull() ?: return null
        val kind = fields[KIND_KEY]?.takeIf { it.isNotBlank() } ?: return null
        if (listOf(id, kind).any { it.length > MAX_FIELD }) return null
        if (frames.any { it.length > MAX_FIELD }) return null
        val exceptionClass = fields[CLASS_KEY]
        val tag = fields[TAG_KEY]
        if (exceptionClass != null && exceptionClass.length > MAX_FIELD) return null
        if (tag != null && tag.length > MAX_FIELD) return null
        return DiagnosticEvent(
            id = id,
            atMs = atMs,
            kind = kind,
            exceptionClass = exceptionClass,
            tag = tag,
            frames = frames,
        )
    }

    // A line break inside a value would let one field masquerade as the next on the way
    // back in; the clip is what makes the file size a bound and not a hope.
    private fun clip(value: String): String =
        value.replace('\r', ' ').replace('\n', ' ').take(MAX_FIELD)

    companion object {
        const val FILE_NAME = "last-crash.txt"
        const val TEMP_NAME = "last-crash.txt.tmp"
        const val SCHEMA = "temper-crash-1"
        const val MAX_FIELD = 200
        const val MAX_FRAMES = DiagnosticRedaction.MAX_FRAMES
        const val MAX_FILE_BYTES = 8 * 1024L

        /** The kind a crash wears once it is re-recorded by the process that came after. */
        const val PREVIOUS_CRASH_KIND = "previous-crash"

        private const val SCHEMA_KEY = "schema"
        private const val ID_KEY = "id"
        private const val AT_KEY = "atMs"
        private const val KIND_KEY = "kind"
        private const val CLASS_KEY = "class"
        private const val TAG_KEY = "tag"
        private const val FRAME_KEY = "frame"
    }
}
