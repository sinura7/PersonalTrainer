package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.timer.RestTimerController
import com.sinura.personaltrainer.util.runCatchingCancellable

private const val TAG = "PT/FinishWorkout"

sealed interface FinishOutcome {
    data class Finished(val sessionId: String) : FinishOutcome

    /** A session with no sets can never be finished — discard is its only exit. */
    data object NothingLogged : FinishOutcome

    data object SessionMissing : FinishOutcome

    data class Failed(val message: String) : FinishOutcome
}

/**
 * Finishing a workout, wherever it is triggered from.
 *
 * Finishing is not one repository call: it stops the rest timer, writes `finishedAt`, and
 * clears the in-memory draft. Those three lived inside [com.sinura.personaltrainer.ui.workout.ActiveWorkoutViewModel],
 * so any *other* surface that finished a session — the live-session bar, a stale-session
 * nudge — would have left a rest notification counting down for a workout that no longer
 * exists, and a draft pointing at it. Every finish in the app goes through here.
 *
 * Invariants, in order:
 * - a session with zero sets is [FinishOutcome.NothingLogged] and is never written;
 * - the rest timer stops before the write, so no alarm can fire for a finished session;
 * - the draft is cleared after the write;
 * - already-finished is [FinishOutcome.Finished], not an error — the call is idempotent.
 */
class FinishWorkout(
    private val workoutRepository: WorkoutRepository,
    private val restTimer: RestTimerController,
    private val draftCache: WorkoutDraftCache,
) {
    /**
     * @param notes `null` keeps whatever the session already has. The bar passes null: it has
     *   no notes field, and passing `""` would blank the user's notes, because
     *   [WorkoutRepository.finishSession] writes the value it is given.
     */
    suspend operator fun invoke(sessionId: String, notes: String? = null): FinishOutcome {
        // The full session, not the in-progress summary: the summary carries no sets, and the
        // zero-set guard is the whole point of reading it.
        val session = runCatchingCancellable { workoutRepository.getSession(sessionId) }
            .getOrElse { thrown ->
                AppLog.w(TAG, "Reading the session failed", thrown)
                return FinishOutcome.Failed("Could not finish this workout. Try again.")
            } ?: return FinishOutcome.SessionMissing

        if (session.isFinished) return FinishOutcome.Finished(sessionId)
        if (session.sets.isEmpty()) return FinishOutcome.NothingLogged

        return runCatchingCancellable {
            restTimer.stop()
            workoutRepository.finishSession(sessionId, notes ?: session.notes)
            draftCache.clear(sessionId)
            FinishOutcome.Finished(sessionId)
        }.getOrElse { thrown ->
            AppLog.w(TAG, "Finishing the workout failed", thrown)
            FinishOutcome.Failed("Could not finish this workout. Try again.")
        }
    }
}
