package com.sinura.personaltrainer.domain

/**
 * Idle rest must look and speak as not running (G-05, W-02, T-12).
 *
 * The planned duration is a label, not a countdown. Warm-ups never start
 * the clock; the copy says so instead of leaving a live-looking 1:00 up.
 * Start next is gone from this row (Packet A): it was a no-op or a
 * duplicate of Next lift. Log set is the filled act. Start is rest only.
 */
object RestIdleCopy {
    const val KICKER = "Not running"
    const val START_NEXT = "Start next"
    const val START = "Start"

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
}
