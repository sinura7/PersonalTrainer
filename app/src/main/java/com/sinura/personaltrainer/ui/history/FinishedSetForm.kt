package com.sinura.personaltrainer.ui.history

import androidx.compose.runtime.staticCompositionLocalOf
import com.sinura.personaltrainer.data.draft.FinishedSetDraft
import com.sinura.personaltrainer.data.draft.FinishedSetDrafts
import com.sinura.personaltrainer.data.draft.FinishedSetHandle
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetSaveResolution
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.ui.workout.WorkoutNumberEntries
import com.sinura.personaltrainer.workout.WorkoutNumberDraft
import com.sinura.personaltrainer.workout.WorkoutNumberField
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class FinishedSetFormState(
    val ready: Boolean = false,
    val draft: FinishedSetDraft? = null,
    val visible: Boolean = false,
    val resumed: Boolean = false,
    val message: String? = null,
    val keeping: Boolean = false,
)
internal val LocalFinishedSetForm = staticCompositionLocalOf<FinishedSetForm?> { null }
internal val LocalFinishedSetDiscard = staticCompositionLocalOf<(() -> Unit)?> { null }
internal val LocalFinishedSetReadAgain = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Main-thread authored state, ordered private work, and an awaited Keep before orderly Close. */
internal class FinishedSetForm(
    private val store: FinishedSetDrafts,
    private val sessionId: String,
    private val scope: CoroutineScope,
    private val operationPending: () -> Boolean,
    private val onPending: (WorkoutSetSave) -> Unit,
) : WorkoutNumberEntries {
    private val mutable = MutableStateFlow(FinishedSetFormState())
    val recovery: StateFlow<FinishedSetFormState> = mutable.asStateFlow()
    override val exerciseId: String? get() = mutable.value.draft?.original?.exerciseId
    override val entries: StateFlow<List<WorkoutNumberDraft>> = recovery.map { it.draft?.numbers.orEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    private var handle: FinishedSetHandle? = null
    val retainedCommand: WorkoutSetSave? get() = handle?.pending ?: handle?.draft?.command
    private var revision = 0L
    private var closing = false
        set(value) { field = value; mutable.value = mutable.value.copy(keeping = value) }
    val isClosing: Boolean get() = closing
    private var started = false
    private var knownSaved: WorkoutSetSave? = null
    var privateCommandRestored: Boolean = false
        private set
    private val initial = CompletableDeferred<Unit>()
    private class Work(val run: suspend () -> Unit, val cancel: () -> Unit = {})
    private val work = Channel<Work>(Channel.UNLIMITED)
    private val consumer = scope.launch { for (item in work) item.run() }

    init {
        consumer.invokeOnCompletion {
            work.close()
            while (true) (work.tryReceive().getOrNull() ?: break).cancel()
            initial.cancel()
        }
    }

    fun start() {
        if (started) return
        started = true
        work.trySend(Work(run = {
            runCatchingCancellable {
                val owner = store.open(sessionId)
                handle = owner
                mutable.value = FinishedSetFormState(ready = !owner.problem, draft = owner.draft,
                    visible = owner.draft != null, resumed = owner.draft != null,
                    message = if (owner.problem) "This saved session changed or could not be read. Your unfinished set is retained." else null)
                privateCommandRestored = owner.pending != null
                owner.pending?.let(onPending)
            }.onFailure { mutable.value = mutable.value.copy(ready = false, message = "Could not reconcile the unfinished set. Read again before editing.") }
            initial.complete(Unit)
        }))
    }

    suspend fun awaitInitial() { start(); initial.await() }
    fun canBegin(): Boolean = mutable.value.ready && !closing && !operationPending()
    override fun allowsInput(): Boolean = canBegin() && mutable.value.visible && handle?.pending == null

    fun begin(draft: FinishedSetDraft): Boolean {
        if (!canBegin()) return false
        if (handle?.pending != null) { showKept(); return false }
        val retained = mutable.value.draft
        if (retained != null && retained.original != draft.original) {
            mutable.value = mutable.value.copy(visible = true, message = "Resume or discard the unfinished set before opening another.")
            return false
        }
        change(retained ?: draft)
        mutable.value = mutable.value.copy(visible = true)
        return true
    }
    fun update(values: WorkoutSetValues) {
        if (!allowsInput()) return
        val current = mutable.value.draft ?: return
        if (current.values != values) change(current.copy(values = values))
    }
    override fun put(value: WorkoutNumberDraft) {
        if (!allowsInput() || value.exerciseId != exerciseId) return
        val current = mutable.value.draft ?: return
        change(current.copy(numbers = current.numbers.filter { it.field != value.field }.map {
            if (value.open) it.copy(open = false) else it
        } + value))
    }
    override fun clear(field: WorkoutNumberField) {
        if (!allowsInput()) return
        val current = mutable.value.draft ?: return
        change(current.copy(numbers = current.numbers.filter { it.field != field }))
    }
    private fun change(draft: FinishedSetDraft) {
        revision++
        val version = revision
        mutable.value = mutable.value.copy(draft = draft, message = null)
        work.trySend(Work(run = {
            if (version != revision) return@Work
            runCatchingCancellable { store.keep(checkNotNull(handle), draft) }
                .onFailure { mutable.value = mutable.value.copy(message = "Could not keep this set edit. Retry Keep before leaving.") }
        }))
    }

    /** Caller freezes admission synchronously, then awaits this immutable draft and command. */
    suspend fun write(command: WorkoutSetSave): WorkoutRepository.SavedWorkoutSet = request {
        val current = checkNotNull(mutable.value.draft)
        check(current.command == command)
        if (handle?.pending == null) store.keep(checkNotNull(handle), current)
        store.write(checkNotNull(handle), command)
    }
    suspend fun inspect(command: WorkoutSetSave): WorkoutSetSaveResolution = request {
        store.inspect(checkNotNull(handle), command)
    }
    suspend fun settle(command: WorkoutSetSave) = request {
        store.settle(checkNotNull(handle), command)
        revision++
        mutable.value = FinishedSetFormState(ready = true)
        knownSaved = null
    }
    suspend fun release(command: WorkoutSetSave) = request { store.release(checkNotNull(handle), command) }
    fun acknowledgeSaved() {
        revision++
        knownSaved = mutable.value.draft?.command
        mutable.value = mutable.value.copy(visible = false, ready = false)
    }
    fun hideKept() { mutable.value = mutable.value.copy(visible = false) }
    fun showKept() {
        if (closing) return
        mutable.value = mutable.value.copy(visible = true)
        if (!operationPending()) handle?.pending?.let { command ->
            runCatchingCancellable { onPending(command) }.onFailure {
                recoveryFailed("Could not reconcile the unfinished set. Read again or review saved sets.")
            }
        }
    }
    fun cleanupFailed() {
        mutable.value = mutable.value.copy(ready = false, visible = false,
            message = "Set saved. Could not clear the recovery checkpoint. Read again to reconcile it.")
    }
    fun recoveryFailed(message: String) { mutable.value = mutable.value.copy(ready = false, message = message) }

    /** Framework state complements an absent durable command; it still must be kept before any mutation. */
    suspend fun recoverFramework(command: WorkoutSetSave) = request {
        val owner = checkNotNull(handle)
        val retained = mutable.value.draft
        if (retained != null) {
            check(retained.command == command) { "Framework and private set drafts differ" }
        } else {
            val baseline = command.copy(values = command.original ?: command.values)
            val draft = FinishedSetDraft(checkNotNull(owner.ownerStamp), baseline, command.values)
            store.keep(owner, draft)
            mutable.value = mutable.value.copy(draft = draft, visible = true, ready = !owner.problem)
        }
    }

    /** Back/Close keeps fields. The reserve happens before suspension so stale input cannot follow the flush. */
    fun close(pending: WorkoutSetSave? = null, onKept: () -> Unit) {
        if (closing || (pending == null && operationPending())) return
        closing = true
        val draft = mutable.value.draft
        scope.launch {
            try {
                request { if (draft != null) {
                    if (pending == null) store.keepForExit(checkNotNull(handle), draft)
                    else store.keepFrozenForExit(checkNotNull(handle), draft, pending)
                } }
                mutable.value = mutable.value.copy(visible = false, message = null)
                onKept()
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                mutable.value = mutable.value.copy(message = "Could not keep this set edit. Retry Close or keep editing.")
            } finally { closing = false }
        }
    }
    fun readAgain(allowPending: Boolean = false) {
        if (closing || (!allowPending && operationPending())) return
        closing = true
        val authored = mutable.value.draft.takeIf { knownSaved == null }
        mutable.value = mutable.value.copy(ready = false)
        scope.launch {
            try {
                request {
                    val owner = store.open(sessionId)
                    handle = owner
                    var keepFailed = false
                    if (authored != null && authored != owner.draft) {
                        keepFailed = runCatchingCancellable { store.keep(owner, authored) }.isFailure
                    }
                    val restored = authored ?: owner.draft
                    mutable.value = FinishedSetFormState(ready = !owner.problem && !keepFailed, draft = restored,
                        visible = restored != null, resumed = owner.draft != null, keeping = true,
                        message = when {
                            keepFailed -> "Your newer edit is retained here. Could not keep it yet; try Close or Read again."
                            owner.problem -> "This saved session changed or could not be read. Your unfinished set is retained."
                            else -> null
                        })
                    privateCommandRestored = owner.pending != null
                    owner.pending?.let(onPending)
                }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                mutable.value = mutable.value.copy(ready = false, message = "Could not reconcile the unfinished set. Your current values are retained; try again.")
            } finally { closing = false }
        }
    }
    /** Only the owning VM calls this after inspecting both pending identities and reserving writes. */
    suspend fun discardAfterInspection() = request { discardAndReopen() }
    fun discard(onDiscarded: () -> Unit) {
        if (closing || operationPending()) return
        closing = true
        revision++
        scope.launch {
            try {
                request { discardAndReopen() }
                closing = false
                onDiscarded()
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                mutable.value = mutable.value.copy(message = "Could not discard the unfinished set. Try again.")
            } finally { closing = false }
        }
    }
    private suspend fun discardAndReopen() {
        store.discard(checkNotNull(handle))
        revision++
        knownSaved = null
        privateCommandRestored = false
        // Cleanup accepted; a fresh owner read, not the retired conflict flag, admits editing.
        val reopened = runCatchingCancellable { store.open(sessionId) }.getOrNull()
        if (reopened != null) handle = reopened
        mutable.value = FinishedSetFormState(ready = reopened?.problem == false,
            message = if (reopened?.problem == false) null else
                "Unfinished edit discarded. Could not read this saved session; Read again before editing.")
    }
    private suspend fun <T> request(block: suspend () -> T): T {
        awaitInitial()
        val result = CompletableDeferred<T>()
        work.send(Work(run = { runCatchingCancellable { block() }.fold(result::complete, result::completeExceptionally) },
            cancel = { result.cancel() }))
        return result.await()
    }
}
