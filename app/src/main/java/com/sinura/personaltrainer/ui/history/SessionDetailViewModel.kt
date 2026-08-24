package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.RepeatOutcome
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PT/SessionDetailViewModel"

/** Long enough that a sentence is one write, short enough that leaving the screen is safe. */
private const val NOTES_WRITE_DEBOUNCE_MS = 400L

data class SessionDetailUiState(
    val isLoading: Boolean = true,
    val session: WorkoutSession? = null,
    val notes: String = "",
)

/**
 * A finished session, now writable.
 *
 * Every write here is deliberately narrow — set fields, notes, the session's existence — and
 * every one of them goes through a repository method that refuses to touch a timestamp. What
 * the screen can change is what a lifter can get wrong; what it cannot change is when any of
 * it happened.
 */
class SessionDetailViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()

    private val session: StateFlow<SessionLoad> = container.workoutRepository.observeSession(sessionId)
        .map { SessionLoad(isLoading = false, session = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionLoad(),
        )

    private val notes = MutableStateFlow("")

    /** What the database already holds. Null until the row has been read — see [writeNotes]. */
    private var lastPersistedNotes: String? = null

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _navigateToSession = MutableStateFlow<String?>(null)
    val navigateToSession: StateFlow<String?> = _navigateToSession.asStateFlow()

    private val _blockedRepeat = MutableStateFlow<RepeatOutcome.Blocked?>(null)
    val blockedRepeat: StateFlow<RepeatOutcome.Blocked?> = _blockedRepeat.asStateFlow()

    private val _deletedSet = MutableStateFlow<WorkoutRepository.DeletedSet?>(null)
    val deletedSet: StateFlow<WorkoutRepository.DeletedSet?> = _deletedSet.asStateFlow()

    val uiState: StateFlow<SessionDetailUiState> = combine(session, notes) { load, typed ->
        SessionDetailUiState(
            isLoading = load.isLoading,
            session = load.session,
            notes = typed,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionDetailUiState(),
        )

    init {
        viewModelScope.launch {
            session.collect { load ->
                val current = load.session ?: return@collect
                lastPersistedNotes = current.notes
                if (notes.value.isEmpty() && current.notes.isNotEmpty()) {
                    notes.value = current.notes
                }
            }
        }
        // The same debounce contract as the live workout's notes field, and for the same
        // reason: Room does not order concurrent writes against each other, so a write per
        // keystroke can land a shorter earlier string on top of a longer later one.
        viewModelScope.launch {
            notes.collectLatest { value ->
                delay(NOTES_WRITE_DEBOUNCE_MS)
                writeNotes(value)
            }
        }
    }

    fun setNotes(value: String) {
        notes.value = value
        // The database write is not launched here. See the debounce collector in init.
    }

    /** Flushes the debounce tail before this back-stack entry is removed. */
    fun persistNotesForExit() {
        viewModelScope.launch { writeNotes(notes.value) }
    }

    private suspend fun writeNotes(value: String) {
        if (sessionId.isBlank()) return
        // Null means the row has not been read yet, so what is on disk is unknown. Writing
        // here would push the empty initial value over real notes on a cold start.
        val known = lastPersistedNotes ?: return
        if (value == known) return
        withContext(NonCancellable) {
            runCatchingCancellable {
                container.workoutRepository.updateSessionNotes(sessionId, value)
                lastPersistedNotes = value
            }.onFailure { thrown ->
                AppLog.w(TAG, "Writing the session notes failed", thrown)
                // No error UI: the next keystroke retries.
            }
        }
    }

    fun updateSet(setId: String, weightKg: Double, reps: Int, rpe: Int?, isWarmup: Boolean) {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
            runCatchingCancellable { container.workoutRepository.deleteFinishedSession(sessionId) }
                .onSuccess { _deleted.value = true }
                .onFailure { report(it, "Could not delete that session. Try again.") }
        }
    }

    fun repeatSession() {
        viewModelScope.launch {
            runCatchingCancellable { container.workoutRepository.repeatSession(sessionId) }
                .onSuccess { outcome ->
                    when (outcome) {
                        is RepeatOutcome.Started -> _navigateToSession.value = outcome.sessionId
                        is RepeatOutcome.Blocked -> _blockedRepeat.value = outcome
                        is RepeatOutcome.Failed -> _error.value = outcome.message
                    }
                }
                .onFailure { report(it, "Could not repeat that workout. Try again.") }
        }
    }

    fun resumeBlockedSession() {
        val blocked = _blockedRepeat.value ?: return
        _blockedRepeat.value = null
        _navigateToSession.value = blocked.inProgressSessionId
    }

    fun dismissBlockedRepeat() {
        _blockedRepeat.value = null
    }

    fun onNavigationHandled() {
        _navigateToSession.value = null
    }

    fun onErrorShown() {
        _error.value = null
    }

    private fun report(thrown: Throwable, fallback: String) {
        AppLog.w(TAG, fallback, thrown)
        _error.value = thrown.message?.takeIf { SetLogRules.isUserMessage(it) } ?: fallback
    }

    /** The session row plus whether it has been read yet, so "loading" and "gone" stay distinct. */
    private data class SessionLoad(
        val isLoading: Boolean = true,
        val session: WorkoutSession? = null,
    )
}
