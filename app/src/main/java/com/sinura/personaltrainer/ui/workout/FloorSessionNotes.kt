package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.ui.components.NotesSaveState
import com.sinura.personaltrainer.ui.components.NotesSaveStatus
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.workout.FinishOutcome
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val NOTES_WRITE_DEBOUNCE_MS = 400L

/** Ordered notes writes and their actual result. Raw text remains the existing draft's. */
internal class FloorSessionNotes(
    private val sessionId: String,
    private val write: suspend (String) -> Unit,
    private val readStored: suspend () -> String?,
    private val onUnconfirmed: (Boolean, Boolean, String) -> Unit = { _, _, _ -> },
) {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()
    private val _saveState = MutableStateFlow(NotesSaveState())
    val saveState: StateFlow<NotesSaveState> = _saveState.asStateFlow()
    private val writeLock = Mutex()
    private val writeRequest = MutableStateFlow(0L)
    private var revision = 0L
    private var lastPersisted: String? = null
    private var hydrated = false
    private var hasUserDraft = false
    private var writingRevision: Long? = null
    private var failedRevision: Long? = null
    private var pausedDiscardRevision: Long? = null
    private var missing = false
    private var cleared = false
    private var retryQueued = false
    private var terminal = false

    fun edit(value: String) {
        if (terminal || value == _text.value) return
        revision += 1
        hasUserDraft = true
        failedRevision = null
        _text.value = value
        writeRequest.value += 1
        publish()
    }

    fun restore(value: String) {
        revision += 1
        hasUserDraft = true
        hydrated = true
        _text.value = value
        writeRequest.value += 1
        publish()
    }

    fun sessionRead(stored: String) {
        val wasUnknown = lastPersisted == null
        lastPersisted = stored.trim()
        missing = false
        if (_text.value.trim() == lastPersisted) failedRevision = null
        if (!hydrated) {
            hydrated = true
            if (_text.value.isEmpty() && stored.isNotEmpty()) _text.value = stored
        }
        // A restored draft's pause may have ended before the first row arrived. Reading that
        // row schedules its ordinary pause again; unrelated row updates do not restart it.
        if (wasUnknown) writeRequest.value += 1
        publish()
    }

    fun sessionMissing() {
        missing = true
        lastPersisted = null
        if (hasUserDraft) failedRevision = revision
        publish()
    }

    suspend fun writeOnTypingPause() {
        writeRequest.collectLatest { request ->
            val requestedRevision = revision
            delay(NOTES_WRITE_DEBOUNCE_MS)
            writeLock.withLock {
                if (request == writeRequest.value && requestedRevision == revision && requestedRevision != pausedDiscardRevision) writeCurrent()
            }
        }
    }

    suspend fun writeNow(): Boolean = writeLock.withLock { writeCurrent() }

    fun beginRetry(): Boolean {
        if (!_saveState.value.canRetry || retryQueued) return false
        retryQueued = true
        publish()
        return true
    }

    suspend fun retryNow() {
        try {
            writeNow()
        } finally {
            retryQueued = false
            publish()
        }
    }

    /** History's explicit discard choice cannot race an ordinary pause after navigation. */
    fun abandonPendingWrites(): Boolean {
        if (_saveState.value.busy) return false
        terminal = true
        publish()
        return true
    }

    /** Forward navigation retains History's entry. Explicit discard reloads its real row,
     * cancels the old pause and leaves this same owner ready for later edits on return. */
    suspend fun discardDraftForForward(): Boolean = writeLock.withLock {
        revision += 1
        writeRequest.value += 1
        terminal = true
        retryQueued = true
        publish()
        try {
            val stored = runCatchingCancellable { readStored() }.getOrElse { thrown ->
                AppLog.w(TAG, "Reloading notes after an explicit discard failed", thrown)
                failedRevision = revision
                // The read's new pause must not save words the user just chose to discard.
                // A new edit has a new revision; an explicit Retry uses writeNow directly.
                pausedDiscardRevision = revision
                return@withLock false
            }
            _text.value = stored.orEmpty()
            lastPersisted = stored?.trim()
            hydrated = true
            hasUserDraft = false
            failedRevision = null
            cleared = false
            missing = stored == null
            true
        } finally {
            terminal = false
            retryQueued = false
            publish()
        }
    }

    /** Finish keeps its original use-case order; no older notes write can follow it. */
    suspend fun finishWithLatest(block: suspend (String) -> FinishOutcome): FinishOutcome =
        writeLock.withLock {
            val finalRevision = revision
            val finalNotes = _text.value
            writingRevision = finalRevision
            publish()
            try {
                val outcome = block(finalNotes)
                if (outcome is FinishOutcome.Finished) {
                    terminal = true
                    failedRevision = null
                    // Finished can also mean the existing idempotent already-finished path.
                    // Only a read of its actual row establishes which notes are saved.
                    runCatchingCancellable { readStored() }.onSuccess { stored ->
                        if (stored == null) {
                            missing = true
                            if (revision == finalRevision) failedRevision = finalRevision
                        } else {
                            lastPersisted = stored.trim()
                            cleared = finalNotes.trim().isEmpty() && lastPersisted!!.isEmpty()
                        }
                    }.onFailure { AppLog.w(TAG, "Confirming final session notes failed", it) }
                } else if (outcome is FinishOutcome.Failed && revision == finalRevision && finalNotes.trim() != lastPersisted) {
                    failedRevision = finalRevision
                } else if (outcome is FinishOutcome.SessionMissing) {
                    sessionMissing()
                }
                outcome
            } finally {
                writingRevision = null
                publish()
            }
        }

    private suspend fun writeCurrent(): Boolean {
        if (terminal) return true
        if (sessionId.isBlank()) return !hasUserDraft
        val known = lastPersisted ?: return !hasUserDraft
        val value = _text.value
        val canonical = value.trim()
        if (canonical == known && failedRevision == null) {
            publish()
            return true
        }
        val writing = revision
        writingRevision = writing
        publish()
        try {
            withContext(NonCancellable) {
                runCatchingCancellable {
                    write(value)
                    val observed = readStored()
                    if (observed == null) {
                        missing = true
                        error("The session no longer exists")
                    }
                    lastPersisted = observed.trim()
                    check(lastPersisted == canonical) { "The notes write was not confirmed" }
                    cleared = canonical.isEmpty()
                    if (failedRevision == writing) failedRevision = null
                }.onFailure { thrown ->
                    AppLog.w(TAG, "Writing the session notes failed", thrown)
                    if (revision == writing && (missing || lastPersisted != canonical)) failedRevision = writing
                }
            }
        } finally {
            writingRevision = null
            publish()
        }
        return _saveState.value.status == NotesSaveStatus.SAVED
    }

    private fun publish() {
        val busy = writingRevision != null || retryQueued
        val status = when {
            missing && hasUserDraft -> NotesSaveStatus.FAILED
            writingRevision == revision -> NotesSaveStatus.SAVING
            failedRevision == revision -> NotesSaveStatus.FAILED
            lastPersisted == null -> NotesSaveStatus.UNKNOWN
            busy -> NotesSaveStatus.PENDING
            _text.value.trim() == lastPersisted -> NotesSaveStatus.SAVED
            else -> NotesSaveStatus.PENDING
        }
        _saveState.value = NotesSaveState(
            status = status,
            busy = busy,
            canRetry = status == NotesSaveStatus.FAILED && !busy && !missing && !terminal,
            cleared = cleared && _text.value.trim().isEmpty(),
            missing = missing,
        )
        // A surface without this editor must not clear an authored or in-flight draft.
        // Busy also protects a reverted value that currently equals the stored row.
        onUnconfirmed(!terminal && (busy || (hasUserDraft && status != NotesSaveStatus.SAVED)), busy, _text.value)
    }

    private companion object {
        const val TAG = "PT/SessionNotes"
    }
}
