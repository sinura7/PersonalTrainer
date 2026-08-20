package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.WorkoutSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SessionDetailUiState(
    val isLoading: Boolean = true,
    val session: WorkoutSession? = null,
)

class SessionDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AppViewModel(application) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()

    val uiState: StateFlow<SessionDetailUiState> = container.workoutRepository.observeSession(sessionId)
        .map { SessionDetailUiState(isLoading = false, session = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionDetailUiState(),
        )
}
