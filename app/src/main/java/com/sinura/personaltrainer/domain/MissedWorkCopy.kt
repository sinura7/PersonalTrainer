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
    const val OTHER = "Other choices"
    const val CAPTION = "Choose once. This week will not ask again."

    fun body(overdueCount: Int): String =
        if (overdueCount == 1) {
            "One planned session was not done. Next week still starts the same way."
        } else {
            "$overdueCount planned sessions were not done. Next week still starts the same way."
        }

    fun actions(otherChoicesOpen: Boolean): List<String> = buildList {
        add(KEEP)
        add(OTHER)
        if (otherChoicesOpen) {
            add(MOVE)
            add(ADAPT)
            add(SKIP)
        }
    }

    /** Three stacked 56 dp secondaries plus the gaps between them. */
    fun collapsedSavingsDp(): Int = 3 * 56 + 2 * 8

    fun actionsHeightDp(otherChoicesOpen: Boolean): Int {
        val n = actions(otherChoicesOpen).size
        return n * 56 + (n - 1) * 8
    }

    /**
     * 360×640 with the tab bar. The old four-button stack filled that
     * window with the rest of Home; collapsing Move/Adapt/Skip pulls
     * today's Start row back into it.
     */
    fun firstStartFitsShortPhone(otherChoicesOpen: Boolean): Boolean {
        val usable = 640 - 56
        val fourButtonStack = 4 * 56 + 3 * 8
        val restOfHome = usable - fourButtonStack
        val startRow = 56
        return restOfHome + actionsHeightDp(otherChoicesOpen) + startRow <= usable
    }

    /** Keep-the-dates is the Volt. Recovery Replay/Suggest/Use stay quiet while this prompt is up. */
    fun suppressRecoveryVolt(missedWorkPrompt: Boolean): Boolean = missedWorkPrompt
}
