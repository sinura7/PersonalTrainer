package com.sinura.personaltrainer.ui.history

import android.app.Application
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.WorkoutSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

data class HistoryUiState(
    val isLoading: Boolean = true,
    val sessions: List<WorkoutSession> = emptyList(),
)

class HistoryViewModel(application: Application) : AppViewModel(application) {
    val uiState: StateFlow<HistoryUiState> = container.workoutRepository.observeHistory()
        .map { HistoryUiState(isLoading = false, sessions = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )
}
