package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

private const val TAG = "PT/Repo"

/**
 * Degrades a database read to [fallback] instead of throwing into the collector.
 *
 * A Room query Flow that errors propagates into whichever ViewModel is collecting it, and
 * viewModelScope has no exception handler — so a single SQLite fault (a corrupt page, a
 * migration surprise, disk pressure) took the whole app down. The screen showing an empty
 * list while the cause is logged is strictly better than a crash, and the log is what makes
 * it diagnosable at all.
 */
internal fun <T> Flow<T>.orLogAndFallback(what: String, fallback: T): Flow<T> =
    catch { error ->
        AppLog.e(TAG, "Reading $what failed; degrading to a safe value", error)
        emit(fallback)
    }
