package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.workout.WorkoutDraftPersistence
import com.sinura.personaltrainer.workout.WorkoutDraftRestore
import com.sinura.personaltrainer.workout.WorkoutEntryCheckpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class WorkoutPrivatePhase { OPENING, READY, PRESERVATION_FAILED, READ_FAILED, CONFLICT }
internal data class WorkoutPrivateState(
    val phase: WorkoutPrivatePhase, val resumed: Boolean = false,
    val issue: String? = null,
) {
    val canEnter: Boolean get() = phase == WorkoutPrivatePhase.READY || phase == WorkoutPrivatePhase.PRESERVATION_FAILED
    val canCommit: Boolean get() = phase == WorkoutPrivatePhase.READY
    val message: String? get() = issue ?: when (phase) {
        WorkoutPrivatePhase.OPENING -> "Restoring your workout entry…"
        WorkoutPrivatePhase.READY -> if (resumed) "Draft resumed. Only logged sets are saved work." else null
        WorkoutPrivatePhase.PRESERVATION_FAILED -> "Could not preserve the latest entry. Keep editing or try keeping it again."
        WorkoutPrivatePhase.READ_FAILED -> "Could not read the saved draft. Your saved sets are unchanged. Try reading it again."
        WorkoutPrivatePhase.CONFLICT -> "This workout changed. Review saved work before continuing."
    }
}

/** Main-owned admission; one ordered writer conflates obsolete raw snapshots, never commands. */
internal class FloorPrivateDrafts(
    private val persistence: WorkoutDraftPersistence?,
    private val sessionId: String,
    private val scope: CoroutineScope,
    private val onRestored: (WorkoutDraftRestore.ReadyEntry) -> Unit,
) {
    private val serial = Mutex()
    private val latestLock = Any()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var latest: WorkoutEntryCheckpoint? = null
    private var opening = false
    private var openedOnce = false
    private val _state = MutableStateFlow(WorkoutPrivateState(
        phase = if (persistence == null) WorkoutPrivatePhase.READY else WorkoutPrivatePhase.OPENING,
    ))
    val recovery: StateFlow<WorkoutPrivateState> = _state.asStateFlow()

    init {
        if (persistence != null) scope.launch {
            for (ignored in requests) {
                // Read the snapshot, then release the lock before the disk write. An exit
                // flush uses the same lock; holding it across the write left that exit waiting
                // until the background write finished, and under load it never did.
                val entry = serial.withLock { synchronized(latestLock) { latest } } ?: continue
                try {
                    persistence.checkpoint(entry)
                    serial.withLock {
                        if (_state.value.canEnter) _state.value = _state.value.copy(phase = WorkoutPrivatePhase.READY)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    serial.withLock {
                        if (_state.value.canEnter) _state.value = _state.value.copy(phase = WorkoutPrivatePhase.PRESERVATION_FAILED)
                    }
                }
            }
        }
    }

    fun open() {
        val port = persistence ?: return
        if (opening || _state.value.canEnter && port.ownsEntry(sessionId)) return
        val replaceLocal = openedOnce && !port.ownsEntry(sessionId)
        opening = true
        _state.value = WorkoutPrivateState(WorkoutPrivatePhase.OPENING)
        scope.launch {
            try {
                serial.withLock {
                    when (val result = port.open(sessionId)) {
                        is WorkoutDraftRestore.ReadyEntry -> {
                            onRestored(result.copy(replaceLocal = replaceLocal))
                            openedOnce = true
                            _state.value = WorkoutPrivateState(WorkoutPrivatePhase.READY, result.resumed)
                        }
                        is WorkoutDraftRestore.OwnerConflict -> _state.value = WorkoutPrivateState(WorkoutPrivatePhase.CONFLICT)
                        WorkoutDraftRestore.ReadUnavailable -> _state.value = WorkoutPrivateState(WorkoutPrivatePhase.READ_FAILED)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = WorkoutPrivateState(WorkoutPrivatePhase.READ_FAILED)
            } finally {
                opening = false
            }
        }
    }

    fun stage(entry: WorkoutEntryCheckpoint) {
        if (accept(entry)) nudge()
    }

    /**
     * Remembers [entry] when it is the newest snapshot. The disk write is [nudge], so a caller
     * that still holds the entry lock can publish the snapshot before the writer runs.
     */
    fun accept(entry: WorkoutEntryCheckpoint): Boolean = synchronized(latestLock) {
        if (persistence == null || !_state.value.canEnter || entry.sessionId != sessionId) return false
        if (!persistence.ownsEntry(sessionId)) { ownershipLost(); return false }
        if (entry.authoredRevision < (latest?.authoredRevision ?: 0)) return false
        latest = entry
        true
    }

    fun nudge() {
        if (persistence == null) return
        requests.trySend(Unit)
    }

    fun preservationFailed() {
        if (_state.value.canEnter) _state.value = _state.value.copy(phase = WorkoutPrivatePhase.PRESERVATION_FAILED)
    }
    fun ownershipLost() {
        if (_state.value.canEnter) _state.value = WorkoutPrivateState(phase = WorkoutPrivatePhase.CONFLICT,
            issue = "This entry was opened elsewhere. Check again to load its latest fields.")
    }

    /** Wait for restoration before the caller captures the fields it promises to keep. */
    suspend fun awaitOpened(): WorkoutPrivateState = recovery.first { it.phase != WorkoutPrivatePhase.OPENING }
    fun reopenAfterDiscard() {
        synchronized(latestLock) { latest = null }
        _state.value = WorkoutPrivateState(WorkoutPrivatePhase.CONFLICT)
        open()
    }
    fun discardFailed() {
        _state.value = _state.value.copy(issue = "Could not discard the unfinished entry. Check again or close and return later.")
    }

    /** Exit owns editing before entering this boundary; failure must keep navigation pending. */
    suspend fun flush(entry: WorkoutEntryCheckpoint): Boolean {
        val port = persistence ?: return true
        if (!port.ownsEntry(sessionId)) { ownershipLost(); return false }
        if (!_state.value.canEnter || entry.sessionId != sessionId) return false
        accept(entry)
        return serial.withLock {
            val chosen = synchronized(latestLock) { latest }
            try {
                port.checkpoint(checkNotNull(chosen))
                _state.value = _state.value.copy(phase = WorkoutPrivatePhase.READY)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(phase = WorkoutPrivatePhase.PRESERVATION_FAILED)
                false
            }
        }
    }
}
