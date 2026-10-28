package com.sinura.personaltrainer.domain

/**
 * Temper redesign R1 (lane B): when coach-suggested effort may enter the live draft.
 *
 * D1 (recommended default): a working-set draft takes the coach's [suggestedRpe] only when
 * the lifter explicitly accepts the coach load (Apply on the Next card, Tempo *Use*, or an
 * extra-set seed). The hint under the track stays separate (UX blueprint R5).
 *
 * D2 (recommended default): warm-ups and holds never receive coach effort in the draft; automatic
 * prefill seeds weight and reps only, never effort.
 */
object EffortPrefillPolicy {
    /** Automatic prefill / resume seed — never selects an effort chip. */
    fun effortForAutomaticSeed(isWarmup: Boolean, isHold: Boolean): Int? {
        if (isWarmup || isHold) return null
        return null
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
}
