package com.sinura.personaltrainer.domain

/**
 * The last five seconds of rest: a tick and a pulse on 5, 4, 3, 2, 1, so
 * the lifter is standing when the cue goes. The owner's R-04; the audit's
 * G-10 in one line — tick means hurry up, tone means stand up.
 *
 * 5 and 4 are light. 3, 2, 1 are heavier (D-06), so the last stretch
 * feels like a different instrument, not the same click five times.
 *
 * Pure: where the ticks fall, given the deadline. The service posts one
 * runnable per boundary from [nextTick]; nothing polls, and a ±15 s simply
 * re-asks.
 *
 * Reach: as far as the shade's countdown — the process alive and the CPU
 * awake. Screen off in doze, the alarm path still delivers the completion
 * cue; five ticks are not worth a wakelock.
 */
object RestTick {
    const val FIRST = 5
    const val WARN_FROM = 3
    const val TITLE = "Last five seconds"
    const val CAPTION = "Light on 5 and 4. Heavier on 3, 2, 1."

    /** Felt pulse for 5 and 4 — a detent, not a cue. */
    const val LIGHT_PULSE_MS = 40L

    /** Felt pulse for 3, 2, 1 — twice as long, still not the complete waveform. */
    const val WARN_PULSE_MS = 80L

    fun isWarn(second: Int): Boolean = second in 1..WARN_FROM

    fun pulseMs(second: Int): Long =
        if (isWarn(second)) WARN_PULSE_MS else LIGHT_PULSE_MS

    /** The instant the countdown crosses to [second]: the tick for that second. */
    fun tickAt(endsAtElapsedRealtime: Long, second: Int): Long =
        endsAtElapsedRealtime - second * MILLIS_PER_SECOND

    /**
     * The next boundary at or after [nowElapsedRealtime], as the second it
     * announces — 5 first, 1 last — or null once the rest is past its last
     * tick. A boundary that is exactly now counts: a -15 s that lands the
     * countdown on five must tick five now, not wait for four.
     *
     * [ticked] is the second this deadline last announced, so a runnable
     * that fired exactly on its boundary asks for the one below it and
     * never itself again. Null for a deadline nothing has ticked for yet.
     */
    fun nextTick(endsAtElapsedRealtime: Long, nowElapsedRealtime: Long, ticked: Int? = null): Int? =
        (FIRST downTo 1).firstOrNull { second ->
            (ticked == null || second < ticked) &&
                tickAt(endsAtElapsedRealtime, second) >= nowElapsedRealtime
        }

    /**
     * True while [second]'s boundary is the current second: a tick posted
     * for one deadline must not sound against another after a ±15 s landed
     * between the post and the fire.
     */
    fun isDue(endsAtElapsedRealtime: Long, second: Int, nowElapsedRealtime: Long): Boolean {
        val since = nowElapsedRealtime - tickAt(endsAtElapsedRealtime, second)
        return since in 0 until MILLIS_PER_SECOND
    }

    private const val MILLIS_PER_SECOND = 1_000L
}
