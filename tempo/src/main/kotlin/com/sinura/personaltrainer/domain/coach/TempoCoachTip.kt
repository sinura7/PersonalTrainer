package com.sinura.personaltrainer.domain.coach

import com.sinura.personaltrainer.domain.RuleTrace
import com.sinura.personaltrainer.domain.SetMicroRec

/** One Tempo popup at a time on the live workout floor. */
sealed class TempoCoachTip {
    abstract val tipShort: String
    abstract val evidenceIds: List<String>

    data class NextSet(
        val rec: SetMicroRec,
        val suggestion: CoachSuggestion,
    ) : TempoCoachTip() {
        override val tipShort: String = suggestion.explanationShort
        override val evidenceIds: List<String> = suggestion.evidenceIds
    }

    data class AddASet(
        val offer: AddASetPolicy.Offer,
        val seedRec: SetMicroRec,
        val trace: RuleTrace,
    ) : TempoCoachTip() {
        override val tipShort: String = offer.tipShort
        override val evidenceIds: List<String> = offer.evidenceIds
    }
}
