package com.sinura.personaltrainer.domain

/**
 * Gym-floor chrome after the live-58 compact floor: the three decisions the floor still
 * reads from here. Phone height is the scarce resource.
 *
 * W2a removed the thirty-one constants no production code read; each decision they named is
 * held by a rendered test (the floor's render tests, mapped in audit T1c-2's ledger).
 */
object FloorCompactChrome {
    /**
     * Packet A: an empty free workout has no rest, stopwatch, or Start next.
     * The dock Volt is Add a lift.
     */
    fun emptySessionHidesTimerDock(): Boolean = true

    /**
     * Before the first working set of the lift, or when the draft already matches the
     * suggestion, the Next-set coach is a one-line strip (numbers, Why?, Apply) instead
     * of the tall card with delta, reason, and inline evidence.
     */
    fun coachUsesCompactStrip(preparePhase: Boolean, entryMatchesSuggestion: Boolean): Boolean =
        preparePhase || entryMatchesSuggestion

    /** Working | Warm-up uses dense chips so the toggle does not outrank the hero numerals. */
    fun setTypeToggleUsesCompactChips(): Boolean = true
}
