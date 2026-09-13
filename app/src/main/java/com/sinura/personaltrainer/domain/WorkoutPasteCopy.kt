package com.sinura.personaltrainer.domain

/**
 * Owner-facing words for pasting a written workout into Create a routine.
 *
 * The user is the author. Temper reads the text and fills lifts. It does
 * not invent a program.
 */
object WorkoutPasteCopy {
    const val FIELD_LABEL = "Paste a written workout"
    const val HINT =
        "Paste the session as written. Temper fills the lifts, sets, reps, and timers from the words. It does not write the program."
    const val CONFIRM = "Fill from paste"
    const val PASTING = "Reading…"
    const val EMPTY = "Paste a workout first."
    const val NOTHING = "Could not read a workout in that text."
    const val FAILED = "Could not fill that workout. Try again."
    const val UNMATCHED = "Unmatched"
    const val UNMATCHED_BODY =
        "Temper did not find these in the library. Pick a lift for each."
    const val PICK = "Pick a lift"
    const val PASTED_HEADING = "Pasted"
    const val UNMATCHED_NOTES = "Unmatched"
    const val HOLDS_HEADING = "Holds (time, not reps)"
    const val WARMUP_HEADING = "Warm-up"
    const val WEEKLY_HEADING = "Weekly layout"
    const val PROGRESSION_HEADING = "Progression"

    fun alsoCreated(names: List<String>): String = when {
        names.isEmpty() -> ""
        names.size == 1 -> "Also made ${names.single()}."
        names.size == 2 -> "Also made ${names[0]} and ${names[1]}."
        else -> "Also made ${names.dropLast(1).joinToString(", ")}, and ${names.last()}."
    }
}
