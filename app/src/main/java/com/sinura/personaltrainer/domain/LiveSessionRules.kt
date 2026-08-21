package com.sinura.personaltrainer.domain

/**
 * The rules behind the live-session bar: when a session counts as left open, and how its
 * elapsed clock reads.
 *
 * Pure so the JVM suite can pin it. The bar itself re-derives everything from wall clock on
 * every tick and accumulates nothing, which is why backgrounding, process death and a clock
 * change cannot desynchronise it.
 */
object LiveSessionRules {

    /**
     * A session with no activity for this long is "left open" — almost always a workout the
     * user walked away from rather than one still running.
     */
    const val STALE_AFTER_MS: Long = 4L * 60 * 60 * 1000

    private const val HOUR_MS: Long = 60L * 60 * 1000

    /**
     * The moment the session was last touched.
     *
     * A warm-up is activity: the last set's [SetLog.completedAt] wins whatever its kind, and a
     * session with no sets at all falls back to when it started.
     */
    fun lastActivityMs(startedAt: Long, lastSetCompletedAt: Long?): Long =
        lastSetCompletedAt ?: startedAt

    fun isStale(lastActivityMs: Long, nowMs: Long): Boolean =
        nowMs - lastActivityMs >= STALE_AFTER_MS

    /** Whole hours since the last activity, floored, never negative. */
    fun staleHours(lastActivityMs: Long, nowMs: Long): Long =
        ((nowMs - lastActivityMs) / HOUR_MS).coerceAtLeast(0L)

    /**
     * "7:42" under an hour, "1:07:42" past it — the same minute:second shape the rest clock
     * uses, extended rather than replaced so the two never look like different instruments.
     */
    fun formatElapsed(elapsedSeconds: Long): String {
        val safe = elapsedSeconds.coerceAtLeast(0L)
        if (safe < 3600L) return RestTimer.formatClock(safe.toInt())
        val hours = safe / 3600L
        val minutes = (safe % 3600L) / 60L
        val seconds = safe % 60L
        return "%d:%02d:%02d".format(hours, minutes, seconds)
    }
}

/** Live counters for one session, read by the live-session bar. */
data class SessionActivity(
    val totalSets: Int,
    val workingSets: Int,
    val lastCompletedAt: Long?,
)
