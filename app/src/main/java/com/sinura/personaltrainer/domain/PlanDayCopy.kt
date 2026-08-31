package com.sinura.personaltrainer.domain

/**
 * Plan day page: schedule blocks, not a start surface.
 */
object PlanDayCopy {
    const val ADD_SESSION = "Add session"
    const val ADD_SESSION_SUBTITLE = "Workout, cardio, or a short extra."
    const val ADD_EXTRA = "Add extra"
    const val EMPTY = "Nothing on this day yet."
    const val EMPTY_BODY = "Add a workout, cardio, or a warm-up / stretch block."
    const val REMOVE = "Remove"
    const val WORKOUT = "Workout"
    const val CARDIO = "Cardio"
    const val AUXILIARY = "Extra"
    const val PICK_KIND = "What to add"
    const val PICK_CARDIO = "Cardio"
    const val PICK_AUX = "Extra"
    const val PICK_WORKOUT = "Workout"
    const val CANCEL = "Cancel"
    const val NEW_WORKOUT = "New workout…"
    const val CARDIO_ALREADY = "This day already has cardio."
    const val PAST = "Past days are a record."
    const val WORKOUT_SUBTITLE = "The lifts for this weekday."
    const val AUX_SUBTITLE = "A warm-up, stretch, core, or holds. Its own session."
    const val WARM_UP = "Warm-up"
    const val MOBILITY = "Mobility"
    const val UP = "Up"
    const val DOWN = "Down"
    const val AUX_ALREADY = "This day already has every extra."
    const val BUILD_WEEKDAY = "Name this weekday and set the lifts."
    const val PICK_NAMED_PINNED = "Or create a named routine on Plan, then pick it here."
    const val PICK_NAMED_OPEN = "Or pick a routine you already named."

    fun weekdayTitle(day: Weekday): String = CustomWeekPolicy.routineName(day)

    fun moveUp(title: String): String = "Move $title up"

    fun moveDown(title: String): String = "Move $title down"

    fun cardioPickLabel(type: CardioType): String = when (type) {
        CardioType.RUN -> "Run / sprints"
        else -> CardioCopy.name(type)
    }
}
