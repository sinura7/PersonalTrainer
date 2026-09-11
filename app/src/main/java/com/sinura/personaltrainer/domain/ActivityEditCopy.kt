package com.sinura.personaltrainer.domain

/** Gym-floor words for activity-detail edits and the writes the store refuses. */
object ActivityEditCopy {
    const val OPTIONS = "Session options"
    const val DELETE = "Delete session…"
    const val DELETE_CONFIRM = "Delete"

    const val SET_REPAIR_REFUSED =
        "Completed activity blocks are a snapshot and cannot be repaired."

    const val REPEAT_REFUSED =
        "Only a live strength session can be repeated."

    const val NOTES_LIVE_REFUSED =
        "Finish this session before editing its notes."

    const val DELETE_LIVE_REFUSED =
        "Only a finished session can be deleted. Discard owns one that is still live."

    const val GONE = "That session is gone."

    fun deleteTitle(title: String): String {
        val name = title.trim()
        return if (name.isNotEmpty()) "Delete $name?" else "Delete this session?"
    }

    fun deleteBody(setCount: Int, hasCardio: Boolean): String {
        val sets = if (setCount == 1) "1 logged set" else "$setCount logged sets"
        val what = when {
            setCount > 0 && hasCardio -> "$sets and its cardio"
            setCount > 0 -> sets
            hasCardio -> "its cardio"
            else -> "this session"
        }
        return "This deletes $what from history. This cannot be undone."
    }
}
