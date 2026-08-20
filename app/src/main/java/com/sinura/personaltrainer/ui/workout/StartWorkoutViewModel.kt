package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/StartWorkoutVM"

data class StartWorkoutUiState(
    val isLoading: Boolean = true,
    val inProgress: WorkoutSession? = null,
    val routines: List<Routine> = emptyList(),
    val error: String? = null,
)

class StartWorkoutViewModel(application: Application) : AppViewModel(application) {
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<StartWorkoutUiState> = combine(
        container.workoutRepository.observeInProgress(),
        container.routineRepository.observeAll(),
        error,
    ) { inProgress, routines, err ->
        StartWorkoutUiState(
            isLoading = false,
            inProgress = inProgress,
            routines = routines,
            error = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StartWorkoutUiState(),
    )

    fun startRoutine(routineId: String, onStarted: (String) -> Unit) {
        viewModelScope.launch {
            val routine = container.routineRepository.getById(routineId)
            if (routine == null) {
                error.value = "That routine is no longer available."
                return@launch
            }
            if (routine.exercises.isEmpty()) {
                error.value = "Add at least one exercise before starting this routine."
                return@launch
            }
            try {
                val session = container.workoutRepository.startRoutine(routine)
                error.value = null
                onStarted(session.id)
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startRoutine failed", thrown)
                error.value = "Could not start that routine. Try again."
            }
        }
    }

    fun startFree(onStarted: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val session = container.workoutRepository.startFreeWorkout()
                error.value = null
                onStarted(session.id)
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startFree failed", thrown)
                error.value = "Could not start a free workout. Try again."
            }
        }
    }
}
