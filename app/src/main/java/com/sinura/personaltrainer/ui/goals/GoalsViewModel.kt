package com.sinura.personaltrainer.ui.goals

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.DailyProjectionBuilder
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.GoalKind
import com.sinura.personaltrainer.domain.GoalPeriod
import com.sinura.personaltrainer.domain.GoalProgress
import com.sinura.personaltrainer.domain.GoalSnapshot
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toSummary
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalsUiState(
    val isLoading: Boolean = true,
    val snapshots: List<GoalSnapshot> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
    val error: String? = null,
)

class GoalsViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val actionError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<GoalsUiState> = combine(
        container.goalRepository.observeAll(),
        combine(
            container.workoutRepository.observeSessionSummaries(),
            container.activityRepository.observeCompleted(),
            container.workoutRepository.observeBestWorkingWeights(),
        ) { summaries, activities, bests -> GoalFacts(summaries, activities, bests) },
        combine(
            container.preferencesRepository.schedulePreferences,
            container.preferencesRepository.bodyweightLog,
            container.preferencesRepository.weightUnit,
            container.exerciseRepository.observeAll(),
        ) { preferences, log, unit, exercises ->
            GoalSettings(preferences.weekStart, preferences.trainingDaysPerWeek, log.lastOrNull()?.kg, unit, exercises)
        },
        actionError,
    ) { goals, facts, settings, error ->
        val projections = DailyProjectionBuilder.project(
            facts.summaries + facts.activities.filter { it.isCompleted }.map { it.toSummary() },
        )
        val today = CivilDate.fromEpochDay(todayEpochDay())
        GoalsUiState(
            isLoading = false,
            snapshots = goals.map { goal ->
                GoalProgress.measure(
                    goal = goal,
                    projections = projections,
                    today = today,
                    weekStart = settings.weekStart,
                    trainingDaysPerWeek = settings.trainingDaysPerWeek,
                    latestBodyweightKg = settings.latestBodyweightKg,
                    liftBestKg = goal.exerciseId?.let { facts.bestWeights[it] },
                )
            },
            exercises = settings.exercises,
            unit = settings.unit,
            error = error,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GoalsUiState(),
        )

    fun add(
        kind: GoalKind,
        targetValue: Double,
        period: GoalPeriod,
        exerciseId: String? = null,
        exerciseName: String? = null,
    ) {
        viewModelScope.launch {
            runCatchingCancellable {
                container.goalRepository.add(
                    kind = kind,
                    targetValue = targetValue,
                    period = period,
                    exerciseId = exerciseId,
                    exerciseName = exerciseName,
                )
            }
                .onSuccess { actionError.value = null }
                .onFailure { thrown ->
                    AppLog.w(TAG, "add goal failed", thrown)
                    actionError.value = "Could not save that goal. Try again."
                }
        }
    }

    fun setPaused(id: String, paused: Boolean) {
        viewModelScope.launch {
            runCatchingCancellable { container.goalRepository.setPaused(id, paused) }
                .onFailure { thrown ->
                    AppLog.w(TAG, "pause goal failed", thrown)
                    actionError.value = "Could not update that goal. Try again."
                }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatchingCancellable { container.goalRepository.delete(id) }
                .onFailure { thrown ->
                    AppLog.w(TAG, "delete goal failed", thrown)
                    actionError.value = "Could not delete that goal. Try again."
                }
        }
    }

    fun onErrorShown() {
        actionError.value = null
    }

    private data class GoalFacts(
        val summaries: List<com.sinura.personaltrainer.domain.SessionSummary>,
        val activities: List<com.sinura.personaltrainer.domain.ActivitySession>,
        val bestWeights: Map<String, Double>,
    )

    private data class GoalSettings(
        val weekStart: com.sinura.personaltrainer.domain.Weekday,
        val trainingDaysPerWeek: Int,
        val latestBodyweightKg: Double?,
        val unit: WeightUnit,
        val exercises: List<Exercise>,
    )
}

private const val TAG = "PT/GoalsVM"
