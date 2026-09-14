package com.sinura.personaltrainer.domain

/**
 * Gym-floor chrome after the live-57 spacing check.
 *
 * Phone height is the scarce resource. The expanded lift card is the
 * one current-lift copy (still, number, name, 0/4). The THIS LIFT dock
 * strip was the same identity stacked under that card. Warm-up / RPE
 * stay collapsed while rest is idle. Log set is the one filled Volt;
 * Start next stays reachable without becoming a second Volt bar.
 */
object FloorCompactChrome {
    /** The expanded card is the identity. Do not pin a second THIS LIFT strip. */
    fun showSelectedLiftDock(): Boolean = false

    /** RPE chips belong to a running rest, not an idle half-screen optional panel. */
    fun showOptionalLogOptions(restRunning: Boolean): Boolean = restRunning

    /** Start next is a quiet keep-going, not a second filled Volt. */
    fun idleStartNextIsVolt(): Boolean = false
}
