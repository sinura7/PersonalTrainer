package com.sinura.personaltrainer.timer

import android.content.Context
import android.os.SystemClock

/**
 * The running rest timer, written to disk so it survives process death, an OEM swipe-kill,
 * and a reboot.
 *
 * SharedPreferences rather than DataStore on purpose: both readers — the alarm
 * BroadcastReceiver and the service's sticky restart — need the value synchronously on the
 * main thread at process start, and DataStore is suspend-only.
 *
 * Two clocks are stored because neither alone survives every case:
 *  - [endsAtElapsedRealtime] is authoritative while the device has not rebooted; it is
 *    immune to the user changing the wall clock mid-rest.
 *  - [endsAtWallClockMillis] is the only thing that still means something after a reboot,
 *    when elapsedRealtime resets to zero.
 * [bootMarker] (wall clock minus elapsed realtime) identifies the boot session, so
 * rehydration can tell those two cases apart.
 */
data class PersistedRestTimer(
    val endsAtElapsedRealtime: Long,
    val totalSeconds: Int,
    val sessionId: String?,
    val bootMarker: Long,
    val endsAtWallClockMillis: Long,
    val timerId: String = "",
)

interface RestTimerStatePersistence {
    fun save(state: PersistedRestTimer)
    fun load(): PersistedRestTimer?
    fun clear()
}

class SharedPrefsRestTimerStatePersistence(context: Context) : RestTimerStatePersistence {
    private val prefs = context.applicationContext
        .getSharedPreferences("rest_timer_state", Context.MODE_PRIVATE)

    override fun save(state: PersistedRestTimer) {
        // commit(), not apply(): the alarm is scheduled on the next line of
        // RestTimerController.start(), and RestTimerAlarmReceiver treats a missing
        // disk row as "already completed". An unflushed apply() plus a process
        // kill is a silent missed rest.
        prefs.edit()
            .putLong(KEY_ENDS_AT_ELAPSED, state.endsAtElapsedRealtime)
            .putInt(KEY_TOTAL_SECONDS, state.totalSeconds)
            .putString(KEY_SESSION_ID, state.sessionId)
            .putLong(KEY_BOOT_MARKER, state.bootMarker)
            .putLong(KEY_ENDS_AT_WALL, state.endsAtWallClockMillis)
            .putString(KEY_TIMER_ID, state.timerId)
            .commit()
    }

    override fun load(): PersistedRestTimer? {
        if (!prefs.contains(KEY_ENDS_AT_ELAPSED)) return null
        return PersistedRestTimer(
            endsAtElapsedRealtime = prefs.getLong(KEY_ENDS_AT_ELAPSED, 0L),
            totalSeconds = prefs.getInt(KEY_TOTAL_SECONDS, 0),
            sessionId = prefs.getString(KEY_SESSION_ID, null),
            bootMarker = prefs.getLong(KEY_BOOT_MARKER, 0L),
            endsAtWallClockMillis = prefs.getLong(KEY_ENDS_AT_WALL, 0L),
            timerId = prefs.getString(KEY_TIMER_ID, "").orEmpty(),
        )
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val KEY_ENDS_AT_ELAPSED = "ends_at_elapsed"
        const val KEY_TOTAL_SECONDS = "total_seconds"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_BOOT_MARKER = "boot_marker"
        const val KEY_ENDS_AT_WALL = "ends_at_wall"
        const val KEY_TIMER_ID = "timer_id"
    }
}

/** What rehydration decided to do with a persisted timer. */
sealed interface RestTimerRehydration {
    /** Nothing was stored, or what was stored is too stale to act on. */
    data object None : RestTimerRehydration

    /** Rest is still running; [endsAtElapsedRealtime] is rebased onto the current boot. */
    data class Running(
        val endsAtElapsedRealtime: Long,
        val totalSeconds: Int,
        val sessionId: String?,
        val timerId: String,
    ) : RestTimerRehydration

    /** Rest ended while the process was dead, recently enough to still be worth announcing. */
    data class Expired(
        val sessionId: String?,
        val lateByMs: Long,
        val endsAtElapsedRealtime: Long,
        val timerId: String,
    ) : RestTimerRehydration
}

object RestTimerRehydrator {
    /**
     * A rest that ended while the process was dead is still announced if it ended within
     * this window — the user is probably standing at the rack waiting. Older than this and
     * announcing would be noise, so the timer is dropped silently.
     */
    const val LATE_ALERT_GRACE_MS = 60_000L

    /** Same-boot detection tolerance, covering NTP nudges to the wall clock. */
    private const val BOOT_MARKER_TOLERANCE_MS = 2_000L

    fun rehydrate(
        stored: PersistedRestTimer?,
        nowElapsedRealtime: Long,
        nowWallClockMillis: Long,
    ): RestTimerRehydration {
        if (stored == null || stored.totalSeconds <= 0) return RestTimerRehydration.None

        val currentBootMarker = nowWallClockMillis - nowElapsedRealtime
        val sameBoot = kotlin.math.abs(stored.bootMarker - currentBootMarker) < BOOT_MARKER_TOLERANCE_MS

        if (!sameBoot) {
            // A rest shorter than a gym set is meaningless after a different boot.
            // Do not rebase it onto the new elapsedRealtime or fire a late alert.
            return RestTimerRehydration.None
        }

        val remainingMs = stored.endsAtElapsedRealtime - nowElapsedRealtime

        return when {
            remainingMs > 0L -> RestTimerRehydration.Running(
                endsAtElapsedRealtime = nowElapsedRealtime + remainingMs,
                totalSeconds = stored.totalSeconds,
                sessionId = stored.sessionId,
                timerId = stored.timerId,
            )
            -remainingMs <= LATE_ALERT_GRACE_MS -> RestTimerRehydration.Expired(
                sessionId = stored.sessionId,
                lateByMs = -remainingMs,
                endsAtElapsedRealtime = stored.endsAtElapsedRealtime,
                timerId = stored.timerId,
            )
            else -> RestTimerRehydration.None
        }
    }

    fun toPersisted(
        endsAtElapsedRealtime: Long,
        totalSeconds: Int,
        sessionId: String?,
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime(),
        nowWallClockMillis: Long = System.currentTimeMillis(),
        timerId: String = "",
    ): PersistedRestTimer = PersistedRestTimer(
        endsAtElapsedRealtime = endsAtElapsedRealtime,
        totalSeconds = totalSeconds,
        sessionId = sessionId,
        bootMarker = nowWallClockMillis - nowElapsedRealtime,
        endsAtWallClockMillis = nowWallClockMillis + (endsAtElapsedRealtime - nowElapsedRealtime),
        timerId = timerId,
    )
}
