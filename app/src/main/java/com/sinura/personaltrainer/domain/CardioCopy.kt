package com.sinura.personaltrainer.domain

/**
 * Gym-floor words for live cardio.
 *
 * Schema names (`RUN`, `RIDE`) stay in the enum and in persistence. The live screen,
 * the leave dialog, the activity summary, and the composer type chips speak
 * Run / Ride / Walk. Finish is the one Volt on the live screen; Leave running is
 * the Volt only on the leave dialog. Discard is never one tap — it always opens
 * a named confirm. Composer chips call [name]; they do not keep a second map.
 */
object CardioCopy {
    const val FINISH = "Finish"
    const val FINISHING = "Finishing…"
    const val LEAVE_RUNNING = "Leave running"
    const val STAY = "Stay"
    const val DISCARD = "Discard"
    const val DISCARD_INSTEAD = "Discard this session instead"
    const val DONE = "Done"

    const val CLOCK_CAPTION =
        "This clock keeps running if you leave. The bar at the bottom of any other screen brings you back."

    const val TYPE = "Type"
    const val INDOOR = "Indoor"
    const val OUTDOOR = "Outdoor"
    const val DISTANCE = "Distance"
    const val DISTANCE_OPTIONAL = "(optional)"

    fun distanceLabel(unit: DistanceUnit): String =
        "$DISTANCE ${unit.suffix} $DISTANCE_OPTIONAL"

    const val LEAVE_TITLE = "Leave cardio?"
    const val LEAVE_BODY =
        "This session keeps running. The bar at the bottom of any other screen brings you back."

    const val DISCARD_TITLE = "Discard this session?"
    const val DISCARD_BODY = "This deletes the session. This cannot be undone."
    const val DISCARD_CONFIRM = "Discard"

    /** Live-screen stack, top to bottom. [FINISH] is the only filled Volt. */
    val LIVE_ACTIONS = listOf(FINISH, LEAVE_RUNNING, DISCARD)

    /** Leave-dialog stack, top to bottom. [LEAVE_RUNNING] is the only filled Volt. */
    val LEAVE_DIALOG_ACTIONS = listOf(LEAVE_RUNNING, STAY, DISCARD_INSTEAD)

    const val LIVE_VOLT = FINISH
    const val LEAVE_DIALOG_VOLT = LEAVE_RUNNING

    fun name(type: CardioType): String = when (type) {
        CardioType.RUN -> "Run"
        CardioType.RIDE -> "Ride"
        CardioType.ROW -> "Row"
        CardioType.SWIM -> "Swim"
        CardioType.WALK -> "Walk"
        CardioType.HIKE -> "Hike"
        CardioType.SKI -> "Ski"
        CardioType.OTHER -> "Other"
    }
}
