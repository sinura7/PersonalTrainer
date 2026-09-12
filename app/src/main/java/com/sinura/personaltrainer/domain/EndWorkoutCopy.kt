package com.sinura.personaltrainer.domain

/**
 * Finish on the live log is the explicit end: save the sets, or throw
 * the session away. X / back is go-Home with the session still live
 * (W-14 / W-15).
 */
object EndWorkoutCopy {
    const val TITLE = "End workout?"
    const val SAVE = "Save as is"
    const val DISCARD = "Leave without saving"
    const val LOG_FIRST = "Log a set to save."

    fun body(loggedSets: Int): String =
        if (loggedSets > 0) {
            "Save keeps the ${setWord(loggedSets)} you logged as a finished session. " +
                "Leave without saving throws this workout away."
        } else {
            "Nothing is logged yet. Save needs a set. Leave without saving throws this workout away."
        }

    fun canSave(loggedSets: Int): Boolean = loggedSets > 0

    private fun setWord(loggedSets: Int): String =
        if (loggedSets == 1) "set" else "$loggedSets sets"
}
