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
)

interface CardioTimerPersistence {
    fun save(state: PersistedCardioTimer)
    fun load(): PersistedCardioTimer?
    fun clear()
}

class SharedPrefsCardioTimerPersistence(context: Context) : CardioTimerPersistence {
    private val prefs = context.applicationContext
        .getSharedPreferences("cardio_timer_state", Context.MODE_PRIVATE)

    @Suppress("ApplySharedPref")
    override fun save(state: PersistedCardioTimer) {
        prefs.edit()
            .putString(KEY_SESSION_ID, state.sessionId)
            .putLong(KEY_STARTED_ELAPSED, state.startedAtElapsedRealtime)
            .putLong(KEY_STARTED_WALL, state.startedAtWallClockMillis)
            .putLong(KEY_BOOT_MARKER, state.bootMarker)
            .putLong(KEY_BASELINE, state.baselineElapsedSeconds)
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
        )
    }

    @Suppress("ApplySharedPref")
    override fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_STARTED_ELAPSED = "started_elapsed"
        const val KEY_STARTED_WALL = "started_wall"
        const val KEY_BOOT_MARKER = "boot_marker"
        const val KEY_BASELINE = "baseline_seconds"
    }
}

object CardioElapsed {
    fun bootMarker(
        wallClockMillis: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
    ): Long = wallClockMillis - elapsedRealtimeMs

    fun seconds(
        persisted: PersistedCardioTimer?,
        sessionStartedAtMs: Long,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        nowWallMs: Long = System.currentTimeMillis(),
        currentBootMarker: Long = bootMarker(nowWallMs, nowElapsedMs),
    ): Long {
        if (persisted != null) {
            val extra = if (persisted.bootMarker == currentBootMarker) {
                ((nowElapsedMs - persisted.startedAtElapsedRealtime) / 1_000L).coerceAtLeast(0L)
            } else {
                ((nowWallMs - persisted.startedAtWallClockMillis) / 1_000L).coerceAtLeast(0L)
            }
            return persisted.baselineElapsedSeconds + extra
        }
        return ((nowWallMs - sessionStartedAtMs) / 1_000L).coerceAtLeast(0L)
    }
}
