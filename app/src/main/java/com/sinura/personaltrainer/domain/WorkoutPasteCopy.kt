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
    const val NO_SESSION_NAME = "No session name at the top (like Lower A)."
    const val IGNORED_STARS = "The leftover ** on the title was ignored."
    const val NO_NUMBERED_LIFTS = "No numbered lifts."
    const val FAILED = "Could not fill that workout. Try again."
    const val EMPTY_LIBRARY = "The lift library is empty, so nothing could be matched."
    const val ISSUE_TITLE = "This line did not fill"
    const val UNMATCHED = "These lines did not fill"
    const val UNMATCHED_BODY =
        "Everything that matched is already in. Each row quotes the line and says why it did not fill."
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

    fun quotedNames(names: List<String>): String =
        names.filter { it.isNotBlank() }.joinToString(" or ") { "“$it”" }

    fun reason(kind: PasteIssueKind, names: List<String>): String = when (kind) {
        PasteIssueKind.UNKNOWN_EXERCISE -> {
            val quoted = quotedNames(names).ifBlank { "that name" }
            "No library lift named $quoted."
        }
        PasteIssueKind.BAD_DOSE ->
            "Could not read the sets, reps, or hold time."
        PasteIssueKind.TWO_LIFTS_NEEDED -> {
            val quoted = quotedNames(names).ifBlank { "one of the names" }
            "This line is two lifts. $quoted did not match the library."
        }
        PasteIssueKind.HOLD_VS_REPS -> {
            val name = names.firstOrNull().orEmpty().ifBlank { "That lift" }
            "$name is a timed hold (seconds), not reps — or the other way around."
        }
    }

    fun issue(item: PastedUnmatched): String = "“${item.displayLine()}” — ${item.reason}"

    fun pasteIssues(items: List<PastedUnmatched>): String {
        if (items.isEmpty()) return ""
        val head = issue(items.first())
        val more = items.size - 1
        return if (more <= 0) head else "$head And $more more below."
    }

    fun wholeFailure(
        noSessionName: Boolean,
        noNumberedLifts: Boolean,
        leftoverStars: Boolean,
    ): String {
        val parts = mutableListOf<String>()
        if (noSessionName) {
            parts += NO_SESSION_NAME
            if (leftoverStars) parts += IGNORED_STARS
        }
        if (noNumberedLifts) parts += NO_NUMBERED_LIFTS
        return parts.joinToString(" ").ifBlank { NO_SESSION_NAME }
    }
}
