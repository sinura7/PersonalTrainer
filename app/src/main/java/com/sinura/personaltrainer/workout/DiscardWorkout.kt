package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.timer.RestTimerGateway
import com.sinura.personaltrainer.util.runCatchingCancellable

private const val TAG = "PT/DiscardWorkout"

sealed interface DiscardOutcome {
    data object Discarded : DiscardOutcome

    data class Failed(val message: String) : DiscardOutcome
}

/**
 * Discarding a workout, wherever it is triggered from. The twin of [FinishWorkout], and the
 * only path in the app that deletes a session row.
 *
 * The rest timer stops first and unconditionally — before the delete, and even if the delete
 * then fails. A timer left running for a row that is about to disappear is the worse of the
 * two failures: its notification deep-links into a session that no longer exists.
 */
class DiscardWorkout(
    private val workoutRepository: WorkoutRepository,
    private val restTimer: RestTimerGateway,
    private val draftCache: WorkoutDraftCache,
) {
    suspend operator fun invoke(sessionId: String): DiscardOutcome {
        restTimer.stop()
        return runCatchingCancellable {
            workoutRepository.discardSession(sessionId)
            draftCache.clear(sessionId)
            DiscardOutcome.Discarded
        }.getOrElse { thrown ->
            AppLog.w(TAG, "Discarding the workout failed", thrown)
            DiscardOutcome.Failed("Could not discard this workout. Try again.")
        }
    }
}
