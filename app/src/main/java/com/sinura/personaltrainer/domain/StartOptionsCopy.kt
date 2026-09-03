package com.sinura.personaltrainer.domain

/**
 * Quiet chrome that opens [com.sinura.personaltrainer.ui.workout.StartOptionsSheet]
 * from Body, History, and Plan. Never a filled Volt — Home owns Start.
 */
object StartOptionsCopy {
    const val OPEN = "Log or start cardio"
    const val OPEN_SPOKEN = "Log a past workout or start unplanned cardio"
    const val PLAN_EMPTY_BODY =
        "Add session puts a workout, cardio, or stretch on the selected day. " +
            "Log a past workout or start unplanned cardio from Log or start cardio."
}
