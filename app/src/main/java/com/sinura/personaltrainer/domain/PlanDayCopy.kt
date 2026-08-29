package com.sinura.personaltrainer.domain

/**
 * Plan day page: schedule blocks, not a start surface.
 */
object PlanDayCopy {
    const val ADD_SESSION = "Add session"
    const val ADD_SESSION_SUBTITLE = "Workout, cardio, or a short auxiliary block."
    const val EMPTY = "Nothing on this day yet."
    const val EMPTY_BODY = "Add a workout, cardio, or a short stretch / hold block."
    const val REMOVE = "Remove"
    const val WORKOUT = "Workout"
    const val CARDIO = "Cardio"
    const val AUXILIARY = "Auxiliary"
    const val PICK_KIND = "What to add"
    const val PICK_CARDIO = "Cardio"
    const val PICK_AUX = "Auxiliary"
    const val PICK_WORKOUT = "Workout"
    const val CANCEL = "Cancel"
    const val NEW_WORKOUT = "New workout…"
    const val CARDIO_ALREADY = "This day already has cardio."
    const val PAST = "Past days are a record."
    const val WORKOUT_SUBTITLE = "The lifts for this weekday."
    const val AUX_SUBTITLE = "Short stretch, core, back, hips, or holds."
    const val WHEN = "When"
    const val AUX_ALREADY = "This day already has every auxiliary block."
    const val BUILD_WEEKDAY = "Name this weekday and set the lifts."
    const val PICK_NAMED_PINNED = "Or create a named routine on Plan, then pick it here."
    const val PICK_NAMED_OPEN = "Or pick a routine you already named."

    fun weekdayTitle(day: Weekday): String = CustomWeekPolicy.routineName(day)

    fun cardioPickLabel(type: CardioType): String = when (type) {
        CardioType.RUN -> "Run / sprints"
        else -> CardioCopy.name(type)
    }
}
