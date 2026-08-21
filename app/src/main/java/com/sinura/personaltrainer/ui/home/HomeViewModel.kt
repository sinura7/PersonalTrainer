package com.sinura.personaltrainer.ui.home

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingRecommendation
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

class HomeViewModel(application: Application) : AppViewModel(application) {
    private val actionError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        container.trainingInsights.observe(),
        container.workoutRepository.observeInProgress(),
        actionError,
    ) { insights, inProgress, error ->
        HomeUiState(
            isLoading = false,
            inProgress = inProgress,
            routines = insights.routines,
            recentSessions = insights.history.take(3),
            readyToProgress = insights.hints,
            heatSnapshot = insights.snapshot,
            // Home already devotes a section to the ready-to-progress lifts, so the card that
            // only says "some lifts are ready" is noise next to the list naming them.
            recommendations = insights.recommendations.filterNot { rec ->
                rec.id == "progression-ready" && insights.hints.isNotEmpty()
            },
            weekPlan = insights.weekPlan,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

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

    fun startSuggestedDay(day: SuggestedTrainingDay) {
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
