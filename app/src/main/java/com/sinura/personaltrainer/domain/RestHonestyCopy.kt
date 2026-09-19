package com.sinura.personaltrainer.domain

/**
 * Packet E: one compact honesty row above the dock clock, never covering Log.
 *
 * Priority is persistence, then notification recovery, then first-rest
 * disclosure. Exact-alarm copy lives on the rest page, never as "precise".
 */
object RestHonestyCopy {
    const val PERSISTENCE = "Rest may not survive leaving the app"
    const val EXACT_DENIED = "May be late when the phone sleeps"
    const val FIRST_REST =
        "Unrestricted battery or the clock dies. Alarm volume — Settings is the off switch."

    enum class Kind {
        PERSISTENCE,
        NOTIFICATION,
        FIRST_REST,
        EXACT,
    }

    data class Honesty(
        val kind: Kind,
        val sentence: String,
        val action: String? = null,
    )

    fun pick(
        persistenceHealthy: Boolean,
        restRunning: Boolean,
        notificationsEnabled: Boolean,
        batteryHint: Boolean,
        exactBestEffort: Boolean,
        onRestPage: Boolean,
    ): Honesty? {
        if (restRunning && !persistenceHealthy) {
            return Honesty(Kind.PERSISTENCE, PERSISTENCE)
        }
        if (!notificationsEnabled) {
            return Honesty(
                kind = Kind.NOTIFICATION,
                sentence = RestNotificationCopy.RECOVERY_TITLE,
                action = RestNotificationCopy.RECOVERY_ACTION,
            )
        }
        if (restRunning && batteryHint) {
            return Honesty(
                kind = Kind.FIRST_REST,
                sentence = FIRST_REST,
                action = RestBatteryCopy.GOT_IT,
            )
        }
        if (onRestPage && exactBestEffort) {
            return Honesty(Kind.EXACT, EXACT_DENIED)
        }
        return null
    }
}
