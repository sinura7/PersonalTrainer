package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.Routine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/RoutinesVM"

data class RoutinesUiState(
    val isLoading: Boolean = true,
    val routines: List<Routine> = emptyList(),
    val error: String? = null,
)

class RoutinesViewModel(application: Application) : AppViewModel(application) {
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<RoutinesUiState> = combine(
        container.routineRepository.observeAll(),
        error,
    ) { routines, err ->
        RoutinesUiState(isLoading = false, routines = routines, error = err)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RoutinesUiState(),
    )

    fun delete(routineId: String) {
        viewModelScope.launch {
            try {
                container.routineRepository.delete(routineId)
                error.value = null
            } catch (thrown: Exception) {
                AppLog.w(TAG, "delete failed", thrown)
                error.value = "Could not delete that routine. Try again."
            }
        }
    }
}
