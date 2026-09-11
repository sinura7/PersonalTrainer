package com.sinura.personaltrainer.domain

/**
 * Idle rest must look and speak as not running (G-05, W-02, T-12).
 *
 * The planned duration is a label, not a countdown. Warm-ups never start
 * the clock; the copy says so instead of leaving a live-looking 1:00 up.
 */
object RestIdleCopy {
    const val KICKER = "Not running"

    fun planned(clock: String): String = "$clock planned"

    fun dockDuration(clock: String, afterWarmup: Boolean): String =
        if (afterWarmup) "Warm-up · $clock" else planned(clock)

    fun afterWarmupHint(): String = "Warm-ups do not start rest"

    fun spoken(clock: String, afterWarmup: Boolean): String =
        if (afterWarmup) {
            "Rest is not running. Warm-ups do not start rest. $clock planned. " +
                "Start rest when you are ready."
        } else {
            "Rest is not running. $clock planned. Start rest when you are ready."
        }
}
