package com.sinura.personaltrainer.ui.home

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.WorkoutSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val isLoading: Boolean = true,
    val inProgress: WorkoutSession? = null,
    val routines: List<Routine> = emptyList(),
    val recentSessions: List<WorkoutSession> = emptyList(),
    val readyToProgress: List<ProgressionHint> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AppViewModel(application) {
    val uiState: StateFlow<HomeUiState> = combine(
        container.workoutRepository.observeInProgress(),
        container.routineRepository.observeAll(),
        container.workoutRepository.observeHistory(),
    ) { inProgress, routines, history ->
        Triple(inProgress, routines, history)
    }.mapLatest { (inProgress, routines, history) ->
        val hints = try {
            container.workoutRepository.readyForProgression(routines)
        } catch (_: Exception) {
            emptyList()
        }
        HomeUiState(
            isLoading = false,
            inProgress = inProgress,
            routines = routines,
            recentSessions = history.take(3),
            readyToProgress = hints,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}
