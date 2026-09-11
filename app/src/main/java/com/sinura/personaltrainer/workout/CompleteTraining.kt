package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.activity.ConfirmActivity
import com.sinura.personaltrainer.activity.FinishActivity
import com.sinura.personaltrainer.domain.ActivityBlock
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CompleteTrainingOutcome
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.domain.toCompleteTrainingOutcome
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.timer.CardioTimerPersistence
import com.sinura.personaltrainer.util.runCatchingCancellable

private const val TAG = "PT/CompleteTraining"

/**
 * Save or finish completed training, whichever store writes it.
 *
 * [FinishWorkout], [ConfirmActivity] and [FinishActivity] stay the real
 * writes — reminder cleanup still runs inside the activity repository after
 * the commit. This façade maps every path onto [CompleteTrainingOutcome] so
 * the composer, live cardio and the live-session bar report the same three
 * answers. A thrown finish is [CompleteTrainingOutcome.Failed], never an
 * uncaught coroutine.
 */
class CompleteTraining(
    private val strengthFinish: FinishWorkout,
    private val activityConfirm: ConfirmActivity,
    private val activityFinish: FinishActivity,
    private val cardioTimerPersistence: CardioTimerPersistence,
) {
    suspend fun finishWorkout(
        sessionId: String,
        notes: String? = null,
    ): CompleteTrainingOutcome = when (val outcome = strengthFinish(sessionId, notes)) {
        is FinishOutcome.Finished -> CompleteTrainingOutcome.Accepted(id = outcome.sessionId)
        FinishOutcome.NothingLogged ->
            CompleteTrainingOutcome.Rejected(reason = NOTHING_LOGGED)
        FinishOutcome.SessionMissing ->
            CompleteTrainingOutcome.Rejected(reason = DataHealthCopy.FINISH_NOT_FOUND)
        is FinishOutcome.Failed ->
            CompleteTrainingOutcome.Failed(message = DataHealthCopy.FINISH_FAILED)
    }

    suspend fun confirm(
        draft: ActivityDraft,
        now: CapturedCivilTime,
    ): CompleteTrainingOutcome = runCatchingCancellable {
        activityConfirm(draft, now).toCompleteTrainingOutcome()
    }.getOrElse { thrown ->
        AppLog.w(TAG, "Confirming an activity threw", thrown)
        CompleteTrainingOutcome.Failed(message = CONFIRM_FAILED)
    }

    suspend fun finishLiveActivity(
        sessionId: String,
        now: CapturedCivilTime,
        blocks: List<ActivityBlock>? = null,
    ): CompleteTrainingOutcome = runCatchingCancellable {
        when (val write = activityFinish(sessionId, now, blocks)) {
            is ActivityWrite.Accepted -> {
                cardioTimerPersistence.clear()
                CompleteTrainingOutcome.Accepted(id = write.session.id)
            }
            is ActivityWrite.Rejected ->
                CompleteTrainingOutcome.Rejected(reason = write.reason)
        }
    }.getOrElse { thrown ->
        AppLog.w(TAG, "Finishing live cardio threw", thrown)
        CompleteTrainingOutcome.Failed(message = LIVE_FINISH_FAILED)
    }

    companion object {
        const val NOTHING_LOGGED = "Log at least one set before finishing."
        const val CONFIRM_FAILED = "Could not save that session. Try again."
        const val LIVE_FINISH_FAILED = "Could not finish that session. Try again."
    }
}
