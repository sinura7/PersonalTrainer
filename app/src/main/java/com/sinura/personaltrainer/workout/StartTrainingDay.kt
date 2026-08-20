package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable

private const val TAG = "PT/StartTrainingDay"

sealed interface StartDayOutcome {
    /** Open this session. Covers both a new session and resuming one already in progress. */
    data class Open(val sessionId: String) : StartDayOutcome

    /** A rest day: nothing to start, and nowhere to navigate. */
    data object Ignored : StartDayOutcome

    data class Failed(val message: String) : StartDayOutcome
}

/**
 * Turns a planned day into an open session.
 *
 * Home and Schedule held byte-identical copies of this, down to the fallback chain
 * (routine missing or empty → free workout named after the focus). Two copies of a rule about
 * what happens to the user's training day is one copy too many.
 */
class StartTrainingDay(
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
) {
    suspend operator fun invoke(day: SuggestedTrainingDay): StartDayOutcome {
        if (day.isRest) return StartDayOutcome.Ignored

        // A shortcut, not a guard: startRoutine/startFreeWorkout resolve the same race inside
        // a transaction, so a failure here costs nothing but an extra round trip.
        val current = runCatchingCancellable { workoutRepository.getInProgress() }
            .getOrElse { thrown ->
                AppLog.w(TAG, "Reading the in-progress session failed", thrown)
                null
            }
        if (current != null) return StartDayOutcome.Open(current.id)

        return runCatchingCancellable {
            val routine = day.routineId?.let { routineRepository.getById(it) }
            val session = if (routine == null || routine.exercises.isEmpty()) {
                workoutRepository.startFreeWorkout(day.focusTitle)
            } else {
                workoutRepository.startRoutine(routine)
            }
            StartDayOutcome.Open(session.id)
        }.getOrElse { thrown ->
            AppLog.w(TAG, "Starting ${day.focusTitle} failed", thrown)
            StartDayOutcome.Failed("Could not start that session. Try again.")
        }
    }
}
