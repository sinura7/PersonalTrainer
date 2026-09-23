package com.sinura.personaltrainer.domain

/**
 * The running rest's three controls, the same everywhere rest runs: the dock card, the rest
 * page and the lock-screen rest page (design audit D11, W1b). Each used to spell its own:
 * "−15" or "−15s", "Minus", "Subtract" or nothing for TalkBack, and Skip in the middle on
 * the lock screen. Left to right they are [MINUS], [PLUS], [SKIP]; the step is
 * [RestTimer.NUDGE_SECONDS]. The duration sheet's planned-length pair uses the first two.
 *
 * The shade notification's actions are the system's own buttons and keep their "−15s" /
 * "+15s" (`timer.RestTimerNotifications`), where no clock beside them says what 15 is.
 */
object RestNudgeCopy {
    val MINUS = "−${RestTimer.NUDGE_SECONDS}"
    val PLUS = "+${RestTimer.NUDGE_SECONDS}"
    const val SKIP = "Skip"
    val MINUS_SPOKEN = "Minus ${RestTimer.NUDGE_SECONDS} seconds"
    val PLUS_SPOKEN = "Plus ${RestTimer.NUDGE_SECONDS} seconds"
}
