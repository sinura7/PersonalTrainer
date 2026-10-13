package com.sinura.personaltrainer.ui.home

import com.sinura.personaltrainer.data.draft.HomeDayDrafts
import com.sinura.personaltrainer.data.draft.HomeDayHandle
import com.sinura.personaltrainer.data.draft.RememberedHomeDay
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class HomeDayMemoryState(
    val loaded: Boolean = false,
    val restored: RememberedHomeDay? = null,
    val saving: Boolean = false,
    val resetting: Boolean = false,
    val error: String? = null,
    val needsReset: Boolean = false,
    val resetSerial: Long = 0L,
)

/** Main-owned admission and one IO consumer; late outcomes never replace a newer choice. */
internal class HomeDayMemory(private val store: HomeDayDrafts?, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(HomeDayMemoryState(loaded = store == null))
    val snapshot = mutable.asStateFlow()
    private val requests = Channel<Request>(Channel.UNLIMITED)
    private var latest: RememberedHomeDay? = null
    private var handle: HomeDayHandle? = null
    private var retryReset = false
    private var resetSequence = 0L

    init {
        scope.launch {
            handle = store?.open()
            mutable.value = mutable.value.copy(
                loaded = true, restored = handle?.remembered,
                error = if (handle?.readable == false) READ_ERROR else null,
                needsReset = handle?.readable == false,
            )
            for (request in requests) {
                if (request.reset) {
                    val owner = handle
                    val outcome = runCatchingCancellable {
                        if (store != null) store.reset(checkNotNull(owner))
                    }
                    retryReset = outcome.isFailure
                    mutable.value = mutable.value.copy(
                        saving = false, resetting = false,
                        restored = if (outcome.isSuccess) null else mutable.value.restored,
                        error = if (outcome.isSuccess) null else RESET_ERROR,
                        needsReset = outcome.isFailure,
                        resetSerial = if (outcome.isSuccess) ++resetSequence else mutable.value.resetSerial,
                    )
                    continue
                }
                if (request.retry) {
                    val recovered = runCatchingCancellable {
                        if (store != null) store.reload(checkNotNull(handle))
                    }
                    if (recovered.isFailure) {
                        mutable.value = mutable.value.copy(saving = false, error = READ_ERROR, needsReset = true)
                        continue
                    }
                    mutable.value = mutable.value.copy(restored = handle?.remembered, error = null, needsReset = false)
                }
                val value = if (request.retry) latest else request.value
                if (value == null || value != latest) {
                    if (value == null) mutable.value = mutable.value.copy(saving = false)
                    continue
                }
                val kept = runCatchingCancellable {
                    if (store != null) store.keep(checkNotNull(handle), value)
                }
                if (value == latest) {
                    mutable.value = mutable.value.copy(
                        saving = false,
                        error = if (kept.isSuccess) null else KEEP_ERROR,
                        needsReset = handle?.readable == false,
                    )
                }
            }
        }
    }

    fun choose(value: RememberedHomeDay): Boolean {
        if (scope.coroutineContext[Job]?.isActive == false || mutable.value.resetting) return false
        retryReset = false
        latest = value
        mutable.value = mutable.value.copy(saving = true, resetSerial = 0L)
        return requests.trySend(Request(value = value)).isSuccess
    }

    fun retry() {
        if (mutable.value.saving) return
        mutable.value = mutable.value.copy(saving = true, resetting = retryReset)
        requests.trySend(Request(retry = !retryReset, reset = retryReset))
    }

    fun reset() {
        if (mutable.value.saving) return
        retryReset = true
        latest = null
        mutable.value = mutable.value.copy(saving = true, resetting = true)
        requests.trySend(Request(reset = true))
    }

    /** Consume only the current reset cue; a newer explicit day supersedes it. */
    fun consumeReset(serial: Long): Boolean {
        if (serial == 0L || mutable.value.resetSerial != serial) return false
        mutable.value = mutable.value.copy(resetSerial = 0L)
        return true
    }

    private data class Request(val value: RememberedHomeDay? = null, val retry: Boolean = false, val reset: Boolean = false)

    private companion object {
        const val READ_ERROR = "Your remembered day could not be read. Home is still available."
        const val KEEP_ERROR = "Your selected day could not be kept for reopening."
        const val RESET_ERROR = "The remembered day could not be reset. Try again."
    }
}
