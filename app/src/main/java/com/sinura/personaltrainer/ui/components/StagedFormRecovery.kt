package com.sinura.personaltrainer.ui.components

import com.sinura.personaltrainer.data.draft.DraftCodec
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.DraftCommandPhase
import com.sinura.personaltrainer.data.draft.StagedFormHandle
import com.sinura.personaltrainer.data.draft.StagedPickerForms
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class StagedFormStatus(val loading: Boolean = true, val keeping: Boolean = false,
    val resumed: Boolean = false, val error: String? = null, val pending: Boolean = false,
    val accepted: Boolean = false) {
    val ready: Boolean get() = !loading && !keeping && error == null && !pending
}

/** Raw fields stay in the caller. Preservation and picker handoff await the same ordered IO. */
internal class StagedFormRecovery<T>(
    private val scope: CoroutineScope, private val drafts: StagedPickerForms,
    private val owner: suspend () -> DraftOwner, private val codec: DraftCodec<T, Unit>,
    private val snapshot: () -> T, private val restore: (T) -> Unit,
    private val available: () -> Boolean,
) {
    private val serial = Mutex()
    private var handle: StagedFormHandle<T>? = null
    private var revision = 0L
    private var authoredSinceFailedRead = false
    private var failedReadConflict = false
    private val mutableStatus: MutableStateFlow<StagedFormStatus> = MutableStateFlow(StagedFormStatus())
    val status: StateFlow<StagedFormStatus> = mutableStatus
    val opening: Job = scope.launch { open() }

    private suspend fun open() = serial.withLock {
        ++revision
        try {
            check(available())
            val opened = drafts.open(owner(), codec)
            handle = opened
            val checkpoint = checkNotNull(opened.checkpoint)
            check(!checkpoint.needsReconciliation)
            val resumed = opened.fields != null
            if (authoredSinceFailedRead && resumed) {
                failedReadConflict = true
                mutableStatus.value = StagedFormStatus(loading = false,
                    error = "A kept draft is available. Your newer fields remain here. Discard this private draft to leave safely.")
                return@withLock
            }
            opened.fields?.let(restore)
            checkpoint.pending?.let {
                mutableStatus.value = StagedFormStatus(loading = false, resumed = resumed, pending = true,
                    accepted = it.phase == DraftCommandPhase.ACCEPTED,
                    error = if (it.phase == DraftCommandPhase.ACCEPTED) null else "The earlier submission needs checking. It will not be repeated. Saved records remain in History or Plan.")
                return@withLock
            }
            drafts.keep(opened, snapshot())
            mutableStatus.value = StagedFormStatus(loading = false, resumed = resumed)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { fail() }
    }

    fun changed() {
        if (status.value.loading) return
        if (handle?.checkpoint == null) authoredSinceFailedRead = true
        if (status.value.pending || handle?.checkpoint == null || failedReadConflict) return
        val admitted = ++revision
        val fields = snapshot()
        scope.launch {
            delay(150L)
            serial.withLock {
                if (admitted != revision) return@withLock
                try { check(available()); drafts.keep(checkNotNull(handle), fields) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { fail() }
            }
        }
    }

    suspend fun isCurrent(): Boolean {
        val current = handle ?: return false
        return current.checkpoint != null && !status.value.loading && !status.value.keeping && !status.value.pending && available() && drafts.current(current)
    }

    suspend fun flush(): Boolean {
        opening.join()
        return serial.withLock {
            ++revision
            if (status.value.pending || failedReadConflict) return@withLock false
            mutableStatus.value = status.value.copy(keeping = true)
            try {
                check(available()); drafts.keep(checkNotNull(handle), snapshot())
                mutableStatus.value = status.value.copy(keeping = false, error = null)
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(); false }
        }
    }
    fun leave(onKept: () -> Unit) { scope.launch { if (status.value.pending || flush()) onKept() } }
    fun retry() { scope.launch { if (handle?.checkpoint == null) open() else flush() } }
    suspend fun beginSubmission(): Boolean {
        if (!flush()) return false
        return serial.withLock {
            try {
                mutableStatus.value = status.value.copy(pending = true)
                check(available()); drafts.submit(checkNotNull(handle), snapshot())
                mutableStatus.value = status.value.copy(pending = true)
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                try {
                    drafts.refresh(checkNotNull(handle))
                    mutableStatus.value = status.value.copy(pending = handle?.checkpoint?.pending != null)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableStatus.value = status.value.copy(pending = true) }
                fail(); false
            }
        }
    }
    suspend fun accepted() = serial.withLock {
        try {
            check(available()); drafts.accept(checkNotNull(handle))
            mutableStatus.value = status.value.copy(pending = true, accepted = true, error = null)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableStatus.value = status.value.copy(pending = true,
            error = "Saved. Private confirmation is still pending; the submission will not be repeated.") }
    }
    suspend fun discard(): Boolean {
        opening.join()
        return serial.withLock {
            ++revision
            try {
                check(available())
                drafts.reset(checkNotNull(handle))
                handle = null
                mutableStatus.value = StagedFormStatus(loading = false)
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(); false }
        }
    }
    private fun fail() { mutableStatus.value = status.value.copy(loading = false, keeping = false,
        error = "Draft was not kept. Your fields remain here. Retry before leaving.") }
}
