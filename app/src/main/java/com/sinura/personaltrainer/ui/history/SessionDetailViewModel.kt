package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.RepeatOutcome
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.CompletedTrainingDetailLoad
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.ui.components.NotesSaveState
import com.sinura.personaltrainer.ui.workout.FloorSessionNotes
import com.sinura.personaltrainer.util.ErrorSlot
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "PT/SessionDetailViewModel"

/** [ErrorSlot] families: a success may clear only its own family's refusal. */
private const val ERR_REPEAT = "repeat"
private const val ERR_DETAIL = "detail"

data class SessionDetailUiState(
    val isLoading: Boolean = true,
    val missing: Boolean = false,
    val failed: Boolean = false,
    val session: WorkoutSession? = null,
    val notes: String = "",
    val notesSave: NotesSaveState = NotesSaveState(),
    val notesExiting: Boolean = false,
)

/**
 * A finished session, now writable.
 *
 * Every write here is deliberately narrow — set fields, notes, the session's existence — and
 * every one of them goes through a repository method that refuses to touch a timestamp. What
 * the screen can change is what a lifter can get wrong; what it cannot change is when any of
 * it happened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()

    private val retryNonce = MutableStateFlow(0)

    private val load: StateFlow<CompletedTrainingDetailLoad<WorkoutSession>> =
        retryNonce.flatMapLatest {
            flow {
                emit(CompletedTrainingDetailLoad.loading())
                emitAll(
                    container.workoutRepository.observeSessionHealth(sessionId)
                        .map { health -> CompletedTrainingDetailLoad.from(health) },
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CompletedTrainingDetailLoad.loading(),
        )

    private val notesOwner = Any()
    private val sessionNotes = FloorSessionNotes(
        sessionId = sessionId,
        write = { notes ->
            container.workoutDraftCache.withNotesWrite(sessionId) {
                container.workoutRepository.updateSessionNotes(sessionId, notes)
            }
        },
        readStored = { container.workoutRepository.getSession(sessionId)?.notes },
        onUnconfirmed = { pending, busy, notes ->
            val live = load.value.value?.isFinished != true
            if (live && pending) container.workoutDraftCache.putSessionNotes(sessionId, notes)
            container.workoutDraftCache.markNotesPending(
                sessionId = sessionId, owner = notesOwner, pending = live && pending, busy = busy, notes = notes,
            )
        },
    )

    private val errors = ErrorSlot()
    val error: StateFlow<String?> = errors.messages

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _navigateToSession = MutableStateFlow<String?>(null)
    val navigateToSession: StateFlow<String?> = _navigateToSession.asStateFlow()

    private val _blockedRepeat = MutableStateFlow<RepeatOutcome.Blocked?>(null)
    val blockedRepeat: StateFlow<RepeatOutcome.Blocked?> = _blockedRepeat.asStateFlow()

    private val _deletedSet = MutableStateFlow<WorkoutRepository.DeletedSet?>(null)
    private val mutating = AtomicBoolean(false)
    val deletedSet: StateFlow<WorkoutRepository.DeletedSet?> = _deletedSet.asStateFlow()
    private val notesExitPending = MutableStateFlow(false)
    private val _notesExitRequested = MutableStateFlow(false)
    val notesExitRequested: StateFlow<Boolean> = _notesExitRequested.asStateFlow()
    private val notesExiting = combine(notesExitPending, _notesExitRequested, _navigateToSession) { pending, back, target ->
        pending || back || target != null
    }

    val uiState: StateFlow<SessionDetailUiState> = combine(load, sessionNotes.text, sessionNotes.saveState, notesExiting) { load, typed, save, exiting ->
        SessionDetailUiState(
            isLoading = load.isLoading,
            missing = load.missing,
            failed = load.failed,
            session = load.value,
            notes = typed,
            notesSave = save,
            notesExiting = exiting,
        )
    }.combine(container.workoutDraftCache.finishing) { state, finishing ->
        state.copy(notesExiting = state.notesExiting || sessionId in finishing)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionDetailUiState(),
        )

    init {
        viewModelScope.launch {
            load.collect { load ->
                if (load.missing) sessionNotes.sessionMissing()
                val current = load.value ?: return@collect
                sessionNotes.sessionRead(current.notes)
            }
        }
        viewModelScope.launch { sessionNotes.writeOnTypingPause() }
    }

    fun setNotes(value: String) {
        if (notesExitIsActive()) return
        container.workoutDraftCache.editSessionNotes(sessionId) { sessionNotes.edit(value) }
    }

    /** Flushes the debounce tail before this back-stack entry is removed. */
    fun persistNotesForExit() {
        viewModelScope.launch { sessionNotes.writeNow() }
    }

    fun retryNotesSave() {
        if (sessionNotes.beginRetry()) viewModelScope.launch { sessionNotes.retryNow() }
    }

    private val _notesExitBlocked = MutableStateFlow(false)
    val notesExitBlocked: StateFlow<Boolean> = _notesExitBlocked.asStateFlow()
    private var pendingNotesExit: NotesExitIntent? = null

    private sealed class NotesExitIntent {
        object Back : NotesExitIntent()
        object Repeat : NotesExitIntent()
        data class Resume(val sessionId: String) : NotesExitIntent()
    }

    fun requestNotesExit(leaveWithoutChanges: Boolean = false) {
        beginNotesExit(pendingNotesExit ?: NotesExitIntent.Back, leaveWithoutChanges)
    }

    private fun notesExitIsActive(): Boolean =
        notesExitPending.value || _notesExitRequested.value || _navigateToSession.value != null

    private fun canBeginNotesExit(): Boolean = !notesExitIsActive() && !mutating.get()

    /** Retry keeps the original destination; no new session or navigation precedes notes. */
    private fun beginNotesExit(intent: NotesExitIntent, leaveWithoutChanges: Boolean = false) {
        if (!canBeginNotesExit()) return
        if (leaveWithoutChanges && sessionNotes.saveState.value.busy) return
        if (leaveWithoutChanges && intent == NotesExitIntent.Back) {
            if (!sessionNotes.abandonPendingWrites()) return
            pendingNotesExit = null
            _notesExitBlocked.value = false
            _notesExitRequested.value = true
            return
        }
        pendingNotesExit = intent
        _notesExitBlocked.value = false
        notesExitPending.value = true
        viewModelScope.launch {
            try {
                val ready = if (leaveWithoutChanges) sessionNotes.discardDraftForForward()
                    else sessionNotes.writeNow()
                if (ready) {
                    pendingNotesExit = null
                    when (intent) {
                        NotesExitIntent.Back -> _notesExitRequested.value = true
                        NotesExitIntent.Repeat -> repeatAfterNotes()
                        is NotesExitIntent.Resume -> _navigateToSession.value = intent.sessionId
                    }
                } else {
                    _notesExitBlocked.value = true
                }
            } finally {
                notesExitPending.value = false
            }
        }
    }

    fun onNotesExitHandled() {
        _notesExitRequested.value = false
    }

    fun keepEditingNotes() {
        if (notesExitPending.value) return
        pendingNotesExit = null
        _notesExitBlocked.value = false
    }

    /** Re-subscribe after a failed load. A no-op unless the last read actually threw. */
    fun retry() {
        if (!load.value.failed) return
        retryNonce.value += 1
    }

    fun updateSet(setId: String, weightKg: Double, reps: Int, rpe: Int?, isWarmup: Boolean) {
        // A corrected working set needs its effort as a fresh one does (P2a). A hold is stored
        // with no reps, which is how this screen tells one apart.
        SetLogRules.validateEffort(rpe = rpe, isWarmup = isWarmup, isHold = reps == 0)?.let { missing ->
            report(IllegalArgumentException(missing), missing)
            return
        }
        viewModelScope.launch {
            runCatchingCancellable {
                container.workoutRepository.updateSet(
                    setId = setId,
                    weightKg = weightKg,
                    reps = reps,
                    rpe = rpe,
                    isWarmup = isWarmup,
                )
            }.onFailure { report(it, "Could not save that set. Try again.") }
        }
    }

    fun addSet(exerciseId: String, weightKg: Double, reps: Int, rpe: Int?, isWarmup: Boolean) {
        SetLogRules.validateEffort(rpe = rpe, isWarmup = isWarmup, isHold = reps == 0)?.let { missing ->
            report(IllegalArgumentException(missing), missing)
            return
        }
        if (!mutating.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                runCatchingCancellable {
                    container.workoutRepository.addSetToFinishedSession(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        weightKg = weightKg,
                        reps = reps,
                        rpe = rpe,
                        isWarmup = isWarmup,
                    )
                }.onFailure { report(it, "Could not add that set. Try again.") }
            } finally {
                mutating.set(false)
            }
        }
    }

    fun deleteSet(setId: String) {
        viewModelScope.launch {
            runCatchingCancellable { container.workoutRepository.deleteSet(setId) }
                .onSuccess { _deletedSet.value = it }
                .onFailure { report(it, "Could not delete that set. Try again.") }
        }
    }

    fun undoDeleteSet() {
        val pending = _deletedSet.value ?: return
        _deletedSet.value = null
        viewModelScope.launch {
            runCatchingCancellable { container.workoutRepository.restoreSet(pending) }
                .onFailure { report(it, "Could not restore that set. Try again.") }
        }
    }

    fun onUndoOfferHandled() {
        _deletedSet.value = null
    }

    fun deleteSession() {
        if (!mutating.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                runCatchingCancellable { container.workoutRepository.deleteFinishedSession(sessionId) }
                    .onSuccess { _deleted.value = true }
                    .onFailure { report(it, "Could not delete that session. Try again.") }
            } finally {
                mutating.set(false)
            }
        }
    }

    fun repeatSession() {
        if (pendingNotesExit != null) return
        beginNotesExit(NotesExitIntent.Repeat)
    }

    private suspend fun repeatAfterNotes() {
        if (!mutating.compareAndSet(false, true)) return
        try {
            runCatchingCancellable { container.workoutRepository.repeatSession(sessionId) }
                .onSuccess { outcome ->
                    when (outcome) {
                        is RepeatOutcome.Started -> _navigateToSession.value = outcome.sessionId
                        is RepeatOutcome.Blocked -> _blockedRepeat.value = outcome
                        is RepeatOutcome.Failed ->
                            errors.fail(source = ERR_REPEAT, message = outcome.message)
                    }
                }
                .onFailure { report(it, "Could not repeat that workout. Try again.") }
        } finally {
            mutating.set(false)
        }
    }

    fun resumeBlockedSession() {
        val blocked = _blockedRepeat.value ?: return
        if (pendingNotesExit != null || !canBeginNotesExit()) return
        _blockedRepeat.value = null
        beginNotesExit(NotesExitIntent.Resume(blocked.inProgressSessionId))
    }

    fun dismissBlockedRepeat() {
        _blockedRepeat.value = null
    }

    fun onNavigationHandled() {
        _navigateToSession.value = null
    }

    fun onErrorShown() {
        errors.dismiss()
    }

    private fun report(thrown: Throwable, fallback: String) {
        AppLog.w(TAG, fallback, thrown)
        errors.fail(
            source = ERR_DETAIL,
            message = thrown.message?.takeIf { SetLogRules.isUserMessage(it) } ?: fallback,
        )
    }

}
