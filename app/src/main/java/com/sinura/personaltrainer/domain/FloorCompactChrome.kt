package com.sinura.personaltrainer.domain

/**
 * Gym-floor chrome after the live-58 compact floor.
 *
 * Phone height is the scarce resource. The expanded lift card is the
 * one current-lift copy (still, number, name, 0/4, overflow ⋮). Weight
 * and reps are stacked snap-scroll wheels, not one cramped pair and not
 * a typing well. The THIS LIFT dock strip stays off. Warm-up / RPE stay
 * collapsed while rest is idle. Log set is the one filled Volt; Start
 * next stays reachable without becoming a second Volt bar.
 */
object FloorCompactChrome {
    /** The expanded card is the identity. Do not pin a second THIS LIFT strip. */
    fun showSelectedLiftDock(): Boolean = false

    /** RPE chips belong to a running rest, not an idle half-screen optional panel. */
    fun showOptionalLogOptions(restRunning: Boolean): Boolean = restRunning

    /** Start next is a quiet keep-going, not a second filled Volt. */
    fun idleStartNextIsVolt(): Boolean = false

    /** Swap / remove live on the identity row, not a row of their own. */
    fun overflowOnHeaderRow(): Boolean = true

    /** Compact floor: a weight row, then a reps (or time) row. */
    fun stackWeightAboveReps(): Boolean = true

    /** Compact floor: swipe a live scroller, do not type the number. */
    fun weightAndRepsAreWheels(): Boolean = true
}
