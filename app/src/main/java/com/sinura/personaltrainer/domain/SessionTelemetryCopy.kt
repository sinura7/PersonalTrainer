package com.sinura.personaltrainer.domain

/**
 * Packet C's session minutes, now read by Session summary.
 *
 * Elapsed is a minute grain so this is not a second ticking clock.
 * Rest / hold / stopwatch numerals stay in the dock (ADR-012).
 */
object SessionTelemetryCopy {
    const val MINUTE_SUFFIX = "min"

    fun elapsedMinutes(elapsedSeconds: Int): Int =
        (elapsedSeconds.coerceAtLeast(0) / 60)

    fun elapsedMinutesLabel(elapsedSeconds: Int): String =
        "${elapsedMinutes(elapsedSeconds)} $MINUTE_SUFFIX"
}
