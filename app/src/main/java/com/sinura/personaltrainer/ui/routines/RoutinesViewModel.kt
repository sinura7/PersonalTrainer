package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.Routine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutinesUiState(
    val isLoading: Boolean = true,
    val routines: List<Routine> = emptyList(),
)

class RoutinesViewModel(application: Application) : AppViewModel(application) {
    val uiState: StateFlow<RoutinesUiState> = container.routineRepository.observeAll()
        .map { RoutinesUiState(isLoading = false, routines = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RoutinesUiState(),
        )

    fun delete(routineId: String) {
        viewModelScope.launch {
            container.routineRepository.delete(routineId)
        }
    }
}
