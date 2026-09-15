package com.sinura.personaltrainer.domain

/**
 * Log-set feedback after the tap (Packet A). The press may tick; commit
 * and reject wait on validation and the durable write.
 */
object LogCommitCopy {
    const val WRITE_FAILED = "Could not save. Your set is still here. Try again."
    const val SUGGESTION_UNAVAILABLE = "Suggestion unavailable"
    const val WAITING_FOR_LIFT = "Waiting for this lift's numbers."
    const val LOGGING_WAIT = "Logging. Wait until this set is saved."

    fun disabledReason(logging: Boolean, liftReady: Boolean): String? = when {
        logging -> LOGGING_WAIT
        !liftReady -> WAITING_FOR_LIFT
        else -> null
    }
}

/** One-shot haptic after a Log tap: success is durable, reject is not. */
enum class LogCommitFeedback {
    SUCCESS,
    REJECT,
}
