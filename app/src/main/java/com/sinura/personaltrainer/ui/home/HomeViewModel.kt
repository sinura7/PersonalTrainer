package com.sinura.personaltrainer.ui.home

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadCalculator
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RecommendationEngine
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.WeeklySchedulePlan
import com.sinura.personaltrainer.domain.WeeklySchedulePlanner
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.WeightUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

private const val TAG = "PT/HomeVM"

data class HomeUiState(
    val isLoading: Boolean = true,
    val inProgress: WorkoutSession? = null,
    val routines: List<Routine> = emptyList(),
    val recentSessions: List<WorkoutSession> = emptyList(),
    val readyToProgress: List<ProgressionHint> = emptyList(),
    val heatSnapshot: BodyHeatSnapshot? = null,
    val recommendations: List<TrainingRecommendation> = emptyList(),
    val weekPlan: WeeklySchedulePlan? = null,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AppViewModel(application) {
    val restRemainingSeconds: StateFlow<Int> = container.restTimerController.remainingSeconds
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    private val actionError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        container.workoutRepository.observeInProgress(),
        container.routineRepository.observeAll(),
        container.workoutRepository.observeHistory(),
        container.preferencesRepository.schedulePreferences,
        container.preferencesRepository.weightUnit,
    ) { inProgress, routines, history, prefs, unit ->
        HomeInputs(inProgress, routines, history, prefs, unit)
    }.mapLatest { inputs ->
        val hints = try {
            container.workoutRepository.readyForProgression(inputs.routines)
        } catch (thrown: Exception) {
            AppLog.w(TAG, "Computing the progression hints failed", thrown)
            emptyList()
        }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val snapshot = try {
            MuscleLoadCalculator.snapshot(
                sessions = inputs.history,
                window = HeatWindow.LAST_7_DAYS,
                nowMs = now,
                zone = zone,
            )
        } catch (thrown: Exception) {
            AppLog.w(TAG, "Computing the muscle heat snapshot failed", thrown)
            null
        }
        val recommendations = if (snapshot != null) {
            RecommendationEngine.recommend(snapshot, hints, inputs.unit)
        } else {
            emptyList()
        }
        val weekPlan = if (snapshot != null) {
            try {
                WeeklySchedulePlanner.plan(
                    preferences = inputs.prefs,
                    snapshot = snapshot,
                    recommendations = recommendations,
                    routines = inputs.routines,
                    recentSessions = inputs.history,
                    nowMs = now,
                    zone = zone,
                )
            } catch (thrown: Exception) {
                AppLog.w(TAG, "Computing the weekly schedule plan failed", thrown)
                null
            }
        } else {
            null
        }
        HomeUiState(
            isLoading = false,
            inProgress = inputs.inProgress,
            routines = inputs.routines,
            recentSessions = inputs.history.take(3),
            readyToProgress = hints,
            heatSnapshot = snapshot,
            recommendations = recommendations.filterNot { rec ->
                rec.id == "progression-ready" && hints.isNotEmpty()
            },
            weekPlan = weekPlan,
            error = actionError.value,
        )
    }.combine(actionError) { state, err ->
        state.copy(error = err)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun startSuggestedDay(day: SuggestedTrainingDay, onStarted: (String) -> Unit) {
        if (day.isRest) return
        viewModelScope.launch {
            val current = try {
                container.workoutRepository.getInProgress()
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startSuggestedDay failed", thrown)
                null
            }
            if (current != null) {
                actionError.value = null
                onStarted(current.id)
                return@launch
            }
            try {
                val session = if (day.routineId != null) {
                    val routine = container.routineRepository.getById(day.routineId)
                    if (routine == null || routine.exercises.isEmpty()) {
                        container.workoutRepository.startFreeWorkout(day.focusTitle)
                    } else {
                        container.workoutRepository.startRoutine(routine)
                    }
                } else {
                    container.workoutRepository.startFreeWorkout(day.focusTitle)
                }
                actionError.value = null
                onStarted(session.id)
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startSuggestedDay failed", thrown)
                actionError.value = "Could not start that session. Try again."
            }
        }
    }

    private data class HomeInputs(
        val inProgress: WorkoutSession?,
        val routines: List<Routine>,
        val history: List<WorkoutSession>,
        val prefs: SchedulePreferences,
        val unit: WeightUnit,
    )
}
