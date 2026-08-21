package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.StartDayDecision
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.decideStart
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable

private const val TAG = "PT/StartTrainingDay"

sealed interface StartDayOutcome {
    /** Open this session. */
    data class Open(val sessionId: String) : StartDayOutcome

    /** A rest day: nothing to start, and nowhere to navigate. */
    data object Ignored : StartDayOutcome

    /**
     * A workout is already running. The caller must ask; it must never silently hand back the
     * session that happens to be open, because that is not what the user tapped.
     */
    data class Blocked(val inProgressSessionId: String) : StartDayOutcome

    data class Failed(val message: String) : StartDayOutcome
}

/**
 * Turns a planned day into an open session.
 *
 * Home and Schedule held byte-identical copies of this, down to the fallback chain
 * (routine missing or empty → free workout named after the focus). Two copies of a rule about
 * what happens to the user's training day is one copy too many.
 *
 * The rules themselves live in [decideStart], where they can be tested without a database.
 * This class does the I/O either side: resolve the routine and the live session, then act.
 */
class StartTrainingDay(
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
) {
    suspend operator fun invoke(day: SuggestedTrainingDay): StartDayOutcome {
        if (day.isRest) return StartDayOutcome.Ignored

        return runCatchingCancellable {
            val inProgress = workoutRepository.getInProgress()
            val routine = day.routineId?.let { routineRepository.getById(it) }
            when (val decision = decideStart(day, routine, inProgress)) {
                StartDayDecision.Rest -> StartDayOutcome.Ignored
                is StartDayDecision.Blocked -> StartDayOutcome.Blocked(decision.inProgressSessionId)
                is StartDayDecision.RoutineGone -> StartDayOutcome.Failed(decision.message)
                // startRoutine/startFreeWorkout resolve the start race inside a transaction, so
                // the read above is a shortcut for the message, not the guard.
                is StartDayDecision.StartRoutine ->
                    StartDayOutcome.Open(workoutRepository.startRoutine(decision.routine).id)
                is StartDayDecision.StartFree ->
                    StartDayOutcome.Open(workoutRepository.startFreeWorkout(decision.focusTitle).id)
            }
        }.getOrElse { thrown ->
            AppLog.w(TAG, "Starting ${day.focusTitle} failed", thrown)
            StartDayOutcome.Failed("Could not start that session. Try again.")
        }
    }
}
