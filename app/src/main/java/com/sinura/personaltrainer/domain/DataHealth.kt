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

    const val SETTINGS_TITLE = "Settings unavailable"
    const val SETTINGS_BODY =
        "Could not read whether setup is finished. Retry. This is not a first install."

    const val START_UNAVAILABLE =
        "Could not read whether a workout is already live. Start refused."

    const val RESTORE_UNAVAILABLE =
        "Could not read whether a workout is live. Restore refused."
}
