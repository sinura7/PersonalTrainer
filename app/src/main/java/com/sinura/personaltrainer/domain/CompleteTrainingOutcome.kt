package com.sinura.personaltrainer.domain

/**
 * How a save or finish of completed training ended, whichever store wrote it.
 *
 * Composer, live cardio and the live-session bar used three different types for
 * the same three answers. [Written] is a durable row (the plan's Accepted).
 * [RuledOut] is a rule the write would have broken, so nothing was stored
 * (Rejected). [Failed] is a thrown read or write: the row may already be
 * there, and retry is the next move.
 */
sealed class CompleteTrainingOutcome {
    data class Written(val id: String) : CompleteTrainingOutcome()
    data class RuledOut(val reason: String) : CompleteTrainingOutcome()
    data class Failed(val message: String) : CompleteTrainingOutcome()
}

fun ActivityWrite.toCompleteTrainingOutcome(): CompleteTrainingOutcome = when (this) {
    is ActivityWrite.Accepted -> CompleteTrainingOutcome.Written(id = session.id)
    is ActivityWrite.Rejected -> CompleteTrainingOutcome.RuledOut(reason = reason)
}
