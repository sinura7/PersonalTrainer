package com.sinura.personaltrainer.domain

/**
 * Packet E: at most one timed mode, one seconds-changing numeral in the dock.
 *
 * Every transition cancels the prior generation before the next begins.
 * Empty sessions stay [NONE] (Packet A). Header minutes are not a mode.
 */
enum class FloorTimedMode {
    NONE,
    REST_IDLE,
    REST_RUNNING,
    REST_COMPLETE,
    HOLD_RUNNING,
    STOPWATCH_RUNNING,
}

object FloorTimedModeResolver {
    fun resolve(
        hasLifts: Boolean,
        holdActive: Boolean,
        stopwatchRunning: Boolean,
        restRunning: Boolean,
        restComplete: Boolean,
    ): FloorTimedMode {
        if (!hasLifts) return FloorTimedMode.NONE
        if (holdActive) return FloorTimedMode.HOLD_RUNNING
        if (stopwatchRunning) return FloorTimedMode.STOPWATCH_RUNNING
        if (restRunning) return FloorTimedMode.REST_RUNNING
        if (restComplete) return FloorTimedMode.REST_COMPLETE
        return FloorTimedMode.REST_IDLE
    }

    fun isActive(mode: FloorTimedMode): Boolean = when (mode) {
        FloorTimedMode.REST_RUNNING,
        FloorTimedMode.HOLD_RUNNING,
        FloorTimedMode.STOPWATCH_RUNNING,
        -> true
        FloorTimedMode.NONE,
        FloorTimedMode.REST_IDLE,
        FloorTimedMode.REST_COMPLETE,
        -> false
    }

    /** Time set: idle or complete rest, never while rest/hold/stopwatch is live. */
    fun offerSetClock(mode: FloorTimedMode, isHoldLift: Boolean): Boolean {
        if (isHoldLift) return false
        return mode == FloorTimedMode.REST_IDLE || mode == FloorTimedMode.REST_COMPLETE
    }
}

/** Dock / screen cues that are not rest-complete (that stays on the service). */
sealed interface FloorTimerCue {
    data object HoldStarted : FloorTimerCue
    data class HoldTarget(val soundEnabled: Boolean) : FloorTimerCue
    data object StopwatchStarted : FloorTimerCue
    data object StopwatchStopped : FloorTimerCue
}

data class PendingLiftSwitch(
    val exerciseId: String,
)
