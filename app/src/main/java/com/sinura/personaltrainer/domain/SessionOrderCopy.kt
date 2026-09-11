package com.sinura.personaltrainer.domain

/**
 * How a session of lifts is spoken: cart order, then the work on each card.
 *
 * The picker, the editor strip, Plan's routine preview, and Home's leftover
 * lift line all read this so "1, 2, 3" means the same thing on every calendar.
 */
object SessionOrderCopy {
    const val SECTION = "Session"
    const val CART = "Cart"
    const val WORK = "Work"
    const val REST = "Rest"
    const val LOAD = "Load"
    const val ORDER = "Order"
    const val EMPTY_PREVIEW = "No lifts yet"
    const val READY = "Ready"
    const val FREE_WORKOUT = "Start a workout"
    const val START_ROW = "Start"
    const val SAVE_ROUTINE = "Save"
    const val ADD_EXTRA = PlanDayCopy.ADD_EXTRA
    const val ADD_LIFT_FAILED = "Could not add that lift. Try again."
    const val LIFT_NAME_REQUIRED = "Lift name is required."
    const val CREATE_LIFT_FAILED = "Could not create that lift. Try again."
    const val SAVE_LIFT_FAILED = "Could not save that lift. Try again."
    const val REMOVE_LIFT_FAILED = "Could not remove that lift. Try again."
    const val REORDER_LIFT_FAILED = "Could not reorder that lift. Try again."
    const val NEED_A_LIFT = "Add at least one lift before starting this routine."
    const val EMPTY_SESSION_BODY = "Pick the first lift, then log weight and reps."
    const val CARDIO_ON_THIS_DAY =
        "Cardio on this day. Does not replace the lifts."
    const val LATER_SESSION =
        "Another session this weekday. Pick a routine. Does not replace the others."
    const val COMPOSE_LATER = "Name a new session and set the lifts."
    const val REMOVE_SESSION = "Stops this session on this weekday. Logged work stays."

    /** Says the two things a tap does now: it counts, and it is already saved. */
    const val PICKER_HINT = "Tap in the order you'll lift. 1 is first, and each tap is saved."
    const val TAP_TO_SET = "Tap a lift to set sets, reps, rest and load."
    const val EMPTY_EDITOR_BODY =
        "Tap lifts in the order you'll do them. Each tap adds one to this routine."
    const val EMPTY_WEEK_BODY =
        "Tap lifts in the order you'll do them. Each tap adds one to this day."
    const val EDIT_LIFTS_SUBTITLE = "Tap a card for sets, reps, rest and load."
    const val AGENDA_SEPARATE =
        "Each session stays its own. Finish one, then start the next."

    fun liftIndex(number: Int, total: Int): String = "Lift $number of $total"

    fun sectionLabel(count: Int): String =
        if (count == 1) "Session · 1 lift" else "Session · $count lifts"

    fun daySection(dayLabel: String, count: Int): String = "$dayLabel · $count"

    fun numberedPreview(names: List<String>, limit: Int = 3): String {
        if (names.isEmpty()) return EMPTY_PREVIEW
        val cap = limit.coerceAtLeast(1)
        val shown = names.take(cap).mapIndexed { index, name ->
            "${index + 1} $name"
        }.joinToString(" · ")
        val rest = names.size - cap
        // Count first so a one-line ellipsis cannot eat the remainder.
        return if (rest > 0) "${names.size} lifts · $shown" else shown
    }

    /**
     * A settled row says so, in front of the order. DONE, SKIPPED and MOVED
     * are the states a row cannot be started from as it stands, and the
     * previous shape returned the lift preview whenever names existed — so
     * every one of them rendered identically to a live planned row, and the
     * words below were unreachable for any session that had lifts. A finished
     * day and a waiting day looked the same, and the only difference on screen
     * was a small trailing "Start" that appeared on one and not the other.
     */
    fun settledLabel(status: OccurrenceStatus): String? = when (status) {
        OccurrenceStatus.DONE -> "Done"
        OccurrenceStatus.SKIPPED -> "Skipped"
        OccurrenceStatus.MOVED -> "Moved"
        OccurrenceStatus.PLANNED, OccurrenceStatus.MISSED -> null
    }

    /**
     * Agenda and Plan day rows. When the routine is known, the order is the
     * line. Empty strength is "No lifts yet"; cardio/mixed planned is "Ready".
     * Never dump a schema enum onto the gym floor.
     *
     * A settled status is prefixed onto the order rather than replacing it:
     * "Done · 1 Bench · 2 Row" still tells you what the day was.
     */
    fun occurrenceLine(
        status: OccurrenceStatus,
        names: List<String>,
        modality: ScheduleModality = ScheduleModality.STRENGTH,
    ): String {
        if (names.isNotEmpty()) {
            val preview = numberedPreview(names)
            return settledLabel(status)?.let { "$it · $preview" } ?: preview
        }
        return when (status) {
            OccurrenceStatus.PLANNED ->
                if (modality == ScheduleModality.STRENGTH) EMPTY_PREVIEW else READY
            OccurrenceStatus.DONE -> "Done"
            OccurrenceStatus.SKIPPED -> "Skipped"
            OccurrenceStatus.MISSED -> "Missed"
            OccurrenceStatus.MOVED -> "Moved"
        }
    }

    fun cardSpoken(
        number: Int,
        name: String,
        muscleGroup: String,
        sets: Int,
        reps: Int,
        restClock: String,
        load: String?,
    ): String = buildString {
        append("$number. $name")
        if (muscleGroup.isNotBlank()) append(". $muscleGroup")
        append(". $sets by $reps. Rest $restClock")
        if (!load.isNullOrBlank()) append(". $load")
    }

    /**
     * How far a finished lift got, in the same `3/3` language the floor card
     * uses while logging. No target means the count stands alone.
     */
    fun filledCount(workingLogged: Int, targetSets: Int): String =
        if (targetSets > 0) "$workingLogged/$targetSets" else workingLogged.toString()

    fun workValue(sets: Int, reps: Int): String = "$sets × $reps"

    /**
     * TalkBack for a filled history card. Prescription language only when the
     * lift had one; otherwise the logged count, never "0 by 0. Rest 0:00".
     */
    fun filledSpoken(
        number: Int,
        name: String,
        muscleGroup: String,
        workingLogged: Int,
        targetSets: Int,
        targetReps: Int,
        restClock: String?,
        load: String?,
    ): String = buildString {
        append("$number. $name")
        if (muscleGroup.isNotBlank()) append(". $muscleGroup")
        append(". ${filledCount(workingLogged, targetSets)}")
        if (targetSets > 0) {
            append(". ${workValue(targetSets, targetReps.coerceAtLeast(1))}")
        } else {
            append(if (workingLogged == 1) " set" else " sets")
        }
        if (!restClock.isNullOrBlank()) append(". Rest $restClock")
        if (!load.isNullOrBlank()) append(". $load")
    }
}
