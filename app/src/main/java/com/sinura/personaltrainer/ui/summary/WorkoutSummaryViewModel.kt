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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PT/WorkoutSummaryVM"

data class WorkoutSummaryUiState(
    val isLoading: Boolean = true,
    /** The session is gone — discarded, or restored over between finishing and arriving here. */
    val missing: Boolean = false,
    val summary: WorkoutSummary = WorkoutSummary(),
)

/**
 * Reads once, not as a live flow.
 *
 * A finished session does not change while its summary is on screen, and the record check
 * behind it walks each lift's whole history — re-running that on every unrelated database
 * write would be pure waste.
 */
class WorkoutSummaryViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()

    private val _uiState = MutableStateFlow(WorkoutSummaryUiState())
    val uiState: StateFlow<WorkoutSummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatchingCancellable {
                val session = container.workoutRepository.getSession(sessionId)
                if (session == null) {
                    _uiState.value = WorkoutSummaryUiState(isLoading = false, missing = true)
                    return@runCatchingCancellable
                }
                val exerciseIds = session.sets.filterNot { it.isWarmup }.map { it.exerciseId }
                val prior = container.workoutRepository.historyBefore(sessionId, exerciseIds)
                val summary = withContext(container.computeDispatcher) {
                    WorkoutSummaryBuilder.build(session, prior)
                }
                _uiState.value = WorkoutSummaryUiState(isLoading = false, summary = summary)
            }.onFailure { thrown ->
                AppLog.w(TAG, "Building the workout summary failed", thrown)
                // Never strand the user on a spinner because a summary would not compute: the
                // workout is saved either way, and this screen is a celebration, not a gate.
                _uiState.value = WorkoutSummaryUiState(isLoading = false, missing = true)
            }
        }
    }
}
