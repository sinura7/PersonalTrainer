package com.sinura.personaltrainer.ui.home

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadCalculator
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RecommendationEngine
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.WorkoutSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

data class HomeUiState(
    val isLoading: Boolean = true,
    val inProgress: WorkoutSession? = null,
    val routines: List<Routine> = emptyList(),
    val recentSessions: List<WorkoutSession> = emptyList(),
    val readyToProgress: List<ProgressionHint> = emptyList(),
    val heatSnapshot: BodyHeatSnapshot? = null,
    val recommendations: List<TrainingRecommendation> = emptyList(),
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
        val snapshot = try {
            MuscleLoadCalculator.snapshot(
                sessions = history,
                window = HeatWindow.LAST_7_DAYS,
                nowMs = System.currentTimeMillis(),
                zone = ZoneId.systemDefault(),
            )
        } catch (_: Exception) {
            null
        }
        val recommendations = if (snapshot != null) {
            RecommendationEngine.recommend(snapshot, hints)
        } else {
            emptyList()
        }
        HomeUiState(
            isLoading = false,
            inProgress = inProgress,
            routines = routines,
            recentSessions = history.take(3),
            readyToProgress = hints,
            heatSnapshot = snapshot,
            recommendations = recommendations,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}
