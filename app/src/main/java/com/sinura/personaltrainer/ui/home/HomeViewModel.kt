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
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.workout.DiscardOutcome
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
    /**
     * Days that already hold a finished session, so the week strip can mark them.
     *
     * Derived from the whole history rather than [recentSessions], which is capped at three for
     * the stat row: a strip that only knew about the last three sessions would leave older days
     * in the current week looking untrained.
     */
    val loggedEpochDays: Set<Long> = emptySet(),
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
            loggedEpochDays = insights.history
                .filter { it.isFinished }
                .map { todayEpochDay(it.date) }
                .toSet(),
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

    /**
     * The day whose Start was refused because a session is already running.
     *
     * Held so the screen can ask, rather than the app deciding: silently opening whatever is in
     * progress is how tapping Thursday's Pull used to land you in Tuesday's Legs.
     */
    private val _blockedByInProgress = MutableStateFlow<BlockedStart?>(null)
    val blockedByInProgress: StateFlow<BlockedStart?> = _blockedByInProgress.asStateFlow()

    fun startSuggestedDay(day: SuggestedTrainingDay) {
        viewModelScope.launch { start(day) }
    }

    private suspend fun start(day: SuggestedTrainingDay) {
        when (val outcome = container.startTrainingDay(day)) {
            is StartDayOutcome.Open -> {
                actionError.value = null
                _navigateToSession.value = outcome.sessionId
            }
            is StartDayOutcome.Blocked ->
                _blockedByInProgress.value = BlockedStart(day = day, sessionId = outcome.inProgressSessionId)
            is StartDayOutcome.Failed -> actionError.value = outcome.message
            StartDayOutcome.Ignored -> Unit
        }
    }

    fun resumeBlocked() {
        val blocked = _blockedByInProgress.value ?: return
        _blockedByInProgress.value = null
        _navigateToSession.value = blocked.sessionId
    }

    fun discardBlockedAndStart() {
        val blocked = _blockedByInProgress.value ?: return
        _blockedByInProgress.value = null
        viewModelScope.launch {
            when (val result = container.discardWorkout(blocked.sessionId)) {
                DiscardOutcome.Discarded -> start(blocked.day)
                is DiscardOutcome.Failed -> actionError.value = result.message
            }
        }
    }

    fun dismissBlockedStart() {
        _blockedByInProgress.value = null
    }

    /**
     * Arms the Plan tab to preview a suggested week.
     *
     * The flag is app-scoped rather than a nav argument because the tap and the arrival are
     * separated by a navigation; the Plan tab consumes it once. Nothing is persisted by this —
     * accepting the preview is still the only thing that writes a slot.
     */
    fun requestWeekSuggestion() {
        container.pendingWeekSuggestion.value = true
    }

    data class BlockedStart(val day: SuggestedTrainingDay, val sessionId: String)
}
