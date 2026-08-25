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

    const val PICKER_HINT = "Tap in the order you'll lift. 1 is first."
    const val TAP_TO_SET = "Tap a lift to set sets, reps, rest and load."
    const val EMPTY_EDITOR_BODY =
        "Tap lifts in the order you'll do them. Accept to line them on this day."
    const val EMPTY_WEEK_BODY =
        "Tap lifts in the order you'll do them, then move to the next day."
    const val EDIT_LIFTS_SUBTITLE = "Tap a card for sets, reps, rest and load."

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
        return if (rest > 0) "$shown · +$rest more" else shown
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
}
