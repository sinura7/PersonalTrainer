package com.sinura.personaltrainer.ui.components

import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.data.draft.CompletedNoteOwner
import com.sinura.personaltrainer.data.draft.CompletedNotesDrafts
import com.sinura.personaltrainer.data.draft.CompletedNotesHandle
import com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class CompletedNotesFormState(
    val loaded: Boolean = false,
    val ready: Boolean = false,
    val resumed: Boolean = false,
    val keeping: Boolean = false,
    val kept: Boolean = false,
    val error: String? = null,
    val discarding: Boolean = false,
)

/** Main-owned ordered requests; durable checkpoints complement this entry's framework state. */
internal class CompletedNotesForm(
    private val store: CompletedNotesDrafts?,
    private val owner: CompletedNoteOwner,
    private val saved: SavedStateHandle,
    private val scope: CoroutineScope,
    private val restoreText: (String, Boolean) -> Unit,
    private val resetText: (String?) -> Unit,
    private val onRecoveredSaved: () -> Unit = {},
    private val onRecoveredDraft: () -> Unit = {},
) {
    private val mutable = MutableStateFlow(CompletedNotesFormState(loaded = store == null, ready = store == null))
    val state = mutable.asStateFlow()
    private val requests = Channel<Request>(Channel.UNLIMITED)
    private val admitted = mutableSetOf<CompletableDeferred<Unit>>()
    private val initial = CompletableDeferred<Unit>()
    private var handle: CompletedNotesHandle? = null
    private var latestRaw: String? = saved[RAW_KEY]
    // A reconstructed Android snapshot is a fallback, not a newer durable revision.
    private var authoredThisVisit = false
    private var revision = 0L
    private var needsReopen = false
    private var consumer: Job? = null
    private var privateRetryQueued = false
    private var saveInProgress = false
    private var discardReserved = false
    val hasUnfinished: Boolean get() = saved.contains(RAW_KEY) || mutable.value.resumed || mutable.value.keeping
    val isDiscarding: Boolean get() = discardReserved

    fun start() {
        if (consumer != null) return
        if (store == null) {
            latestRaw?.let { restoreText(it, true) }
            initial.complete(Unit)
            return
        }
        consumer = scope.launch {
            val opened = runCatchingCancellable { reopen(restore = true) }
            if (opened.isFailure) fail()
            initial.complete(Unit)
            for (request in requests) {
                try {
                    when (request) {
                        is Request.Keep -> {
                            if (request.revision != revision && request.done == null) continue
                            if (request.retaining) {
                                if (handle == null || needsReopen) {
                                    handle = store.open(owner)
                                    needsReopen = handle?.problem == true
                                }
                                store.keepRetained(checkNotNull(handle), request.raw)
                            } else {
                                ensureOwner()
                                store.keep(checkNotNull(handle), request.raw)
                            }
                            if (request.raw == latestRaw) mutable.value = mutable.value.copy(keeping = false, kept = true, error = null)
                        }
                        is Request.Save -> {
                            ensureOwner()
                            // Keep the latest authored text, even when this older frozen write is still owed.
                            val checkpointed = latestRaw ?: request.raw
                            store.keep(checkNotNull(handle), checkpointed)
                            if (checkpointed == latestRaw) mutable.value = mutable.value.copy(keeping = false, kept = true, error = null)
                            store.save(checkNotNull(handle), request.raw)
                            if (latestRaw == request.raw) {
                                latestRaw = null
                                authoredThisVisit = false
                                saved.remove<String>(RAW_KEY)
                                mutable.value = mutable.value.copy(resumed = false, kept = false)
                            }
                        }
                        is Request.Reopen -> reopen(restore = true)
                        is Request.Discard -> {
                            if (handle == null) reopen(restore = false)
                            store.discard(checkNotNull(handle))
                            latestRaw = null
                            authoredThisVisit = false
                            saved.remove<String>(RAW_KEY)
                            val next = store.open(owner)
                            handle = next
                            needsReopen = next.problem
                            resetText(next.record?.notes)
                            mutable.value = mutable.value.copy(
                                ready = !next.problem, resumed = false, kept = false,
                                error = if (next.problem) DISCARDED_READ_ERROR else null,
                            )
                        }
                    }
                    request.done?.complete(Unit)
                } catch (cancelled: CancellationException) {
                    request.done?.completeExceptionally(cancelled)
                    throw cancelled
                } catch (_: Exception) {
                    needsReopen = true
                    if (request !is Request.Save) fail()
                    request.done?.completeExceptionally(IllegalStateException("Completed notes were not confirmed"))
                } finally {
                    request.done?.let(admitted::remove)
                }
            }
        }.also { job ->
            job.invokeOnCompletion {
                requests.close()
                initial.complete(Unit)
                admitted.toList().forEach { it.completeExceptionally(CancellationException("Notes owner closed")) }
                admitted.clear()
            }
        }
    }

    suspend fun awaitInitial() { initial.await() }

    fun edit(raw: String): Boolean {
        if (store == null) return true
        if (discardReserved || !mutable.value.ready) return false
        latestRaw = raw
        authoredThisVisit = true
        saved[RAW_KEY] = raw
        revision++
        mutable.value = mutable.value.copy(keeping = true, kept = false)
        requests.trySend(Request.Keep(raw, revision)).getOrThrow()
        return true
    }

    suspend fun save(raw: String) {
        check(store != null && !discardReserved)
        saveInProgress = true
        try { send { Request.Save(raw, it) } } finally { saveInProgress = false }
    }

    /** Navigation promises only a completed Keep; an uncompleted IO write cannot authorize exit. */
    suspend fun keepForExit(raw: String): Boolean {
        if (store == null || discardReserved) return false
        latestRaw = raw
        authoredThisVisit = true
        saved[RAW_KEY] = raw
        val sequence = ++revision
        return runCatchingCancellable { send { Request.Keep(raw, sequence, it, retaining = true) } }.isSuccess
    }

    fun retryPrivate() {
        if (privateRetryQueued || discardReserved || store == null) return
        privateRetryQueued = true
        scope.launch {
            try { runCatchingCancellable { send { Request.Reopen(it) } } }
            finally { privateRetryQueued = false }
        }
    }

    fun dataAvailable() { if (mutable.value.loaded && !mutable.value.ready) retryPrivate() }

    /** Reserve on Main before launching asynchronous discard; old queued raw writes are retired. */
    fun beginDiscard(): Boolean {
        if (store == null || discardReserved || saveInProgress) return false
        discardReserved = true
        revision++
        mutable.value = mutable.value.copy(ready = false, keeping = false, discarding = true)
        return true
    }

    suspend fun discard(): Boolean {
        check(discardReserved)
        return try { runCatchingCancellable { send { Request.Discard(it) } }.isSuccess }
        finally {
            discardReserved = false
            mutable.value = mutable.value.copy(discarding = false, ready = handle?.problem == false)
        }
    }

    private suspend fun send(make: (CompletableDeferred<Unit>) -> Request) {
        val done = CompletableDeferred<Unit>()
        admitted.add(done)
        val queued = requests.trySend(make(done))
        if (queued.isFailure) {
            admitted.remove(done)
            error("Notes owner is unavailable")
        }
        done.await()
    }

    private suspend fun ensureOwner() {
        if (needsReopen || handle == null) reopen(restore = false)
        check(handle?.problem == false)
    }

    private suspend fun reopen(restore: Boolean) {
        val opened = checkNotNull(store).open(owner)
        handle = opened
        // Readable authored text is independent of pending-command cleanup. Expose it even
        // when cleanup cannot complete; reconstruction never authorizes a business write.
        if (restore) {
            val raw = if (authoredThisVisit) latestRaw ?: opened.raw else opened.raw ?: latestRaw
            if (raw != null) {
                onRecoveredDraft()
                latestRaw = raw
                saved[RAW_KEY] = raw
                restoreText(raw, true)
            } else if (!opened.problem) {
                resetText(opened.record?.notes)
            }
        }
        val outcome = if (opened.pending && !opened.problem) store.inspect(opened) else null
        if (outcome == DraftRecoveryOutcome.EXACT_SAVED) {
            onRecoveredSaved()
            if (opened.raw == null && latestRaw?.trim() == opened.record?.notes) {
                latestRaw = null
                saved.remove<String>(RAW_KEY)
            }
        }
        val blocked = opened.problem || outcome == DraftRecoveryOutcome.CONFLICT || outcome == DraftRecoveryOutcome.UNAVAILABLE
        needsReopen = blocked
        mutable.value = mutable.value.copy(
            loaded = true, ready = !blocked, resumed = opened.raw != null || latestRaw != null,
            keeping = false, kept = opened.raw == latestRaw && latestRaw != null,
            error = if (blocked) RECOVERY_ERROR else null,
        )
        check(!blocked) { "Completed notes need reconciliation" }
    }

    private fun fail() {
        mutable.value = mutable.value.copy(loaded = true, ready = false, keeping = false, error = RECOVERY_ERROR)
    }

    private sealed interface Request {
        val done: CompletableDeferred<Unit>?
        data class Keep(val raw: String, val revision: Long, override val done: CompletableDeferred<Unit>? = null, val retaining: Boolean = false) : Request
        data class Save(val raw: String, override val done: CompletableDeferred<Unit>) : Request
        data class Reopen(override val done: CompletableDeferred<Unit>) : Request
        data class Discard(override val done: CompletableDeferred<Unit>) : Request
    }
    private companion object {
        const val RAW_KEY = "completed.notes.raw.v1"
        const val RECOVERY_ERROR = "Unfinished notes need review. Retry to read them again, or discard the unfinished draft. Saved notes stay unchanged."
        const val DISCARDED_READ_ERROR = "The unfinished draft was discarded. Saved notes could not be read. Read them again to continue."
    }
}
