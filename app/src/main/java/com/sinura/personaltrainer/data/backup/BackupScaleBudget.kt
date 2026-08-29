package com.sinura.personaltrainer.data.backup

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
