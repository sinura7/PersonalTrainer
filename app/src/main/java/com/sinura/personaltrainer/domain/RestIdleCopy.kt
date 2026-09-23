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
    const val NOT_RUNNING = "Not running"
    const val START_NEXT = "Start next"
    const val START = "Start"
    const val SHEET_TITLE = "Rest length"
    const val CHANGE_DURATION = "Tap to change duration."
    const val WARMUP_KICKER = "Warm-up"

    /**
     * The rest length the next rest will start with, named one way wherever it shows (design
     * audit D11, W1b): "Planned rest" on the rest page and in every spoken form. "Planned"
     * keeps it apart from the time left, which is the clock itself.
     */
    const val PLANNED_REST = "Planned rest"

    /**
     * The dock card's short form, under its REST kicker: [PLANNED] alone at rest, and
     * [plannedBeside] while rest runs. The card's text column is narrow beside Time set and
     * Start rest, or beside −15 / +15 / Skip, and the kicker already says rest.
     */
    const val PLANNED = "Planned"

    /** `Planned rest · 1:30`, the rest page's line under the lift. */
    fun planned(clock: String): String = "$PLANNED_REST · $clock"

    /** `Planned 1:30`, under the dock card's running clock. */
    fun plannedBeside(clock: String): String = "$PLANNED $clock"

    /** `Planned rest 1:30`, the same words for TalkBack, which reads the dot aloud. */
    fun plannedSpoken(clock: String): String = "$PLANNED_REST $clock"

    fun afterWarmupHint(): String = "Warm-ups do not start rest"

    fun spoken(clock: String, afterWarmup: Boolean): String =
        if (afterWarmup) {
            "Rest is not running. Warm-ups do not start rest. ${plannedSpoken(clock)}. " +
                "Start starts rest only."
        } else {
            "Rest is not running. ${plannedSpoken(clock)}. Start starts rest only."
        }

    fun dockSpoken(clock: String, afterWarmup: Boolean): String =
        if (afterWarmup) {
            "Rest is not running. Warm-ups do not start rest. ${plannedSpoken(clock)}. " +
                CHANGE_DURATION
        } else {
            "Rest is not running. ${plannedSpoken(clock)}. $CHANGE_DURATION"
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
