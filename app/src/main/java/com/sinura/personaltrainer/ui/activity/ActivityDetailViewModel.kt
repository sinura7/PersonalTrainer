package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "PT/ActivityDetail"
private const val ERR_DETAIL = "detail"
private const val NOTES_WRITE_DEBOUNCE_MS = 400L

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
    val strengthSetCount: Int = 0,
    val cardioMinutes: Int = 0,
    val volumeKg: Double = 0.0,
    val durationMinutes: Int = 0,
)

class ActivityDetailViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val activityId: String =
        savedStateHandle.get<String>("activityId")
            ?: savedStateHandle.get<String>("sessionId").orEmpty()

    private val load = MutableStateFlow(ActivityDetailUiState())
    private val notes = MutableStateFlow("")
    private var lastPersistedNotes: String? = null
    private var notesHydrated = false
    private val errors = ErrorSlot()
    val error: StateFlow<String?> = errors.messages

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val mutating = AtomicBoolean(false)
    private var loading: Job? = null

    val uiState: StateFlow<ActivityDetailUiState> = combine(load, notes) { loaded, typed ->
        loaded.copy(notes = typed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityDetailUiState(),
    )

    init {
        load()
        viewModelScope.launch {
            notes.collectLatest { value ->
                delay(NOTES_WRITE_DEBOUNCE_MS)
                writeNotes(value)
            }
        }
    }

    /** Re-read after a failed load. A no-op unless the last read actually threw. */
    fun retry() {
        if (!load.value.failed) return
        load()
    }

    fun setNotes(value: String) {
        notes.value = value
    }

    /** Flushes the debounce tail before this back-stack entry is removed. */
    fun persistNotesForExit() {
        viewModelScope.launch { writeNotes(notes.value) }
    }

    fun deleteSession() {
        if (!mutating.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                runCatchingCancellable { container.activityRepository.deleteCompleted(activityId) }
                    .onSuccess { write ->
                        when (write) {
                            is ActivityWrite.Accepted -> _deleted.value = true
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
                load.value = ActivityDetailUiState(isLoading = false, missing = true)
                return@launch
            }
            val session = checkNotNull(detail.value)
            val work = session.strengthWork()
            val cardioMinutes = session.cardioMinutes()
            lastPersistedNotes = session.notes
            if (!notesHydrated) {
                notesHydrated = true
                if (notes.value.isEmpty() && session.notes.isNotEmpty()) {
                    notes.value = session.notes
                }
            }
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

    private suspend fun writeNotes(value: String) {
        if (activityId.isBlank()) return
        val known = lastPersistedNotes ?: return
        if (value == known) return
        withContext(NonCancellable) {
            runCatchingCancellable {
                val write = container.activityRepository.updateCompletedNotes(
                    sessionId = activityId,
                    notes = value,
                    nowMs = container.time.nowMillis(),
                )
                when (write) {
                    is ActivityWrite.Accepted -> lastPersistedNotes = write.session.notes
                    is ActivityWrite.Rejected ->
                        AppLog.w(TAG, "Writing the activity notes was refused: ${write.reason}")
                }
            }.onFailure { thrown ->
                AppLog.w(TAG, "Writing the activity notes failed", thrown)
            }
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
