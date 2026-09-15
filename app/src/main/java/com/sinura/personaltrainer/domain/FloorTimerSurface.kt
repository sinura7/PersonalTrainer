package com.sinura.personaltrainer.domain

/**
 * One clock on the gym floor, two modes.
 *
 * Rest counts down with the signed [elapsedRealtime] alarm path.
 * A running set (today: a static hold) counts up. Switching modes
 * hides the other surface — no second countdown on the live log,
 * and no overlay rest clock (ADR-012 / ADR-013).
 */
object FloorTimerSurface {
    enum class Mode {
        /** Planned or running rest. */
        REST,

        /** In-set work clock (hold / future manual stopwatch). */
        SET,
    }

    const val SET_KICKER = "SET"
    const val HOLD_KICKER = "HOLD"
    const val REST_STATE = "rest"
    const val SET_STATE = "set"

    fun mode(holdRunning: Boolean): Mode =
        if (holdRunning) Mode.SET else Mode.REST

    /**
     * Quiet instrument readout for the header strip.
     * Numbers stay tabular; colour is not the only channel.
     */
    fun instrumentState(
        holdRunning: Boolean,
        holdElapsedSeconds: Int,
        restRunning: Boolean,
        restRemainingSeconds: Int,
        plannedRestSeconds: Int,
    ): String {
        return when {
            holdRunning ->
                "$HOLD_KICKER ${HoldWork.clock(holdElapsedSeconds.coerceAtLeast(0))}"
            restRunning ->
                "$REST_STATE ${RestTimer.formatClock(restRemainingSeconds.coerceAtLeast(0))}"
            else ->
                "$REST_STATE ${RestTimer.formatClock(plannedRestSeconds.coerceAtLeast(0))}"
        }
    }

    fun setClockSeconds(elapsedSeconds: Int): Int = elapsedSeconds.coerceAtLeast(0)
}
