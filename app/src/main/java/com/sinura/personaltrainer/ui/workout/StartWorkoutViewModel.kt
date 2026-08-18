package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
            if (routine == null || routine.exercises.isEmpty()) {
                error.value = "Add at least one exercise before starting this routine."
                return@launch
            }
            val session = container.workoutRepository.startRoutine(routine)
            onStarted(session.id)
        }
    }

    fun startFree(onStarted: (String) -> Unit) {
        viewModelScope.launch {
            val session = container.workoutRepository.startFreeWorkout()
            onStarted(session.id)
        }
    }
}
