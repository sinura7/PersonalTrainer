package com.sinura.personaltrainer.timer

import android.content.Context
import android.os.SystemClock

/**
 * Elapsed-time bookkeeping for a live cardio session, mirrored on the
 * rest-timer persistence: elapsedRealtime plus a boot marker so process
 * death and reboot can be told apart.
 */
data class PersistedCardioTimer(
    val sessionId: String,
    val startedAtElapsedRealtime: Long,
    val startedAtWallClockMillis: Long,
    val bootMarker: Long,
    val baselineElapsedSeconds: Long = 0L,
    /**
     * [BootSession] counter at save time; [BootSession.UNKNOWN] when the row
     * predates the stamp. The persistence layer stamps it on save so the
     * start-site constructors stay clock-only.
     */
    val bootCount: Long = BootSession.UNKNOWN,
)

/**
 * [save] and [clear] report whether the row reached disk, as the rest-timer
 * persistence does. A false save is not fatal here — [CardioElapsed.seconds]
 * falls back to the session's wall-clock start when no row is loaded — but the
 * caller should know the baseline it just wrote is not on disk.
 */
interface CardioTimerPersistence {
    fun save(state: PersistedCardioTimer): Boolean
    fun load(): PersistedCardioTimer?
    fun clear(): Boolean
}

class SharedPrefsCardioTimerPersistence(context: Context) : CardioTimerPersistence {
    private val appContext = context.applicationContext
    private val prefs = appContext
        .getSharedPreferences("cardio_timer_state", Context.MODE_PRIVATE)

    @Suppress("ApplySharedPref")
    override fun save(state: PersistedCardioTimer): Boolean {
        val bootCount = state.bootCount.takeIf { it != BootSession.UNKNOWN }
            ?: BootSession.count(appContext)
        return prefs.edit()
            .putString(KEY_SESSION_ID, state.sessionId)
            .putLong(KEY_STARTED_ELAPSED, state.startedAtElapsedRealtime)
            .putLong(KEY_STARTED_WALL, state.startedAtWallClockMillis)
            .putLong(KEY_BOOT_MARKER, state.bootMarker)
            .putLong(KEY_BASELINE, state.baselineElapsedSeconds)
            .putLong(KEY_BOOT_COUNT, bootCount)
            .commit()
    }

    override fun load(): PersistedCardioTimer? {
        val sessionId = prefs.getString(KEY_SESSION_ID, null) ?: return null
        return PersistedCardioTimer(
            sessionId = sessionId,
            startedAtElapsedRealtime = prefs.getLong(KEY_STARTED_ELAPSED, 0L),
            startedAtWallClockMillis = prefs.getLong(KEY_STARTED_WALL, 0L),
            bootMarker = prefs.getLong(KEY_BOOT_MARKER, 0L),
            baselineElapsedSeconds = prefs.getLong(KEY_BASELINE, 0L),
            bootCount = prefs.getLong(KEY_BOOT_COUNT, BootSession.UNKNOWN),
        )
    }

    @Suppress("ApplySharedPref")
    override fun clear(): Boolean = prefs.edit().clear().commit()

    private companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_STARTED_ELAPSED = "started_elapsed"
        const val KEY_STARTED_WALL = "started_wall"
        const val KEY_BOOT_MARKER = "boot_marker"
        const val KEY_BASELINE = "baseline_seconds"
        const val KEY_BOOT_COUNT = "boot_count"
    }
}

object CardioElapsed {
    fun bootMarker(
        wallClockMillis: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
    ): Long = wallClockMillis - elapsedRealtimeMs

    /**
     * Same-boot tolerance. The marker is wall minus elapsed, so an NTP nudge
     * or two non-atomic clock reads shift it by milliseconds; exact equality
     * dropped the reliable elapsedRealtime path over a 1 ms skew and let a
     * mid-run clock resync corrupt the recorded duration.
     */
    const val BOOT_MARKER_TOLERANCE_MS = 2_000L

    fun seconds(
        persisted: PersistedCardioTimer?,
        sessionStartedAtMs: Long,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        nowWallMs: Long = System.currentTimeMillis(),
        currentBootMarker: Long = bootMarker(nowWallMs, nowElapsedMs),
        nowBootCount: Long = BootSession.UNKNOWN,
    ): Long {
        if (persisted != null) {
            // The boot counter, when both sides carry it, is definitive: the
            // marker heuristic misreads a wall-clock step as a reboot, and the
            // monotonic heuristic misreads a second reboot as the same boot
            // (turning the recorded duration into new-boot uptime minus
            // old-boot uptime — an arbitrary number).
            val sameBoot = if (persisted.bootCount != BootSession.UNKNOWN && nowBootCount != BootSession.UNKNOWN) {
                persisted.bootCount == nowBootCount
            } else {
                kotlin.math.abs(persisted.bootMarker - currentBootMarker) < BOOT_MARKER_TOLERANCE_MS ||
                    nowElapsedMs >= persisted.startedAtElapsedRealtime
            }
            val extra = if (sameBoot) {
                ((nowElapsedMs - persisted.startedAtElapsedRealtime) / 1_000L).coerceAtLeast(0L)
            } else {
                ((nowWallMs - persisted.startedAtWallClockMillis) / 1_000L).coerceAtLeast(0L)
            }
            return persisted.baselineElapsedSeconds + extra
        }
        return ((nowWallMs - sessionStartedAtMs) / 1_000L).coerceAtLeast(0L)
    }
}
