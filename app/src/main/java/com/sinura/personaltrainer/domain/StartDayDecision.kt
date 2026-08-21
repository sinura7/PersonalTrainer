package com.sinura.personaltrainer.domain

/**
 * What tapping Start on a planned day should do, decided before anything is written.
 *
 * Pure and separate from `StartTrainingDay` so the rules can be tested. It lives in `domain`
 * rather than beside the use case because that is the only tree the pure-JVM test lane
 * compiles — a rule this consequential that no lane can execute is a rule nobody is checking.
 * They are worth testing
 * because two of them replace behaviours that were actively misleading:
 *
 * - A day pinned to a routine that has since been deleted or emptied used to fall through to a
 *   free workout named after the focus. You tapped "Start Legs", got an empty session titled
 *   "Legs", and only found out at the gym that none of your lifts were in it.
 * - An in-progress session used to be returned as if it were the thing you asked for. You
 *   tapped Thursday's Pull and landed in Tuesday's half-finished Legs, with nothing on screen
 *   saying so.
 *
 * Both now have a name, and both surface to the user as themselves.
 */
sealed interface StartDayDecision {
    /** A rest day: nothing to start, nowhere to go. */
    data object Rest : StartDayDecision

    /** A session is already running; the caller asks the user, it never decides for them. */
    data class Blocked(val inProgressSessionId: String) : StartDayDecision

    data class StartRoutine(val routine: Routine) : StartDayDecision

    /** A focus-only day legitimately starts a free workout named after its focus. */
    data class StartFree(val focusTitle: String) : StartDayDecision

    data class RoutineGone(val message: String) : StartDayDecision
}

internal fun decideStart(
    day: SuggestedTrainingDay,
    routine: Routine?,
    inProgress: WorkoutSession?,
): StartDayDecision {
    if (day.isRest) return StartDayDecision.Rest
    if (inProgress != null) return StartDayDecision.Blocked(inProgress.id)
    val pinnedRoutineId = day.routineId
    if (pinnedRoutineId != null) {
        if (routine == null) return StartDayDecision.RoutineGone(ROUTINE_DELETED)
        if (routine.exercises.isEmpty()) {
            return StartDayDecision.RoutineGone(routineEmpty(routine.name))
        }
        return StartDayDecision.StartRoutine(routine)
    }
    return StartDayDecision.StartFree(day.focusTitle)
}

internal const val ROUTINE_DELETED =
    "That day's routine no longer exists. Swap or unpin it in Plan."

internal fun routineEmpty(name: String): String =
    "$name has no lifts yet. Add lifts or swap the day's routine."
