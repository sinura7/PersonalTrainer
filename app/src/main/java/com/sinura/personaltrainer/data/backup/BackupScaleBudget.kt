package com.sinura.personaltrainer.data.backup

/**
 * Signed scale budgets for whole-document backup (P3.7 / FND-038).
 *
 * The fixture is 500 finished sessions and 15,000 sets. Encode and snapshot
 * stay in-memory until a measured run on this fixture exceeds a budget.
 * Streaming is P8.5, and only if these numbers fail.
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
}
