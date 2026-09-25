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
    const val HEADER_FINISH = "Finish"
    const val HEADER_DISCARD = "Discard"

    /**
     * Said first while a logged set is open for correction. Finish used to end the workout and
     * drop the change without a word; the set kept its saved values (audit UI-2).
     */
    const val EDIT_OPEN =
        "A set you logged is open for changes. Save as is keeps it as it was saved. " +
            "To keep a change, go back and tap Save changes."
    const val BACK_TO_EDIT = "Back to my change"

    /** The bottom bar's Finish while a logged set is open for changes on the workout screen. */
    const val BAR_EDIT_OPEN =
        "A set you logged is open for changes. Go to the session to save or cancel it, then finish."

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
