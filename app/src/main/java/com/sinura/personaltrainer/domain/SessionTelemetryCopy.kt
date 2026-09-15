package com.sinura.personaltrainer.domain

/**
 * Packet C top-strip Row B: session minutes, working sets, and volume.
 *
 * Elapsed is a minute grain so this strip is not a second ticking clock.
 * Rest / hold / stopwatch numerals stay in the dock (ADR-012).
 */
object SessionTelemetryCopy {
    const val OPEN_DETAILS = "Session details. Open rest timer."
    const val MINUTE_SUFFIX = "min"

    fun elapsedMinutes(elapsedSeconds: Int): Int =
        (elapsedSeconds.coerceAtLeast(0) / 60)

    fun elapsedMinutesLabel(elapsedSeconds: Int): String =
        "${elapsedMinutes(elapsedSeconds)} $MINUTE_SUFFIX"

    fun setsLabel(workingSets: Int): String {
        val count = workingSets.coerceAtLeast(0)
        return if (count == 1) "1 set" else "$count sets"
    }

    /**
     * At 360 dp / font 2.0, drop volume before truncating the session title.
     */
    fun includeVolume(fontScale: Float, widthDp: Int): Boolean =
        !(fontScale >= 2f && widthDp <= 360)

    fun line(
        elapsedSeconds: Int,
        workingSets: Int,
        volume: String?,
        includeVolume: Boolean,
    ): String {
        val parts = buildList {
            add(elapsedMinutesLabel(elapsedSeconds))
            add(setsLabel(workingSets))
            if (includeVolume && !volume.isNullOrBlank()) add(volume)
        }
        return parts.joinToString(" · ")
    }

    fun spoken(
        elapsedSeconds: Int,
        workingSets: Int,
        volume: String?,
        includeVolume: Boolean,
    ): String = "${line(elapsedSeconds, workingSets, volume, includeVolume)}. $OPEN_DETAILS"
}
