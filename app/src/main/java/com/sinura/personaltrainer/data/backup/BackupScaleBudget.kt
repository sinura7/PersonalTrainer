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
 */
object BackupScaleBudget {
    const val SESSIONS = 500
    const val SETS_PER_SESSION = 30
    const val SETS = SESSIONS * SETS_PER_SESSION

    const val ENCODE_MS = 2_000L
    const val DECODE_MS = 2_000L
    const val SNAPSHOT_AND_ENCODE_MS = 4_000L
    const val ENCODED_BYTES_MAX = 8L * 1024L * 1024L

    /** The byte budget as the Int a bounded stream read needs. */
    const val IMPORT_BYTES_MAX = 8 * 1024 * 1024

    const val TOO_BIG_TO_IMPORT =
        "That file is far larger than any Temper backup. Pick the backup file itself."
}

/** Buffer for [readAtMost]. Large enough that an 8 MB budget is ~128 reads, not 8192. */
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
