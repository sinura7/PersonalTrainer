package com.sinura.personaltrainer.ui.navigation

/**
 * Gym-floor words for the live session bar.
 *
 * Strength still speaks workout / sets. Live cardio is a session with a
 * clock — never a fake 0-set workout.
 */
object LiveBarCopy {
    const val IN_PROGRESS = "In progress"
    const val SETS = "sets"

    fun resumeLabel(kind: LiveBarKind): String = when (kind) {
        LiveBarKind.WORKOUT -> "Back to the workout"
        LiveBarKind.ACTIVITY -> "Back to the session"
    }

    fun actionsDescription(kind: LiveBarKind): String = when (kind) {
        LiveBarKind.WORKOUT -> "Workout actions"
        LiveBarKind.ACTIVITY -> "Session actions"
    }

    fun finish(kind: LiveBarKind): String = when (kind) {
        LiveBarKind.WORKOUT -> "Finish workout"
        LiveBarKind.ACTIVITY -> "Finish session"
    }

    fun discard(kind: LiveBarKind): String = when (kind) {
        LiveBarKind.WORKOUT -> "Discard workout…"
        LiveBarKind.ACTIVITY -> "Discard session…"
    }

    fun discardTitle(kind: LiveBarKind): String = when (kind) {
        LiveBarKind.WORKOUT -> "Discard this workout?"
        LiveBarKind.ACTIVITY -> "Discard this session?"
    }

    fun discardBody(kind: LiveBarKind, totalSets: Int): String {
        if (kind == LiveBarKind.ACTIVITY || totalSets <= 0) {
            return "This deletes the session. This cannot be undone."
        }
        val noun = if (totalSets == 1) "set" else "sets"
        return "This deletes the session and its $totalSets logged $noun. " +
            "This cannot be undone."
    }

    fun showsSets(kind: LiveBarKind): Boolean = kind == LiveBarKind.WORKOUT
}
