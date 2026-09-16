package com.sinura.personaltrainer.domain

/**
 * Idle rest must look and speak as not running (G-05, W-02, T-12).
 *
 * The planned duration is a dim instrument face, not a countdown: REST
 * kicker, `numeralMd` in secondary ink, empty track, and Start. Warm-ups
 * never start the clock; the copy says so instead of leaving a live-looking
 * 1:00 up. Start next is gone from this row (Packet A): it was a no-op or a
 * duplicate of Next lift. Log set is the filled act. Start is rest only.
 */
object RestIdleCopy {
    const val KICKER = "Not running"
    const val START_NEXT = "Start next"
    const val START = "Start"
    const val SHEET_TITLE = "Rest length"
    const val CHANGE_DURATION = "Tap to change duration."
    const val WARMUP_KICKER = "Warm-up"

    fun planned(clock: String): String = "Rest $clock"

    fun dockDuration(clock: String, afterWarmup: Boolean): String =
        if (afterWarmup) "Warm-up · $clock" else planned(clock)

    fun afterWarmupHint(): String = "Warm-ups do not start rest"

    fun spoken(clock: String, afterWarmup: Boolean): String =
        if (afterWarmup) {
            "Rest is not running. Warm-ups do not start rest. Rest $clock. " +
                "Start starts rest only."
        } else {
            "Rest is not running. Rest $clock. Start starts rest only."
        }

    fun dockSpoken(clock: String, afterWarmup: Boolean): String =
        if (afterWarmup) {
            "Rest is not running. Warm-ups do not start rest. Rest $clock. " +
                CHANGE_DURATION
        } else {
            "Rest is not running. Rest $clock. $CHANGE_DURATION"
        }

    fun startSpoken(totalSeconds: Int): String =
        "Start rest, ${spokenAmount(totalSeconds.coerceAtLeast(0))}"

    fun spokenAmount(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val minutes = safe / 60
        val seconds = safe % 60
        val minutePart = when {
            minutes <= 0 -> null
            minutes == 1 -> "1 minute"
            else -> "$minutes minutes"
        }
        val secondPart = when {
            seconds <= 0 && minutes > 0 -> null
            seconds == 1 -> "1 second"
            else -> "$seconds seconds"
        }
        return listOfNotNull(minutePart, secondPart).joinToString(" ")
    }
}
