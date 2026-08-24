package com.sinura.personaltrainer.domain

/**
 * The one sentence before Android's POST_NOTIFICATIONS dialog.
 *
 * The system dialog has no gym why. After a denial the workout banner already
 * deep-links to settings — that is recovery, not this sentence.
 */
object RestNotificationCopy {
    const val TITLE = "Rest alerts"

    const val SENTENCE =
        "Rest stays visible in the shade when the phone is in your pocket."

    const val CONTINUE = "Continue"

    const val NOT_NOW = "Not now"

    /** Persistent recovery after the one explanation. One line, not a banner. */
    const val RECOVERY_TITLE = "Rest alerts off"

    const val RECOVERY_ACTION = "Turn on"
}
