package com.sinura.personaltrainer.domain

/**
 * How a save or finish of completed training ended, whichever store wrote it.
 *
 * Composer, live cardio and the live-session bar used three different types for
 * the same three answers. [Accepted] is a durable row. [Rejected] is a rule
 * the write would have broken, so nothing was stored. [Failed] is a thrown
 * read or write: the row may already be there, and retry is the next move.
 */
sealed class CompleteTrainingOutcome {
    data class Accepted(val id: String) : CompleteTrainingOutcome()
    data class Rejected(val reason: String) : CompleteTrainingOutcome()
    data class Failed(val message: String) : CompleteTrainingOutcome()
}

fun ActivityWrite.toCompleteTrainingOutcome(): CompleteTrainingOutcome = when (this) {
    is ActivityWrite.Accepted -> CompleteTrainingOutcome.Accepted(id = session.id)
    is ActivityWrite.Rejected -> CompleteTrainingOutcome.Rejected(reason = reason)
}
