package com.sinura.personaltrainer.ui.schedule

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadCalculator
import com.sinura.personaltrainer.domain.RecommendationEngine
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.WeeklySchedulePlan
import com.sinura.personaltrainer.domain.WeeklySchedulePlanner
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZoneId

private const val TAG = "PT/ScheduleVM"

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val preferences: SchedulePreferences = SchedulePreferences.DEFAULT,
    val plan: WeeklySchedulePlan? = null,
    val inProgress: WorkoutSession? = null,
    val loggedEpochDays: Set<Long> = emptySet(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(application: Application) : AppViewModel(application) {
    private val refreshAt = MutableStateFlow(0L)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ScheduleUiState> = combine(
        combine(
            container.workoutRepository.observeHistory(),
            container.routineRepository.observeAll(),
            container.preferencesRepository.schedulePreferences,
            container.workoutRepository.observeInProgress(),
            refreshAt,
        ) { history, routines, prefs, inProgress, tick ->
            ScheduleInputs(history, routines, prefs, inProgress, tick)
        },
        container.preferencesRepository.weightUnit,
        error,
    ) { inputs, unit, _ ->
        inputs.copy(unit = unit)
    }.mapLatest { inputs ->
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val snapshot = try {
            MuscleLoadCalculator.snapshot(inputs.history, HeatWindow.LAST_7_DAYS, now, zone)
        } catch (thrown: Exception) {
            AppLog.w(TAG, "Computing the muscle heat snapshot failed", thrown)
            MuscleLoadCalculator.snapshot(emptyList(), HeatWindow.LAST_7_DAYS, now, zone)
        }
        val hints = try {
            container.workoutRepository.readyForProgression(inputs.routines)
        } catch (thrown: Exception) {
            AppLog.w(TAG, "Computing the progression hints failed", thrown)
            emptyList()
        }
        val recs = RecommendationEngine.recommend(snapshot, hints, inputs.unit)
        val plan = try {
            WeeklySchedulePlanner.plan(
                preferences = inputs.prefs,
                snapshot = snapshot,
                recommendations = recs,
                routines = inputs.routines,
                recentSessions = inputs.history,
                nowMs = now,
                zone = zone,
            )
        } catch (thrown: Exception) {
            AppLog.w(TAG, "Computing the weekly schedule plan failed", thrown)
            null
        }
        val logged = inputs.history
            .filter { it.isFinished }
            .map { java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate().toEpochDay() }
            .toSet()
        ScheduleUiState(
            isLoading = false,
            preferences = inputs.prefs,
            plan = plan,
            inProgress = inputs.inProgress,
            loggedEpochDays = logged,
            error = error.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(),
    )

    fun setTrainingDays(days: Int) {
        viewModelScope.launch { container.preferencesRepository.setTrainingDaysPerWeek(days) }
    }

    fun setSplit(style: SplitStyle) {
        viewModelScope.launch { container.preferencesRepository.setSplitStyle(style) }
    }

    fun setWeekStart(day: DayOfWeek) {
        viewModelScope.launch { container.preferencesRepository.setWeekStart(day) }
    }

    fun regenerate() {
        refreshAt.value = System.currentTimeMillis()
        error.value = null
    }

    fun startDay(day: SuggestedTrainingDay, onStarted: (String) -> Unit) {
        if (day.isRest) return
        viewModelScope.launch {
            val current = try {
                container.workoutRepository.getInProgress()
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startDay failed", thrown)
                null
            }
            if (current != null) {
                error.value = null
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
                error.value = null
                onStarted(session.id)
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startDay failed", thrown)
                error.value = "Could not start that day. Try again."
            }
        }
    }

    private data class ScheduleInputs(
        val history: List<WorkoutSession>,
        val routines: List<Routine>,
        val prefs: SchedulePreferences,
        val inProgress: WorkoutSession?,
        val tick: Long,
        val unit: WeightUnit = WeightUnit.KG,
    )
}
