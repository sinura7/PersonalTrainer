package com.sinura.personaltrainer.domain.coach

import com.sinura.personaltrainer.domain.SetMicroRecCalculator

/**
 * Maps locked micro-rec reason codes to literature / heuristic evidence ids.
 */
object CoachPolicyEvidence {
    fun evidenceIdsForReason(reasonCode: String): List<String> = when (reasonCode) {
        SetMicroRecCalculator.IN_TANK -> listOf(
            "helms-2016-rpe-application",
            "zourdos-2016-rpe-rir",
            "heuristic-plate-increment",
        )
        SetMicroRecCalculator.RPE_HOLD,
        SetMicroRecCalculator.TOP_SET,
        SetMicroRecCalculator.CLOSE_HOLD,
        SetMicroRecCalculator.QUALITY,
        SetMicroRecCalculator.SKIP_RPE_HOLD,
        -> listOf(
            "hackett-2017-rtf-accuracy",
            "helms-2016-rpe-application",
            "zourdos-2016-rpe-rir",
        )
        SetMicroRecCalculator.FAILED_DROP,
        SetMicroRecCalculator.SKIP_RPE_DROP,
        SetMicroRecCalculator.BW_DROP_REP,
        -> listOf(
            "helms-2016-rpe-application",
            "hackett-2017-rtf-accuracy",
        )
        SetMicroRecCalculator.CLIMB_REPS,
        SetMicroRecCalculator.BW_ADD_REP,
        -> listOf(
            "schoenfeld-2017-volume",
            "helms-2016-rpe-application",
        )
        SetMicroRecCalculator.FIRST_SET,
        SetMicroRecCalculator.WARMUP_DONE,
        -> listOf(
            "heuristic-conservative-first-set",
            "jeffreys-2007-ramp",
            "heuristic-warmup-percent-ladder",
        )
        SetMicroRecCalculator.NO_HISTORY -> listOf("heuristic-conservative-first-set")
        SetMicroRecCalculator.LIGHTER_HOLD -> listOf("helms-2016-rpe-application")
        SetMicroRecCalculator.BW_HOLD -> listOf("helms-2016-rpe-application")
        else -> emptyList()
    }
}
