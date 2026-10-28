package com.sinura.personaltrainer.domain

/**
 * Temper redesign R1 (lane B): when effort enters the live draft and how history is read.
 *
 * D1: a working-set draft takes the last logged working-set effort for this lift (this session
 * first, else any prior session), exposed as [EffortSource.PREFILL_LAST] so lane A can render it
 * as suggested; it satisfies commit validation like a chosen chip.
 *
 * D2: with no such history there is no automatic effort; warm-ups and holds never receive effort.
 * Explicit coach Apply still copies [suggestedRpe] as [EffortSource.COACH].
 */
object EffortPrefillPolicy {
    fun effortForAutomaticSeed(
        isWarmup: Boolean,
        isHold: Boolean,
        exerciseId: String,
        sessionSets: List<SetLog>,
        priorWorkingSets: List<ExerciseSetRecord>,
    ): Int? {
        if (isWarmup || isHold) return null
        return lastWorkingSetEffort(
            exerciseId = exerciseId,
            sessionSets = sessionSets,
            priorWorkingSets = priorWorkingSets,
        )
    }

    /** Explicit coach Apply — copies a valid suggestion for the next working set. */
    fun effortWhenApplyingCoachRecommendation(
        isWarmup: Boolean,
        isHold: Boolean,
        suggestedRpe: Int?,
    ): Int? {
        if (isWarmup || isHold) return null
        return suggestedRpe?.takeIf { it in RpeCopy.VALUES }
    }

    internal fun lastWorkingSetEffort(
        exerciseId: String,
        sessionSets: List<SetLog>,
        priorWorkingSets: List<ExerciseSetRecord>,
    ): Int? {
        val fromSession = sessionSets.asSequence()
            .filter { it.exerciseId == exerciseId && !it.isWarmup }
            .mapNotNull { set -> set.rpe?.takeIf { it in RpeCopy.VALUES } }
            .lastOrNull()
        if (fromSession != null) return fromSession
        return priorWorkingSets.asSequence()
            .filter { it.rpe != null && it.rpe in RpeCopy.VALUES }
            .maxWithOrNull(exerciseSetOrder)
            ?.rpe
    }
}
