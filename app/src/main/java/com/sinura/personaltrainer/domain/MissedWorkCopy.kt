package com.sinura.personaltrainer.domain

/**
 * Gym-floor sentences for the one missed-work prompt (FND-017).
 * Recurrence never changes; the copy must not teach that word.
 */
object MissedWorkCopy {
    const val KEEP = "Keep the dates"
    const val MOVE = "Move them later this week"
    const val ADAPT = "Rebuild the rest of the week"
    const val SKIP = "Skip them"
    const val CAPTION = "Choose once. This week will not ask again."

    fun body(overdueCount: Int): String =
        if (overdueCount == 1) {
            "One planned session was not done. Next week still starts the same way."
        } else {
            "$overdueCount planned sessions were not done. Next week still starts the same way."
        }

    /** Keep-the-dates is the Volt. Recovery Replay/Suggest/Use stay quiet while this prompt is up. */
    fun suppressRecoveryVolt(missedWorkPrompt: Boolean): Boolean = missedWorkPrompt
}
