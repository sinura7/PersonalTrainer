package com.sinura.personaltrainer.ui.progress

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadCalculator
import com.sinura.personaltrainer.domain.RecommendationEngine
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.WorkoutSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

data class ProgressUiState(
    val isLoading: Boolean = true,
    val window: HeatWindow = HeatWindow.LAST_7_DAYS,
    val snapshot: BodyHeatSnapshot? = null,
    val recommendations: List<TrainingRecommendation> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(application: Application) : AppViewModel(application) {
    private val window = MutableStateFlow(HeatWindow.LAST_7_DAYS)

    val uiState: StateFlow<ProgressUiState> = combine(
        container.workoutRepository.observeHistory(),
        container.exerciseRepository.observeAll(),
        container.routineRepository.observeAll(),
        window,
    ) { history, exercises, routines, selectedWindow ->
        ProgressInputs(history, exercises.associateBy { it.id }, routines, selectedWindow)
    }.mapLatest { inputs ->
        val snapshot = try {
            MuscleLoadCalculator.snapshot(
                sessions = inputs.history,
                window = inputs.window,
                nowMs = System.currentTimeMillis(),
                zone = ZoneId.systemDefault(),
                exerciseCatalog = inputs.exercises,
            )
        } catch (_: Exception) {
            null
        }
        val hints = try {
            container.workoutRepository.readyForProgression(inputs.routines)
        } catch (_: Exception) {
            emptyList()
        }
        val recs = if (snapshot != null) {
            RecommendationEngine.recommend(snapshot, hints)
        } else {
            emptyList()
        }
        ProgressUiState(
            isLoading = false,
            window = inputs.window,
            snapshot = snapshot,
            recommendations = recs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProgressUiState(),
    )

    fun setWindow(value: HeatWindow) {
        window.value = value
    }

    private data class ProgressInputs(
        val history: List<WorkoutSession>,
        val exercises: Map<String, Exercise>,
        val routines: List<Routine>,
        val window: HeatWindow,
    )
}
