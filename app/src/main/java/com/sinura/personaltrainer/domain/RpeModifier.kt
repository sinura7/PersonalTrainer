package com.sinura.personaltrainer.domain

/**
 * Stops the app telling you to add weight to a lift that is already grinding.
 *
 * RPE has been stored on every set and backed up faithfully since the app existed, and read by
 * absolutely nothing — the progression suggestion looked only at whether you hit your target
 * reps. Hitting five reps at RPE 9 and hitting five reps at RPE 6 are the same event to that
 * rule, and only one of them means "ready for more".
 *
 * Deliberately narrow. It fires only on an INCREASE suggestion (holding a hold changes
 * nothing, and softening a DECREASE would be the app arguing with itself), only when BOTH of
 * the last two sessions recorded an RPE, and only at 9 or above averaged. One hard session is
 * a hard session; two in a row at the edge is a pattern. A missing RPE is not evidence of an
 * easy set, so an absent value blocks the rule rather than being treated as low.
 */
object RpeModifier {
    const val RPE_HOLD_THRESHOLD = 9.0
    const val RPE_HOLD_SESSIONS = 2

    /**
     * @param recentTopSetRpes the top set's RPE for each of the last [RPE_HOLD_SESSIONS]
     * finished sessions containing this lift, most recent first. Nulls are "not recorded".
     */
    fun apply(hint: ProgressionHint, recentTopSetRpes: List<Int?>): ProgressionHint {
        if (hint.action != ProgressionAction.INCREASE) return hint
        val considered = recentTopSetRpes.take(RPE_HOLD_SESSIONS)
        if (considered.size < RPE_HOLD_SESSIONS) return hint
        if (considered.any { it == null }) return hint
        val average = considered.filterNotNull().average()
        if (average < RPE_HOLD_THRESHOLD) return hint
        return hint.copy(
            action = ProgressionAction.HOLD,
            suggestedWeightKg = hint.lastWeightKg,
            rpeHold = true,
        )
    }
}
