package com.sinura.personaltrainer.timer

import android.content.Context
import android.os.SystemClock

/**
 * The running rest timer, written to disk so it survives process death and an OEM
 * swipe-kill. A reboot deliberately DROPS the rest (ADR-012 §7): a countdown shorter
 * than a gym set means nothing after the minutes a reboot takes.
 *
 * SharedPreferences rather than DataStore on purpose: both readers — the alarm
 * BroadcastReceiver and the service's sticky restart — need the value synchronously on the
 * main thread at process start, and DataStore is suspend-only.
 *
 * Two clocks are stored because neither alone survives every case:
 *  - [endsAtElapsedRealtime] is authoritative while the device has not rebooted; it is
 *    immune to the user changing the wall clock mid-rest.
 *  - [endsAtWallClockMillis] is diagnostic: rehydration never reads it, because a
 *    different boot drops the rest rather than rebasing it.
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
    /**
     * [BootSession] counter at save time; [BootSession.UNKNOWN] when the row
     * predates the stamp. Definitive same-boot evidence where the two clock
     * heuristics each fail in one direction.
     */
    val bootCount: Long = BootSession.UNKNOWN,
)

/**
 * [save] and [clear] report whether the row reached disk. The controller arms
 * the wakeup only on a true [save]: an alarm whose row never landed is one the
 * receiver reads as already completed, so the rest ends in silence after a
 * process kill. Dropping the Boolean was how that path stayed invisible.
 */
interface RestTimerStatePersistence {
    fun save(state: PersistedRestTimer): Boolean
    fun load(): PersistedRestTimer?
    fun clear(): Boolean
}

class SharedPrefsRestTimerStatePersistence(context: Context) : RestTimerStatePersistence {
    private val appContext = context.applicationContext
    private val prefs = appContext
        .getSharedPreferences("rest_timer_state", Context.MODE_PRIVATE)

    @Suppress("ApplySharedPref")
    override fun save(state: PersistedRestTimer): Boolean {
        // The store layer is clock-pure, so the boot stamp lands here.
        val bootCount = state.bootCount.takeIf { it != BootSession.UNKNOWN }
            ?: BootSession.count(appContext)
        // commit(), not apply(): RestTimerController arms after this returns,
        // and RestTimerAlarmReceiver treats a missing disk row as already
        // completed. An unflushed apply() plus a process kill is a silent
        // missed rest. The controller runs this on IO, not the Log frame.
        // commit()'s Boolean is the only word on whether the write landed
        // (full disk, a corrupt prefs file): it goes back to the caller.
        return prefs.edit()
            .putLong(KEY_ENDS_AT_ELAPSED, state.endsAtElapsedRealtime)
            .putInt(KEY_TOTAL_SECONDS, state.totalSeconds)
            .putString(KEY_SESSION_ID, state.sessionId)
            .putLong(KEY_BOOT_MARKER, state.bootMarker)
            .putLong(KEY_ENDS_AT_WALL, state.endsAtWallClockMillis)
            .putString(KEY_TIMER_ID, state.timerId)
            .putLong(KEY_BOOT_COUNT, bootCount)
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
            bootCount = prefs.getLong(KEY_BOOT_COUNT, BootSession.UNKNOWN),
        )
    }

    @Suppress("ApplySharedPref")
    override fun clear(): Boolean = prefs.edit().clear().commit()

    private companion object {
        const val KEY_ENDS_AT_ELAPSED = "ends_at_elapsed"
        const val KEY_TOTAL_SECONDS = "total_seconds"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_BOOT_MARKER = "boot_marker"
        const val KEY_ENDS_AT_WALL = "ends_at_wall"
        const val KEY_TIMER_ID = "timer_id"
        const val KEY_BOOT_COUNT = "boot_count"
    }
}

/** What rehydration decided to do with a persisted timer. */
sealed interface RestTimerRehydration {
    /** Nothing was stored, or the rest belonged to a different boot. */
    data object None : RestTimerRehydration

    /** Rest is still running; [endsAtElapsedRealtime] is rebased onto the current boot. */
    data class Running(
        val endsAtElapsedRealtime: Long,
        val totalSeconds: Int,
        val sessionId: String?,
        val timerId: String,
    ) : RestTimerRehydration

    /**
     * Rest ended while the process was dead. [lateByMs] is how late we noticed.
     * Within [RestTimerRehydrator.LATE_ALERT_GRACE_MS] the cue still plays; beyond
     * it the "Rest done" notification is silent. Same-boot expiry is never dropped.
     */
    data class Expired(
        val sessionId: String?,
        val lateByMs: Long,
        val endsAtElapsedRealtime: Long,
        val timerId: String,
    ) : RestTimerRehydration {
        val playCue: Boolean
            get() = lateByMs <= RestTimerRehydrator.LATE_ALERT_GRACE_MS
    }
}

object RestTimerRehydrator {
    /**
     * Within this window a rest that ended while the process was dead still plays the
     * cue — the user is probably standing at the rack. Older than this, the cue is
     * suppressed and a silent "Rest done" notification is posted instead. Same-boot
     * expiry is never [RestTimerRehydration.None]; dropping it let a late alarm find
     * an empty disk and complete in silence.
     */
    const val LATE_ALERT_GRACE_MS = 60_000L

    /** Same-boot detection tolerance, covering NTP nudges to the wall clock. */
    private const val BOOT_MARKER_TOLERANCE_MS = 2_000L

    fun rehydrate(
        stored: PersistedRestTimer?,
        nowElapsedRealtime: Long,
        nowWallClockMillis: Long,
        nowBootCount: Long = BootSession.UNKNOWN,
    ): RestTimerRehydration {
        if (stored == null || stored.totalSeconds <= 0) return RestTimerRehydration.None

        val currentBootMarker = nowWallClockMillis - nowElapsedRealtime
        // The marker moves whenever the wall clock steps (carrier/NTP resync after
        // airplane mode, a manual set) — that is not a reboot, and treating it as
        // one silently destroyed a running rest while its alarm stayed armed.
        // elapsedRealtime is monotonic within a boot and restarts near zero after
        // one, so not having gone backwards past the rest's start is same-boot
        // evidence that survives any wall-clock step — but it misreads a genuine
        // reboot whenever the new boot's uptime already exceeds the old start.
        // When both sides carry the system boot counter, that comparison is the
        // answer and the clock heuristics are not consulted.
        val startedAtElapsedRealtime =
            stored.endsAtElapsedRealtime - stored.totalSeconds * 1_000L
        val sameBoot = if (stored.bootCount != BootSession.UNKNOWN && nowBootCount != BootSession.UNKNOWN) {
            stored.bootCount == nowBootCount
        } else {
            kotlin.math.abs(stored.bootMarker - currentBootMarker) < BOOT_MARKER_TOLERANCE_MS ||
                nowElapsedRealtime >= startedAtElapsedRealtime
        }

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
            else -> RestTimerRehydration.Expired(
                sessionId = stored.sessionId,
                lateByMs = -remainingMs,
                endsAtElapsedRealtime = stored.endsAtElapsedRealtime,
                timerId = stored.timerId,
            )
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
