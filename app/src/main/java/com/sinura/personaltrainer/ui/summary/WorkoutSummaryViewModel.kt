package com.sinura.personaltrainer.ui.summary

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.WorkoutSummary
import com.sinura.personaltrainer.domain.WorkoutSummaryBuilder
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PT/WorkoutSummaryVM"

/**
 * Four answers, each tied to what the read established.
 *
 * [missing] is a read that succeeded and found no row: the session is not on this phone.
 * [failed] is a read or a computation that threw: the session may well be there. They used to
 * be one flag, and the screen said "Workout saved. It is in your history." for both — which
 * for a missing row was untrue, and for a Room fault was a guess dressed as a receipt.
 *
 * [savedConfirmed] is the evidence for the word "saved": the finished row was read back from
 * Room during this load. It is true alongside [failed] when the row came back but the
 * history read or the summary build threw, which is the one case where "saved, summary
 * unavailable" is both honest and useful.
 */
data class WorkoutSummaryUiState(
    val isLoading: Boolean = true,
    val sessionId: String = "",
    val missing: Boolean = false,
    val failed: Boolean = false,
    val savedConfirmed: Boolean = false,
    val summary: WorkoutSummary = WorkoutSummary(),
)

/**
 * Reads once, not as a live flow.
 *
 * A finished session does not change while its summary is on screen, and the record check
 * behind it walks each lift's whole history — re-running that on every unrelated database
 * write would be pure waste. [retry] re-runs the same read; nothing here ever writes, so a
 * retry can never finish a workout twice or create one.
 */
class WorkoutSummaryViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()

    private val _uiState = MutableStateFlow(WorkoutSummaryUiState(sessionId = sessionId))
    val uiState: StateFlow<WorkoutSummaryUiState> = _uiState.asStateFlow()

    private var loading: Job? = null

    init {
        load()
    }

    /** Re-read after a failed load. A no-op unless the last read actually threw. */
    fun retry() {
        if (!_uiState.value.failed) return
        load()
    }

    private fun load() {
        loading?.cancel()
        _uiState.value = WorkoutSummaryUiState(sessionId = sessionId)
        loading = viewModelScope.launch {
            // Blank means the route was reached with no id at all. There is nothing to read
            // and so nothing to claim; the honest answer is the same as a row that is not there.
            if (sessionId.isBlank()) {
                _uiState.value = WorkoutSummaryUiState(sessionId = sessionId, isLoading = false, missing = true)
                return@launch
            }
            val session = runCatchingCancellable { container.workoutRepository.getSession(sessionId) }
                .getOrElse { thrown ->
                    AppLog.w(TAG, "Reading the finished session failed", thrown)
                    _uiState.value = WorkoutSummaryUiState(
                        sessionId = sessionId,
                        isLoading = false,
                        failed = true,
                        savedConfirmed = false,
                    )
                    return@launch
                }
            if (session == null) {
                _uiState.value = WorkoutSummaryUiState(sessionId = sessionId, isLoading = false, missing = true)
                return@launch
            }
            // The row is in hand: from here on "saved" is a fact, whatever the summary does.
            val saved = session.isFinished
            runCatchingCancellable {
                val exerciseIds = session.sets.filterNot { it.isWarmup }.map { it.exerciseId }
                val prior = container.workoutRepository.historyBefore(sessionId, exerciseIds)
                withContext(container.computeDispatcher) {
                    WorkoutSummaryBuilder.build(session, prior)
                }
            }.onSuccess { summary ->
                _uiState.value = WorkoutSummaryUiState(
                    sessionId = sessionId,
                    isLoading = false,
                    savedConfirmed = saved,
                    summary = summary,
                )
            }.onFailure { thrown ->
                AppLog.w(TAG, "Building the workout summary failed", thrown)
                // Never strand the user on a spinner because a summary would not compute — but
                // never call the row missing either. It was just read.
                _uiState.value = WorkoutSummaryUiState(
                    sessionId = sessionId,
                    isLoading = false,
                    failed = true,
                    savedConfirmed = saved,
                )
            }
        }
    }
}
