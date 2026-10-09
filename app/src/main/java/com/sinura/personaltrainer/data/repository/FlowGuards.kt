package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.DataHealthFold
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

private fun <T> DataHealth<T>.valueForHealthMap(): T = when (this) {
    is DataHealth.Available -> value
    is DataHealth.Degraded -> lastValue
    is DataHealth.Unavailable -> error("An unavailable read has no value")
}

/** An unread sibling must not hide a known failure. Successful nulls remain values. */
internal fun <A, B, R> combineHealth(
    first: Flow<DataHealth<A>>,
    second: Flow<DataHealth<B>>,
    transform: (A, B) -> R,
): Flow<DataHealth<R>> = combine(
    first.map<DataHealth<A>, DataHealth<A>?> { it }.onStart { emit(null) },
    second.map<DataHealth<B>, DataHealth<B>?> { it }.onStart { emit(null) },
) { a, b ->
    when {
        a is DataHealth.Unavailable -> a
        b is DataHealth.Unavailable -> b
        a is DataHealth.Degraded && b == null -> DataHealth.Unavailable(a.what)
        b is DataHealth.Degraded && a == null -> DataHealth.Unavailable(b.what)
        a == null || b == null -> null
        else -> {
            val value = transform(a.valueForHealthMap(), b.valueForHealthMap())
            val failed = (a as? DataHealth.Degraded<*>)?.what ?: (b as? DataHealth.Degraded<*>)?.what
            if (failed == null) DataHealth.Available(value) else DataHealth.Degraded(value, failed)
        }
    }
}.transform { health -> if (health != null) emit(health) }

/**
 * Publish required-read failure before joining a cancelled calculation. A slow cleanup
 * must not keep an old Available replay admitting writes, or later overwrite that failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T, R> Flow<DataHealth<T>>.mapHealthCatching(
    what: String,
    transform: suspend (T) -> R,
): Flow<DataHealth<R>> = channelFlow {
    // The wrapper distinguishes an unread value from a successful nullable R.
    var last: DataHealth.Available<R>? = null
    var revision = 0L
    val publication = Mutex()
    fun failed(reason: String): DataHealth<R> =
        last?.let { DataHealth.Degraded(it.value, reason) } ?: DataHealth.Unavailable(reason)
    this@mapHealthCatching.catch { thrown ->
        if (thrown is CancellationException) throw thrown
        AppLog.e(TAG, "Reading $what failed", thrown)
        emit(DataHealth.Unavailable(what))
    }.map { health ->
        publication.withLock {
            revision += 1L
            when (health) {
                is DataHealth.Unavailable -> send(failed(health.what))
                is DataHealth.Degraded -> send(failed(health.what))
                is DataHealth.Available -> Unit
            }
            revision to health
        }
    }.buffer(Channel.CONFLATED) // Observe faults while joining; keep only one pending calculation.
        .collectLatest { (expectedRevision, health) ->
        if (health is DataHealth.Unavailable) return@collectLatest
        try {
            val value = transform(health.valueForHealthMap())
            currentCoroutineContext().ensureActive()
            publication.withLock {
                if (revision == expectedRevision) {
                    last = DataHealth.Available(value)
                    send(
                        if (health is DataHealth.Degraded) DataHealth.Degraded(value, health.what)
                        else DataHealth.Available(value),
                    )
                }
            }
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (thrown: Throwable) {
            AppLog.e(TAG, "Reading $what failed", thrown)
            publication.withLock {
                if (revision == expectedRevision) send(failed(what))
            }
        }
    }
}

/**
 * Restart before sharing, so an attached sibling never prevents a retry. The request's
 * generation is allocated on collection and filters old replay even with a frozen clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SharedReadRecovery<T>(
    scope: CoroutineScope,
    graceMs: Long,
    what: String,
    private val read: () -> Flow<DataHealth<T>>,
) {
    private val generations = MutableStateFlow(0L)
    private val lock = Any()
    private data class Tagged<T>(val generation: Long, val health: DataHealth<T>)
    private val shared = generations.flatMapLatest { generation ->
        flow { emitAll(read()) }.mapHealthCatching(what) { it }
            .map { Tagged(generation, it) }
    }.shareIn(scope, SharingStarted.WhileSubscribed(graceMs), replay = 1)

    fun observe(): Flow<DataHealth<T>> = shared.map { it.health }

    fun retry(): Flow<DataHealth<T>> = flow {
        val requested = synchronized(lock) {
            (generations.value + 1L).also { generations.value = it }
        }
        emitAll(shared.filter { it.generation >= requested }.map { it.health })
    }
}
