package com.sinura.personaltrainer.domain

/**
 * A durable read that may fail after zero, one, or many successful values.
 *
 * Empty is a successful value. Unavailable is not. Primary screens must not
 * render [Unavailable] as "nothing here yet".
 */
sealed class DataHealth<out T> {
    data class Available<T>(val value: T) : DataHealth<T>()

    /** Last successful value, held because a later read failed. */
    data class Degraded<T>(val lastValue: T, val what: String) : DataHealth<T>()

    data class Unavailable(val what: String) : DataHealth<Nothing>()

    fun presentValue(): T? = when (this) {
        is Available -> value
        is Degraded -> lastValue
        is Unavailable -> null
    }

    val isUnavailable: Boolean get() = this is Unavailable
}

/** Pure fold so Flow adapters and tests share one rule. */
object DataHealthFold {
    fun <T> onValue(value: T): DataHealth.Available<T> = DataHealth.Available(value)

    /**
     * @param seen true when a value already arrived, including a successful null.
     * A null [last] without [seen] is unread, not empty.
     */
    fun <T> onFailure(last: T?, what: String, seen: Boolean = last != null): DataHealth<T> =
        if (seen) {
            @Suppress("UNCHECKED_CAST")
            DataHealth.Degraded(last as T, what)
        } else {
            DataHealth.Unavailable(what)
        }
}

object DataHealthCopy {
    const val HISTORY_TITLE = "History unavailable"
    const val HISTORY_BODY =
        "Sessions could not be read. Retry, or export a backup from Settings. " +
            "Do not start a new workout until this list is readable."
    const val RETRY = "Retry"

    const val ROUTINE_EDITOR_TITLE = "Routine unavailable"
    const val ROUTINE_EDITOR_BODY =
        "This routine could not be read. Retry, or go back to the list and open it again."

    const val SETTINGS_TITLE = "Settings unavailable"
    const val SETTINGS_BODY =
        "Could not read whether setup is finished. Retry. This is not a first install."

    const val START_UNAVAILABLE =
        "Could not read whether a workout is already live. Start refused."

    const val RESTORE_UNAVAILABLE =
        "Could not read whether a workout is live. Restore refused."

    /** A single activity's read threw. Not the same thing as a row that is not there. */
    const val ACTIVITY_TITLE = "Session unavailable"
    const val ACTIVITY_BODY =
        "That activity could not be read. Nothing was changed. Retry, or go back."

    /** A finished strength session's read threw. Same distinction as [ACTIVITY_TITLE]. */
    const val SESSION_TITLE = "Session unavailable"
    const val SESSION_BODY =
        "That session could not be read. Nothing was changed. Retry, or go back."

    /** The live cardio row's read threw. The session is neither finished nor discarded. */
    const val LIVE_CARDIO_TITLE = "Live cardio unavailable"
    const val LIVE_CARDIO_BODY =
        "That live session could not be read. It has not been finished or discarded. Retry."

    /**
     * Finish found no live row: a successful read with nothing to finish. Not a retry case,
     * and not the same sentence as a write that failed (UX23).
     */
    const val FINISH_NOT_FOUND =
        "Workout not found. It is no longer on this phone, so there is nothing to finish."

    /** The finish write itself failed or the row could not be read. The logged sets are untouched. */
    const val FINISH_FAILED =
        "Could not finish this workout. Your logged sets are still there. Try again."
}
