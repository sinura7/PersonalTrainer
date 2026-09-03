package com.sinura.personaltrainer.domain

data class RestTimerPreferences(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val defaultRestSeconds: Int = DEFAULT_SECONDS,
    val lastPresetSeconds: Int? = null,
) {
    fun sanitized(): RestTimerPreferences = copy(
        defaultRestSeconds = defaultRestSeconds.coerceIn(MIN_SECONDS, MAX_SECONDS),
        lastPresetSeconds = lastPresetSeconds?.coerceIn(MIN_SECONDS, MAX_SECONDS),
    )

    companion object {
        const val MIN_SECONDS = 15
        const val MAX_SECONDS = 30 * 60
        const val DEFAULT_SECONDS = 90
        val DEFAULT = RestTimerPreferences()
    }
}

data class RestTimerSnapshot(
    val running: Boolean = false,
    val endsAtElapsedRealtime: Long = 0L,
    val totalSeconds: Int = 0,
    val sessionId: String? = null,
    /** Generation of this rest. A stale alarm carries a different id and cannot complete it. */
    val timerId: String = "",
) {
    fun remainingSeconds(nowElapsedRealtime: Long): Int {
        if (!running) return 0
        return RestTimer.remainingSeconds(endsAtElapsedRealtime, nowElapsedRealtime)
    }
}

/** What a completion attempt decided, before any alert plays. */
enum class RestTimerClaim {
    /** This id matches the current rest, is due, and has not been claimed. */
    CLAIMED,

    /** This id already produced the cue. */
    ALREADY_CLAIMED,

    /** This id is not the current rest. Ignore it. */
    STALE,

    /** This id is current but elapsed realtime has not reached the deadline. Reschedule. */
    EARLY,
}

/**
 * Atomic claim ledger for rest completion.
 *
 * Three delivery paths can notice that rest is over. Only the first matching,
 * due attempt for an id may announce. An old id cannot clear a replacement.
 */
class RestTimerClaimLedger {
    private val lock = Any()
    private val claimed = linkedSetOf<String>()

    fun decide(
        incomingId: String,
        expectedId: String,
        nowElapsedRealtime: Long,
        deadlineElapsedRealtime: Long,
    ): RestTimerClaim = synchronized(lock) {
        if (incomingId.isBlank() || incomingId != expectedId) return RestTimerClaim.STALE
        if (incomingId in claimed) return RestTimerClaim.ALREADY_CLAIMED
        if (nowElapsedRealtime < deadlineElapsedRealtime) return RestTimerClaim.EARLY
        claimed += incomingId
        RestTimerClaim.CLAIMED
    }

    fun reset() {
        synchronized(lock) { claimed.clear() }
    }
}

object RestTimer {
    val PRESETS_SECONDS: List<Int> = listOf(60, 90, 120)

    /**
     * Whole seconds left, rounded UP, never negative.
     *
     * Ceiling (not floor) matters twice: a 90s rest displays "1:30" for a full second
     * instead of flicking to "1:29" immediately, and — because ceiling makes
     * `remaining <= 0` true exactly when `leftMs <= 0` — every completion check in the
     * service fires at the wall instead of up to a second early.
     */
    fun remainingSeconds(endsAtElapsedRealtime: Long, nowElapsedRealtime: Long): Int {
        if (endsAtElapsedRealtime <= 0L) return 0
        val leftMs = endsAtElapsedRealtime - nowElapsedRealtime
        if (leftMs <= 0L) return 0
        return ((leftMs + 999L) / 1000L).toInt()
    }

    /**
     * Wall-clock instant the rest hits zero. Notification chronometers
     * ([android.app.Notification.Builder.setWhen]) use this base, not elapsed
     * realtime.
     */
    fun endsAtWallClockMillis(
        endsAtElapsedRealtime: Long,
        nowElapsedRealtime: Long,
        nowWallClockMillis: Long,
    ): Long {
        val remainingMs = (endsAtElapsedRealtime - nowElapsedRealtime).coerceAtLeast(0L)
        return nowWallClockMillis + remainingMs
    }

    fun formatClock(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val minutes = safe / 60
        val seconds = safe % 60
        return "%d:%02d".format(minutes, seconds)
    }

    /**
     * How much of the ring is still filled. Full at the start of a rest, empty at zero.
     * The log bar's linear track uses the same fraction so the two surfaces cannot disagree.
     */
    fun sweepFraction(remainingSeconds: Int, totalSeconds: Int): Float {
        if (totalSeconds <= 0) return 0f
        return (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    }

    fun parseCustom(input: String): Int? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val seconds = if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            if (parts.size != 2) return null
            val minutes = parts[0].trim().toIntOrNull() ?: return null
            val secs = parts[1].trim().toIntOrNull() ?: return null
            if (minutes < 0 || secs !in 0..59) return null
            minutes * 60 + secs
        } else {
            trimmed.toIntOrNull() ?: return null
        }
        if (seconds < RestTimerPreferences.MIN_SECONDS || seconds > RestTimerPreferences.MAX_SECONDS) return null
        return seconds
    }

    fun secondsToStart(
        exerciseRestSeconds: Int?,
        preferences: RestTimerPreferences,
    ): Int {
        val fromExercise = exerciseRestSeconds?.takeIf { it > 0 }
        val fromLast = preferences.lastPresetSeconds
        val fromDefault = preferences.defaultRestSeconds
        return (fromExercise ?: fromLast ?: fromDefault)
            .coerceIn(RestTimerPreferences.MIN_SECONDS, RestTimerPreferences.MAX_SECONDS)
    }

    /**
     * Rest follows a working set that still has work after it.
     *
     * Warm-ups do not start the clock. The last prescribed working set of a lift
     * does not either — that lift is done, and the next rest is a choice on the dock.
     * A free lift with no target still rests after every working set.
     */
    fun shouldStartAfterLog(
        isWarmup: Boolean,
        workingSetsAfterLog: Int,
        targetSets: Int,
    ): Boolean {
        if (isWarmup) return false
        if (targetSets > 0 && workingSetsAfterLog >= targetSets) return false
        return true
    }

    /**
     * A set logged past the prescription is more of this lift, so rest
     * starts. The last prescribed set still does not ([shouldStartAfterLog]).
     */
    fun shouldStartAfterExtra(
        isWarmup: Boolean,
        workingSetsAfterLog: Int,
        targetSets: Int,
    ): Boolean {
        if (isWarmup) return false
        return targetSets > 0 && workingSetsAfterLog > targetSets
    }
}

/**
 * Gold "Back to the bar" is keyed on a completion id, not on running going
 * false. Skip and a cleared store both look like `running == false` and
 * used to flash finished.
 */
object RestFinishFlash {
    fun shouldFlash(completedTimerId: String?, lastFlashedTimerId: String?): Boolean =
        !completedTimerId.isNullOrBlank() && completedTimerId != lastFlashedTimerId

    fun lockShowsFinished(
        running: Boolean,
        finishedLaunch: Boolean,
        completedTimerId: String?,
    ): Boolean {
        if (running) return false
        return finishedLaunch || !completedTimerId.isNullOrBlank()
    }

    fun lockShouldDismiss(
        running: Boolean,
        finishedLaunch: Boolean,
        completedTimerId: String?,
    ): Boolean = !running && !lockShowsFinished(running, finishedLaunch, completedTimerId)
}
