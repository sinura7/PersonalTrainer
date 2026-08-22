package com.sinura.personaltrainer.domain

/**
 * Same shape as [RpeModifier]: a boolean in, a hint out, no I/O.
 *
 * A missed top set still drops. Everything else holds at last week's load so the in-workout
 * strip cannot tell them to climb during a week they marked lighter.
 */
object LighterWeekModifier {
    fun apply(hint: ProgressionHint, lighter: Boolean): ProgressionHint {
        if (!lighter) return hint
        if (hint.action == ProgressionAction.DECREASE) return hint
        return hint.copy(
            action = ProgressionAction.HOLD,
            suggestedWeightKg = hint.lastWeightKg,
            lighterHold = true,
        )
    }
}
