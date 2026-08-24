package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.DataHealthFold
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform

private const val TAG = "PT/Repo"

/**
 * Turns a Room or DataStore read into [DataHealth].
 *
 * A successful empty list stays [DataHealth.Available]. A thrown read becomes
 * [DataHealth.Degraded] when a value was already seen, otherwise
 * [DataHealth.Unavailable]. Cancellation is not a read fault.
 */
internal fun <T> Flow<T>.observeHealth(what: String): Flow<DataHealth<T>> = flow {
    var last: Any? = UNSET
    try {
        collect { value ->
            last = value
            emit(DataHealthFold.onValue(value))
        }
    } catch (thrown: CancellationException) {
        throw thrown
    } catch (thrown: Throwable) {
        AppLog.e(TAG, "Reading $what failed", thrown)
        val seen = last !== UNSET
        @Suppress("UNCHECKED_CAST")
        val held = if (seen) last as T else null
        emit(DataHealthFold.onFailure(held, what, seen))
    }
}

/** Values that actually arrived. Unavailable is dropped, never turned into empty. */
internal fun <T> Flow<DataHealth<T>>.presentValues(): Flow<T> = transform { health ->
    when (health) {
        is DataHealth.Available -> emit(health.value)
        is DataHealth.Degraded -> emit(health.lastValue)
        is DataHealth.Unavailable -> Unit
    }
}

private val UNSET = Any()
