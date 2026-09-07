package com.sinura.personaltrainer.data.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Signed scale budgets for whole-document backup (P3.7 / FND-038).
 *
 * The fixture is 500 finished sessions and 15,000 sets. Encode and snapshot
 * stay in-memory until a measured run on this fixture exceeds a budget.
 * P8.5 re-measured the same whole-document path. Streaming stays off
 * unless a later run exceeds these numbers.
 *
 * Times are JVM-host ceilings with headroom. They are not phone SLAs.
 *
 * Two byte budgets, and the contract between them is that every export the app reports
 * as saved is one its own import will accept:
 *
 * - [DOCUMENT_BYTES_MAX] bounds the plaintext JSON document, measured in UTF-8 bytes.
 * - [RAW_FILE_BYTES_MAX] bounds what a bounded read takes from a picked file or a Drive
 *   download. A protected envelope carries the document as base64 ciphertext — four
 *   thirds of the size plus a 16-byte tag and a fixed header — so an 8 MiB document
 *   becomes about 11.2 MB of envelope. The old single 8 MiB ceiling on both sides let the
 *   app write a protected file its own import then refused as "far larger than any
 *   Temper backup".
 *
 * Every export path calls [requireExportable] on the exact bytes it is about to write,
 * before writing them and before reporting success. Sizes are always UTF-8 byte counts
 * ([utf8Length]), never `String.length`: a note in Cyrillic or with emoji is one to three
 * bytes longer per character than its length says.
 */
object BackupScaleBudget {
    const val SESSIONS = 500
    const val SETS_PER_SESSION = 30
    const val SETS = SESSIONS * SETS_PER_SESSION

    const val ENCODE_MS = 2_000L
    const val DECODE_MS = 2_000L
    const val SNAPSHOT_AND_ENCODE_MS = 4_000L

    /** UTF-8 bytes of the plaintext JSON document: the signed P3.7 / P8.5 ceiling. */
    const val DOCUMENT_BYTES_MAX = 8L * 1024L * 1024L

    /** The older name for [DOCUMENT_BYTES_MAX]; the scale tests assert against it. */
    const val ENCODED_BYTES_MAX = DOCUMENT_BYTES_MAX

    /**
     * Bytes a bounded read will take from a file or a Drive response. Sized so that the
     * envelope of a [DOCUMENT_BYTES_MAX] document fits with room to spare; see
     * [envelopeBytesUpperBound]. Raising it costs transient memory on import — roughly the
     * raw bytes, their String, the decoded ciphertext, and the plaintext — about 70 MB at
     * this ceiling against about 48 MB at the old one.
     */
    const val RAW_FILE_BYTES_MAX = 12L * 1024L * 1024L

    /** [RAW_FILE_BYTES_MAX] as the Int a bounded stream read needs. */
    const val IMPORT_BYTES_MAX = 12 * 1024 * 1024

    const val TOO_BIG_TO_IMPORT =
        "That file is far larger than any Temper backup. Pick the backup file itself."

    const val TOO_BIG_TO_EXPORT =
        "Your history is larger than a single Temper backup file can hold, so nothing was " +
            "written. Export without a password if you chose one; if that is also refused, " +
            "keep this phone's data safe and report it."

    /**
     * Base64 is 4 output bytes per 3 input bytes rounded up; the ciphertext is the plaintext
     * plus the 128-bit GCM tag; the envelope's fixed fields (format, version, app, kdf,
     * cipher, key size, iterations, salt, nonce, pretty-printing) stay well under 1 KiB.
     */
    fun envelopeBytesUpperBound(plaintextUtf8Bytes: Long): Long {
        val ciphertext = plaintextUtf8Bytes + BackupEnvelope.TAG_BITS / 8
        val base64 = (ciphertext + 2) / 3 * 4
        return base64 + ENVELOPE_HEADER_BYTES
    }

    /** The message import would give this payload, or null when import accepts it. */
    fun exportRefusal(payloadUtf8Bytes: Long, protected: Boolean): String? {
        val ceiling = if (protected) RAW_FILE_BYTES_MAX else DOCUMENT_BYTES_MAX
        return if (payloadUtf8Bytes > ceiling) TOO_BIG_TO_EXPORT else null
    }

    /**
     * Refuses a payload the app's own import would reject. Called with the exact text about
     * to be written, so the check and the write cannot disagree.
     */
    fun requireExportable(payload: String, protected: Boolean) {
        exportRefusal(utf8Length(payload), protected)?.let { throw BackupException(it) }
    }

    /** Import-side twin: the decoded document must fit the plaintext budget. */
    fun requireDocumentFits(plaintext: String) {
        if (utf8Length(plaintext) > DOCUMENT_BYTES_MAX) throw BackupException(TOO_BIG_TO_IMPORT)
    }

    /**
     * UTF-8 byte length without allocating the encoded copy — the same count
     * `toByteArray(Charsets.UTF_8).size` gives. A surrogate pair is one four-byte character;
     * a lone surrogate is the single `?` the JVM encoder substitutes for it.
     */
    fun utf8Length(text: CharSequence): Long {
        var bytes = 0L
        var index = 0
        val length = text.length
        while (index < length) {
            val ch = text[index]
            when {
                ch.code < 0x80 -> bytes += 1
                ch.code < 0x800 -> bytes += 2
                Character.isHighSurrogate(ch) && index + 1 < length && Character.isLowSurrogate(text[index + 1]) -> {
                    bytes += 4
                    index += 1
                }
                Character.isSurrogate(ch) -> bytes += 1
                else -> bytes += 3
            }
            index += 1
        }
        return bytes
    }

    private const val ENVELOPE_HEADER_BYTES = 1024L
}

/** Buffer for [readAtMost]. Large enough that a 12 MB budget is ~192 reads, not 12288. */
private const val READ_CHUNK_BYTES = 64 * 1024

/**
 * Reads at most [limit] bytes, then stops.
 *
 * Deliberately not `InputStream.readNBytes`, which is API 33 while this app ships to
 * API 26: there the method does not exist and the call dies with `NoSuchMethodError`.
 * Both bounded reads used it, so importing a backup — from a file or from Drive —
 * crashed on every device below Android 13. Lint named it the first time it was ever
 * run, on 2 Sep 2026; nothing had run it before, which is why a crash on most of the
 * supported range shipped.
 *
 * Semantics match `readNBytes`: keep reading until [limit] bytes are in hand or the
 * stream ends, rather than trusting one `read` to fill the buffer — a short read from
 * a socket would otherwise look like a short file. Callers ask for budget + 1 and
 * refuse an over-budget result, so stopping exactly at [limit] is what makes that
 * test mean anything.
 */
internal fun InputStream.readAtMost(limit: Int): ByteArray {
    val collected = ByteArrayOutputStream(minOf(limit, READ_CHUNK_BYTES))
    val chunk = ByteArray(READ_CHUNK_BYTES)
    var remaining = limit
    while (remaining > 0) {
        val read = read(chunk, 0, minOf(chunk.size, remaining))
        if (read < 0) break
        collected.write(chunk, 0, read)
        remaining -= read
    }
    return collected.toByteArray()
}
