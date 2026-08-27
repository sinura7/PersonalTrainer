package com.sinura.personaltrainer.domain

/**
 * First-visit Home: pin a week, start now, or generate a schedule.
 *
 * The questionnaire used to own the launch. That trapped anyone who already
 * knew what they wanted to lift. Home is the front door; this copy is the
 * invitation, not a gate.
 */
object GetStartedCopy {
    const val TITLE = "Get started"
    const val BODY =
        "Build a week, start a workout, or answer a few questions and the app " +
            "will generate the schedule."

    /** The generated path — the one Volt on a first visit. */
    const val GENERATE = "Generate a schedule"

    const val BUILD = "Build a week"

    const val WORKOUT = "Start a workout"

    const val EMPTY_CAPTION =
        "No week is pinned yet. Generate one, build it yourself, or just train."
}
