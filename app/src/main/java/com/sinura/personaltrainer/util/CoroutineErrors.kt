package com.sinura.personaltrainer.util

import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but never swallows cancellation.
 *
 * `catch (_: Exception)` was the universal pattern in this codebase, and
 * [CancellationException] extends Exception — so when `mapLatest` cancelled a transform
 * mid-suspend, the cancellation was eaten and the block carried on computing with fallback
 * values. That defeats the point of `mapLatest` and of structured concurrency generally.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    Result.failure(error)
}

/**
 * Runs [block], returning [fallback] and logging if it fails. The failure is recorded rather
 * than silently discarded, which is the whole point.
 */
inline fun <T> recoverWith(tag: String, what: String, fallback: T, block: () -> T): T =
    runCatchingCancellable(block).getOrElse { error ->
        AppLog.w(tag, "$what failed; using fallback", error)
        fallback
    }
