package com.sinura.personaltrainer.domain

/**
 * How the routine editor and the week workshop nudge a lift's targets.
 *
 * The workout's weight and rep plates already live on [WeightConverter] and [NumericEntry].
 * Sets and rest did not, because they were typed into tiny boxes. The same plates now sit on
 * those two fields, so the step has to be one function the UI and the tests both call —
 * otherwise the well would move by 1 while a test asserted 15, or the other way around.
 *
 * Rest steps by [REST_STEP_SECONDS], the same 15 s floor the live timer will honour. A
 * prescription of 10 s is still legal (storage never forbade it); the minus plate just
 * lands on 0 rather than inventing 15. Weight keeps using [WeightConverter.incrementKg].
 */
object TargetStepper {
    const val REST_STEP_SECONDS: Int = RestTimerPreferences.MIN_SECONDS

    fun nextSets(current: Int, direction: Int): Int =
        (current + direction).coerceAtLeast(1)

    fun nextReps(current: Int, direction: Int): Int =
        (current + direction).coerceAtLeast(1)

    fun nextRestSeconds(current: Int, direction: Int): Int =
        (current + direction * REST_STEP_SECONDS).coerceAtLeast(0)

    /**
     * The kilograms to stage from a well. Zero is "no target", the same answer a cleared
     * box has always given ([TargetEntry.typedWeightKg]).
     */
    fun weightToStage(kg: Double): Double? = kg.takeIf { it > 0.0 }
}
