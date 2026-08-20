package com.sinura.personaltrainer.ui.schedule

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.InsightFailure
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.WeeklySchedulePlan
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.workout.StartDayOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val preferences: SchedulePreferences = SchedulePreferences.DEFAULT,
    val plan: WeeklySchedulePlan? = null,
    val inProgress: WorkoutSession? = null,
    val loggedEpochDays: Set<Long> = emptySet(),
    val error: String? = null,
)

class ScheduleViewModel(application: Application) : AppViewModel(application) {
    /** "Regenerate" re-runs the planner against the current clock; nothing else changes. */
    private val refreshAt = MutableStateFlow(0L)
    private val actionError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ScheduleUiState> = combine(
        container.trainingInsights.observe(refresh = refreshAt),
        container.workoutRepository.observeInProgress(),
        container.preferencesRepository.schedulePreferences,
        actionError,
    ) { insights, inProgress, preferences, error ->
        val zone = ZoneId.systemDefault()
        val logged = insights.history
            .filter { it.isFinished }
            .map { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate().toEpochDay() }
            .toSet()
        ScheduleUiState(
            isLoading = false,
            preferences = preferences,
            plan = insights.weekPlan,
            inProgress = inProgress,
            loggedEpochDays = logged,
            // A planner fault used to leave this screen showing "No week plan yet" with the
            // Generate button that had just failed, and no hint that anything went wrong.
            error = error ?: when {
                insights.failed(InsightFailure.PLAN) ->
                    "Couldn’t build this week’s plan. Try regenerating."
                insights.failed(InsightFailure.HEAT) ->
                    "Couldn’t read your recent training, so the week isn’t balanced to it."
                else -> null
            },
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
        actionError.value = null
    }

    /**
     * The session to open, held as state rather than passed as a callback.
     *
     * A navigation lambda captured into a viewModelScope coroutine closes over the
     * composition's NavController; if the Activity is recreated between the tap and the
     * database write completing, that controller is dead and the navigation is simply lost —
     * the workout starts but the screen never moves. A StateFlow survives recreation and is
     * re-read by the new composition.
     */
    private val _navigateToSession = MutableStateFlow<String?>(null)
    val navigateToSession: StateFlow<String?> = _navigateToSession.asStateFlow()

    fun onSessionNavigationHandled() {
        _navigateToSession.value = null
    }

    fun startDay(day: SuggestedTrainingDay) {
        viewModelScope.launch {
            when (val outcome = container.startTrainingDay(day)) {
                is StartDayOutcome.Open -> {
                    actionError.value = null
                    _navigateToSession.value = outcome.sessionId
                }
                is StartDayOutcome.Failed -> actionError.value = outcome.message
                StartDayOutcome.Ignored -> Unit
            }
        }
    }
}
