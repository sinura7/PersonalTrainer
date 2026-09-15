package com.sinura.personaltrainer.domain

/**
 * One clock on the gym floor.
 *
 * Rest counts down with the signed [elapsedRealtime] alarm path.
 * A running set (hold countdown or a manual stopwatch) counts up in
 * the same dock slot. Switching modes hides the other surface — no
 * second countdown on the live log, and no overlay rest clock
 * (ADR-012 / ADR-013). Starting hold or the manual stopwatch cancels
 * a pending rest generation so a hidden alarm cannot ring mid-set.
 */
object FloorTimerSurface {
    const val SET_KICKER = "SET"
    const val HOLD_KICKER = HoldWork.HOLD_KICKER
    const val REST_STATE = "rest"
    const val SET_STATE = "set"

    fun mode(
        holdRunning: Boolean,
        stopwatchRunning: Boolean = false,
        hasLifts: Boolean = true,
        restRunning: Boolean = false,
        restComplete: Boolean = false,
        holdActive: Boolean = holdRunning,
    ): FloorTimedMode = FloorTimedModeResolver.resolve(
        hasLifts = hasLifts,
        holdActive = holdActive || holdRunning,
        stopwatchRunning = stopwatchRunning,
        restRunning = restRunning,
        restComplete = restComplete,
    )

    /**
     * Quiet instrument readout for the header strip.
     * Numbers stay tabular; colour is never the only channel.
     */
    fun instrumentState(
        holdRunning: Boolean,
        holdElapsedSeconds: Int,
        restRunning: Boolean,
        restRemainingSeconds: Int,
        plannedRestSeconds: Int,
        stopwatchRunning: Boolean = false,
        stopwatchElapsedSeconds: Int = 0,
    ): String {
        return when {
            holdRunning ->
                "$HOLD_KICKER ${HoldWork.clock(holdElapsedSeconds.coerceAtLeast(0))}"
            stopwatchRunning ->
                "$SET_KICKER ${HoldWork.clock(stopwatchElapsedSeconds.coerceAtLeast(0))}"
            restRunning ->
                "$REST_STATE ${RestTimer.formatClock(restRemainingSeconds.coerceAtLeast(0))}"
            else ->
                "$REST_STATE ${RestTimer.formatClock(plannedRestSeconds.coerceAtLeast(0))}"
        }
    }

    fun setClockSeconds(elapsedSeconds: Int): Int = elapsedSeconds.coerceAtLeast(0)

    /**
     * Seconds to persist on a logged set. Holds always write time.
     * A strength set writes time only when the manual clock was used.
     */
    fun durationToLog(
        hold: Boolean,
        holdElapsedSeconds: Int,
        holdTotalSeconds: Int,
        holdRemainingSeconds: Int,
        holdDraftSeconds: Int?,
        stopwatch: SetStopwatchUiState,
        existingDurationSeconds: Int? = null,
    ): Int? {
        if (hold) {
            return if (holdTotalSeconds > 0) {
                HoldWork.elapsedSeconds(holdTotalSeconds, holdRemainingSeconds)
            } else {
                HoldWork.countdownSeconds(holdDraftSeconds)
            }
        }
        if (stopwatch.used) {
            return setClockSeconds(stopwatch.elapsedSeconds).coerceAtLeast(1)
                .coerceAtMost(HoldWork.MAX_SECONDS)
        }
        return existingDurationSeconds?.takeIf { it > 0 }
    }
}

/**
 * Manual in-set count-up. Idle until started. [used] is sticky until
 * the lift changes so a pause still writes seconds; never-started
 * leaves [durationSeconds] null.
 */
data class SetStopwatchUiState(
    val running: Boolean = false,
    val elapsedSeconds: Int = 0,
    val used: Boolean = false,
    val startElapsedRealtime: Long = 0L,
    val frozenElapsedSeconds: Int = 0,
    val exerciseId: String? = null,
)

object SetStopwatchWork {
    fun elapsedFromRealtime(
        startElapsedRealtime: Long,
        frozenElapsedSeconds: Int,
        nowElapsedRealtime: Long,
    ): Int {
        if (!runningClock(startElapsedRealtime, nowElapsedRealtime)) {
            return frozenElapsedSeconds.coerceIn(0, HoldWork.MAX_SECONDS)
        }
        val live = ((nowElapsedRealtime - startElapsedRealtime) / 1_000L).toInt()
        return (frozenElapsedSeconds + live).coerceIn(0, HoldWork.MAX_SECONDS)
    }

    /** A start of 0 is a real boot-time timestamp, not a stopped sentinel. */
    fun runningClock(startElapsedRealtime: Long, nowElapsedRealtime: Long): Boolean =
        nowElapsedRealtime >= startElapsedRealtime
}

/** Quiet dock copy. Not a Volt. Log set stays the filled act. */
object SetStopwatchCopy {
    const val START = "Time set"
    const val STOP = "Stop"
    const val SWITCH_TITLE = "Stop timing and switch?"
    const val SWITCH_BODY =
        "This set clock is still running. Stop it to change lifts. " +
            "The time already counted stays with this lift."
    const val SWITCH_CONFIRM = "Stop and switch"
}
