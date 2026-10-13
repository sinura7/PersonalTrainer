package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.draft.CompletedNoteKind
import com.sinura.personaltrainer.data.draft.CompletedNoteOwner
import com.sinura.personaltrainer.data.draft.CompletedNotesDrafts
import com.sinura.personaltrainer.ui.components.CompletedNotesForm
import com.sinura.personaltrainer.ui.components.NotesSaveState
import com.sinura.personaltrainer.ui.workout.FloorSessionNotes
import com.sinura.personaltrainer.domain.ActivityDetailCopy
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CompletedTrainingDetailLoad
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.cardioMinutes
import com.sinura.personaltrainer.domain.strengthWork
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.ErrorSlot
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "PT/ActivityDetail"
private const val ERR_DETAIL = "detail"

/**
 * [missing] and [failed] are different answers to different questions. Missing is a query
 * that succeeded and found no row: the activity is not on this phone. Failed is a query that
 * threw: the activity may well be there and the read is what broke.
 *
 * Notes and delete are the activity-edit split (completed-training-convergence.md §2
 * step 4). Set repair and repeat are refused by the store and not offered here.
 */
data class ActivityDetailUiState(
    val isLoading: Boolean = true,
    val missing: Boolean = false,
    val failed: Boolean = false,
    val session: ActivitySession? = null,
    val notes: String = "",
    val notesSave: NotesSaveState = NotesSaveState(),
    val strengthSetCount: Int = 0,
    val cardioMinutes: Int = 0,
    val volumeKg: Double = 0.0,
    val durationMinutes: Int = 0,
)

class ActivityDetailViewModel internal constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies,
    private val drafts: CompletedNotesDrafts?,
) : AppViewModel(application, container) {
    @JvmOverloads constructor(application: Application, savedStateHandle: SavedStateHandle, container: AppDependencies = application.appContainer()) :
        this(application, savedStateHandle, container, (container as? AppContainer)?.completedNotesDrafts)
    private val activityId: String =
        savedStateHandle.get<String>("activityId")
            ?: savedStateHandle.get<String>("sessionId").orEmpty()

    private val load = MutableStateFlow(ActivityDetailUiState())
    private val notes: FloorSessionNotes = FloorSessionNotes(
        sessionId = activityId,
        write = { value ->
            if (drafts != null) notesForm.save(value)
            else check(container.activityRepository.updateCompletedNotes(activityId, value, container.time.nowMillis()) is ActivityWrite.Accepted) {
                "Completed notes were refused"
            }
        },
        readStored = { container.activityRepository.get(activityId)?.notes },
    )
    private val notesForm: CompletedNotesForm = CompletedNotesForm(
        store = drafts.takeIf { activityId.isNotBlank() }, owner = CompletedNoteOwner(CompletedNoteKind.ACTIVITY, activityId),
        saved = savedStateHandle, scope = viewModelScope,
        restoreText = notes::restore,
        resetText = notes::resetAfterPrivateDiscard,
    )
    internal val notesRecovery = notesForm.state
    internal val durableNotes: Boolean get() = drafts != null
    private val errors = ErrorSlot()
    val error: StateFlow<String?> = errors.messages

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()
    private val _notesExitRequested = MutableStateFlow(false)
    internal val notesExitRequested = _notesExitRequested.asStateFlow()
    private val _notesExitBlocked = MutableStateFlow(false)
    internal val notesExitBlocked = _notesExitBlocked.asStateFlow()
    private val _exiting = MutableStateFlow(false)
    internal val notesExiting = _exiting.asStateFlow()
    private var exiting: Boolean
        get() = _exiting.value
        set(value) { _exiting.value = value }

    private val mutating = AtomicBoolean(false)
    private var loading: Job? = null

    val uiState: StateFlow<ActivityDetailUiState> = combine(load, notes.text, notes.saveState) { loaded, typed, save ->
        loaded.copy(notes = typed, notesSave = save)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityDetailUiState(),
    )

    init {
        notesForm.start()
        load()
        viewModelScope.launch { notes.writeOnTypingPause() }
    }

    /** Re-read after a failed load. A no-op unless the last read actually threw. */
    fun retry() {
        if (!load.value.failed) return
        load()
    }

    fun setNotes(value: String) {
        if (exiting || _notesExitRequested.value || mutating.get() || !notesRecovery.value.ready) return
        if (!notesForm.edit(value)) return
        notes.edit(value)
    }

    /** Flushes the debounce tail before this back-stack entry is removed. */
    fun persistNotesForExit() {
        viewModelScope.launch { notes.writeNow() }
    }

    internal fun retryNotesSave() {
        if (exiting || _notesExitRequested.value) return
        if (notes.beginRetry()) viewModelScope.launch { notes.retryNow() }
    }

    internal fun retryPrivateNotes() { if (!exiting && !_notesExitRequested.value) notesForm.retryPrivate() }

    internal fun discardPrivateNotes() {
        if (exiting || _notesExitRequested.value || mutating.get() || notes.saveState.value.busy || !notesForm.beginDiscard()) return
        notes.abandonPendingWrites()
        viewModelScope.launch { if (!notesForm.discard()) notes.resumeAfterFailedDiscard() }
    }

    internal fun requestNotesExit() {
        if (exiting || _notesExitRequested.value || mutating.get() || notesForm.isDiscarding) return
        exiting = true
        _notesExitBlocked.value = false
        viewModelScope.launch {
            try {
                val saved = notes.writeNow()
                val kept = if (drafts != null && (!saved || notesForm.hasUnfinished)) notesForm.keepForExit(notes.text.value) else saved
                if (kept) _notesExitRequested.value = true else _notesExitBlocked.value = true
            } finally { exiting = false }
        }
    }

    internal fun keepEditingNotes() { if (!exiting && !_notesExitRequested.value) _notesExitBlocked.value = false }

    internal fun leaveWithoutNotes() {
        if (exiting || _notesExitRequested.value || mutating.get() || notesForm.isDiscarding || notes.saveState.value.busy) return
        exiting = true
        _notesExitBlocked.value = false
        viewModelScope.launch {
            try {
                val ready = if (drafts == null) notes.abandonPendingWrites() else notesForm.keepForExit(notes.text.value)
                if (ready) _notesExitRequested.value = true else _notesExitBlocked.value = true
            } finally { exiting = false }
        }
    }

    internal fun onNotesExitHandled() { _notesExitRequested.value = false }

    fun deleteSession() {
        if (exiting || _notesExitRequested.value || !mutating.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                runCatchingCancellable { container.activityRepository.deleteCompleted(activityId) }
                    .onSuccess { write ->
                        when (write) {
                            is ActivityWrite.Accepted -> {
                                if (drafts != null && notesForm.beginDiscard()) notesForm.discard()
                                _deleted.value = true
                            }
                            is ActivityWrite.Rejected ->
                                report(IllegalStateException(write.reason), write.reason)
                        }
                    }
                    .onFailure { report(it, "Could not delete that session. Try again.") }
            } finally {
                mutating.set(false)
            }
        }
    }

    private fun load() {
        loading?.cancel()
        load.value = ActivityDetailUiState()
        loading = viewModelScope.launch {
            val result = runCatchingCancellable { container.activityRepository.get(activityId) }
            val detail = CompletedTrainingDetailLoad.fromResult(result)
            if (detail.failed) {
                AppLog.e(TAG, "Loading activity detail failed", result.exceptionOrNull())
                load.value = ActivityDetailUiState(isLoading = false, failed = true)
                return@launch
            }
            if (detail.missing) {
                notes.sessionMissing()
                load.value = ActivityDetailUiState(isLoading = false, missing = true)
                return@launch
            }
            val session = checkNotNull(detail.value)
            val work = session.strengthWork()
            val cardioMinutes = session.cardioMinutes()
            notesForm.awaitInitial()
            notesForm.dataAvailable()
            notes.sessionRead(session.notes)
            load.value = ActivityDetailUiState(
                isLoading = false,
                session = session,
                notes = session.notes,
                strengthSetCount = session.strengthSetCount(),
                cardioMinutes = cardioMinutes,
                volumeKg = work.volumeKg,
                durationMinutes = ActivityDetailCopy.receiptDurationMinutes(session, cardioMinutes),
            )
        }
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
