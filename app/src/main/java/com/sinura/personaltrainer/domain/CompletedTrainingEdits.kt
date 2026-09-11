package com.sinura.personaltrainer.domain

/**
 * What a finished piece of training may still change, decided per capability
 * rather than by which store holds the row (completed-training-convergence.md
 * §2 step 4).
 *
 * Notes and delete are cheap on both stores. Set repair on an activity block
 * needs an ADR: blocks are snapshots ([activity-contract.md] §8). Repeat-as-live
 * stays strength only until live activities carry strength.
 */
object CompletedTrainingEdits {
    fun canEditNotes(kind: CompletedTraining.Kind): Boolean = true

    fun canDelete(kind: CompletedTraining.Kind): Boolean = true

    fun canRepairSets(kind: CompletedTraining.Kind): Boolean =
        kind == CompletedTraining.Kind.STRENGTH_SESSION

    fun canRepeat(kind: CompletedTraining.Kind): Boolean =
        kind == CompletedTraining.Kind.STRENGTH_SESSION
}
